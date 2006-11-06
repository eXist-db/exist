/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.security;

import java.io.DataInput;
import java.io.IOException;

import org.exist.util.SyntaxException;


public interface Permission {
	
	public final static int DEFAULT_PERM = 0755;
	public final static Permission SYSTEM_DEFAULT = null;
	
	public final static int READ = 4;
	public final static int WRITE = 2;
	public final static int UPDATE = 1;
	
	public final static String USER_STRING = "user";
    public final static String GROUP_STRING = "group";
    public final static String OTHER_STRING = "other";
	
	public int getGroupPermissions();

    /**
     *  Gets the user who owns this resource
     *
     *@return    The owner value
     */
    public String getOwner();

    /**
     *  Gets the group 
     *
     *@return    The ownerGroup value
     */
    public String getOwnerGroup();

    /**
     *  Get the permissions
     *
     *@return    The permissions value
     */
    public int getPermissions();

    /**
     *  Get the active permissions for others
     *
     *@return    The publicPermissions value
     */
    public int getPublicPermissions();

    /**
     *  Get the active permissions for the owner
     *
     *@return    The userPermissions value
     */
    public int getUserPermissions();

    /**
     *  Read the Permission from an input stream
     *
     *@param  istream          Description of the Parameter
     *@exception  IOException  Description of the Exception
     */
    public void read(DataInput istream) throws IOException;
    
    /**
     *  Set the owner group
     *
     *@param  group  The new group value
     */
    public void setGroup(String group);

    /**
     *  Sets permissions for group
     *
     *@param  perm  The new groupPermissions value
     */
    public void setGroupPermissions(int perm);

    /**
     *  Set the owner passed as User object
     *
     *@param  user  The new owner value
     */
    public void setOwner(User user);

    /**
     *  Set the owner
     *
     *@param  user  The new owner value
     */
    public void setOwner(String user);

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
    public void setPermissions(String str) throws SyntaxException;

    /**
     *  Set permissions
     *
     *@param  perm  The new permissions value
     */
    public void setPermissions( int perm );

    /**
     *  Set permissions for others
     *
     *@param  perm  The new publicPermissions value
     */
    public void setPublicPermissions( int perm );

    /**
     *  Set permissions for the owner
     *
     *@param  perm  The new userPermissions value
     */
    public void setUserPermissions( int perm );

    /**
     *  Format permissions 
     *
     *@return    Description of the Return Value
     */
    public String toString();

    /**
     *  Check  if user has the requested permissions for this resource.
     *
     *@param  user  The user
     *@param  perm  The requested permissions
     *@return       true if user has the requested permissions
     */
    public boolean validate(User user, int perm);	
}
