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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.w3c.dom.Document;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

public class LocalCollectionManagementService implements CollectionManagementServiceImpl {
    
    protected BrokerPool brokerPool;

    protected LocalCollection parent = null;
    protected User user;

    private static Logger LOG =
        Logger.getLogger( LocalCollectionManagementService.class );
        
    public LocalCollectionManagementService( User user, BrokerPool pool,
                                             LocalCollection parent ) {
        if ( user == null )
            throw new RuntimeException();
        this.parent = parent;
        this.brokerPool = pool;
        this.user = user;
    }

    public Collection createCollection( String collName ) throws XMLDBException {
        collName = parent.getPath() + '/' + collName;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            org.exist.collections.Collection coll =
                broker.getOrCreateCollection( collName );
            broker.saveCollection( coll );
            broker.flush();
            //broker.sync();
        } catch ( EXistException e ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to create collection " + collName, e);
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "not allowed to create collection", e );
        } finally {
            brokerPool.release( broker );
        }
        return new LocalCollection( user, brokerPool, parent, collName );
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
    	String path = (collName.startsWith("/db") ? collName : 
    		parent.getPath() + '/' + collName);
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            LOG.debug( "removing collection " + path );
            broker.removeCollection( path );
        } catch ( EXistException e ) {
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to remove collection " + collName, e );
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } finally {
            brokerPool.release( broker );
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionManagementServiceImpl#move(org.xmldb.api.base.Collection, org.xmldb.api.base.Collection, java.lang.String)
     */
    public void move(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
        if(!collectionPath.startsWith("/db"))
            collectionPath = parent.getPath() + '/' + collectionPath;
        if(!destinationPath.startsWith("/db"))
            destinationPath = parent.getPath() + '/' + destinationPath;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            org.exist.collections.Collection collection = broker.getCollection(collectionPath);
            if(collection == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + collectionPath + " not found");
            org.exist.collections.Collection destination = broker.getCollection(destinationPath);
            if(destination == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            broker.moveCollection(collection, destination, newName);
        } catch ( EXistException e ) {
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move collection " + collectionPath, e );
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
            brokerPool.release( broker );
        }
    }
    
    public void moveResource(String resourcePath, String destinationPath,
            String newName) throws XMLDBException {
        if(!resourcePath.startsWith("/db"))
            resourcePath = parent.getPath() + '/' + resourcePath;
        if(!destinationPath.startsWith("/db"))
            destinationPath = parent.getPath() + '/' + destinationPath;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            DocumentImpl doc = (DocumentImpl)broker.getDocument(resourcePath);
            if(doc == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            org.exist.collections.Collection destination = broker.getCollection(destinationPath);
            if(destination == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            broker.moveResource(doc, destination, newName);
        } catch ( EXistException e ) {
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
            brokerPool.release( broker );
        }
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xmldb.CollectionManagementServiceImpl#copyResource(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void copyResource(String resourcePath, String destinationPath,
			String newName) throws XMLDBException {
		if(!resourcePath.startsWith("/db"))
            resourcePath = parent.getPath() + '/' + resourcePath;
        if(!destinationPath.startsWith("/db"))
            destinationPath = parent.getPath() + '/' + destinationPath;
        DBBroker broker = null;
        try {
            broker = brokerPool.get(user);
            DocumentImpl doc = (DocumentImpl)broker.getDocument(resourcePath);
            if(doc == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_RESOURCE, "Resource " + resourcePath + " not found");
            org.exist.collections.Collection destination = broker.getCollection(destinationPath);
            if(destination == null)
                throw new XMLDBException(ErrorCodes.NO_SUCH_COLLECTION, "Collection " + destinationPath + " not found");
            broker.copyResource(doc, destination, newName);
        } catch ( EXistException e ) {
        	e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "failed to move resource " + resourcePath, e );
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(), e );
        } catch (LockException e) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                    e.getMessage(), e );
        } finally {
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

