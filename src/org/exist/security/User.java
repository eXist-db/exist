
package org.exist.security;

import java.util.ArrayList;
import java.util.Iterator;

import org.exist.util.DatabaseConfigurationException;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    18. August 2002
 */
public class User {

    /**  Description of the Field */
    public final static User DEFAULT =
        new User( "guest", null, "guest" );
    private final static String GROUP = "group";

    private final static String NAME = "name";
    private final static String PASS = "password";
    private ArrayList groups = new ArrayList( 2 );
    private String password = null;

    private String user;


    /**
     *  Constructor for the User object
     *
     *@param  user      Description of the Parameter
     *@param  password  Description of the Parameter
     */
    public User( String user, String password ) {
        this.user = user;
        setPassword( password );

    }


    /**
     *  Constructor for the User object
     *
     *@param  user  Description of the Parameter
     */
    public User( String user ) {
        this.user = user;
    }


    /**
     *  Constructor for the User object
     *
     *@param  user          Description of the Parameter
     *@param  password      Description of the Parameter
     *@param  primaryGroup  Description of the Parameter
     */
    public User( String user, String password, String primaryGroup ) {
        this( user, password );
        addGroup( primaryGroup );
    }


    /**
     *  Constructor for the User object
     *
     *@param  node                                Description of the Parameter
     *@exception  DatabaseConfigurationException  Description of the Exception
     */
    public User( Element node ) throws DatabaseConfigurationException {
        this.user = node.getAttribute( NAME );
        if ( user == null )
            throw new DatabaseConfigurationException( "user needs a name" );
        this.password = node.getAttribute( PASS );

        NodeList gl = node.getElementsByTagName( GROUP );
        Element group;
        for ( int i = 0; i < gl.getLength(); i++ ) {
            group = (Element) gl.item( i );
            groups.add( group.getFirstChild().getNodeValue() );
        }

    }


    /**
     *  Adds a feature to the Group attribute of the User object
     *
     *@param  group  The feature to be added to the Group attribute
     */
    public final void addGroup( String group ) {
        groups.add( group );
    }


    /**
     *  Gets the groups attribute of the User object
     *
     *@return    The groups value
     */
    public final Iterator getGroups() {
        return groups.iterator();
    }


    /**
     *  Gets the user attribute of the User object
     *
     *@return    The user value
     */
    public final String getName() {
        return user;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public final String getPassword() {
        return password;
    }


    /**
     *  Gets the primaryGroup attribute of the User object
     *
     *@return    The primaryGroup value
     */
    public final String getPrimaryGroup() {
        if ( groups.size() == 0 )
            return null;
        return (String) groups.get( 0 );
    }


    /**
     *  Description of the Method
     *
     *@param  group  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final boolean hasGroup( String group ) {
        String g;
        for ( Iterator i = getGroups(); i.hasNext();  ) {
            g = (String) i.next();
            if ( g.equals( group ) )
                return true;
        }
        return false;
    }


    /**
     *  Sets the password attribute of the User object
     *
     *@param  passwd  The new password value
     */
    public final void setPassword( String passwd ) {
        this.password = ( passwd == null ? null : MD5.md( passwd ) );
    }


    /**
     *  Sets the passwordDigest attribute of the User object
     *
     *@param  passwd  The new passwordDigest value
     */
    public final void setPasswordDigest( String passwd ) {
        this.password = ( passwd == null ) ? null : passwd;
    }


    /**
     *  Description of the Method
     *
     *@return    Description of the Return Value
     */
    public final String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( "<user name=\"" );
        buf.append( user );
        buf.append( "\"" );
        if ( password != null ) {
            buf.append( " password=\"" );
            buf.append( password );
            buf.append( "\">" );
        }
        else
            buf.append( ">" );

        String group;
        for ( Iterator i = groups.iterator(); i.hasNext();  ) {
            group = (String) i.next();
            buf.append( "<group>" );
            buf.append( group );
            buf.append( "</group>" );
        }
        buf.append( "</user>" );
        return buf.toString();
    }


    /**
     *  Description of the Method
     *
     *@param  passwd  Description of the Parameter
     *@return         Description of the Return Value
     */
    public final boolean validate( String passwd ) {
        if ( password == null )
            return true;
        if ( passwd == null )
            return false;
        return MD5.md( passwd ).equals( password );
    }
}

