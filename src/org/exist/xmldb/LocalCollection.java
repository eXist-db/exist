
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.xmldb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Random;

import javax.xml.transform.OutputKeys;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.LockToken;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.validation.service.LocalValidationService;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

/**
 *  A local implementation of the Collection interface. This
 * is used when the database is running in embedded mode.
 *
 * Extends Observable to allow status callbacks during indexing.
 * Methods storeResource notifies registered observers about the
 * progress of the indexer by passing an object of type ProgressIndicator
 * to the observer.
 *
 *@author     wolf
 */
public class LocalCollection extends Observable implements CollectionImpl {
    
    private static Logger LOG = Logger.getLogger(LocalCollection.class);

    /**
     * Property to be passed to {@link #setProperty(String, String)}.
     * When storing documents, pass HTML files through an HTML parser
     * (NekoHTML) instead of the XML parser. The HTML parser will normalize
     * the HTML into well-formed XML.
     */
    public final static String NORMALIZE_HTML = "normalize-html";

    protected final static Properties defaultProperties = new Properties();
    static {
        defaultProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
        defaultProperties.setProperty(NORMALIZE_HTML, "no");
    }
    
    protected XmldbURI path = null;
    protected BrokerPool brokerPool = null;
    protected Properties properties = new Properties(defaultProperties);
    protected LocalCollection parent = null;
    protected User user = null;
    protected ArrayList observers = new ArrayList(1);
    protected boolean needsSync = false;
    
    private XMLReader userReader = null;
    
    private AccessContext accessCtx;
    
    private LocalCollection() {}
    /**
     * Create a collection with no parent (root collection).
     *
     * @param user
     * @param brokerPool
     * @param collection
     * @throws XMLDBException
     */
    public LocalCollection(User user, BrokerPool brokerPool, XmldbURI collection, AccessContext accessCtx)
    throws XMLDBException {
        this(user, brokerPool, null, collection, accessCtx);
    }
    
    /**
     * Create a collection identified by its name. Load the collection from the database.
     *
     * @param user
     * @param brokerPool
     * @param parent
     * @param name
     * @throws XMLDBException
     */
    public LocalCollection(
            User user,
            BrokerPool brokerPool,
            LocalCollection parent,
            XmldbURI name,
            AccessContext accessCtx)
            throws XMLDBException {
        if(accessCtx == null)
            throw new NullAccessContextException();
        this.accessCtx = accessCtx;
        if (user == null)
            user = new User("guest", "guest", "guest");
        this.user = user;
        this.parent = parent;
        this.brokerPool = brokerPool;
        this.path = name;
        if (path == null)
            path = XmldbURI.ROOT_COLLECTION_URI;
        path = path.toCollectionPathURI();
        getCollection();
    }
    
    public AccessContext getAccessContext() {
        return accessCtx;
    }
    
    protected Collection getCollectionWithLock(int lockMode) throws XMLDBException {
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, lockMode);
            if(collection == null)
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            collection.setReader(userReader);
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
        }
        return collection;
    }
    
    protected void saveCollection() throws XMLDBException {
        DBBroker broker = null;
        Collection collection = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null)
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            broker.saveCollection(transaction, collection);
            transact.commit(transaction);
        } catch (IOException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } finally {
            if(collection != null)
                collection.release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    protected Collection getCollection() throws XMLDBException {
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            org.exist.collections.Collection collection = broker.getCollection(path);
            if (collection == null)
                throw new XMLDBException(
                        ErrorCodes.NO_SUCH_COLLECTION,
                        "Collection " + path + " not found");
            
            collection.setReader(userReader);
            
            return collection;
        } catch (EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    protected boolean checkOwner(Collection collection, User user) throws XMLDBException {
        return user.getName().equals(collection.getPermissions().getOwner());
    }
    
    protected boolean checkPermissions(Collection collection, int perm) throws XMLDBException {
        return collection.getPermissions().validate(user, perm);
    }
    
    /**
     * Close the current collection. Calling this method will flush all
     * open buffers to disk.
     */
    public void close() throws XMLDBException {
        if (needsSync) {
            DBBroker broker = null;
            try {
                broker = brokerPool.get(user);
                broker.sync(Sync.MAJOR_SYNC);
            } catch (EXistException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
            } finally {
                brokerPool.release(broker);
            }
        }
    }
    
    /**
     * Creates a unique name for a database resource
     * Uniqueness is only guaranteed within the eXist instance
     * 
     * The name is based on a hex encoded string of a random integer
     * and will have the format xxxxxxxx.xml where x is in the range
     * 0 to 9 and a to f 
     * 
     * @return the unique resource name 
     */
    public String createId() throws XMLDBException 
    {
        //TODO: api change to XmldbURI?
    	
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            XmldbURI id;
            Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this id does already exist
                if (collection.hasDocument(id))
                    ok = false;
                
                if (collection.hasSubcollection(id))
                    ok = false;
                
            } while (!ok);
            return id.toString();
        } finally {
            collection.getLock().release(Lock.READ_LOCK);
        }
    }
    
    //TODO: api change to XmldbURI?
    public Resource createResource(String id, String type) throws XMLDBException {
        if (id == null)
            id = createId();
        
        XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        Resource r = null;
        if (type.equals("XMLResource"))
            r = new LocalXMLResource(user, brokerPool, this, idURI);
        else if (type.equals("BinaryResource"))
            r = new LocalBinaryResource(user, brokerPool, this, idURI);
        else
            throw new XMLDBException(
                    ErrorCodes.INVALID_RESOURCE,
                    "unknown resource type: " + type);
        ((AbstractEXistResource)r).isNewResource = true;
        return r;
    }
    
    //TODO: api change to XmldbURI?
    public org.xmldb.api.base.Collection getChildCollection(String name) throws XMLDBException {
        XmldbURI childName = null;
        XmldbURI childURI;
        try {
            childURI = XmldbURI.xmldbUriFor(name);
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            if (!checkPermissions(collection, Permission.READ))
                throw new XMLDBException(
                        ErrorCodes.PERMISSION_DENIED,
                        "You are not allowed to access this collection");
            if(collection.hasChildCollection(childURI))
                childName = getPathURI().append(childURI);
        } finally {
            collection.release(Lock.READ_LOCK);
        }
        if(childName != null)
            return new LocalCollection(user, brokerPool, this, childName, accessCtx);
        else
            return null;
    }
    
    public int getChildCollectionCount() throws XMLDBException {
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            if (checkPermissions(collection, Permission.READ))
                return collection.getChildCollectionCount();
            else
                return 0;
        } finally {
            collection.getLock().release(Lock.READ_LOCK);
        }
    }
    
    //TODO: api change to XmldbURI?
    public String getName() throws XMLDBException {
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            return collection.getURI().toString();
        } finally {
            collection.release(Lock.READ_LOCK);
        }
    }
    
    public org.xmldb.api.base.Collection getParentCollection() throws XMLDBException {
        if (getName().equals(DBBroker.ROOT_COLLECTION))
            return null;
        if (parent == null) {
            // load the collection to check if it is valid
            DBBroker broker = null;
            Collection collection = null;
            try {
                broker = brokerPool.get(user);
                collection = broker.openCollection(path, Lock.READ_LOCK);
                if(collection == null)
                    throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
                parent = new LocalCollection(user, brokerPool, null, collection.getParentURI(), accessCtx);
            } catch (EXistException e) {
                throw new XMLDBException(
                        ErrorCodes.UNKNOWN_ERROR,
                        "error while retrieving parent collection: " + e.getMessage(),
                        e);
            } finally {
                if(collection != null)
                    collection.getLock().release(Lock.READ_LOCK);
                brokerPool.release(broker);
            }
        }
        return parent;
    }
    
    public String getPath() throws XMLDBException {
        return path.toString();
    }
    
    public XmldbURI getPathURI() {
        return path;
    }
    
    public String getProperty(String property) throws XMLDBException {
        return properties.getProperty(property);
    }
    
    public Resource getResource(String id) throws XMLDBException {
        Collection collection = null;
        DBBroker broker = null;
        XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.READ_LOCK);
            if(collection == null)
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            if (!checkPermissions(collection, Permission.READ))
                throw new XMLDBException(
                        ErrorCodes.PERMISSION_DENIED,
                        "not allowed to read collection");
            DocumentImpl document = collection.getDocument(broker, idURI);
            if (document == null) {
                LOG.warn("Resource " + idURI + " not found");
                return null;
            }
            Resource r;
            if (document.getResourceType() == DocumentImpl.XML_FILE)
                r = new LocalXMLResource(user, brokerPool, this, idURI);
            else if (document.getResourceType() == DocumentImpl.BINARY_FILE)
                r = new LocalBinaryResource(user, brokerPool, this, idURI);
            else
                throw new XMLDBException(
                        ErrorCodes.INVALID_RESOURCE,
                        "unknown resource type");
            ((AbstractEXistResource)r).setMimeType(document.getMetadata().getMimeType());
            return r;
        } catch (EXistException e) {
            throw new XMLDBException(
                    ErrorCodes.UNKNOWN_ERROR,
                    "error while retrieving resource: " + e.getMessage(),
                    e);
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public int getResourceCount() throws XMLDBException {
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            if (!checkPermissions(collection, Permission.READ))
                return 0;
            else
                return collection.getDocumentCount();
        } finally {
            collection.getLock().release(Lock.READ_LOCK);
        }
    }
    
    /** Possible services: XPathQueryService, XQueryService,
     * CollectionManagementService (CollectionManager), UserManagementService,
     * DatabaseInstanceManager, XUpdateQueryService,  IndexQueryService,
     * ValidationService. */
    public Service getService(String name, String version) throws XMLDBException {
        if (name.equals("XPathQueryService"))
            return new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        
        if (name.equals("XQueryService"))
            return new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        
        if (name.equals("CollectionManagementService")
        || name.equals("CollectionManager"))
            return new LocalCollectionManagementService(user, brokerPool, this, accessCtx);
        
        if (name.equals("UserManagementService"))
            return new LocalUserManagementService(user, brokerPool, this);
        
        if (name.equals("DatabaseInstanceManager"))
            return new LocalDatabaseInstanceManager(user, brokerPool);
        
        if (name.equals("XUpdateQueryService"))
            return new LocalXUpdateQueryService(user, brokerPool, this);
        
        if (name.equals("IndexQueryService"))
            return new LocalIndexQueryService(user, brokerPool, this);
        
        if (name.equals("ValidationService"))
            return new LocalValidationService(user, brokerPool, this);
        
        throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
    }
    
    public Service[] getServices() throws XMLDBException {
        Service[] services = new Service[7];
        services[0] = new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        services[1] = new LocalCollectionManagementService(user, brokerPool, this, accessCtx);
        services[2] = new LocalUserManagementService(user, brokerPool, this);
        services[3] = new LocalDatabaseInstanceManager(user, brokerPool);
        services[4] = new LocalXUpdateQueryService(user, brokerPool, this);
        services[5] = new LocalIndexQueryService(user, brokerPool, this);
        services[6] = new LocalValidationService(user, brokerPool, this);
        return services; // jmv null;
    }
    
    public boolean isOpen() throws XMLDBException {
        return true;
    }
    
    //TODO: api change to XmldbURI?
    public String[] listChildCollections() throws XMLDBException {
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            if (!checkPermissions(collection, Permission.READ))
                return new String[0];
            String[] collections = new String[collection.getChildCollectionCount()];
            int j = 0;
            for (Iterator i = collection.collectionIterator(); i.hasNext(); j++)
                collections[j] = ((XmldbURI) i.next()).toString();
            return collections;
        } finally {
            collection.release(Lock.READ_LOCK);
        }
    }
    
    public String[] getChildCollections() throws XMLDBException {
        return listChildCollections();
    }
    
    public String[] listResources() throws XMLDBException {
        Collection collection = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.READ_LOCK);
            if(collection == null)
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            if (!checkPermissions(collection, Permission.READ))
                return new String[0];
            
            List allresources = new ArrayList();
            DocumentImpl doc;
            for (Iterator i = collection.iterator(broker); i.hasNext(); ) {
                doc = (DocumentImpl) i.next();
                
                // Include only when (1) locktoken is present or (2)
                // locktoken indicates that it is not a null resource
                LockToken lock = doc.getMetadata().getLockToken();
                if(lock==null || (!lock.isNullResource()) ){
                    allresources.add( doc.getFileURI() );
                }
                
            }
            
            // Copy content of list into String array.
            int j=0;
            String[] resources = new String[allresources.size()];
            for(Iterator i = allresources.iterator(); i.hasNext(); j++){
                resources[j]= ((XmldbURI) i.next()).toString();
            }
            
            return resources;
        } catch (EXistException e) {
            throw new XMLDBException(
                    ErrorCodes.UNKNOWN_ERROR,
                    "error while retrieving resource: " + e.getMessage(),
                    e);
        } finally {
            if(collection != null)
                collection.release(Lock.READ_LOCK);
            brokerPool.release(broker);
        }
    }
    
    public String[] getResources() throws XMLDBException {
        return listResources();
    }
    
    public void registerService(Service serv) throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
    }
    
    public void removeResource(Resource res) throws XMLDBException {
        if (res == null)
            return;
        
        XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
        Collection collection = null;
        DBBroker broker = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            if (LOG.isDebugEnabled())
                LOG.debug("removing " + resURI);
            
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
           
            //Check that the document exists
            DocumentImpl doc = collection.getDocument(broker, resURI);
            if (doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "resource " + resURI + " not found");
            }
            
            if (res.getResourceType().equals("XMLResource"))
                collection.removeXMLResource(transaction, broker, resURI);
            else
                collection.removeBinaryResource(transaction, broker, resURI);
            transact.commit(transaction);
        } catch (EXistException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch (TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } catch (LockException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if (collection != null)
                collection.getLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
        needsSync = true;
    }
    
    public void setProperty(String property, String value) throws XMLDBException {
        properties.setProperty(property, value);
    }
    
    public void storeResource(Resource resource) throws XMLDBException {
        storeResource(resource, null, null);
    }
    
    public void storeResource(Resource resource, Date a, Date b) throws XMLDBException {
        if (resource.getResourceType().equals("XMLResource")) {
            if (LOG.isDebugEnabled())
                LOG.debug("storing document " + resource.getId());
            ((LocalXMLResource)resource).datecreated =a;
            ((LocalXMLResource)resource).datemodified =b;
            storeXMLResource((LocalXMLResource) resource);

        } else if (resource.getResourceType().equals("BinaryResource")) {
            if (LOG.isDebugEnabled())
                LOG.debug("storing binary resource " + resource.getId());
            ((LocalBinaryResource)resource).datecreated =a;
            ((LocalBinaryResource)resource).datemodified =b;
            storeBinaryResource((LocalBinaryResource) resource);
            
        } else
            throw new XMLDBException(
                    ErrorCodes.UNKNOWN_RESOURCE_TYPE,
                    "unknown resource type: " + resource.getResourceType());
        ((AbstractEXistResource)resource).isNewResource = false;
        needsSync = true;
    }
    
    private void storeBinaryResource(LocalBinaryResource res) throws XMLDBException {
        XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        Collection collection = null;
        DBBroker broker = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(txn);
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            
            long conLength=res.getStreamLength();
            if(conLength!=-1) {
	            collection.addBinaryResource(txn, broker, resURI, res.getStreamContent(),
	                    res.getMimeType(), (int)conLength, res.datecreated, res.datemodified);
            } else {
	            collection.addBinaryResource(txn, broker, resURI, (byte[])res.getContent(),
	                    res.getMimeType(), res.datecreated, res.datemodified);
            }
            transact.commit(txn);
        } catch (Exception e) {
            transact.abort(txn);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR,
                    "Exception while storing binary resource: " + e.getMessage(), e);
        } finally {
            if(collection != null)
                collection.getLock().release(Lock.WRITE_LOCK);
            brokerPool.release(broker);
        }
    }
    
    private void storeXMLResource(LocalXMLResource res) throws XMLDBException {
        XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        DBBroker broker = null;
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            String uri = null;
            if(res.file != null) uri = res.file.toURI().toASCIIString();
            IndexInfo info = null;
            Collection collection = null;
            try {
                collection = broker.openCollection(path, Lock.WRITE_LOCK);
                if(collection == null) {
                    transact.abort(txn);
                    throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
                }
                Observer observer;
                for (Iterator i = observers.iterator(); i.hasNext();) {
                    observer = (Observer) i.next();
                    collection.addObserver(observer);
                }
                if (uri != null || res.inputSource!=null) {
                    setupParser(collection, res);
                    info = collection.validateXMLResource(txn, broker, resURI, (uri!=null)?new InputSource(uri):res.inputSource);
                } else if (res.root != null)
                    info = collection.validateXMLResource(txn, broker, resURI, res.root);
                else
                    info = collection.validateXMLResource(txn, broker, resURI, res.content);
                //Notice : the document should now have a Lock.WRITE_LOCK update lock
                //TODO : check that no exception occurs in order to allow it to be released
                info.getDocument().getMetadata().setMimeType(res.getMimeType());                
                if (res.datecreated  != null)
                    info.getDocument().getMetadata().setCreated( res.datecreated.getTime());
                
                if (res.datemodified != null)
                    info.getDocument().getMetadata().setLastModified( res.datemodified.getTime());          
            } finally {
            	if (collection != null)
            		collection.release(Lock.WRITE_LOCK);
            }
            if (uri != null || res.inputSource!=null) {
                collection.store(txn, broker, info, (uri!=null)?new InputSource(uri):res.inputSource, false);
            } else if (res.root != null) {
                collection.store(txn, broker, info, res.root, false);
            } else {
                collection.store(txn, broker, info, res.content, false);
            }
            //Notice : the document should now have its update lock released
            transact.commit(txn);
            collection.deleteObservers();
        } catch (Exception e) {
            transact.abort(txn);
            LOG.error(e);            
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
        }
    }
    
    private void setupParser(Collection collection, LocalXMLResource res) throws XMLDBException {
        String normalize = properties.getProperty(NORMALIZE_HTML, "no");
        if ((normalize.equalsIgnoreCase("yes") || normalize.equalsIgnoreCase("true")) &&
                (res.getMimeType().equals("text/html") || res.getId().endsWith(".htm") || res.getId().endsWith(".html"))) {
            try {
                if (LOG.isDebugEnabled())
                    LOG.debug("Converting HTML to XML using NekoHTML parser.");
                Class clazz = Class.forName("org.cyberneko.html.parsers.SAXParser");
                XMLReader htmlReader = (XMLReader) clazz.newInstance();
                //do not modify the case of elements and attributes
                htmlReader.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
                htmlReader.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
                collection.setReader(htmlReader);
            } catch (Exception e) {
                LOG.error("Error while involing NekoHTML parser. (" + e.getMessage()
                        + "). If you want to parse non-wellformed HTML files, put "
                        + "nekohtml.jar into directory 'lib/optional'.", e);
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "NekoHTML parser error", e);
            }
        }
    }
    
    public Date getCreationTime() throws XMLDBException {
        Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            return new Date(collection.getCreationTime());
        } finally {
            collection.getLock().release(Lock.READ_LOCK);
        }
    }
    
    /**
     * Add a new observer to the list. Observers are just passed
     * on to the indexer to be notified about the indexing progress.
     */
    public void addObserver(Observer o) {
        if (!observers.contains(o))
            observers.add(o);
    }
    
    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionImpl#isRemoteCollection()
     */
    public boolean isRemoteCollection() throws XMLDBException {
        return false;
    }
    
    /** set user-defined Reader
     * @param reader
     */
    public void setReader(XMLReader reader){
        userReader = reader;
    }
    
    //You probably will have to call this methed from this cast :
    //((org.exist.xmldb.CollectionImpl)collection).getURI()
    public XmldbURI getURI() {
        StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        //TODO : get the name from client
        accessor.append("exist");
        accessor.append("://");
        //No host ;-)
        accessor.append("");
        //No port ;-)
        if (-1 != -1)
            accessor.append(":").append(-1);
        //No context ;-)
        //accessor.append(getContext());
        try {
            //TODO : cache it when constructed
            return XmldbURI.create(accessor.toString(), getPath());
        } catch (XMLDBException e) {
            //TODO : should never happen
            return null;
        }
    }
}
