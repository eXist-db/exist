
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
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
 *  $Id:
 */
package org.exist.xmldb;

import java.util.Iterator;
import java.util.Random;
import java.util.ArrayList;
import java.util.Observable;
import java.util.Observer;
import java.io.File;
import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.Parser;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.XMLResource;

/**
 *  A local implementation of the Collection interface. This
 * is used when the database is running in embedded mode.
 *
 * Extends Observable to allow status callbacks during indexing.
 * Methods storeResource notifies registered observers about the
 * progress of the indexer. It passed an Object of type
 * ProgressIndicator to the Observer.
 * 
 *@author     wolf
 *@created    April 2, 2002
 */
public class LocalCollection extends Observable implements Collection {

    private static Category LOG =
        Category.getInstance( LocalCollection.class.getName() );

    protected BrokerPool brokerPool = null;
    protected org.exist.dom.Collection collection = null;
    protected String encoding = "ISO-8859-1";
    protected boolean indentXML = true;
    protected LocalCollection parent = null;
    protected boolean saxDocumentEvents = true;
    protected boolean processXInclude = true;
    protected User user = null;
	
	protected ArrayList observers = new ArrayList(1);
	
    public LocalCollection( User user, BrokerPool brokerPool, String collection )
         throws XMLDBException {
        this( user, brokerPool, null, collection );
    }

    public LocalCollection( User user, BrokerPool brokerPool,
                            LocalCollection parent, org.exist.dom.Collection collection ) {
        this.user = user;
        this.brokerPool = brokerPool;
        this.parent = parent;
        this.collection = collection;
    }


    public LocalCollection( User user, BrokerPool brokerPool,
                            LocalCollection parent,
                            String name ) throws XMLDBException {
        if ( user == null )
            user = new User( "guest", "guest", "guest" );
        this.user = user;
        this.parent = parent;
        this.brokerPool = brokerPool;
		load(name);
    }

    private void load(String name) throws XMLDBException {
		DBBroker broker = null;
        try {
            broker = brokerPool.get();
            if ( name == null )
                name = "/db";

            collection =
                broker.getCollection( name );
            if ( collection == null )
                throw new XMLDBException( ErrorCodes.NO_SUCH_RESOURCE,
                    "collection not found" );
        } catch ( EXistException e ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                e.getMessage(),
                e );
        } finally {
            brokerPool.release( broker );
        }
    }

    protected boolean checkOwner( User user ) {
        return user.getName().equals( collection.getPermissions().getOwner() );
    }


    protected boolean checkPermissions( int perm ) {
        return collection.getPermissions().validate( user, perm );
    }


	/**
	 * Close the current collection. Calling this method will flush all
	 * open buffers to disk.
	 */
    public void close() throws XMLDBException {
		DBBroker broker = null;
		try {
			broker = brokerPool.get();
			broker.sync();
		} catch (EXistException e) {
			throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
		} finally {
			brokerPool.release(broker);
		}
    }


    public String createId() throws XMLDBException {
        String id;
        Random rand = new Random();
        boolean ok;
        do {
            ok = true;
            id = Integer.toHexString( rand.nextInt() ) + ".xml";
            // check if this id does already exist
            if ( collection.hasDocument( id ) )
                ok = false;

            if ( collection.hasSubcollection( id ) )
                ok = false;

        } while ( !ok )
            ;
        return id;
    }


    public Resource createResource( String id,
                                    String type ) throws XMLDBException {
        if ( id == null )
            id = createId();

        LocalXMLResource r =
            new LocalXMLResource( user, brokerPool, this, id, -1, indentXML );
        r.setEncoding( encoding );
        r.setSAXDocEvents( this.saxDocumentEvents );
	r.setProcessXInclude( processXInclude );
        return r;
    }


    public Collection getChildCollection( String name )
         throws XMLDBException {
        if ( !checkPermissions( Permission.READ ) )
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "you are not allowed to access this collection" );
        String cname;
        for ( Iterator i = collection.collectionIterator();
            i.hasNext();  ) {
            cname = (String) i.next();
            if ( cname.equals( name ) ) {
                cname = getPath() + '/' + cname;
                Collection temp =
                    new LocalCollection( user, brokerPool, this, cname );
                return temp;
            }
        }
        return null;
    }


    public int getChildCollectionCount() throws XMLDBException {
        if ( collection.getPermissions().validate( user, Permission.READ ) )
            return collection.getChildCollectionCount();
        else
            return 0;
    }


    protected org.exist.dom.Collection getCollection() {
        return collection;
    }


    public String getName() throws XMLDBException {
        return collection.getName();
    }


    public Collection getParentCollection() throws XMLDBException {
        if(getName().equals("/db"))
            return null;
        if( parent == null && collection != null )
            parent = new LocalCollection( user, brokerPool, null, collection.getParent() );
//        if ( parent == null )
//            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, "parent is null" );
        return parent;
    }


    public String getPath() throws XMLDBException {
        //if (parent == null)
        return collection.getName();
        //return (parent.getName().equals("/") ? '/' + collection.getName() :
        //       parent.getPath() + '/' + collection.getName());
    }


    public String getProperty( String property ) throws XMLDBException {
        if ( property.equals( "pretty" ) )
            return indentXML ? "true" : "false";
        if ( property.equals( "encoding" ) )
            return encoding;
        if ( property.equals( "sax-document-events" ) )
            return saxDocumentEvents ? "true" : "false";
		if ( property.equals( "expand-xincludes" ) )
			return processXInclude ? "true" : "false";
        return null;
    }


    public Resource getResource( String id ) throws XMLDBException {
        if ( !collection.getPermissions().validate( user, Permission.READ ) )
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                "not allowed to read collection" );
        String name =
            collection.getName() + '/' + id;
        DocumentImpl document =
            collection.getDocument( name );
        if ( document == null )
            return null;
        LocalXMLResource r =
            new LocalXMLResource( user, brokerPool, this, document, -1, indentXML );
        r.setEncoding( encoding );
        r.setSAXDocEvents( saxDocumentEvents );
		r.setProcessXInclude( processXInclude );
        return r;
    }


    public int getResourceCount() throws XMLDBException {
        if ( !collection.getPermissions().validate( user, Permission.READ ) )
            return 0;
        else
            return collection.getDocumentCount();
    }

    public Service getService( String name,
                               String version ) throws XMLDBException {
        if ( name.equals( "XPathQueryService" ) )
            return new LocalXPathQueryService( user, brokerPool, this );

        if ( name.equals( "CollectionManagementService" ) ||
            name.equals( "CollectionManager" ) )
            return new LocalCollectionManagementService( user, brokerPool, this );

        if ( name.equals( "UserManagementService" ) )
            return new LocalUserManagementService( user, brokerPool, this );
        
        if ( name.equals( "DatabaseInstanceManager" ) )
        	return new LocalDatabaseInstanceManager( user, brokerPool );
        
        if ( name.equals( "XUpdateQueryService" ) )
            return new LocalXUpdateQueryService( user, brokerPool, this );
            
        if ( name.equals( "IndexQueryService" ) )
        	return new LocalIndexQueryService( user, brokerPool, this );
        	
        throw new XMLDBException( ErrorCodes.NO_SUCH_SERVICE );
    }


    public Service[] getServices() throws XMLDBException {
        Service[] services = new Service[6];
        services[0] = new LocalXPathQueryService( user, brokerPool, this );
        services[1] = new LocalCollectionManagementService( user, brokerPool, this );
        services[2] = new LocalUserManagementService( user, brokerPool, this );
        services[3] = new LocalDatabaseInstanceManager( user, brokerPool );
        services[4] = new LocalXUpdateQueryService( user, brokerPool, this );
        services[5] = new LocalIndexQueryService( user, brokerPool, this );
        return services; // jmv null;
    }


    protected boolean hasChildCollection( String name ) {
        return collection.hasSubcollection( name );
    }


    public boolean isOpen() throws XMLDBException {
        return true;
    }


    public boolean isValid() {
        return collection != null;
    }


    public String[] listChildCollections() throws XMLDBException {
        if ( !checkPermissions( Permission.READ ) )
            return new String[0];
        String[] collections = new String[collection.getChildCollectionCount()];
        int j = 0;
        for ( Iterator i = collection.collectionIterator(); i.hasNext(); j++ )
            collections[j] = (String) i.next();
        return collections;
    }


    public String[] listResources() throws XMLDBException {
        if ( !collection.getPermissions().validate( user, Permission.READ ) )
            return new String[0];
        String[] resources = new String[collection.getDocumentCount()];
        int j = 0;
        int p;
        DocumentImpl doc;
        String resource;
        for ( Iterator i = collection.iterator(); i.hasNext(); j++ ) {
            doc = (DocumentImpl) i.next();
            resource = doc.getFileName();
            p = resource.lastIndexOf( '/' );
            resources[j] =
                ( p < 0 ? resource : resource.substring( p + 1 ) );
        }
        return resources;
    }


    public void registerService( Service serv ) throws XMLDBException {
        throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
    }

    public void removeResource( Resource res ) throws XMLDBException {
        if( !(res instanceof XMLResource) )
            throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
        if ( res == null )
            return;
        String name = getPath();
        name = name + '/' + ((XMLResource)res).getDocumentId();
        DocumentImpl doc = collection.getDocument( name );
        if ( doc == null )
            throw new XMLDBException( ErrorCodes.INVALID_RESOURCE,
                "resource " + name + " not found" );
        DBBroker broker = null;
        try {
            broker = brokerPool.get();
            broker.removeDocument( user, name );
        } catch ( EXistException e ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                e.getMessage(),
                e );
        } catch ( PermissionDeniedException e ) {
            throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                e.getMessage(),
                e );
        } finally {
            brokerPool.release( broker );
        }
		load(getPath());
    }


    public void setProperty( String property,
                             String value ) throws XMLDBException {
        if ( property.equals( "pretty" ) )
            indentXML = value.equals( "true" );

        if ( property.equals( "encoding" ) )
            encoding = value;

        if ( property.equals( "sax-document-events" ) )
            saxDocumentEvents = value.equals( "true" );

		if ( property.equals( "expand-xincludes" ) )
			processXInclude = value.equals( "true" );
    }


    protected void setUser( User user ) {
        this.user = user;
    }


    public void storeResource( Resource resource ) throws XMLDBException {
        if( !(resource instanceof LocalXMLResource) )
            throw new XMLDBException( ErrorCodes.NOT_IMPLEMENTED );
		final LocalXMLResource res = (LocalXMLResource)resource;
        final String name = getPath() + '/' + res.getDocumentId();
        DBBroker broker = null;
        try {
            broker = brokerPool.get();
            broker.flush();
            final Parser parser = new Parser( broker, user, true );
			Observer observer;
			for(Iterator i = observers.iterator(); i.hasNext(); ) {
				observer = (Observer)i.next();
				parser.addObserver( observer );
				broker.addObserver( observer );
			}
            LOG.debug( "storing document " + res.getDocumentId() );
			if(res.file != null)
                res.document = parser.parse( collection, (File)res.file, name );
			else if(res.root != null)
				res.document = parser.parse( collection, res.root, name);
			else
				res.document = parser.parse( collection, res.content, name);
            broker.flush();
        } catch ( Exception e ) {
            e.printStackTrace();
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                e.getMessage(),
                e );
        } finally {
            brokerPool.release( broker );
        }
    }

	/**
	 * Add a new observer to the list. Observers are just passed
	 * on to the indexer to be notified about the indexing progress.
	 */
	public void addObserver(Observer o) {
		observers.add(o);
	}
}

