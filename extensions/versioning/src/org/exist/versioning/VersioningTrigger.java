/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.versioning;

import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.storage.DBBroker;
import org.exist.storage.BrokerPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.exist.dom.DocumentImpl;
import org.exist.security.*;
import org.exist.util.LockException;
import org.exist.util.Configuration;
import org.exist.EXistException;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.XPathException;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.Iterator;
import java.util.Properties;
import java.util.Date;

public class VersioningTrigger extends FilteringTrigger {

    public final static Logger LOG = Logger.getLogger(VersioningTrigger.class);

    public final static XmldbURI VERSIONS_COLLECTION = XmldbURI.SYSTEM_COLLECTION_URI.append("versions");

    public final static String BASE_SUFFIX = ".base";
    public final static String TEMP_SUFFIX = ".tmp";
    
    private final static Object latch = new Object();

    private DocumentImpl lastRev = null;
    private boolean removeLast = false;
    private Collection vCollection;

    public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument)
    throws TriggerException {
        User activeUser = broker.getUser();
        try {
            broker.setUser(org.exist.security.SecurityManager.SYSTEM_USER);
            if (event == UPDATE_DOCUMENT_EVENT || event == REMOVE_DOCUMENT_EVENT) {
                existingDocument.getMetadata().setReferenced(true);
                Collection collection = existingDocument.getCollection();
                if (collection.getURI().startsWith(VERSIONS_COLLECTION))
                    return;
                vCollection = getCollection(broker, transaction, documentPath.removeLastSegment());
                XmldbURI baseURI = XmldbURI.create(existingDocument.getFileURI().toString() + BASE_SUFFIX);
                DocumentImpl baseRev = vCollection.getDocument(broker, baseURI);
                String vFileName;
                if (baseRev == null) {
                    vFileName = baseURI.toString();
                    removeLast = false;
                } else {
                    vFileName = existingDocument.getFileURI().toString() + TEMP_SUFFIX;
                    removeLast = true;
                }

                DocumentImpl vDoc = new DocumentImpl(broker.getBrokerPool(), vCollection, XmldbURI.createInternal(vFileName));
                try {
                    vDoc.getUpdateLock().acquire(Lock.WRITE_LOCK);
                    vDoc.copyOf(existingDocument);
                    vDoc.copyChildren(existingDocument);
                    vCollection.addDocument(transaction, broker, vDoc);
                    broker.storeXMLResource(transaction, vDoc);
                } finally {
                    vDoc.getUpdateLock().release(Lock.WRITE_LOCK);
                }
                lastRev = vDoc;
            }
        } catch (PermissionDeniedException e) {
            throw new TriggerException("Permission denied in VersioningTrigger: " + e.getMessage(), e);
        } catch (Exception e) {
            LOG.warn("Caught exception in VersioningTrigger: " + e.getMessage(), e);
        } finally {
            broker.setUser(activeUser);
        }
    }

    public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
        if (lastRev != null) {
            XMLDiff diff = new XMLDiff(broker);
            DocumentImpl base = null;
            try {
                vCollection.setTriggersEnabled(false);

                long rev = newRevision(broker.getBrokerPool());
                Properties properties = new Properties();
                properties.setProperty("document", document.getFileURI().toString());
                properties.setProperty("revision", Long.toString(rev));
                properties.setProperty("date", new DateTimeValue(new Date()).getStringValue());
                String editscript = diff.diff(lastRev, document, properties);

                XmldbURI diffUri = XmldbURI.createInternal(document.getFileURI().toString() + '.' + rev);
                IndexInfo info = vCollection.validateXMLResource(transaction, broker, diffUri, editscript);
                vCollection.store(transaction, broker, info, editscript, false);
                
                System.out.println(editscript);

                if (removeLast)
                    vCollection.removeXMLResource(transaction, broker, lastRev.getFileURI());
            } catch (Exception e) {
                LOG.warn("Caught exception in VersioningTrigger: " + e.getMessage(), e);
            } finally {
                if (base != null)
                    base.getUpdateLock().release(Lock.READ_LOCK);
                vCollection.setTriggersEnabled(true);
            }
        }
    }

    private Collection getCollection(DBBroker broker, Txn transaction, XmldbURI collectionPath) throws IOException, PermissionDeniedException {
        XmldbURI path = VERSIONS_COLLECTION.append(collectionPath);
        Collection collection = broker.openCollection(path, Lock.WRITE_LOCK);
        if (collection == null) {
            if(LOG.isDebugEnabled())
                LOG.debug("Creating versioning collection: " + path);
            collection = broker.getOrCreateCollection(transaction, path);
            broker.saveCollection(transaction, collection);
        } else {
            transaction.registerLock(collection.getLock(), Lock.WRITE_LOCK);
        }
        return collection;
    }

    private DocumentImpl getLastRevision(DBBroker broker, Collection vCollection, XmldbURI documentPath) {
        String docName = documentPath.lastSegment().toString();
        long lastRev = -1;
        DocumentImpl lastDoc = null;
        for (Iterator i = vCollection.iterator(broker); i.hasNext(); ) {
            DocumentImpl doc = (DocumentImpl) i.next();
            String fname = doc.getFileURI().toString();
            if (fname.startsWith(docName)) {
                int p = fname.lastIndexOf('.');
                if (p > -1) {
                    String revStr = fname.substring(p + 1);
                    try {
                        long rev = Long.parseLong(revStr);
                        if (rev > lastRev) {
                            lastRev = rev;
                            lastDoc = doc;
                        }
                    } catch (NumberFormatException e) {
                    }
                }
            }
        }
        return lastDoc;
    }

    private long newRevision(BrokerPool pool) {
        String dataDir = (String) pool.getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR);
        synchronized (latch) {
            File f = new File(dataDir, "versions.dbx");
            long rev = 0;
            if (f.canRead()) {
                try {
                    DataInputStream is = new DataInputStream(new FileInputStream(f));
                    rev = is.readLong();
                    is.close();
                } catch (FileNotFoundException e) {
                    LOG.warn("Failed to read versions.dbx: " + e.getMessage(), e);
                } catch (IOException e) {
                    LOG.warn("Failed to read versions.dbx: " + e.getMessage(), e);
                }
            }
            ++rev;
            try {
                DataOutputStream os = new DataOutputStream(new FileOutputStream(f));
                os.writeLong(rev);
                os.close();
            } catch (FileNotFoundException e) {
                LOG.warn("Failed to write versions.dbx: " + e.getMessage(), e);
            } catch (IOException e) {
                LOG.warn("Failed to write versions.dbx: " + e.getMessage(), e);
            }
            return rev;
        }
    }
}
