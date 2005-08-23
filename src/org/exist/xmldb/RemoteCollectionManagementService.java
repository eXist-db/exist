
package org.exist.xmldb;
import java.io.IOException;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.w3c.dom.Document;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;
import java.util.Date; 


public class RemoteCollectionManagementService implements CollectionManagementServiceImpl {

    protected XmlRpcClient client;
    protected RemoteCollection parent = null;

    public RemoteCollectionManagementService( RemoteCollection parent, XmlRpcClient client ) {
        this.client = client;
        this.parent = parent;
    }

    public Collection createCollection( String collName ) throws XMLDBException {
           return createCollection(collName, (Date)null);
    }

    public Collection createCollection( String collName, Date created) throws XMLDBException {
        String name = collName;
        if ( ( !collName.startsWith( "/db" ) ) && parent != null )
            name = parent.getPath() + "/" + collName;

        Vector params = new Vector();
        params.addElement( name );
        
        if (created != null) {
    		params.addElement( created );			
    		}
        
        try {
            client.execute( "createCollection", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe );
        }
        RemoteCollection collection =
            new RemoteCollection( client, (RemoteCollection) parent, name );
        parent.addChildCollection( collection );
        return collection;
    }
    
    
    

    /**
     *  Implements createCollection from interface CollectionManager. Gets
     *  called by some applications based on Xindice.
     *
     *@param  path                Description of the Parameter
     *@param  configuration       Description of the Parameter
     *@return                     Description of the Return Value
     *@exception  XMLDBException  Description of the Exception
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
        String name = collName;
        if ( !collName.startsWith( "/" ) )
            name = parent.getPath() + '/' + collName;

        Vector params = new Vector();
        params.addElement( name );
        try {
            client.execute( "removeCollection", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe);
        }
        parent.removeChildCollection( collName );
    }

    public void setCollection( Collection parent ) throws XMLDBException {
        this.parent = (RemoteCollection) parent;
    }

    public void setProperty( String property,
                             String value ) {
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionManagementServiceImpl#move(java.lang.String, java.lang.String, java.lang.String)
     */
    public void move(String collectionPath, String destinationPath, String newName) throws XMLDBException {
        if(!collectionPath.startsWith("/db"))
            collectionPath = parent.getPath() + '/' + collectionPath;
        if(destinationPath != null)
        {
        	if(!destinationPath.startsWith("/db"))
        		destinationPath = parent.getPath() + '/' + destinationPath;
        }
        else
        {
        	destinationPath = parent.getPath();
        }
        if(newName == null) {
            int p = collectionPath.lastIndexOf(('/'));
            newName = collectionPath.substring(p + 1);
        }
        Vector params = new Vector();
        params.addElement( collectionPath );
        params.addElement( destinationPath );
        params.addElement( newName );
        try {
            client.execute( "moveCollection", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.CollectionManagementServiceImpl#moveResource(java.lang.String, java.lang.String, java.lang.String)
     */
    public void moveResource(String resourcePath, String destinationPath, String newName) throws XMLDBException {
        if(!resourcePath.startsWith("/db"))
            resourcePath = parent.getPath() + '/' + resourcePath;
        if(destinationPath != null)
        {
        	if(!destinationPath.startsWith("/db"))
        		destinationPath = parent.getPath() + '/' + destinationPath;
        }
        else
        {
        	destinationPath = parent.getPath();
        }
        if(newName == null) {
            int p = resourcePath.lastIndexOf(('/'));
            newName = resourcePath.substring(p + 1);
        }
        Vector params = new Vector();
        params.addElement( resourcePath );
        params.addElement( destinationPath );
        params.addElement( newName );
        try {
            client.execute( "moveResource", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe);
        }
    }

    /* (non-Javadoc)
	 * @see org.exist.xmldb.CollectionManagementServiceImpl#copy(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void copy(String collectionPath, String destinationPath, String newName)
			throws XMLDBException {
		if(!collectionPath.startsWith("/db"))
            collectionPath = parent.getPath() + '/' + collectionPath;
        if(!destinationPath.startsWith("/db"))
            destinationPath = parent.getPath() + '/' + destinationPath;
        if(newName == null) {
            int p = collectionPath.lastIndexOf(('/'));
            newName = collectionPath.substring(p + 1);
        }
        Vector params = new Vector();
        params.addElement( collectionPath );
        params.addElement( destinationPath );
        params.addElement( newName );
        try {
            client.execute( "copyCollection", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe);
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
        if(newName == null) {
            int p = resourcePath.lastIndexOf(('/'));
            newName = resourcePath.substring(p + 1);
        }
        Vector params = new Vector();
        params.addElement( resourcePath );
        params.addElement( destinationPath );
        params.addElement( newName );
        try {
            client.execute( "copyResource", params );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                xre.getMessage(),
                xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                ioe.getMessage(),
                ioe);
        }
    }
}

