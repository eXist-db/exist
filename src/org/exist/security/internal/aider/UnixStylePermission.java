/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
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
 *  $Id$
 */
package org.exist.security.internal.aider;

import java.io.IOException;
import java.util.StringTokenizer;

import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.security.SecurityManager;
import org.exist.security.Account;
import org.exist.security.Subject;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.SyntaxException;

/**
 * Unix style permission details.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class UnixStylePermission implements Permission {

    //owner, default to DBA
    private Account owner;
    private Group ownerGroup;

    //permissions
    private int permissions = DEFAULT_PERM;

    public UnixStylePermission() {
    	owner = new UserAider(SecurityManager.DBA_USER);
    }


    /**
     * Construct a Permission with given permissions 
     *
     * @param  permissions  Description of the Parameter
     */
    public UnixStylePermission(int permissions) {
    	this();
        this.permissions = permissions;
    }

    
    /**
     * Construct a permission with given user, group and permissions
     *
     * @param  user
     * @param  group
     * @param  permissions
     */
    public UnixStylePermission(String user, String group, int permissions) {
        this.owner = new UserAider(user);
        this.ownerGroup = new GroupAider(group);
        this.permissions = permissions;
    }
    


    /**
     * Get the active permissions for group
     *
     * @return The groupPermissions value
     */
    @Override
    public int getGroupMode() {
        return ( permissions & 0x38 ) >> 3;
    }


    /**
     * Gets the user who owns this resource
     *
     * @return    The owner value
     */
    @Override
    public Account getOwner() {
        return owner;
    }


    /**
     * Gets the group 
     *
     * @return    The ownerGroup value
     */
    @Override
    public Group getGroup() {
        return ownerGroup;
    }


    /**
     * Get the permissions
     *
     * @return    The permissions value
     */
    @Override
    public int getMode() {
        return permissions;
    }


    /**
     * Get the active permissions for others
     *
     * @return    The publicPermissions value
     */
    @Override
    public int getOtherMode() {
        return permissions & 0x7;
    }


    /**
     * Get the active permissions for the owner
     *
     * @return    The userPermissions value
     */
    @Override
    public int getOwnerMode() {
        return ( permissions & 0x1c0 ) >> 6;
    }


    /**
     * Set the owner group
     *
     * @param  group  The group value
     */
    @Override
    public void setGroup(Subject invokingUser, Group group) {
        this.ownerGroup = group;
    }

    /**
     * Set the owner group
     *
     * @param  group  The group name
     */
    @Override
    public void setGroup(Subject invokingUser, String group) {
        this.ownerGroup = new GroupAider(group);
    }

    /**
     *  Sets permissions for group
     *
     *@param  perm  The new groupPermissions value
     */
    @Override
    public void setGroupMode( int perm ) {
        permissions = permissions | ( perm << 3 );
    }


    /**
     *  Set the owner passed as User object
     *
     *@param  user  The new owner value
     */
    @Override
    public void setOwner(Subject invokingUser, Account user) {
   		this.owner = user;
    }


    /**
     *  Set the owner
     *
     *@param  user  The new owner value
     */
    @Override
    public void setOwner(Subject invokingUser, String user) {
        this.owner = new UserAider(user);
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
    @Override
    public void setMode( String str ) throws SyntaxException {
        StringTokenizer tokenizer = new StringTokenizer( str, ",= " );
        String token;
        int shift = -1;
        while ( tokenizer.hasMoreTokens() ) {
            token = tokenizer.nextToken();
            if ( token.equalsIgnoreCase( USER_STRING ) )
                shift = 6;
            else if ( token.equalsIgnoreCase( GROUP_STRING ) )
                shift = 3;
            else if ( token.equalsIgnoreCase( OTHER_STRING ) )
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
    @Override
    public void setMode( int perm ) {
        this.permissions = perm;
    }


    /**
     *  Set permissions for others
     *
     *@param  perm  The new publicPermissions value
     */
    @Override
    public void setOtherMode( int perm ) {
        permissions = permissions | perm;
    }


    /**
     *  Set permissions for the owner
     *
     *@param  perm  The new userPermissions value
     */
    @Override
    public void setOwnerMode( int perm ) {
        permissions = permissions | ( perm << 6 );
    }


    /**
     *  Format permissions 
     *
     *@return    Description of the Return Value
     */
    @Override
    public String toString() {
    	final char[] ch;
    	if (permissions <= 511) {
    		ch = new char[] {
                    ( permissions & ( READ << 6 ) ) == 0 ? '-' : 'r',
                    ( permissions & ( WRITE << 6 ) ) == 0 ? '-' : 'w',
                    ( permissions & ( UPDATE << 6 ) ) == 0 ? '-' : 'u',
                    ( permissions & ( READ << 3 ) ) == 0 ? '-' : 'r',
                    ( permissions & ( WRITE << 3 ) ) == 0 ? '-' : 'w',
                    ( permissions & ( UPDATE << 3 ) ) == 0 ? '-' : 'u',
                    ( permissions & READ ) == 0 ? '-' : 'r',
                    ( permissions & WRITE ) == 0 ? '-' : 'w',
                    ( permissions & UPDATE ) == 0 ? '-' : 'u'
        		};
    	} else {
    		ch = new char[] {
                ( permissions & ( READ << 9 ) ) == 0 ? '-' : 'a',
                ( permissions & ( WRITE << 9 ) ) == 0 ? '-' : 'g',
                ( permissions & ( UPDATE << 9 ) ) == 0 ? '-' : 's',
                ( permissions & ( READ << 6 ) ) == 0 ? '-' : 'r',
                ( permissions & ( WRITE << 6 ) ) == 0 ? '-' : 'w',
                ( permissions & ( UPDATE << 6 ) ) == 0 ? '-' : 'u',
                ( permissions & ( READ << 3 ) ) == 0 ? '-' : 'r',
                ( permissions & ( WRITE << 3 ) ) == 0 ? '-' : 'w',
                ( permissions & ( UPDATE << 3 ) ) == 0 ? '-' : 'u',
                ( permissions & READ ) == 0 ? '-' : 'r',
                ( permissions & WRITE ) == 0 ? '-' : 'w',
                ( permissions & UPDATE ) == 0 ? '-' : 'u'
    		};
    	}
        return new String(ch);
    }

    public static UnixStylePermission fromString(String permissionString) throws SyntaxException {
        if(permissionString == null || !(permissionString.length() == 9 || permissionString.length() == 12)){
            throw new SyntaxException("Invalid Permission String '" + permissionString + "'");
        }

        //XXX: check position 
        int permission = 0;
        int factor = 64;
        if (permissionString.length() == 12) {
        	factor = 512;
        }
        int val = 0;
        for(int i = 0; i < permissionString.length(); i++) {
            char c = permissionString.charAt(i);
            switch(c){
                case 'r':
                    val += 4;
                    break;
                case 'w':
                    val += 2;
                    break;
                case 'u':
            		val += 1;
                    break;
                case 'a':
                	if (i == 0 && permissionString.length() == 12) {
                		val += 4;
                	} else
                		throw new SyntaxException("Invalid Permission String '" + permissionString + "'");
                	break;
                case 'g':
                	if (i == 1 && permissionString.length() == 12) {
                		val += 2;
                	} else
                		throw new SyntaxException("Invalid Permission String '" + permissionString + "'");
                	break;
                case 's':
                	if (i == 2 && permissionString.length() == 12) {
                		val += 1;
                	} else
                		throw new SyntaxException("Invalid Permission String '" + permissionString + "'");
                	break;
                case '-':
                    break;
                default:
                    throw new SyntaxException("Invalid Permission String '" + permissionString + "'");
            }

            if((i + 1) % 3 == 0) {
                permission += (val * factor);
                factor = factor / 8;
                val = 0;
            }
        }

        return new UnixStylePermission(permission);
    }
    
    @Override
    public boolean validate( Subject user, int perm ) {
    	return false;
    }


	@Override
	public void setGroup(Subject invokingUser, int id) {
		ownerGroup = new GroupAider(id);
	}

	@Override
	public void setOwner(Subject invokingUser, int id) {
		owner = new UserAider(id);
	}

           @Override
    public void setGroup(int id) {
        setGroup(null, id);
    }

    @Override
    public void setGroup(Group group) {
        setGroup(null, group);
    }

    @Override
    public void setGroup(String name) {
        setGroup(null, name);
    }

    @Override
    public void setOwner(int id) {
        setOwner(null, id);
    }

    @Override
    public void setOwner(Account user) {
        setOwner(null, user);
    }

    @Override
    public void setOwner(String user) {
        setOwner(null, user);
    }

    @Override
    public void write(VariableByteOutputStream ostream) {
       throw new UnsupportedOperationException("Serialization of permission Aider is unsupported");
    }

    @Override
    public void read(VariableByteInput istream) throws IOException {
        throw new UnsupportedOperationException("De-Serialization of permission Aider is unsupported");
    }
}