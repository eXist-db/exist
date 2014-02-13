/*
 * eXist Open Source Native XML Database
 *   
 * Copyright (C) 2001-2006 The eXist team
 * http://exist-db.org
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
 * Inc.,  51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 * 
 * $Id$
 */
package org.exist.xmldb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
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
    protected Subject user;
    protected AccessContext accessCtx;

    private static Logger LOG =
        Logger.getLogger( LocalCollectionManagementService.class );
        
    @SuppressWarnings("unused")
	private LocalCollectionManagementService() {}
    public LocalCollectionManagementService( Subject user, BrokerPool pool,
                                             LocalCollection parent,
											 AccessContext accessCtx) {
    	if(accessCtx == null)
    		{throw new NullAccessContextException();}
    	this.accessCtx = accessCtx;
        if ( user == null )
            {throw new NullPointerException("User cannot be null");}
        this.parent = parent;
        this.brokerPool = pool;
        this.user = user;
    }

    /**
     * @deprecated Use XmldbURI version instead
     */
    public Collection createCollection( String collName ) throws XMLDBException {
        return createCollection (collName, (Date)null);
    }

    public Collection createCollection( XmldbURI collName ) throws XMLDBException {
        return createCollection (collName, (Date)null);
    }

    /**
     * @deprecated Use XmldbURI version instead
     */
    public Collection createCollection( String collName, Date created ) throws XMLDBException {
    	try{
    		return createCollection(XmldbURI.xmldbUriFor(collName), created);
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    
    public Collection createCollection( XmldbURI collName, Date created ) throws XMLDBException {
        if (parent != null)
        	{collName = parent.getPathURI().resolveCollectionPath(collName);}        

    	final Subject preserveSubject = brokerPool.getSubject();
		final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            final org.exist.collections.Collection coll =
                broker.getOrCreateCollection( transaction, collName );
            if (created != null)
                {coll.setCreationTime(created.getTime());}
            broker.saveCollection(transaction, coll);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to create collection " + collName, e);
        } catch ( final IOException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to create collection " + collName, e);
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "not allowed to create collection", e );
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "not allowed to create collection", e );
		} finally {
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
        return new LocalCollection( user, brokerPool, parent, collName, accessCtx );
    }

    /**
     *  Creates a new collection in the database identified by name and using
     *  the provided configuration.
     *
     * @deprecated Use XmldbURI version instead
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

    /**
     * @deprecated Use XmldbURI version instead
     */
    public void removeCollection( String collName ) throws XMLDBException {
    	try{
    		removeCollection(XmldbURI.xmldbUriFor(collName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    public void removeCollection( XmldbURI collName ) throws XMLDBException {
        if (parent != null)
        	{collName = parent.getPathURI().resolveCollectionPath(collName);}        
        

    	final Subject preserveSubject = brokerPool.getSubject();
    	final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection collection = null;
        try {
            broker = brokerPool.get(user);
            collection = broker.openCollection(collName, Lock.WRITE_LOCK);
            if(collection == null) {
                transact.abort(transaction);
            	throw new XMLDBException(ErrorCodes.INVALID_COLLECTION,
            			"Collection " + collName + " not found");
            }
            LOG.debug( "removing collection " + collName );
            broker.removeCollection(transaction, collection);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to remove collection " + collName, e );
        } catch ( final IOException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to remove collection " + collName, e );
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to remove collection " + collName, e );
		} finally {
        	if(collection != null)
        		{collection.release(Lock.WRITE_LOCK);}
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
    }

    
    /**
     * @deprecated Use XmldbURI version instead
     */
    public void move(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		move(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
   /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionManagementServiceImpl#move(org.xmldb.api.base.Collection, org.xmldb.api.base.Collection, java.lang.String)
     */
    public void move(XmldbURI collectionPath, XmldbURI destinationPath,
            XmldbURI newName) throws XMLDBException {
    	collectionPath = parent.getPathURI().resolveCollectionPath(collectionPath);
    	destinationPath = destinationPath == null ? collectionPath.removeLastSegment() : parent.getPathURI().resolveCollectionPath(destinationPath);
    	

    	final Subject preserveSubject = brokerPool.getSubject();
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
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
            if (newName == null)
                {newName = collectionPath.lastSegment();}
            broker.moveCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( final IOException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
		} finally {
        	if(destination != null)
        		{destination.release(Lock.WRITE_LOCK);}
        	if(collection != null)
        		{collection.release(Lock.WRITE_LOCK);}
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
    }
    
    /**
     * @deprecated Use XmldbURI version instead
     */
    public void moveResource(String resourcePath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		moveResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath), XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    public void moveResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) 
    		throws XMLDBException { 
    	resourcePath = parent.getPathURI().resolveCollectionPath(resourcePath);
    	if (destinationPath == null)
    		{destinationPath = resourcePath.removeLastSegment();}
    	else
    		{destinationPath = parent.getPathURI().resolveCollectionPath(destinationPath);}


    	final Subject preserveSubject = brokerPool.getSubject();
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection destination = null;
        org.exist.collections.Collection source = null;
        try {
            broker = brokerPool.get(user);
     		source = broker.openCollection(resourcePath.removeLastSegment(), Lock.WRITE_LOCK);
    		if(source == null) {
                transact.abort(transaction);
    			throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + resourcePath.removeLastSegment() + " not found");
            }
    		final DocumentImpl doc = source.getDocument(broker, resourcePath.lastSegment());
            if(doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            }
            if (newName == null)
                {newName = resourcePath.lastSegment();}
            
            broker.moveResource(transaction, doc, destination, newName);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( final IOException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } catch (final TriggerException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
		} finally {
        	if(source != null)
        		{source.release(Lock.WRITE_LOCK);}
        	if(destination != null)
        		{destination.release(Lock.WRITE_LOCK);}
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
    }
    
    /**
     * @deprecated Use XmldbURI version instead
     */
    public void copy(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		copy(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
	/* (non-Javadoc)
	 * @see org.exist.xmldb.CollectionManagementServiceImpl#copy(java.lang.String, java.lang.String, java.lang.String)
	 */
    public void copy(XmldbURI collectionPath, XmldbURI destinationPath,
            XmldbURI newName) throws XMLDBException {
    	collectionPath = parent.getPathURI().resolveCollectionPath(collectionPath);
    	destinationPath = destinationPath == null ? collectionPath.removeLastSegment() : parent.getPathURI().resolveCollectionPath(destinationPath);


    	final Subject preserveSubject = brokerPool.getSubject();
        final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
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
	    if (newName == null) {
		newName = collectionPath.lastSegment();
	    }
            broker.copyCollection(transaction, collection, destination, newName);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( final IOException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } catch (final TriggerException e) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
		} finally {
        	if(collection != null) {collection.release(Lock.READ_LOCK);}
        	if(destination != null) {destination.release(Lock.WRITE_LOCK);}
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
    }

	/**
	 * @deprecated Use XmldbURI version instead
	 */
    public void copyResource(String resourcePath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		copyResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(final URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    
    public void copyResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) 
    		throws XMLDBException { 
    	resourcePath = parent.getPathURI().resolveCollectionPath(resourcePath);

    	if(destinationPath == null) {
            destinationPath = resourcePath.removeLastSegment();
        } else {
            destinationPath = parent.getPathURI().resolveCollectionPath(destinationPath);
        }

    	final Subject preserveSubject = brokerPool.getSubject();
    	final TransactionManager transact = brokerPool.getTransactionManager();
        final Txn transaction = transact.beginTransaction();
        DBBroker broker = null;
        org.exist.collections.Collection destination = null;
        org.exist.collections.Collection source = null;
        try {
            broker = brokerPool.get(user);
     		source = broker.openCollection(resourcePath.removeLastSegment(), Lock.WRITE_LOCK);
    		if(source == null) {
                transact.abort(transaction);
    			throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + resourcePath.removeLastSegment() + " not found");
            }
    		final DocumentImpl doc = source.getDocument(broker, resourcePath.lastSegment());
            if(doc == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            }
            destination = broker.openCollection(destinationPath, Lock.WRITE_LOCK);
            if(destination == null) {
                transact.abort(transaction);
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            }
	        if(newName == null) {
		        newName = resourcePath.lastSegment();
	        }
            broker.copyResource(transaction, doc, destination, newName);
            transact.commit(transaction);
        } catch ( final EXistException e ) {
            transact.abort(transaction);
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( final PermissionDeniedException e ) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (final LockException e) {
            transact.abort(transaction);
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
        	if(source != null) {source.release(Lock.WRITE_LOCK);}
        	if(destination != null) {destination.release(Lock.WRITE_LOCK);}
            transact.close(transaction);
            brokerPool.release( broker );
            brokerPool.setSubject(preserveSubject);
        }
    }
	
    public void setCollection(Collection parent) throws XMLDBException {
        this.parent = (LocalCollection) parent;
    }

    public void setProperty(String property,
                             String value) {
    }
	
    @Override
	public void runCommand(String[] params) throws XMLDBException {
    	

    	final Subject preserveSubject = brokerPool.getSubject();
    	DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            
            org.exist.plugin.command.Commands.command(XmldbURI.create(parent.getPath()), params);

        } catch (final EXistException e) {
        	throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "", e);
		} finally {
        	brokerPool.release(broker);
            brokerPool.setSubject(preserveSubject);
        }
	}
}

