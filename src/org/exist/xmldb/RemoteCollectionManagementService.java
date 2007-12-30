/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
 * http://exist-db.org
 *  
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */

package org.exist.xmldb;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;
import org.w3c.dom.Document;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;


public class RemoteCollectionManagementService implements CollectionManagementServiceImpl {

    protected XmlRpcClient client;
    protected RemoteCollection parent = null;

    public RemoteCollectionManagementService( RemoteCollection parent, XmlRpcClient client ) {
        this.client = client;
        this.parent = parent;
    }

    public Collection createCollection( String collName ) throws XMLDBException {
        return createCollection (collName, (Date)null);
    }

    public Collection createCollection( XmldbURI collName ) throws XMLDBException {
        return createCollection (collName, (Date)null);
    }

    public Collection createCollection( String collName, Date created ) throws XMLDBException {
    	try{
    		return createCollection(XmldbURI.xmldbUriFor(collName), created);
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    
    public Collection createCollection( XmldbURI collName, Date created ) throws XMLDBException {
        if (parent != null)
        	collName = parent.getPathURI().resolveCollectionPath(collName);        

        Vector params = new Vector();
        params.addElement( collName.toString() );
        
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
            new RemoteCollection( client, (RemoteCollection) parent, collName );
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
    	try{
    		removeCollection(XmldbURI.xmldbUriFor(collName));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    
    public void removeCollection( XmldbURI collName ) throws XMLDBException {
        if (parent != null)
        	collName = parent.getPathURI().resolveCollectionPath(collName);        

        Vector params = new Vector();
        params.addElement( collName.toString() );
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

    public void move(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		move(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(URISyntaxException e) {
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
    	
        if(newName == null) {
            newName = collectionPath.lastSegment();
        }
        Vector params = new Vector();
        params.addElement( collectionPath.toString() );
        params.addElement( destinationPath.toString() );
        params.addElement( newName.toString() );
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

    public void moveResource(String resourcePath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		moveResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    public void moveResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) 
    		throws XMLDBException { 
    	resourcePath = parent.getPathURI().resolveCollectionPath(resourcePath);
	if (destinationPath == null)
	    destinationPath = resourcePath.removeLastSegment();
    	else
	    destinationPath = parent.getPathURI().resolveCollectionPath(destinationPath);
        if(newName == null) {
            newName = resourcePath.lastSegment();
        }

        Vector params = new Vector();
        params.addElement( resourcePath.toString() );
        params.addElement( destinationPath.toString() );
        params.addElement( newName.toString() );
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

    public void copy(String collectionPath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		copy(XmldbURI.xmldbUriFor(collectionPath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(URISyntaxException e) {
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

        if(newName == null) {
            newName = collectionPath.lastSegment();
        }

        Vector params = new Vector();
        params.addElement( collectionPath.toString() );
        params.addElement( destinationPath.toString() );
        params.addElement( newName.toString() );
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
	
    public void copyResource(String resourcePath, String destinationPath,
            String newName) throws XMLDBException {
    	try{
    		copyResource(XmldbURI.xmldbUriFor(resourcePath), XmldbURI.xmldbUriFor(destinationPath),XmldbURI.xmldbUriFor(newName));
    	} catch(URISyntaxException e) {
    		throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }
    
    public void copyResource(XmldbURI resourcePath, XmldbURI destinationPath, XmldbURI newName) 
    		throws XMLDBException { 
    	resourcePath = parent.getPathURI().resolveCollectionPath(resourcePath);
    	if (destinationPath == null)
	    destinationPath = resourcePath.removeLastSegment();
    	else
	    destinationPath = parent.getPathURI().resolveCollectionPath(destinationPath);
        if(newName == null) {
            newName = resourcePath.lastSegment();
        }
        Vector params = new Vector();
        params.addElement( resourcePath.toString() );
        params.addElement( destinationPath.toString() );
        params.addElement( newName.toString() );
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

