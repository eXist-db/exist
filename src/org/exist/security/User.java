
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

    public final static User DEFAULT =
        new User( "guest", null, "guest" );
        
    private final static String GROUP = "group";
    private final static String NAME = "name";
    private final static String PASS = "password";
    private final static String USER_ID = "uid";
    
    private ArrayList groups = new ArrayList( 2 );
    private String password = null;
    private String user;
    private int uid = -1;


    /**
     *  Create a new user with name and password
     *
     *@param  user      Description of the Parameter
     *@param  password  Description of the Parameter
     */
    public User( String user, String password ) {
        this.user = user;
        setPassword( password );

    }


    /**
     *  Create a new user with name
     *
     *@param  user  Description of the Parameter
     */
    public User( String user ) {
        this.user = user;
    }


    /**
     *  Create a new user with name, password and primary group
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
     *  Read a new user from the given DOM node
     *
     *@param  node                                Description of the Parameter
     *@exception  DatabaseConfigurationException  Description of the Exception
     */
    public User( Element node ) throws DatabaseConfigurationException {
        this.user = node.getAttribute( NAME );
        if ( user == null )
            throw new DatabaseConfigurationException( "user needs a name" );
        this.password = node.getAttribute( PASS );
		String userId = node.getAttribute( USER_ID );
		if(userId == null)
			throw new DatabaseConfigurationException("attribute id missing");
		try {
			uid = Integer.parseInt(userId);
		} catch(NumberFormatException e) {
			throw new DatabaseConfigurationException("illegal user id: " + 
				userId + " for user " + user);
		}
        NodeList gl = node.getElementsByTagName( GROUP );
        Element group;
        for ( int i = 0; i < gl.getLength(); i++ ) {
            group = (Element) gl.item( i );
            groups.add( group.getFirstChild().getNodeValue() );
        }
    }


    /**
     *  Add the user to a group
     *
     *@param  group  The feature to be added to the Group attribute
     */
    public final void addGroup( String group ) {
        groups.add( group );
    }


    /**
     *  Get all groups this user belongs to
     *
     *@return    The groups value
     */
    public final Iterator getGroups() {
        return groups.iterator();
    }


    /**
     *  Get the user name
     *
     *@return    The user value
     */
    public final String getName() {
        return user;
    }

	public final int getUID() {
		return uid;
	}

    /**
     *  Get the user's password
     *
     *@return    Description of the Return Value
     */
    public final String getPassword() {
        return password;
    }


    /**
     *  Get the primary group this user belongs to
     *
     *@return    The primaryGroup value
     */
    public final String getPrimaryGroup() {
        if ( groups.size() == 0 )
            return null;
        return (String) groups.get( 0 );
    }


    /**
     *  Is the user a member of group?
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
        buf.append( "\" " );
        buf.append( "uid=\"");
        buf.append( Integer.toString(uid) );
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
    
    protected void setUID(int uid) {
    	this.uid = uid;
    }
}

