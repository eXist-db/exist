/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist Project
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
package org.exist.security;

import java.io.IOException;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.SyntaxException;


public interface Permission {
	
	public final static int DEFAULT_PERM = 0755;
	
	public final static int READ = 4;
	public final static int WRITE = 2;
	public final static int UPDATE = 1;
	public final static int EXECUTE = 1;
	
	public final static String USER_STRING = "user";
    public final static String GROUP_STRING = "group";
    public final static String OTHER_STRING = "other";
	
	public int getGroupPermissions();

    /**
     * Gets the user who owns this resource
     *
     * @return The owner value
     */
    public Account getOwner();

    /**
     * Gets the group 
     *
     * @return The ownerGroup value
     */
    public Group getOwnerGroup();

    /**
     * Get the permissions
     *
     * @return The permissions value
     */
    public int getPermissions();

    /**
     * Get the active permissions for others
     *
     * @return The publicPermissions value
     */
    public int getPublicPermissions();

    /**
     * Get the active permissions for the owner
     *
     * @return The userPermissions value
     */
    public int getUserPermissions();
    
    /**
     * Set the owner group by group id
     *
     * @param  id  The group id
     */
    public void setGroup(int id);
    @Deprecated
    public void setGroup(Subject invokingUser, int id);

    /**
     * Set the owner group
     *
     * @param  group  The group value
     */
    public void setGroup(Group group);
    @Deprecated
    public void setGroup(Subject invokingUser, Group group);

    /**
     * Set the owner group
     *
     * @param  name The group's name
     */
    public void setGroup(String name);
    @Deprecated
    public void setGroup(Subject invokingUser, String name);

    /**
     * Sets permissions for group
     *
     * @param  perm  The new groupPermissions value
     */
    public void setGroupPermissions(int perm);

    /**
     * Set the owner passed as account id
     *
     * @param  id  The new owner id
     */
    public void setOwner(int id);
    @Deprecated
    public void setOwner(Subject invokingUser, int id);

    /**
     * Set the owner passed as User object
     *
     * @param  user  The new owner value
     */
    public void setOwner(Account user);
    @Deprecated
    public void setOwner(Subject invokingUser, Account user);

    /**
     * Set the owner
     *
     * @param  user  The new owner value
     */
    public void setOwner(String user);
    @Deprecated
    public void setOwner(Subject invokingUser, String user);

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
    @Override
    public String toString();

    /**
     *  Check  if user has the requested permissions for this resource.
     *
     *@param  user  The user
     *@param  perm  The requested permissions
     *@return       true if user has the requested permissions
     */
    public boolean validate(Subject user, int perm);

    public void write(VariableByteOutputStream ostream);

    public void read(VariableByteInput istream) throws IOException;
}