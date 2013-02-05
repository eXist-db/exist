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
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
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
    protected Subject user = null;
    protected ArrayList<Observer> observers = new ArrayList<Observer>(1);
    protected boolean needsSync = false;

    private XMLReader userReader = null;

    private AccessContext accessCtx;

    /**
     * Create a collection with no parent (root collection).
     *
     * @param user
     * @param brokerPool
     * @param collection
     * @throws XMLDBException
     */
    public LocalCollection(final Subject user, final BrokerPool brokerPool, final XmldbURI collection, final AccessContext accessCtx) throws XMLDBException {
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
    public LocalCollection(Subject user, final BrokerPool brokerPool, final LocalCollection parent, final XmldbURI name, final AccessContext accessCtx) throws XMLDBException {
        if(accessCtx == null) {
            throw new NullAccessContextException();
        }
        this.accessCtx = accessCtx;
        if (user == null) {
            user = brokerPool.getSecurityManager().getGuestSubject();
        }
        this.user = user;
        this.parent = parent;
        this.brokerPool = brokerPool;
        this.path = name;
        if (path == null) {
            path = XmldbURI.ROOT_COLLECTION_URI;
        }
        path = path.toCollectionPathURI();
        getCollection();
    }

    public AccessContext getAccessContext() {
        return accessCtx;
    }

    protected Collection getCollectionWithLock(final int lockMode) throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, lockMode);
            if(collection == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            collection.setReader(userReader);
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
        return collection;
    }

    protected void saveCollection() throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        Collection collection = null;
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            broker.saveCollection(transaction, collection);
            transact.commit(transaction);
        } catch(final IOException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch(final EXistException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } finally {
            if(collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    protected Collection getCollection() throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            final org.exist.collections.Collection collection = broker.getCollection(path);
            if(collection == null) {
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + path + " not found");
            }
            collection.setReader(userReader);
            return collection;
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    protected boolean checkOwner(final Collection collection, final Account account) throws XMLDBException {
        return account.equals(collection.getPermissions().getOwner());
    }

    protected boolean checkPermissions(final Collection collection, final int perm) throws XMLDBException {
        return collection.getPermissions().validate(user, perm);
    }

    /**
     * Close the current collection. Calling this method will flush all
     * open buffers to disk.
     */
    @Override
    public void close() throws XMLDBException {
        if (needsSync) {
            final Subject subject = brokerPool.getSubject();
            DBBroker broker = null;
            try {
                broker = brokerPool.get(user);
                broker.sync(Sync.MAJOR_SYNC);
            } catch(final EXistException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
            } finally {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
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
    @Override
    public String createId() throws XMLDBException {
        //TODO: API change to XmldbURI ?
    	final Subject subject = brokerPool.getSubject();
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            XmldbURI id;
            final Random rand = new Random();
            boolean ok;
            do {
                ok = true;
                id = XmldbURI.create(Integer.toHexString(rand.nextInt()) + ".xml");
                // check if this ID does already exist
                if (collection.hasDocument(broker, id)) {
                    ok = false;
                }
                
                if (collection.hasSubcollection(broker, id)) {
                    ok = false;
                }
                
            } while (!ok);
            return id.toString();
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
            collection.getLock().release(Lock.READ_LOCK);
        }
    }

    //TODO: API change to XmldbURI?
    @Override
    public Resource createResource(String id, final String type) throws XMLDBException {
        if(id == null) {
            id = createId();
        }
        
        final XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        final Resource r;
        if (type.equals("XMLResource")) {
            r = new LocalXMLResource(user, brokerPool, this, idURI);
        } else if (type.equals("BinaryResource")) {
            r = new LocalBinaryResource(user, brokerPool, this, idURI);
        } else {
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Unknown resource type: " + type);
        }
        
        ((AbstractEXistResource)r).isNewResource = true;
        return r;
    }

    //TODO: API change to XmldbURI ?
    /**
     *
     * @param name
     * @return
     * @throws XMLDBException
     */
    @Override
    public org.xmldb.api.base.Collection getChildCollection(String name) throws XMLDBException {
        XmldbURI childName = null;
        final XmldbURI childURI;
        try {
            childURI = XmldbURI.xmldbUriFor(name);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            if(!checkPermissions(collection, Permission.READ)) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "You are not allowed to read this collection");
            }
            if(collection.hasChildCollection(broker, childURI)) {
                childName = getPathURI().append(childURI);
            }
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
            collection.release(Lock.READ_LOCK);
        }
        if(childName != null) {
            return new LocalCollection(user, brokerPool, this, childName, accessCtx);
        }
        
        return null;
    }

    @Override
    public int getChildCollectionCount() throws XMLDBException {
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            if(checkPermissions(collection, Permission.READ)) {
                return collection.getChildCollectionCount(broker);
            } else {
                return 0;
            }
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
            collection.getLock().release(Lock.READ_LOCK);
        }
    }

    //TODO: API change to XmldbURI?
    @Override
    public String getName() throws XMLDBException {
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
        try {
            return collection.getURI().toString();
        } finally {
            collection.release(Lock.READ_LOCK);
        }
    }

    @Override
    public org.xmldb.api.base.Collection getParentCollection() throws XMLDBException {
        if(getName().equals(XmldbURI.ROOT_COLLECTION)) {
            return null;
        }
        
        if(parent == null) {
            // load the collection to check if it is valid
            final Subject subject = brokerPool.getSubject();
            DBBroker broker = null;
            Collection collection = null;
            try {
                broker = brokerPool.get(user);
                collection = broker.openCollection(path, Lock.READ_LOCK);
                if(collection == null) {
                    throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
                }
                parent = new LocalCollection(user, brokerPool, null, collection.getParentURI(), accessCtx);
            } catch(final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            } catch(final EXistException e) {
                throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "Error while retrieving parent collection: " + e.getMessage(), e);
            } finally {
                if(collection != null) {
                    collection.getLock().release(Lock.READ_LOCK);
                }
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
        }
        return parent;
    }

    public String getPath() throws XMLDBException {
        return path.toString();
    }

    @Override
    public XmldbURI getPathURI() {
        return path;
    }

    @Override
    public String getProperty(final String property) throws XMLDBException {
        return properties.getProperty(property);
    }

    @Override
    public Resource getResource(final String id) throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        Collection collection = null;
        DBBroker broker = null;
        
        final XmldbURI idURI;
        try {
            idURI = XmldbURI.xmldbUriFor(id);
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.READ_LOCK);
            if(collection == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }

            //you only need execute access on the collection, this is enforced in broker.openCollection above!
            /*if (!checkPermissions(collection, Permission.READ)) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED,
                    "Not allowed to read collection");
            }*/
            
            final DocumentImpl document = collection.getDocument(broker, idURI);
            if(document == null) {
                LOG.warn("Resource " + idURI + " not found");
                return null;
            }
            final Resource r;
            if(document.getResourceType() == DocumentImpl.XML_FILE) {
                r = new LocalXMLResource(user, brokerPool, this, idURI);
            } else if(document.getResourceType() == DocumentImpl.BINARY_FILE) {
                r = new LocalBinaryResource(user, brokerPool, this, idURI);
            } else {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Unknown resource type");
            }
            ((AbstractEXistResource)r).setMimeType(document.getMetadata().getMimeType());
            return r;
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR,
                "Error while retrieving resource: " + e.getMessage(), e);
        } finally {
            if(collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    @Override
    public int getResourceCount() throws XMLDBException {
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
    	final Subject subject = brokerPool.getSubject();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            if(!checkPermissions(collection, Permission.READ)) {
                return 0;
            } else {
                return collection.getDocumentCount(broker);
            }
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
            collection.getLock().release(Lock.READ_LOCK);
        }
    }

    /** Possible services: XPathQueryService, XQueryService,
     * CollectionManagementService (CollectionManager), UserManagementService,
     * DatabaseInstanceManager, XUpdateQueryService,  IndexQueryService,
     * ValidationService. */
    @Override
    public Service getService(final String name, final String version) throws XMLDBException {
        if(name.equals("XPathQueryService")) {
            return new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        }
        if(name.equals("XQueryService")) {
            return new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        }
        if(name.equals("CollectionManagementService") || name.equals("CollectionManager")) {
            return new LocalCollectionManagementService(user, brokerPool, this, accessCtx);
        }
        if(name.equals("UserManagementService")) {
            return new LocalUserManagementService(user, brokerPool, this);
        }
        if(name.equals("DatabaseInstanceManager")) {
            return new LocalDatabaseInstanceManager(user, brokerPool);
        }
        if(name.equals("XUpdateQueryService")) {
            return new LocalXUpdateQueryService(user, brokerPool, this);
        }
        if(name.equals("IndexQueryService")) {
            return new LocalIndexQueryService(user, brokerPool, this);
        }
        throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
    }

    @Override
    public Service[] getServices() throws XMLDBException {
        final Service[] services = new Service[6];
        services[0] = new LocalXPathQueryService(user, brokerPool, this, accessCtx);
        services[1] = new LocalCollectionManagementService(user, brokerPool, this, accessCtx);
        services[2] = new LocalUserManagementService(user, brokerPool, this);
        services[3] = new LocalDatabaseInstanceManager(user, brokerPool);
        services[4] = new LocalXUpdateQueryService(user, brokerPool, this);
        services[5] = new LocalIndexQueryService(user, brokerPool, this);
        return services;
    }

    @Override
    public boolean isOpen() throws XMLDBException {
        return true;
    }

    //TODO: API change to XmldbURI?
    @Override
    public String[] listChildCollections() throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        Collection collection = null;
        DBBroker broker = null;
        try {
            collection = getCollectionWithLock(Lock.READ_LOCK);
            broker = brokerPool.get(user);
            final String[] collections = new String[collection.getChildCollectionCount(broker)];
            int j = 0;
            for(final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); j++) {
                collections[j] = i.next().toString();
            }
            return collections;
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "error while retrieving resource: " + e.getMessage(), e);
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "error while retrieving resource: " + e.getMessage(), e);
        } finally {
            if(broker != null) {
                brokerPool.release(broker);
                brokerPool.setSubject(subject);
            }
            
            if(collection != null) {
                collection.release(Lock.READ_LOCK);
            }
        }
    }

    @Override
    public String[] getChildCollections() throws XMLDBException {
        return listChildCollections();
    }

    /**
     * Retrieve the list of resources in the collection
     * 
     * @throws XMLDBException if and invalid collection was specified, or if permission is denied
     */
    @Override
    public String[] listResources() throws XMLDBException {
    	final Subject subject = brokerPool.getSubject();
        Collection collection = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.READ_LOCK);
            if(collection == null) {
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            
            final List<XmldbURI> allresources = new ArrayList<XmldbURI>();
            DocumentImpl doc;
            for(final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                doc = i.next();
                // Include only when (1) lockToken is present or (2)
                // lockToken indicates that it is not a null resource
                final LockToken lock = doc.getMetadata().getLockToken();
                if (lock==null || (!lock.isNullResource())){
                    allresources.add(doc.getFileURI());
                }
            }
            // Copy content of list into String array.
            int j = 0;
            final String[] resources = new String[allresources.size()];
            for(final Iterator<XmldbURI> i = allresources.iterator(); i.hasNext(); j++){
                resources[j] = i.next().toString();
            }
            return resources;
        } catch(final PermissionDeniedException e) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final EXistException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, "Error while retrieving resource: " + e.getMessage(), e);
        } finally {
            if(collection != null) {
                collection.release(Lock.READ_LOCK);
            }
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    @Override
    public String[] getResources() throws XMLDBException {
        return listResources();
    }

    public void registerService(final Service serv) throws XMLDBException {
        throw new XMLDBException(ErrorCodes.NOT_IMPLEMENTED);
    }

    @Override
    public void removeResource(final Resource res) throws XMLDBException {
        if(res == null) {
            return;
        }
        
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
    	final Subject subject = brokerPool.getSubject();
        Collection collection = null;
        DBBroker broker = null;
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        try {
            if(LOG.isDebugEnabled()) {
                LOG.debug("removing " + resURI);
            }
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            //Check that the document exists
            final DocumentImpl doc = collection.getDocument(broker, resURI);
            if(doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resURI + " not found");
            }
            
            if(res.getResourceType().equals("XMLResource")) {
                collection.removeXMLResource(transaction, broker, resURI);
            } else {
                collection.removeBinaryResource(transaction, broker, resURI);
            }
            transact.commit(transaction);
        } catch(final EXistException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } catch(final PermissionDeniedException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
        } catch(final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, e.getMessage(), e);
        } catch(final LockException e) {
            transact.abort(transaction);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            if(collection != null) {
                collection.getLock().release(Lock.WRITE_LOCK);
            }
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
        needsSync = true;
    }

    @Override
    public void setProperty(final String property, final String value) throws XMLDBException {
        properties.setProperty(property, value);
    }

    @Override
    public void storeResource(final Resource resource) throws XMLDBException {
        storeResource(resource, null, null);
    }

    @Override
    public void storeResource(final Resource resource, final Date a, final Date b) throws XMLDBException {
        if(resource.getResourceType().equals("XMLResource")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("storing document " + resource.getId());
            }
            
            ((LocalXMLResource)resource).datecreated = a;
            ((LocalXMLResource)resource).datemodified = b;
            storeXMLResource((LocalXMLResource) resource);
        } else if(resource.getResourceType().equals("BinaryResource")) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("storing binary resource " + resource.getId());
            }
            ((LocalBinaryResource)resource).datecreated = a;
            ((LocalBinaryResource)resource).datemodified = b;
            storeBinaryResource((LocalBinaryResource) resource);   
        } else {
            throw new XMLDBException(ErrorCodes.UNKNOWN_RESOURCE_TYPE, "unknown resource type: " + resource.getResourceType());
        }
        
        ((AbstractEXistResource)resource).isNewResource = false;
        needsSync = true;
    }

    private void storeBinaryResource(final LocalBinaryResource res) throws XMLDBException {
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
    	final Subject subject = brokerPool.getSubject();
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        
        Collection collection = null;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(txn);
                throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
            }
            
            final long conLength = res.getStreamLength();
            if(conLength != -1) {
                collection.addBinaryResource(txn, broker, resURI, res.getStreamContent(), res.getMimeType(), conLength, res.datecreated, res.datemodified);
            } else {
                collection.addBinaryResource(txn, broker, resURI, (byte[])res.getContent(), res.getMimeType(), res.datecreated, res.datemodified);
            }
            
            transact.commit(txn);
        } catch(final Exception e) {
            transact.abort(txn);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "Exception while storing binary resource: " + e.getMessage(), e);
        } finally {
            if(collection != null) {
                collection.getLock().release(Lock.WRITE_LOCK);
            }
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    private void storeXMLResource(final LocalXMLResource res) throws XMLDBException {
        final XmldbURI resURI;
        try {
            resURI = XmldbURI.xmldbUriFor(res.getId());
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }
        
    	final Subject subject = brokerPool.getSubject();
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn txn = transact.beginTransaction();
        
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            String uri = null;
            if(res.file != null) {
                uri = res.file.toURI().toASCIIString();
            }
            
            Collection collection = null;
            final IndexInfo info;
            try {
                collection = broker.openCollection(path, Lock.WRITE_LOCK);
                if(collection == null) {
                    transact.abort(txn);
                    throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + path + " not found");
                }
                for(final Observer observer : observers) {
                    collection.addObserver(observer);
                }
                if (uri != null || res.inputSource!=null) {
                    setupParser(collection, res);
                    info = collection.validateXMLResource(txn, broker, resURI, (uri != null) ? new InputSource(uri) : res.inputSource);
                } else if (res.root != null) {
                    info = collection.validateXMLResource(txn, broker, resURI, res.root);
                } else {
                    info = collection.validateXMLResource(txn, broker, resURI, res.content);
                }
                //Notice : the document should now have a Lock.WRITE_LOCK update lock
                //TODO : check that no exception occurs in order to allow it to be released
                info.getDocument().getMetadata().setMimeType(res.getMimeType());
                if (res.datecreated  != null) {
                    info.getDocument().getMetadata().setCreated(res.datecreated.getTime());
                }
                if (res.datemodified != null) {
                    info.getDocument().getMetadata().setLastModified(res.datemodified.getTime());
                }
            } finally {
                if(collection != null) {
                    collection.release(Lock.WRITE_LOCK);
                }
            }
            
            if (uri != null || res.inputSource!=null) {
                collection.store(txn, broker, info, (uri!=null) ? new InputSource(uri):res.inputSource, false);
            } else if (res.root != null) {
                collection.store(txn, broker, info, res.root, false);
            } else {
                collection.store(txn, broker, info, res.content, false);
            }
            
            //Notice : the document should now have its update lock released
            transact.commit(txn);
            collection.deleteObservers();
        } catch(final Exception e) {
            transact.abort(txn);
            LOG.error(e);
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        } finally {
            brokerPool.release(broker);
            brokerPool.setSubject(subject);
        }
    }

    private void setupParser(final Collection collection, final LocalXMLResource res) throws XMLDBException {
        String normalize = properties.getProperty(NORMALIZE_HTML, "no");
        if((normalize.equalsIgnoreCase("yes") || normalize.equalsIgnoreCase("true")) &&
                (res.getMimeType().equals("text/html") || res.getId().endsWith(".htm") ||
                    res.getId().endsWith(".html"))) {
            try {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Converting HTML to XML using NekoHTML parser.");
                }
                final Class<?> clazz = Class.forName("org.cyberneko.html.parsers.SAXParser");
                final XMLReader htmlReader = (XMLReader) clazz.newInstance();
                //do not modify the case of elements and attributes
                htmlReader.setProperty("http://cyberneko.org/html/properties/names/elems", "match");
                htmlReader.setProperty("http://cyberneko.org/html/properties/names/attrs", "no-change");
                collection.setReader(htmlReader);
            } catch(final Exception e) {
                LOG.error("Error while involing NekoHTML parser. (" + e.getMessage()
                    + "). If you want to parse non-wellformed HTML files, put "
                    + "nekohtml.jar into directory 'lib/optional'.", e);
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "NekoHTML parser error", e);
            }
        }
    }

    @Override
    public Date getCreationTime() throws XMLDBException {
        final Collection collection = getCollectionWithLock(Lock.READ_LOCK);
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
    @Override
    public void addObserver(final Observer o) {
        if(!observers.contains(o)) {
            observers.add(o);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionImpl#isRemoteCollection()
     */
    @Override
    public boolean isRemoteCollection() throws XMLDBException {
        return false;
    }

    /** set user-defined Reader
     * @param reader
     */
    public void setReader(final XMLReader reader){
        userReader = reader;
    }

    //You probably will have to call this method from this cast :
    //((org.exist.xmldb.CollectionImpl)collection).getURI()
    public XmldbURI getURI() {
        final StringBuilder accessor = new StringBuilder(XmldbURI.XMLDB_URI_PREFIX);
        //TODO : get the name from client
        accessor.append("exist");
        accessor.append("://");
        //No host ;-)
        accessor.append("");
        //No port ;-)
        //No context ;-)
        //accessor.append(getContext());
        try {
            //TODO : cache it when constructed
            return XmldbURI.create(accessor.toString(), getPath());
        } catch(final XMLDBException e) {
            //TODO : should never happen
            return null;
        }
    }

    @Override
    public void setTriggersEnabled(final boolean triggersEnabled) throws XMLDBException {
        final Collection collection = getCollection();
        collection.setTriggersEnabled(triggersEnabled);
    }
}
