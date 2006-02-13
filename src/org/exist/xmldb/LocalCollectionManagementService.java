/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-04 Wolfgang M. Meier wolfgang@exist-db.org
 * 
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */
package org.exist.xmldb;

import java.util.Date;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.NullAccessContextException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.w3c.dom.Document;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class LocalCollectionManagementService implements CollectionManagementServiceImpl {
    
    protected BrokerPool brokerPool;

    protected LocalCollection parent = null;
    protected User user;
    protected AccessContext accessCtx;

    private static Logger LOG =
        Logger.getLogger( LocalCollectionManagementService.class );
        
    private LocalCollectionManagementService() {}
    public LocalCollectionManagementService( User user, BrokerPool pool,
                                             LocalCollection parent,
											 AccessContext accessCtx) {
    	if(accessCtx == null)
    		throw new NullAccessContextException();
    	this.accessCtx = accessCtx;
        if ( user == null )
            throw new NullPointerException("User cannot be null");
        this.parent = parent;
        this.brokerPool = pool;
        this.user = user;
    }

    public Collection createCollection( String collName ) throws XMLDBException {
        return createCollection (collName, (Date)null);
    }

    
    public Collection createCollection( String collName, Date created) throws XMLDBException {
    	//Is collection's name relative ?
    	//TODO : use dedicated function in XmldbURI
        if (!collName.startsWith(DBBroker.ROOT_COLLECTION) && parent != null)
        	collName = parent.getPath() + "/" + collName;
        String path = XmldbURI.checkPath(collName, parent.getPath());
        
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            org.exist.collections.Collection coll =
                broker.getOrCreateCollection( transaction, collName );
            if (created != null)
                coll.setCreationTime(created.getTime());
            broker.saveCollection(transaction, coll);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to create collection " + collName, e);
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "not allowed to create collection", e );
        } finally {
            brokerPool.release( broker );
        }
        return new LocalCollection( user, brokerPool, parent, collName, accessCtx );
    }

    /**
     *  Creates a new collection in the database identified by name and using
     *  the provided configuration.
     *
     *@param  path                the path of the new collection
     *@param  configuration       the XML collection configuration to use for
     *      creating this collection.
     *@return                     The newly created collection
     *@exception  XMLDBException
     */
    public Collection createCollection( String path, Document configuration )
         throws XMLDBException {
        return createCollection( path );
    }

    public String getName() throws XMLDBException {
        return "CollectionManagementService";
    }

    public String getProperty( String property ) {
        return null;
    }

    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    public void removeCollection( String collName ) throws XMLDBException {
    	//Is collection's name relative ?
    	//TODO : use dedicated function in XmldbURI
        if (!collName.startsWith(DBBroker.ROOT_COLLECTION) && parent != null)
        	collName = parent.getPath() + "/" + collName;        
    	String path = XmldbURI.checkPath(collName, parent.getPath());
        
    	TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(transaction);
            	throw new XMLDBException(ErrorCodes.INVALID_COLLECTION,
            			"Collection " + path + " not found");
            }
            LOG.debug( "removing collection " + path );
            broker.removeCollection(transaction, collection);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to remove collection " + collName, e );
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } finally {
        	if(collection != null)
        		collection.release();
            brokerPool.release( broker );
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionManagementServiceImpl#move(org.xmldb.api.base.Collection, org.xmldb.api.base.Collection, java.lang.String)
     */
    public void move(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
    	collectionPath = XmldbURI.checkPath(collectionPath, parent.getPath());
    	destinationPath = XmldbURI.checkPath(destinationPath, parent.getPath());    	
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection collection = null;
        org.exist.collections.Collection destination = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collectionPath, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + collectionPath + " not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            }
            broker.moveCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
        	if(destination != null)
        		destination.release();
        	if(collection != null)
        		collection.release();
            brokerPool.release( broker );
        }
    }
    
    public void moveResource(String resourcePath, String destinationPath, String newName) 
    		throws XMLDBException { 
    	resourcePath = XmldbURI.checkPath(resourcePath, parent.getPath());
    	destinationPath = XmldbURI.checkPath(destinationPath, parent.getPath());
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection destination = null;
        org.exist.collections.Collection source = null;
        try {
            broker = brokerPool.get(user);
            //TODO : use dedicated function in XmldbURI
            int pos = resourcePath.lastIndexOf("/");
    		String collName = resourcePath.substring(0, pos);
    		String docName = resourcePath.substring(pos + 1);
    		source = broker.openCollection(collName, Lock.WRITE_LOCK);
    		if(source == null) {
                transact.abort(transaction);
    			throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collName + " not found");
            }
    		DocumentImpl doc = source.getDocument(broker, docName);
            if(doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            }
            
            broker.moveXMLResource(transaction, doc, destination, newName);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
        	if(source != null)
        		source.release();
        	if(destination != null)
        		destination.release();
            brokerPool.release( broker );
        }
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xmldb.CollectionManagementServiceImpl#copyResource(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void copyResource(String resourcePath, String destinationPath, String newName) 
		throws XMLDBException {
		resourcePath = XmldbURI.checkPath(resourcePath, parent.getPath());
		destinationPath = XmldbURI.checkPath(destinationPath, parent.getPath());
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection destination = null;
        org.exist.collections.Collection source = null;
        try {
            broker = brokerPool.get(user);
            //TODO : use dedicated function in XmldbURI
            int pos = resourcePath.lastIndexOf("/");
    		String collName = resourcePath.substring(0, pos);
    		String docName = resourcePath.substring(pos + 1);
    		source = broker.openCollection(collName, Lock.WRITE_LOCK);
    		if(source == null) {
                transact.abort(transaction);
    			throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collName + " not found");
            }
    		DocumentImpl doc = source.getDocument(broker, docName);
            if(doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            }
            broker.copyXMLResource(transaction, doc, destination, newName);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
        	if(source != null) source.release();
        	if(destination != null) destination.release();
            brokerPool.release( broker );
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.CollectionManagementServiceImpl#copy(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void copy(String collectionPath, String destinationPath, String newName) throws XMLDBException {
		collectionPath = XmldbURI.checkPath(collectionPath, parent.getPath());
		destinationPath = XmldbURI.checkPath(destinationPath, parent.getPath());
        System.out.println("Copying '" + collectionPath + "' to '" + destinationPath + "' as '" + newName + "'");
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection collection = null;
        org.exist.collections.Collection destination = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collectionPath, Lock.READ_LOCK);
            if(collection == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection '" + collectionPath + "' not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection '" + destinationPath + "' not found");
            }
            broker.copyCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
        } catch ( EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
        	if(collection != null) collection.release();
        	if(destination != null) destination.release();
            brokerPool.release( broker );
        }
	}
	
    public void setCollection( Collection parent ) throws XMLDBException {
        this.parent = (LocalCollection) parent;
    }

    public void setProperty( String property,
                             String value ) {
    }
}

