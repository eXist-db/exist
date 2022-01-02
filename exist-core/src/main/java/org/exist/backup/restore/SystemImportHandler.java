/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.backup.restore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentTypeImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.SecurityManager;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;
import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.annotation.Nullable;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

// TODO(AR) consider merging with org.exist.backup.restore.RestoreHandler

/**
 * Handler for parsing __contents__.xml files during
 * restoration of a db backup
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class SystemImportHandler extends DefaultHandler {
    
    private final static Logger LOG = LogManager.getLogger(SystemImportHandler.class);
    private final static SAXParserFactory saxFactory = ExistSAXParserFactory.getSAXParserFactory();
    static {
        saxFactory.setNamespaceAware(true);
        saxFactory.setValidating(false);
    }
    private static final int STRICT_URI_VERSION = 1;
    
    private final DBBroker broker;
    @Nullable private final Txn transaction;
    
    private final org.exist.backup.RestoreHandler rh;
    
    private final RestoreListener listener;
    private final BackupDescriptor descriptor;
    
    //handler state
    private int version = 0;
    private Collection currentCollection;
    private final Deque<DeferredPermission> deferredPermissions = new ArrayDeque<>();

    /**
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     */
    public SystemImportHandler(final DBBroker broker, @Nullable final Txn transaction, final BackupDescriptor descriptor, final RestoreListener listener) {
        this.broker = broker;
        this.transaction = transaction;
        this.listener = listener;
        this.descriptor = descriptor;
        
        rh = broker.getDatabase().getPluginsManager().getRestoreHandler();
    }

    /**
     * Either reuses the provided transaction
     * in a safe manner or starts a new transaction.
     */
    private Txn beginTransaction() {
        if (transaction == null) {
            return broker.continueOrBeginTransaction();
        }
        return new Txn.ReusableTxn(transaction);
    }

    @Override
    public void startDocument() throws SAXException {
        listener.processingDescriptor(descriptor.getSymbolicPath());
        rh.startDocument();
    }
    
    /**
     * @see  org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {

        //only process entries in the exist namespace
//        if(namespaceURI != null && !namespaceURI.equals(Namespaces.EXIST_NS)) {
//            return;
//        }

        if("collection".equals(localName) || "resource".equals(localName)) {
            
            final DeferredPermission df;
            
            if("collection".equals(localName)) {
                df = restoreCollectionEntry(atts);
            } else {
                df = restoreResourceEntry(atts);
            }
            
            deferredPermissions.push(df);
            
        } else if("subcollection".equals(localName)) {
            restoreSubCollectionEntry(atts);
        } else if("deleted".equals(localName)) {
            restoreDeletedEntry(atts);
        } else if("ace".equals(localName)) {
            addACEToDeferredPermissions(atts);
        } else {
        	rh.startElement(namespaceURI, localName, qName, atts);
        }
    }
    
    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        if(namespaceURI.equals(Namespaces.EXIST_NS) && ("collection".equals(localName) || "resource".equals(localName))) {
            setDeferredPermissions();
        }
        rh.endElement(namespaceURI, localName, qName);
        super.endElement(namespaceURI, localName, qName);
    }
    
    private DeferredPermission restoreCollectionEntry(final Attributes atts) throws SAXException {
        
        final String name = atts.getValue("name");
        
        if(name == null) {
            throw new SAXException("Collection requires a name attribute");
        }
        
        final String owner = atts.getValue("owner");
        final String group = atts.getValue("group");
        final String mode = atts.getValue("mode");
        final String created = atts.getValue("created");
        final String strVersion = atts.getValue("version");

        if(strVersion != null) {
            try {
                this.version = Integer.parseInt(strVersion);
            } catch(final NumberFormatException nfe) {
                final String msg = "Could not parse version number for Collection '" + name + "', defaulting to version 0";
                listener.warn(msg);
                LOG.warn(msg);
                
                this.version = 0;
            }
        }
        
        try {
            listener.createdCollection(name);
            final XmldbURI collUri;

            if(version >= STRICT_URI_VERSION) {
                collUri = XmldbURI.create(name);
            } else {
                try {
                    collUri = URIUtils.encodeXmldbUriFor(name);
                } catch(final URISyntaxException e) {
                    listener.warn("Could not parse document name into a URI: " + e.getMessage());
                    return new SkippedEntryDeferredPermission();
                }
            }

        	try(final Txn transaction = beginTransaction()) {
        		currentCollection = broker.getOrCreateCollection(transaction, collUri);
        		
        		rh.startCollectionRestore(currentCollection, atts);
        		
                broker.saveCollection(transaction, currentCollection);

                transaction.commit();
        	} catch (final Exception e) {
        		throw new SAXException(e);
    		}

            currentCollection = mkcol(collUri);
            
            if(currentCollection == null) {
                throw new SAXException("Collection not found: " + collUri);
            }
            
            final DeferredPermission deferredPermission;
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing System collection ownership
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection.getURI(), SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection.getURI(), owner, group, Integer.parseInt(mode, 8));
            }

            rh.endCollectionRestore(currentCollection);
            
            return deferredPermission;
            
        } catch(final Exception e) {
            final String msg = "An unrecoverable error occurred while restoring\ncollection '" + name + "': "  + e.getMessage() + ". Aborting restore!";
            LOG.error(msg, e);
            listener.warn(msg);
            throw new SAXException(msg, e);
        }
    }

    private void restoreSubCollectionEntry(final Attributes atts) throws SAXException {
        
        final String name;
        if(atts.getValue("filename") != null) {
            name = atts.getValue("filename");
        } else {
            name = atts.getValue("name");
        }
        
        //exclude /db/system collection and sub-collections, as these have already been restored
//        if ((currentCollection.getURI().startsWith(XmldbURI.SYSTEM)))
//            return;
        
        //parse the sub-collection descriptor and restore
        final BackupDescriptor subDescriptor = descriptor.getChildBackupDescriptor(name);
        if(subDescriptor != null) {

            final XMLReaderPool parserPool = broker.getBrokerPool().getParserPool();
            XMLReader reader = null;
            try {
                reader = parserPool.borrowXMLReader();

                final EXistInputSource is = subDescriptor.getInputSource();
                is.setEncoding(UTF_8.displayName());

                final SystemImportHandler handler = new SystemImportHandler(broker, transaction, subDescriptor, listener);

                reader.setContentHandler(handler);
                reader.parse(is);
            } catch(final SAXParseException e) {
                throw new SAXException("Could not process collection: " + descriptor.getSymbolicPath(name, false), e);
            } catch(final IOException ioe) {
                throw new SAXException("Could not read sub-collection for processing: " + ioe.getMessage(), ioe);
            } finally {
                if (reader != null) {
                    parserPool.returnXMLReader(reader);
                }
            }
        } else {
            listener.error("Collection " + descriptor.getSymbolicPath(name, false) + " does not exist or is not readable.");
        }
    }
    
    private DeferredPermission restoreResourceEntry(final Attributes atts) throws SAXException {
        @Nullable final String skip = atts.getValue( "skip" );

        // Don't process entries which should be skipped
        if (skip != null && !"no".equals(skip)) {
            return new SkippedEntryDeferredPermission();
        }

        @Nullable final String name = atts.getValue("name");
        if (name == null) {
            throw new SAXException("Resource requires a name attribute");
        }

        final boolean xmlType = Optional.ofNullable(atts.getValue("type")).filter(s -> s.equals("XMLResource")).isPresent();

        final String owner = getAttr(atts, "owner", SecurityManager.SYSTEM);
        final String group = getAttr(atts, "group", SecurityManager.DBA_GROUP);
        final String perms = getAttr(atts, "mode", "644");

        final String filename = getAttr(atts, "filename", name);

        @Nullable final String mimeTypeStr = atts.getValue("mimetype");
        @Nullable final String dateCreatedStr = atts.getValue("created");
        @Nullable final String dateModifiedStr = atts.getValue("modified");

        @Nullable final String publicId = atts.getValue("publicid");
        @Nullable final String systemId = atts.getValue("systemid");
        @Nullable final String nameDocType = atts.getValue("namedoctype");


        MimeType mimeType = null;
        if (mimeTypeStr != null) {
            mimeType = MimeTable.getInstance().getContentType(mimeTypeStr);
        }
        if (mimeType == null) {
            mimeType = xmlType ? MimeType.XML_TYPE : MimeType.BINARY_TYPE;
        }

        Date dateCreated = null;
        if (dateCreatedStr != null) {
            try {
                dateCreated = new DateTimeValue(dateCreatedStr).getDate();
            } catch (final XPathException xpe) {
                listener.warn("Illegal creation date. Ignoring date...");
            }
        }

        Date dateModified = null;
        if (dateModifiedStr != null) {
            try {
                dateModified = new DateTimeValue(dateModifiedStr).getDate();
            } catch (final XPathException xpe) {
                listener.warn("Illegal modification date. Ignoring date...");
            }
        }

        final DocumentType docType;
        if (publicId != null || systemId != null) {
            docType = new DocumentTypeImpl(nameDocType, publicId, systemId);
        } else {
            docType = null;
        }

        final XmldbURI docUri;
        if(version >= STRICT_URI_VERSION) {
            docUri = XmldbURI.create(name);
        } else {
            try {
                docUri = URIUtils.encodeXmldbUriFor(name);
            } catch(final URISyntaxException e) {
                final String msg = "Could not parse document name into a URI: " + e.getMessage();
                listener.error(msg);
                LOG.error(msg, e);
                return new SkippedEntryDeferredPermission();
            }
        }


        try (final EXistInputSource is = descriptor.getInputSource(filename)){

            if(is == null) {
                final String msg = "Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath( name, false ) + "'.\nReason: Unable to obtain its EXistInputSource";
                listener.warn(msg);
                throw new RuntimeException(msg);
            }

            try (final Txn transaction = beginTransaction()) {

                currentCollection.storeDocument(transaction, broker, docUri, is, mimeType, dateCreated, dateModified, null, docType, null);


                try (final LockedDocument doc = currentCollection.getDocumentWithLock(broker, docUri, Lock.LockMode.READ_LOCK)) {
                    rh.startDocumentRestore(doc.getDocument(), atts);
                }

                transaction.commit();

                final DeferredPermission deferredPermission;
                if (name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                    //prevents restore of a backup from changing system collection resource ownership
                    deferredPermission = new ResourceDeferredPermission(listener, currentCollection.getURI().append(name), SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(perms, 8));
                } else {
                    deferredPermission = new ResourceDeferredPermission(listener, currentCollection.getURI().append(name), owner, group, Integer.parseInt(perms, 8));
                }

                try (final LockedDocument doc = currentCollection.getDocumentWithLock(broker, docUri, Lock.LockMode.READ_LOCK)) {
                    rh.endDocumentRestore(doc.getDocument());
                }

                listener.restoredResource(name);
                
                return deferredPermission;
			} catch (final Exception e) {
                throw new IOException(e);
            }

        } catch(final Exception e) {
            listener.warn("Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath(name, false) + "'.\nReason: " + e.getMessage());
            LOG.error(e.getMessage(), e);
            return new SkippedEntryDeferredPermission();
        }
    }

    private void restoreDeletedEntry(final Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");
        
        if("collection".equals(type)) {

        	try {
		        final Collection col = broker.getCollection(currentCollection.getURI().append(name));
		        if(col != null) {
		        	//delete
		        	try(final Txn transaction = beginTransaction()) {
		                broker.removeCollection(transaction, col);
                        transaction.commit();
		        	} catch (final Exception e) {
		                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
                    }
		        }
        	} catch (final Exception e) {
                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
			}

        } else if("resource".equals(type)) {

        	try {
	        	final XmldbURI uri = XmldbURI.create(name);
	        	final DocumentImpl doc = currentCollection.getDocument(broker, uri);
	        	
	        	if (doc != null) {
		            try(final Txn transaction = beginTransaction()) {
		            	if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
		                	currentCollection.removeBinaryResource(transaction, broker, uri);
		            	} else {
		            		currentCollection.removeXMLResource(transaction, broker, uri);
		            	}
                        transaction.commit();
	
		            } catch(final Exception e) {
		                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
		            }
                }
        	} catch (final Exception e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
			}
        }
    }

    private void addACEToDeferredPermissions(final Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACE_TARGET target = ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACE_ACCESS_TYPE access_type = ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);

        deferredPermissions.peek().addACE(index, target, who, access_type, mode);
    }

    private void setDeferredPermissions() {
        final DeferredPermission deferredPermission = deferredPermissions.pop();
        try (final Txn transaction = beginTransaction()) {
            deferredPermission.apply(broker, transaction);

            transaction.commit();
        } catch (final TransactionException e) {
            final String msg = "ERROR: Failed to set permissions on: '" + deferredPermission.getTarget() + "'.";
            LOG.error(msg, e);
            listener.warn(msg);
        }
    }
    
    private Date getDateFromXSDateTimeStringForItem(final String strXSDateTime, final String itemName) {
        Date date_created = null;

        if(strXSDateTime != null) {
            try {
                date_created = new DateTimeValue(strXSDateTime).getDate();
            } catch(final XPathException e2) {
            }
        }

        if(date_created == null) {
            final String msg = "Could not parse created date '" + strXSDateTime + "' from backup for: '" + itemName + "', using current time!";
            listener.error(msg);
            LOG.error(msg);

            date_created = Calendar.getInstance().getTime();
        }

        return date_created;
    }
    
    private Collection mkcol(final XmldbURI collPath) throws SAXException {
    	try(final Txn transaction = beginTransaction()) {
    		final Collection col = broker.getOrCreateCollection(transaction, collPath);

            transaction.commit();
    		
    		return col;
    	} catch (final Exception e) {
    		throw new SAXException(e);
		}
    }

    private static String getAttr(final Attributes atts, final String name, final String fallback) {
        final String value = atts.getValue(name);
        if (value == null) {
            return fallback;
        }
        return value;
    }
}
