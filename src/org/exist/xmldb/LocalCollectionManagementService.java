
package org.exist.xmldb;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.apache.log4j.Category;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;

public class LocalCollectionManagementService implements CollectionManagementService {
    protected BrokerPool brokerPool;

    protected LocalCollection parent = null;
    protected User user;

    private static Category LOG =
        Category.getInstance( LocalCollection.class.getName() );
        
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

    public void setCollection( Collection parent ) throws XMLDBException {
        this.parent = (LocalCollection) parent;
    }

    public void setProperty( String property,
                             String value ) {
    }
}

