
package org.exist.xmldb;

import org.apache.xindice.client.xmldb.services.CollectionManager;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Document;
import org.apache.log4j.Category;
import org.xmldb.api.base.*;

public class LocalCollectionManagementService extends CollectionManager {
    protected BrokerPool brokerPool;

    protected LocalCollection parent = null;
    protected User user;

    private static Category LOG =
        Category.getInstance( LocalCollection.class.getName() );
        
    /**
     *  Constructor for the LocalCollectionManagementService object
     *
     *@param  pool    Description of the Parameter
     *@param  parent  Description of the Parameter
     *@param  user    Description of the Parameter
     */
    public LocalCollectionManagementService( User user, BrokerPool pool,
                                             LocalCollection parent ) {
        if ( user == null )
            throw new RuntimeException();
        this.parent = parent;
        this.brokerPool = pool;
        this.user = user;
    }


    /**
     *  Description of the Method
     *
     *@param  collName            Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  XMLDBException  Description of the Exception
     */
    public Collection createCollection( String collName ) throws XMLDBException {
        collName = parent.getPath() + '/' + collName;
        DBBroker broker = null;
        try {
            broker = brokerPool.get();
            org.exist.dom.Collection coll =
                broker.getOrCreateCollection( user, collName );
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


    /**
     *  Gets the name attribute of the LocalCollectionManagementService object
     *
     *@return                     The name value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getName() throws XMLDBException {
        return "CollectionManagementService";
    }


    /**
     *  Gets the property attribute of the LocalCollectionManagementService
     *  object
     *
     *@param  property  Description of the Parameter
     *@return           The property value
     */
    public String getProperty( String property ) {
        return null;
    }


    /**
     *  Gets the version attribute of the LocalCollectionManagementService
     *  object
     *
     *@return                     The version value
     *@exception  XMLDBException  Description of the Exception
     */
    public String getVersion() throws XMLDBException {
        return "1.0";
    }


    /**
     *  Description of the Method
     *
     *@param  collName            Description of the Parameter
     *@exception  XMLDBException  Description of the Exception
     */
    public void removeCollection( String collName ) throws XMLDBException {
    	String path = (collName.startsWith("/db") ? collName : 
    		parent.getPath() + '/' + collName);
        DBBroker broker = null;
        try {
            broker = brokerPool.get();
            LOG.debug( "removing collection " + path );
            broker.removeCollection( user, path );
            //parent.getCollection().removeCollection( collName );
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


    /**
     *  Sets the collection attribute of the LocalCollectionManagementService
     *  object
     *
     *@param  parent              The new collection value
     *@exception  XMLDBException  Description of the Exception
     */
    public void setCollection( Collection parent ) throws XMLDBException {
        this.parent = (LocalCollection) parent;
    }


    /**
     *  Sets the property attribute of the LocalCollectionManagementService
     *  object
     *
     *@param  property  The new property value
     *@param  value     The new property value
     */
    public void setProperty( String property,
                             String value ) {
    }
}

