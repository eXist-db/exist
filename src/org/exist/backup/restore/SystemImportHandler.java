/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  $Id$
 */
package org.exist.backup.restore;

import java.io.IOException;

import org.w3c.dom.DocumentType;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentMetadata;
import org.exist.dom.DocumentTypeImpl;
import org.exist.security.ACLPermission;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.util.EXistInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.URIUtils;
import org.exist.xquery.value.DateTimeValue;

import java.net.URISyntaxException;
import java.util.Calendar;
import java.util.Date;
import java.util.Observable;
import java.util.Stack;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.apache.log4j.Logger;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.internal.aider.ACEAider;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.xml.sax.XMLReader;

/**
 * Handler for parsing __contents.xml__ files during
 * restoration of a db backup
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author  Adam Retter <adam@exist-db.org>
 */
public class SystemImportHandler extends DefaultHandler {
    
    private final static Logger LOG = Logger.getLogger(SystemImportHandler.class);
    private final static SAXParserFactory saxFactory = SAXParserFactory.newInstance();
    static {
        saxFactory.setNamespaceAware(true);
        saxFactory.setValidating(false);
    }
    private static final int STRICT_URI_VERSION = 1;
    
    private DBBroker broker;
    
    private org.exist.backup.RestoreHandler rh;
    
    private final RestoreListener listener;
    private final String dbBaseUri;
    private final BackupDescriptor descriptor;
    
    //handler state
    private int version = 0;
    private Collection currentCollection;
    private Stack<DeferredPermission> deferredPermissions = new Stack<DeferredPermission>();
    
    public SystemImportHandler(DBBroker broker, RestoreListener listener, String dbBaseUri, BackupDescriptor descriptor) {
        this.broker = broker;
        this.listener = listener;
        this.dbBaseUri = dbBaseUri;
        this.descriptor = descriptor;
        
        rh = broker.getDatabase().getPluginsManager().getRestoreHandler();
    }

    @Override
    public void startDocument() throws SAXException {
        listener.setCurrentBackup(descriptor.getSymbolicPath());
        rh.startDocument();
    }
    
    /**
     * @see  org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String namespaceURI, String localName, String qName, Attributes atts) throws SAXException {

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
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {

        if(namespaceURI.equals(Namespaces.EXIST_NS) && ("collection".equals(localName) || "resource".equals(localName))) {
            setDeferredPermissions();
        }
        
        rh.endElement(namespaceURI, localName, qName);

        super.endElement(namespaceURI, localName, qName);
    }
    
    private DeferredPermission restoreCollectionEntry(Attributes atts) throws SAXException {
        
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
            listener.createCollection(name);
            XmldbURI collUri;

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

        	final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
        	final Txn txn = txnManager.beginTransaction();
        	try {
        		currentCollection = broker.getOrCreateCollection(txn, collUri);
        		
        		rh.startCollectionRestore(currentCollection, atts);
        		
                broker.saveCollection(txn, currentCollection);

        		txnManager.commit(txn);
        	} catch (final Exception e) {
        		txnManager.abort(txn);
        		throw new SAXException(e);
    		} finally {
                txnManager.close(txn);
            }

            currentCollection = mkcol(collUri, getDateFromXSDateTimeStringForItem(created, name));

            listener.setCurrentCollection(name);
            
            if(currentCollection == null) {
                throw new SAXException("Collection not found: " + collUri);
            }
            
            final DeferredPermission deferredPermission;
            if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                //prevents restore of a backup from changing System collection ownership
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(mode, 8));
            } else {
                deferredPermission = new CollectionDeferredPermission(listener, currentCollection, owner, group, Integer.parseInt(mode, 8));
            }

            rh.endCollectionRestore(currentCollection);
            
            return deferredPermission;
            
        } catch(final Exception e) {
            final String msg = "An unrecoverable error occurred while restoring\ncollection '" + name + "'. " + "Aborting restore!";
            LOG.error(msg, e);
            listener.warn(msg);
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void restoreSubCollectionEntry(Attributes atts) throws SAXException {
        
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
            
            final SAXParser sax;
            try { 
                sax = saxFactory.newSAXParser();
            
                final XMLReader reader = sax.getXMLReader();

                final EXistInputSource is = subDescriptor.getInputSource();
                is.setEncoding( "UTF-8" );

                final SystemImportHandler handler = new SystemImportHandler(broker, listener, dbBaseUri, subDescriptor);

                reader.setContentHandler(handler);
                reader.parse(is);
            } catch(final SAXParseException e) {
                throw new SAXException("Could not process collection: " + descriptor.getSymbolicPath(name, false), e);
            } catch(final ParserConfigurationException pce) {
                throw new SAXException("Could not initalise SAXParser for processing sub-collection: " + descriptor.getSymbolicPath(name, false), pce);
            } catch(final IOException ioe) {
                throw new SAXException("Could not read sub-collection for processing: " + ioe.getMessage(), ioe);
            }
        } else {
            listener.error("Collection " + descriptor.getSymbolicPath(name, false) + " does not exist or is not readable.");
        }
    }
    
    private DeferredPermission restoreResourceEntry(Attributes atts) throws SAXException {
        
        final String skip = atts.getValue( "skip" );

        //dont process entries which should be skipped
        if(skip != null && !"no".equals(skip)) {
            return new SkippedEntryDeferredPermission();
        }
        
        final String name = atts.getValue("name");
        if(name == null) {
            throw new SAXException("Resource requires a name attribute");
        }
        
        final String type;
        if(atts.getValue("type") != null) {
            type = atts.getValue("type");
        } else {
            type = "XMLResource";
        }
        
        final String owner = atts.getValue("owner");
        final String group = atts.getValue("group");
        final String perms = atts.getValue("mode");

        final String filename;
        if(atts.getValue("filename") != null) {
            filename = atts.getValue("filename");
        } else  {
            filename = name;
        }

        final String mimetype = atts.getValue("mimetype");
        final String created = atts.getValue("created");
        final String modified = atts.getValue("modified");

        final String publicid = atts.getValue("publicid");
        final String systemid = atts.getValue("systemid");
        final String namedoctype = atts.getValue("namedoctype");


        Date date_created = null;
        Date date_modified = null;

        if(created != null) {
            try {
                date_created = (new DateTimeValue(created)).getDate();
            } catch(final XPathException xpe) {
                listener.warn("Illegal creation date. Ignoring date...");
            }
        }
        if(modified != null) {
            try {
                date_modified = (Date) (new DateTimeValue(modified)).getDate();
            } catch(final XPathException xpe) {
                listener.warn("Illegal modification date. Ignoring date...");
            }
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

        final EXistInputSource is = descriptor.getInputSource(filename);
        if(is == null) {
            final String msg = "Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath( name, false ) + "'.\nReason: Unable to obtain its EXistInputSource";
            listener.warn(msg);
            throw new RuntimeException(msg);
        }

        try {
            
            listener.setCurrentResource(name);
            if(currentCollection instanceof Observable) {
                listener.observe((Observable)currentCollection);
            }

			final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
			final Txn txn = txnManager.beginTransaction();
	
			DocumentImpl resource = null;
			try {
				if ("XMLResource".equals(type)) {
					// store as xml resource
					
					final IndexInfo info = currentCollection.validateXMLResource(txn, broker, docUri, is);
					
					resource = info.getDocument();
					final DocumentMetadata meta = resource.getMetadata();
					meta.setMimeType(mimetype);
					meta.setCreated(date_created.getTime());
					meta.setLastModified(date_modified.getTime());
					
	                if((publicid != null) || (systemid != null)) {
	                	final DocumentType docType = new DocumentTypeImpl(namedoctype, publicid, systemid);
	                	meta.setDocType(docType);
	                }

					rh.startDocumentRestore(resource, atts);

					currentCollection.store(txn, broker, info, is, false);
	
				} else {
					// store as binary resource
					resource = currentCollection.validateBinaryResource(txn, broker, docUri, is.getByteStream(), mimetype, is.getByteStreamLength() , date_created, date_modified);
					
					rh.startDocumentRestore(resource, atts);

					resource = currentCollection.addBinaryResource(txn, broker, (BinaryDocument)resource, is.getByteStream(), mimetype, is.getByteStreamLength() , date_created, date_modified);
				}

				txnManager.commit(txn);

                final DeferredPermission deferredPermission;
                if(name.startsWith(XmldbURI.SYSTEM_COLLECTION)) {
                    //prevents restore of a backup from changing system collection resource ownership
                    deferredPermission = new ResourceDeferredPermission(listener, resource, SecurityManager.SYSTEM, SecurityManager.DBA_GROUP, Integer.parseInt(perms, 8));
                } else {
                    deferredPermission = new ResourceDeferredPermission(listener, resource, owner, group, Integer.parseInt(perms, 8));
                }
                
                rh.endDocumentRestore(resource);

                listener.restored(name);
                
                return deferredPermission;
			} catch (final Exception e) {
				txnManager.abort(txn);
				throw new IOException(e);
			} finally {
                txnManager.close(txn);
//				if (resource != null)
//					resource.getUpdateLock().release(Lock.READ_LOCK);
			}

        } catch(final Exception e) {
            listener.warn("Failed to restore resource '" + name + "'\nfrom file '" + descriptor.getSymbolicPath(name, false) + "'.\nReason: " + e.getMessage());
            LOG.error(e.getMessage(), e);
            return new SkippedEntryDeferredPermission();
        } finally {
            is.close();
        }
    }

    private void restoreDeletedEntry(Attributes atts) {
        final String name = atts.getValue("name");
        final String type = atts.getValue("type");
        
        if("collection".equals(type)) {

        	try {
		        final Collection col = broker.getCollection(currentCollection.getURI().append(name));
		        if(col != null) {
		        	//delete
		        	final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
		        	final Txn txn = txnManager.beginTransaction();
		        	try {
		                broker.removeCollection(txn, col);
		        		txnManager.commit(txn);
		        	} catch (final Exception e) {
		        		txnManager.abort(txn);
		        		
		                listener.warn("Failed to remove deleted collection: " + name + ": " + e.getMessage());
					} finally {
                        txnManager.close(txn);
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
	        		final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
	        		final Txn txn = txnManager.beginTransaction();
		            try {
		            	
		            	if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
		                	currentCollection.removeBinaryResource(txn, broker, uri);
		            	} else {
		            		currentCollection.removeXMLResource(txn, broker, uri);
		            	}
		            	txnManager.commit(txn);
	
		            } catch(final Exception e) {
		            	txnManager.abort(txn);
		            	
		                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
		            } finally {
                        txnManager.close(txn);
                    }
                }
        	} catch (final Exception e) {
                listener.warn("Failed to remove deleted resource: " + name + ": " + e.getMessage());
			}
        }
    }

    private void addACEToDeferredPermissions(Attributes atts) {
        final int index = Integer.parseInt(atts.getValue("index"));
        final ACE_TARGET target = ACE_TARGET.valueOf(atts.getValue("target"));
        final String who = atts.getValue("who");
        final ACE_ACCESS_TYPE access_type = ACE_ACCESS_TYPE.valueOf(atts.getValue("access_type"));
        final int mode = Integer.parseInt(atts.getValue("mode"), 8);

        deferredPermissions.peek().addACE(index, target, who, access_type, mode);
    }

    private void setDeferredPermissions() {
        
        final DeferredPermission deferredPermission = deferredPermissions.pop();
        deferredPermission.apply();
    }
    
    private Date getDateFromXSDateTimeStringForItem(String strXSDateTime, String itemName) {
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
    
    private Collection mkcol(XmldbURI collPath, Date created) throws SAXException {
        
    	final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
    	final Txn txn = txnManager.beginTransaction();
    	try {
    		final Collection col = broker.getOrCreateCollection(txn, collPath);
    		
    		txnManager.commit(txn);
    		
    		return col;
    	} catch (final Exception e) {
    		txnManager.abort(txn);
    		throw new SAXException(e);
		} finally {
            txnManager.close(txn);
        }
    }
    
    class CollectionDeferredPermission extends AbstractDeferredPermission<Collection> {
        
        public CollectionDeferredPermission(RestoreListener listener, Collection collection, String owner, String group, Integer mode) {
            super(listener, collection, owner, group, mode);
        }

        @Override
        public void apply() {
            try {
            	getTarget().getLock().acquire(Lock.WRITE_LOCK);

                final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
                final Txn txn = txnManager.beginTransaction();
            	try {
                    final Permission permission = getTarget().getPermissions();
	                permission.setOwner(getOwner());
	                permission.setGroup(getGroup());
	                permission.setMode(getMode());
	                if(permission instanceof ACLPermission) {
	                    final ACLPermission aclPermission = (ACLPermission)permission;
	                    aclPermission.clear();
	                    for(final ACEAider ace : getAces()) {
	                        aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
	                    }
	                }
	                broker.saveCollection(txn, getTarget());
	                
	                txnManager.commit(txn);
                
            	} catch (final Exception xe) {
                	txnManager.abort(txn);
                	
                	throw xe;

            	} finally {
                    txnManager.close(txn);
                	getTarget().release(Lock.WRITE_LOCK);
                }
                
            } catch (final Exception xe) {
                final String msg = "ERROR: Failed to set permissions on Collection '" + getTarget().getURI() + "'.";
                LOG.error(msg, xe);
                getListener().warn(msg);
            }
        }
    }

    class ResourceDeferredPermission extends AbstractDeferredPermission<DocumentImpl> {

        public ResourceDeferredPermission(RestoreListener listener, DocumentImpl resource, String owner, String group, Integer mode) {
            super(listener, resource, owner, group, mode);
        }

        @Override
        public void apply() {
            try {
            	getTarget().getUpdateLock().acquire(Lock.WRITE_LOCK);

            	final TransactionManager txnManager = broker.getDatabase().getTransactionManager();
                final Txn txn = txnManager.beginTransaction();

	            try {
	            	
	            	final Permission permission = getTarget().getPermissions();
	                permission.setOwner(getOwner());
	                permission.setGroup(getGroup());
	                permission.setMode(getMode());
	                if(permission instanceof ACLPermission) {
	                    final ACLPermission aclPermission = (ACLPermission)permission;
	                    aclPermission.clear();
	                    for(final ACEAider ace : getAces()) {
	                        aclPermission.addACE(ace.getAccessType(), ace.getTarget(), ace.getWho(), ace.getMode());
	                    }
	                }
	                broker.storeXMLResource(txn, getTarget());
	                txnManager.commit(txn);
	            	
	            } catch(final Exception xe) {
	            	txnManager.abort(txn);
	            	
	            	throw xe;
	            	
	            } finally {
                    txnManager.close(txn);
	                getTarget().getUpdateLock().release(Lock.WRITE_LOCK);
	            }
            
            } catch (final Exception xe) {
                final String msg = "ERROR: Failed to set permissions on Document '" + getTarget().getURI() + "'.";
                LOG.error(msg, xe);
                getListener().warn(msg);
			}
        }
    }
}