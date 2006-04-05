
package org.exist.security;

import java.io.IOException;
import java.util.Properties;

import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 *  Represents a user within the database.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class User {

    public final static User DEFAULT =
        new User( "guest", null, "guest" );
        
    private final static String GROUP = "group";
    private final static String NAME = "name";
    private final static String PASS = "password";
    private final static String USER_ID = "uid";
    private final static String HOME = "home";

    public final static int PLAIN_ENCODING = 0;
	public final static int SIMPLE_MD5_ENCODING = 1;
	public final static int MD5_ENCODING = 2;
	
	public static int PASSWORD_ENCODING;
	
	static {
		Properties props = new Properties(); 
		try {
			props.load(
					User.class.getClassLoader().getResourceAsStream("org/exist/security/security.properties")
			);
		} catch (IOException e) {
		}
		String encoding = props.getProperty("passwords.encoding", "md5");
		if(encoding != null) {
			if(encoding.equalsIgnoreCase("plain"))
				PASSWORD_ENCODING = PLAIN_ENCODING;
			else if(encoding.equalsIgnoreCase("md5"))
				PASSWORD_ENCODING = MD5_ENCODING;
			else
				PASSWORD_ENCODING = SIMPLE_MD5_ENCODING;
		}
	}
	
    private String[] groups = null;
    private String password = null;
    private String user;
    private int uid = -1;
    private String home = null;
    
    /** 
     * Indicates if the user belongs to the dba group,
     * i.e. is a superuser.
     */
    private boolean hasDbaRole = false;

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
        this.password = node.hasAttribute(PASS) ? node.getAttribute( PASS ) : null;
		String userId = node.getAttribute( USER_ID );
		if(userId == null)
			throw new DatabaseConfigurationException("attribute id missing");
		try {
			uid = Integer.parseInt(userId);
		} catch(NumberFormatException e) {
			throw new DatabaseConfigurationException("illegal user id: " + 
				userId + " for user " + user);
		}
		this.home = node.getAttribute( HOME );
		NodeList gl = node.getChildNodes();
        Node group;
        for ( int i = 0; i < gl.getLength(); i++ ) {
            group = gl.item( i );
            if(group.getNodeType() == Node.ELEMENT_NODE &&
            	group.getLocalName().equals(GROUP))
            	addGroup( group.getFirstChild().getNodeValue() );
        }
    }


    /**
     *  Add the user to a group
     *
     *@param  group  The feature to be added to the Group attribute
     */
    public final void addGroup( String group ) {
    	if (groups == null) {
    		groups = new String[1];
    		groups[0] = group;
    	} else {
    		int len = groups.length;
    		String[] ngroups = new String[len + 1];
    		System.arraycopy(groups, 0, ngroups, 0, len);
    		ngroups[len] = group;
    		groups = ngroups;
    	}
    	if (SecurityManager.DBA_GROUP.equals(group))
    		hasDbaRole = true;
    }

    public final void setGroups(String[] groups) {
    	this.groups = groups;
    	for (int i = 0; i < groups.length; i++)
    		if (SecurityManager.DBA_GROUP.equals(groups[i]))
    			hasDbaRole = true;
    }

    /**
     *  Get all groups this user belongs to
     *
     *@return    The groups value
     */
    public final String[] getGroups() {
        return groups == null ? new String[0] : groups;
    }

    public final boolean hasDbaRole() {
    	return hasDbaRole;
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
        if ( groups == null || groups.length == 0 )
            return null;
        return (String) groups[0];
    }


    /**
     *  Is the user a member of group?
     *
     *@param  group  Description of the Parameter
     *@return        Description of the Return Value
     */
    public final boolean hasGroup( String group ) {
    	if (groups == null)
    		return false;
        for (int i = 0; i < groups.length; i++) {
        	if (groups[i].equals(group))
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
        this.password = ( passwd == null ? null : digest( passwd ) );
    }


    /**
     *  Sets the passwordDigest attribute of the User object
     *
     *@param  passwd  The new passwordDigest value
     */
    public final void setPasswordDigest( String passwd ) {
        this.password = ( passwd == null ) ? null : passwd;
    }

    public final String digest(String passwd) {
    	switch(PASSWORD_ENCODING) {
    		case PLAIN_ENCODING:
    			return passwd;
    		case MD5_ENCODING:
    			return MD5.md(user + ":exist:" + passwd);
    		default:
    			return MD5.md(passwd);
    	}
    	
    }
    
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
            buf.append( '"' );
        }
		if( home != null ) {
			buf.append(" home=\"" );
			buf.append(home);
			buf.append("\">");
		} else
			buf.append(">");
        for (int i = 0; i < groups.length; i++) {
            buf.append( "<group>" );
            buf.append( groups[i] );
            buf.append( "</group>" );
        }
        buf.append( "</user>" );
        return buf.toString();
    }

    public final boolean validate( String passwd ) {
        if ( password == null )
            return true;
        if ( passwd == null )
            return false;
        return digest( passwd ).equals( password );
    }
    
    public void setUID(int uid) {
    	this.uid = uid;
    }
    
    public void setHome(String homeCollection) {
    	home = homeCollection;
    }
    
    public String getHome() {
    	return home;
    }
    
    /* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		User other = (User)obj;
		return uid == other.uid;
	}
}

