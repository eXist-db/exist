package org.exist.security;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import org.exist.util.SyntaxException;
import org.exist.util.VariableByteInputStream;
import org.exist.util.VariableByteOutputStream;

/**
 *  Manages the permissions assigned to a ressource. This includes
 *  the user who owns the ressource, the owner group and the permissions
 *  for user, group and others. Permissions are encoded in a single byte
 *  according to common unix conventions.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class Permission {

    /**  Description of the Field */
    public final static int DEFAULT_PERM = 0755;
    /**  Description of the Field */
    public final static String DEFAULT_STRING = "other";
    /**  Description of the Field */
    public final static String GROUP_STRING = "group";

    /**  Description of the Field */
    public final static int READ = 4;
    /**  Description of the Field */
    public final static int UPDATE = 1;

    /**  Description of the Field */
    public final static String USER_STRING = "user";
    /**  Description of the Field */
    public final static int WRITE = 2;

    private String owner = SecurityManager.DBA_USER;
    private String ownerGroup = SecurityManager.DBA_GROUP;

    private int permissions = DEFAULT_PERM;


    public Permission() { }


    /**
     *  Construct a Permission with given permissions 
     *
     *@param  perm  Description of the Parameter
     */
    public Permission( int perm ) {
        this.permissions = perm;
    }


    /**
     *  Construct a Permission with given user and group
     *
     *@param  user   Description of the Parameter
     *@param  group  Description of the Parameter
     */
    public Permission( String user, String group ) {
        this.owner = user;
        this.ownerGroup = group;
    }


    /**
     *  Construct a permission with given user, group and
     *  permissions
     *
     *@param  user         Description of the Parameter
     *@param  group        Description of the Parameter
     *@param  permissions  Description of the Parameter
     */
    public Permission( String user, String group, int permissions ) {
        this.owner = user;
        this.ownerGroup = group;
        this.permissions = permissions;
    }


    /**
     *  Description of the Method
     *
     *@param  args           Description of the Parameter
     *@exception  Exception  Description of the Exception
     */
    public static void main( String args[] ) throws Exception {
        Permission perm = new Permission( "wolf", "bla", 0 );
        System.out.println( perm );
        perm.setPermissions( "user=+read,+write,+update,group=+read,other=+read" );
        System.out.println( perm );
        perm.setPermissions( "group=-read,user=+write,-update,group=+read" );
        System.out.println( perm );
    }


    /**
     *  Get the active permissions for group
     *
     *@return    The groupPermissions value
     */
    public int getGroupPermissions() {
        return ( permissions & 0x38 ) >> 3;
    }


    /**
     *  Gets the user who owns this resource
     *
     *@return    The owner value
     */
    public String getOwner() {
        return owner;
    }


    /**
     *  Gets the group 
     *
     *@return    The ownerGroup value
     */
    public String getOwnerGroup() {
        return ownerGroup;
    }


    /**
     *  Get the permissions
     *
     *@return    The permissions value
     */
    public int getPermissions() {
        return permissions;
    }


    /**
     *  Get the active permissions for others
     *
     *@return    The publicPermissions value
     */
    public int getPublicPermissions() {
        return permissions & 0x7;
    }


    /**
     *  Get the active permissions for the owner
     *
     *@return    The userPermissions value
     */
    public int getUserPermissions() {
        return ( permissions & 0x1c0 ) >> 6;
    }


    /**
     *  Read the Permission from an input stream
     *
     *@param  istream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void read( DataInput istream ) throws IOException {
        owner = istream.readUTF();
        ownerGroup = istream.readUTF();
        permissions = istream.readByte();
    }

    public void read( VariableByteInputStream istream ) throws IOException {
        owner = istream.readUTF();
        ownerGroup = istream.readUTF();
        permissions = istream.readInt();
    }
    
    /**
     *  Set the owner group
     *
     *@param  group  The new group value
     */
    public void setGroup( String group ) {
        this.ownerGroup = group;
    }


    /**
     *  Sets permissions for group
     *
     *@param  perm  The new groupPermissions value
     */
    public void setGroupPermissions( int perm ) {
        permissions = permissions | ( perm << 3 );
    }


    /**
     *  Set the owner passed as User object
     *
     *@param  user  The new owner value
     */
    public void setOwner( User user ) {
        this.owner = user.getName();
        //this.ownerGroup = user.getPrimaryGroup();
    }


    /**
     *  Set the owner
     *
     *@param  user  The new owner value
     */
    public void setOwner( String user ) {
        this.owner = user;
    }


    /**
     *  Set permissions using a string. The string has the
     * following syntax:
     * 
     * [user|group|other]=[+|-][read|write|update]
     * 
     * For example, to set read and write permissions for the group, but
     * not for others:
     * 
     * group=+read,+write,other=-read,-write
     * 
     * The new settings are or'ed with the existing settings.
     * 
     *@param  str                  The new permissions
     *@exception  SyntaxException  Description of the Exception
     */
    public void setPermissions( String str ) throws SyntaxException {
        StringTokenizer tokenizer = new StringTokenizer( str, ",= " );
        String token;
        int shift = -1;
        while ( tokenizer.hasMoreTokens() ) {
            token = tokenizer.nextToken();
            if ( token.equalsIgnoreCase( USER_STRING ) )
                shift = 6;
            else if ( token.equalsIgnoreCase( GROUP_STRING ) )
                shift = 3;
            else if ( token.equalsIgnoreCase( DEFAULT_STRING ) )
                shift = 0;
            else {
                char modifier = token.charAt( 0 );
                if ( !( modifier == '+' || modifier == '-' ) )
                    throw new SyntaxException( "expected modifier +|-" );
                else
                    token = token.substring( 1 );
                if ( token.length() == 0 )
                    throw new SyntaxException( "'read', 'write' or 'update' " +
                        "expected in permission string" );
                int perm;
                if ( token.equalsIgnoreCase( "read" ) )
                    perm = READ;
                else if ( token.equalsIgnoreCase( "write" ) )
                    perm = WRITE;
                else
                    perm = UPDATE;
                switch ( modifier ) {
                    case '+':
                        permissions = permissions | ( perm << shift );
                        break;
                    default:
                        permissions = permissions & ( ~( perm << shift ) );
                        break;
                }
            }
        }
    }


    /**
     *  Set permissions
     *
     *@param  perm  The new permissions value
     */
    public void setPermissions( int perm ) {
        this.permissions = perm;
    }


    /**
     *  Set permissions for others
     *
     *@param  perm  The new publicPermissions value
     */
    public void setPublicPermissions( int perm ) {
        permissions = permissions | perm;
    }


    /**
     *  Set permissions for the owner
     *
     *@param  perm  The new userPermissions value
     */
    public void setUserPermissions( int perm ) {
        permissions = permissions | ( perm << 6 );
    }


    /**
     *  Format permissions 
     *
     *@return    Description of the Return Value
     */
    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append( ( permissions & ( READ << 6 ) ) == 0 ? '-' : 'r' );
        buf.append( ( permissions & ( WRITE << 6 ) ) == 0 ? '-' : 'w' );
        buf.append( ( permissions & ( UPDATE << 6 ) ) == 0 ? '-' : 'u' );
        buf.append( ( permissions & ( READ << 3 ) ) == 0 ? '-' : 'r' );
        buf.append( ( permissions & ( WRITE << 3 ) ) == 0 ? '-' : 'w' );
        buf.append( ( permissions & ( UPDATE << 3 ) ) == 0 ? '-' : 'u' );
        buf.append( ( permissions & READ ) == 0 ? '-' : 'r' );
        buf.append( ( permissions & WRITE ) == 0 ? '-' : 'w' );
        buf.append( ( permissions & UPDATE ) == 0 ? '-' : 'u' );
        buf.append( '\t' );
        buf.append( owner );
        buf.append( '\t' );
        buf.append( ownerGroup );
        return buf.toString();
    }


    /**
     *  Check  if user has the requested permissions for this resource.
     *
     *@param  user  The user
     *@param  perm  The requested permissions
     *@return       true if user has the requested permissions
     */
    public boolean validate( User user, int perm ) {
        // group dba has full access
        if ( user.hasGroup( SecurityManager.DBA_GROUP ) )
            return true;
        // check if the user owns this resource
        if ( user.getName().equals( owner ) )
            return validateUser( perm );
        // check groups
        for ( Iterator i = user.getGroups(); i.hasNext();  )
            if ( ( (String) i.next() ).equals( ownerGroup ) )
                return validateGroup( perm );

        // finally, check public access rights
        return validatePublic( perm );
    }


    /**
     *  Description of the Method
     *
     *@param  perm  Description of the Parameter
     *@return       Description of the Return Value
     */
    public boolean validateGroup( int perm ) {
        perm = perm << 3;
        return ( permissions & perm ) == perm;
    }


    /**
     *  Description of the Method
     *
     *@param  perm  Description of the Parameter
     *@return       Description of the Return Value
     */
    public boolean validatePublic( int perm ) {
        return ( permissions & perm ) == perm;
    }


    /**
     *  Description of the Method
     *
     *@param  perm  Description of the Parameter
     *@return       Description of the Return Value
     */
    public boolean validateUser( int perm ) {
        perm = perm << 6;
        return ( permissions & perm ) == perm;
    }


    /**
     *  Description of the Method
     *
     *@param  ostream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void write( DataOutput ostream ) throws IOException {
        ostream.writeUTF( owner );
        ostream.writeUTF( ownerGroup );
        ostream.writeByte( permissions );
    }
    
    public void write( VariableByteOutputStream ostream )
    throws IOException {
        ostream.writeUTF( owner );
        ostream.writeUTF( ownerGroup );
        ostream.writeInt( permissions );
    }
    
    public void store( String prefix, Properties props ) {
    	props.setProperty( prefix + ".owner", owner );
    	props.setProperty( prefix + ".group", ownerGroup );
    	props.setProperty( prefix + ".permissions", 
    		Integer.toOctalString( permissions ) );
    }
}

