/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; er version.
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

import java.io.File;
import java.net.MalformedURLException;
import java.util.StringTokenizer;

import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.exist.EXistException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * The XMLDB driver class for eXist. This driver manages two different
 * internal implementations. The first communicates with a remote 
 * database using the XMLRPC protocol. The second has direct access
 * to an embedded database instance running in the same virtual machine.
 * The driver chooses an implementation depending on the XML:DB URI passed
 * to getCollection().
 * 
 * When running in embedded mode, the driver can create a new database
 * instance if none is available yet. It will do so if the property
 * "create-database" is set to "true" or if there is a system property
 * "exist.initdb" with value "true".
 * 
 * You may optionally provide the location of an alternate configuration
 * file through the "configuration" property. The driver is also able to
 * address different database instances - which may have been installed at
 * different places.
 * 
 * @author Wolfgang Meier
 */
public class DatabaseImpl implements Database {

    protected final static String DEFAULT_HOST = "localhost:8081";
    protected final static String DEFAULT_NAME = "exist";
    
    protected final static int LOCAL = 0;
    protected final static int REMOTE = 1;
    
    protected boolean autoCreate = false;
    protected String configuration = null;
    protected String dbName = DEFAULT_NAME;
    protected String selector = dbName + ':'; 
    protected XmlRpcClient rpcClient;
    protected ShutdownListener shutdown = null;
	protected int mode = 0;
	
    public DatabaseImpl() {
        try {
            XmlRpc.setEncoding( "UTF-8" );
        } catch ( Exception e ) {
        }
    	String initdb = System.getProperty( "exist.initdb" );
    	if(initdb != null)
    		autoCreate = initdb.equalsIgnoreCase("true");
    }

    public static Collection readCollection( String c, XmlRpcClient rpcClient,
                                             String address ) throws XMLDBException {
        StringTokenizer tok = new StringTokenizer( c, "/" );
        String temp = tok.nextToken();
        if(temp.equals("db"))
        	temp = '/' + temp;
        Collection current =
            new RemoteCollection( rpcClient, null, address, temp );
        while ( current != null && tok.hasMoreTokens() ) {
            temp = tok.nextToken();
            current =
                current.getChildCollection( ( (RemoteCollection) current ).getPath() + '/' + temp );
        }
        return current;
    }

    public boolean acceptsURI( String uri ) throws XMLDBException {
        return uri.startsWith( selector );
    }


    /**
     *  In embedded mode: configure the database instance
     *
     *@exception  XMLDBException  Description of the Exception
     */
    private void configure() throws XMLDBException {
        String home, file = "conf.xml";
        if(configuration == null) {
        	home = System.getProperty( "exist.home" );
        	if ( home == null )
            	home = System.getProperty( "user.dir" );
        } else {
        	File f = new File(configuration);
        	home = f.getParentFile().getAbsolutePath();
        	file = f.getName();
        }
		System.out.println("configuring " + dbName + " using " + home + '/' + file);
        try {
            Configuration config = new Configuration( file, home );
            BrokerPool.configure( dbName, 1, 5, config );
            if(shutdown != null)
            	BrokerPool.getInstance(dbName).registerShutdownListener(shutdown);
        } catch ( Exception e ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                "configuration error", e );
        }
    }

    public Collection getCollection( String collection, String user,
                                     String password ) throws XMLDBException {
        if ( !collection.startsWith( selector ) )
            throw new XMLDBException( ErrorCodes.INVALID_DATABASE,
                "collection " + collection +
                " does not start with '" + selector + "'" );
        String address = DEFAULT_HOST;
        String c = collection.substring( selector.length() );
        if(c.endsWith("/") && c.length() > 1)
            c = c.substring(0, c.length() - 1);
        Collection current = null;
        if ( c.startsWith( "///" ) ) {
        	mode = LOCAL;
            // use local database instance
            if ( !BrokerPool.isConfigured( dbName ) ) {
                if ( autoCreate )
                    configure();
                else
                    throw new XMLDBException( ErrorCodes.COLLECTION_CLOSED,
                        "local database server not running" );
            }
            BrokerPool pool;
            try {
                pool = BrokerPool.getInstance( dbName );
            } catch ( EXistException e ) {
                throw new XMLDBException( ErrorCodes.VENDOR_ERROR,
                    "db not correctly initialized",
                    e );
            }
            User u = null;
            if ( user == null ) {
                user = "guest";
                password = "guest";
            }
            if ( user != null ) {
                u = pool.getSecurityManager().getUser( user );
                if ( u == null ) {
                	throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                        "user " + user + " does not exist" );
                }
                if ( !u.validate( password ) ) {
                	throw new XMLDBException( ErrorCodes.PERMISSION_DENIED,
                        "invalid password" );
                }
            }
            try {
                current = new LocalCollection( u, pool, c.substring( 2 ) );
                return ( current != null )
                     ? current : null;
            } catch ( XMLDBException e ) {
                switch ( e.errorCode ) {
                    case ErrorCodes.NO_SUCH_RESOURCE:
                    case ErrorCodes.NO_SUCH_COLLECTION:
                    case ErrorCodes.INVALID_COLLECTION:
                    case ErrorCodes.INVALID_RESOURCE:
                        return null;
                    default:
                        throw e;
                }
            }
        }
        else if ( c.startsWith( "//" ) ) {
            // use remote database via XML-RPC
            mode = REMOTE;
            if ( user == null ) {
                user = "guest";
                password = "guest";
            } else if(password == null)
                password = "";
            // try to figure out server address
            int p = 0;
            if ( ( p = c.indexOf( "/db", 2 ) ) > -1 ) {
                address = "http://" + c.substring( 2, p );
                c = c.substring( p );
            }
            else
                throw new XMLDBException( ErrorCodes.INVALID_DATABASE,
                    "malformed url: " + address );
            if ( rpcClient == null )
                try {
                    rpcClient = new XmlRpcClient( address );
                } catch ( MalformedURLException e ) {
                    throw new XMLDBException( ErrorCodes.INVALID_DATABASE,
                        "malformed url: " + address,
                        e );
                }
            rpcClient.setBasicAuthentication( user, password );
            return readCollection( c, rpcClient, address );
        }
        else
            throw new XMLDBException( ErrorCodes.INVALID_DATABASE,
                "malformed url: " + address );
    }

    public String getConformanceLevel() throws XMLDBException {
        return "0";
    }

    public String getName() throws XMLDBException {
        return dbName;
    }
	
	/**
	 * Register a ShutdownListener for the current database instance. The ShutdownListener is called
	 * after the database has shut down. You have to register a listener before any calls to getCollection().
	 * 
	 * @param listener
	 * @throws XMLDBException
	 */
	public void setDatabaseShutdownListener(ShutdownListener listener) throws XMLDBException {
		shutdown = listener;
	}
	
    public String getProperty( String property ) throws XMLDBException {
        if ( property.equals( "create-database" ) )
            return Boolean.valueOf( autoCreate ).toString();
        if ( property.equals( "database-id" ) )
        	return dbName;
        if ( property.equals( "configuration" ) )
        	return configuration;
        return null;
    }

    public void setProperty( String property, String value ) throws XMLDBException {
        if ( property.equals( "create-database" ) )
            autoCreate = value.equals( "true" );
		if ( property.equals( "database-id" ) ) {
			dbName = value;
			selector = dbName + ':';
		}
		if ( property.equals( "configuration" ) )
			configuration = value;
    }

}

