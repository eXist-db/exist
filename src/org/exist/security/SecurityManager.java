
package org.exist.security;
import java.util.Iterator;
import java.io.IOException;
import java.util.TreeMap;

import org.apache.log4j.Category;
import org.exist.Parser;
import org.exist.dom.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.EXistException;
import org.exist.storage.DBBroker;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    19. August 2002
 */
public class SecurityManager {
    /**  Description of the Field */
    public final static String ACL_FILE = "users.xml";
    /**  Description of the Field */
    public final static String DBA_GROUP = "dba";

    /**  Description of the Field */
    public final static String DBA_USER = "admin";

    /**  Description of the Field */
    public final static String GUEST_GROUP = "guest";
    /**  Description of the Field */
    public final static String GUEST_USER = "guest";

    private static Category LOG =
        Category.getInstance( SecurityManager.class.getName() );
    /**  Description of the Field */
    public final static String SYSTEM = "/db/system";

    private BrokerPool pool;
    private TreeMap users = new TreeMap();


    /**
     *  Constructor for the SecurityManager object
     *
     *@param  pool       Description of the Parameter
     *@param  sysBroker  Description of the Parameter
     */
    public SecurityManager( BrokerPool pool, DBBroker sysBroker ) {
        this.pool = pool;
        DBBroker broker = sysBroker;

        try {
            Collection sysCollection = broker.getCollection( SYSTEM );
            if(sysCollection == null) {
            	sysCollection = broker.getOrCreateCollection( SYSTEM );
            	broker.saveCollection( sysCollection );
            }
            sysCollection.setPermissions( 0777 );
            Document acl = broker.getDocument( SYSTEM + '/' + ACL_FILE );
            Element docElement = null;
            if(acl != null)
            	docElement = acl.getDocumentElement();
            if ( docElement == null ) {
                LOG.debug( "creating system users" );
                User user = new User( DBA_USER, null );
                user.addGroup( DBA_GROUP );
                users.put( user.getName(), user );
                user = new User( GUEST_USER, GUEST_USER, GUEST_GROUP );
                users.put( user.getName(), user );
                save( broker );
            }
            else {
                LOG.debug( "loading acl" );
                NodeList ul = acl.getDocumentElement().getElementsByTagName( "user" );
                Element node;
                User user;
                for ( int i = 0; i < ul.getLength(); i++ ) {
                    node = (Element) ul.item( i );
                    user = new User( node );
                    users.put( user.getName(), user );
                }
            }
        } catch ( Exception e ) {
            e.printStackTrace();
            LOG.debug( "loading acl failed: " + e.getMessage() );
        }
    }


    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     */
    public synchronized void deleteUser( String name ) {
        if ( users.containsKey( name ) )
            users.remove( name );
        DBBroker broker = null;
        try {
            broker = pool.get();
            save( broker );
        } catch ( EXistException e ) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
    }


    /**
     *  Gets the user attribute of the SecurityManager object
     *
     *@param  name  Description of the Parameter
     *@return       The user value
     */
    public synchronized User getUser( String name ) {
        return (User) users.get( name );
    }


    /**
     *  Gets the users attribute of the SecurityManager object
     *
     *@return    The users value
     */
    public synchronized User[] getUsers() {
        User u[] = new User[users.size()];
        int j = 0;
        for ( Iterator i = users.values().iterator(); i.hasNext(); j++ )
            u[j] = (User) i.next();

        return u;
    }


    /**
     *  Description of the Method
     *
     *@param  user  Description of the Parameter
     *@return       Description of the Return Value
     */
    public synchronized boolean hasAdminPrivileges( User user ) {
        return user.hasGroup( DBA_GROUP );
    }


    /**
     *  Description of the Method
     *
     *@param  name  Description of the Parameter
     *@return       Description of the Return Value
     */
    public synchronized boolean hasUser( String name ) {
        return users.containsKey( name );
    }


    /**
     *  Description of the Method
     *
     *@param  broker              Description of the Parameter
     *@exception  EXistException  Description of the Exception
     */
    public synchronized void save( DBBroker broker )
         throws EXistException {
         LOG.debug("storing acl file");
        StringBuffer buf = new StringBuffer();
        buf.append( "<users>" );
        for ( Iterator i = users.values().iterator(); i.hasNext();  )
            buf.append( ( (User) i.next() ).toString() );

        buf.append( "</users>" );

        broker.flush();
        broker.sync();
        try {
            Parser parser = new Parser( broker, getUser( DBA_USER ), true );
            DocumentImpl doc =
                parser.parse( buf.toString(), SYSTEM + '/' + ACL_FILE );
            doc.setPermissions( 0770 );
            broker.saveCollection( doc.getCollection() );
        } catch ( IOException e ) {
            e.printStackTrace();
        } catch ( SAXException e ) {
            e.printStackTrace();
        } catch ( PermissionDeniedException e ) {
            e.printStackTrace();
        }
        broker.flush();
        broker.sync();
    }


    /**
     *  Description of the Method
     *
     *@param  user  Description of the Parameter
     */
    public synchronized void setUser( User user ) {
        users.put( user.getName(), user );
        DBBroker broker = null;
        try {
            broker = pool.get();
            save( broker );
        } catch ( EXistException e ) {
            e.printStackTrace();
        } finally {
            pool.release( broker );
        }
    }
}

