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
import org.exist.dom.QName;
import org.exist.security.*;
import org.exist.util.LockException;
import org.exist.util.Configuration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.util.serializer.Receiver;
import org.exist.EXistException;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.XPathException;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;

import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.File;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.StringWriter;
import java.util.List;
import java.util.Iterator;
import java.util.Properties;
import java.util.Date;

public class VersioningTrigger extends FilteringTrigger {

    public final static Logger LOG = Logger.getLogger(VersioningTrigger.class);

    public final static XmldbURI VERSIONS_COLLECTION = XmldbURI.SYSTEM_COLLECTION_URI.append("versions");

    public final static String BASE_SUFFIX = ".base";
    public final static String TEMP_SUFFIX = ".tmp";
    public final static String DELETED_SUFFIX = ".deleted";

    public final static QName ELEMENT_VERSION = new QName("version", XMLDiff.NAMESPACE, XMLDiff.PREFIX);
    public final static QName ELEMENT_REMOVED = new QName("removed", XMLDiff.NAMESPACE, XMLDiff.PREFIX);
    public final static QName PROPERTIES_ELEMENT = new QName("properties", XMLDiff.NAMESPACE, XMLDiff.PREFIX);

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

                String existingURI = existingDocument.getFileURI().toString();
                XmldbURI baseURI = XmldbURI.create(existingURI + BASE_SUFFIX);
                DocumentImpl baseRev = vCollection.getDocument(broker, baseURI);

                String vFileName;
                if (baseRev == null) {
                    vFileName = baseURI.toString();
                    removeLast = false;
                } else if (event == REMOVE_DOCUMENT_EVENT) {
                    vFileName = existingURI + DELETED_SUFFIX;
                    removeLast = false;
                } else {
                    vFileName = existingURI + TEMP_SUFFIX;
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

                if (event != REMOVE_DOCUMENT_EVENT)
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
        User activeUser = broker.getUser();
        try {
            broker.setUser(org.exist.security.SecurityManager.SYSTEM_USER);
            if (event == STORE_DOCUMENT_EVENT) {
                try {
                    Collection collection = document.getCollection();
                    if (collection.getURI().startsWith(VERSIONS_COLLECTION))
                        return;
                    vCollection = getCollection(broker, transaction, documentPath.removeLastSegment());

                    String existingURI = document.getFileURI().toString();
                    XmldbURI deletedURI = XmldbURI.create(existingURI + DELETED_SUFFIX);
                    lastRev = vCollection.getDocument(broker, deletedURI);
                    if (lastRev == null) {
                        lastRev = vCollection.getDocument(broker, XmldbURI.create(existingURI + BASE_SUFFIX));
                        removeLast = false;
                    } else
                        removeLast = true;
                } catch (IOException e) {
                    LOG.warn("Caught exception in VersioningTrigger: " + e.getMessage(), e);
                } catch (PermissionDeniedException e) {
                    LOG.warn("Permission denied in VersioningTrigger: " + e.getMessage(), e);
                }
            }
            if (lastRev != null || event == REMOVE_DOCUMENT_EVENT) {
                XMLDiff diff = new XMLDiff(broker);
                if (documentPath.isCollectionPathAbsolute())
                    documentPath = documentPath.lastSegment();
                DocumentImpl base = null;
                try {
                    vCollection.setTriggersEnabled(false);

                    long rev = newRevision(broker.getBrokerPool());
                    Properties properties = new Properties();
                    properties.setProperty("document", documentPath.toString());
                    properties.setProperty("revision", Long.toString(rev));
                    properties.setProperty("date", new DateTimeValue(new Date()).getStringValue());
                    properties.setProperty("user", broker.getUser().getName());
                    StringWriter writer = new StringWriter();
                    SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                            SAXSerializer.class);
                    Properties outputProperties = new Properties();
                    outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
                    outputProperties.setProperty(OutputKeys.INDENT, "no");
                    sax.setOutput(writer, outputProperties);
                    sax.startDocument();

                    sax.startElement(ELEMENT_VERSION, null);
                    writeProperties(sax, properties);
                    if (event == REMOVE_DOCUMENT_EVENT) {
                        sax.startElement(ELEMENT_REMOVED, null);
                        sax.endElement(ELEMENT_REMOVED);
                    } else {
                        diff.diff(lastRev, document);
                        diff.diff2XML(sax);
                    }
                    sax.endElement(ELEMENT_VERSION);

                    sax.endDocument();
                    String editscript = writer.toString();

//                    System.out.println("documentPath: " + documentPath);
//                    System.out.println(editscript);

                    if (removeLast)
                        vCollection.removeXMLResource(transaction, broker, lastRev.getFileURI());

                    XmldbURI diffUri = XmldbURI.createInternal(documentPath.toString() + '.' + rev);
                    IndexInfo info = vCollection.validateXMLResource(transaction, broker, diffUri, editscript);
                    vCollection.store(transaction, broker, info, editscript, false);
                } catch (Exception e) {
                    LOG.warn("Caught exception in VersioningTrigger: " + e.getMessage(), e);
                } finally {
                    if (base != null)
                        base.getUpdateLock().release(Lock.READ_LOCK);
                    vCollection.setTriggersEnabled(true);
                }
            }
        } finally {
            broker.setUser(activeUser);
        }
    }

    protected void writeProperties(Receiver receiver, Properties properties) throws SAXException {
        receiver.startElement(PROPERTIES_ELEMENT, null);
        for (Iterator i = properties.keySet().iterator(); i.hasNext();) {
            String key = (String) i.next();
            QName qn = new QName(key, XMLDiff.NAMESPACE, XMLDiff.PREFIX);
            receiver.startElement(qn, null);
            receiver.characters(properties.get(key).toString());
            receiver.endElement(qn);
        }
        receiver.endElement(PROPERTIES_ELEMENT);
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
