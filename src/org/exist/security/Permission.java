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
	
    public int getGroupMode();

    /**
     * Gets the user who owns this resource
     *
     * @return The owner value
     */
    public Account getOwner();

    /**
     * Gets the group 
     *
     * @return The group value
     */
    public Group getGroup();

    /**
     * Get the mode
     *
     * @return The mode value
     */
    public int getMode();

    /**
     * Get the active mode for others
     *
     * @return The mode value
     */
    public int getOtherMode();

    /**
     * Get the active mode for the owner
     *
     * @return The mode value
     */
    public int getOwnerMode();
    
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
     * Sets mode for group
     *
     * @param  perm  The new group mode value
     */
    public void setGroupMode(int perm);

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
     *  Set mode using a string. The string has the
     * following syntax:
     * 
     * [user|group|other]=[+|-][read|write|update]
     * 
     * For example, to set read and write mode for the group, but
     * not for others:
     * 
     * group=+read,+write,other=-read,-write
     * 
     * The new settings are or'ed with the existing settings.
     * 
     *@param  modeStr                  The new mode
     *@exception  SyntaxException  Description of the Exception
     */
    public void setMode(String modeStr) throws SyntaxException;

    /**
     *  Set mode
     *
     *@param  mode  The new mode value
     */
    public void setMode( int mode );

    /**
     *  Set mode for others
     *
     *@param  mode  The new mode value
     */
    public void setOtherMode( int mode );

    /**
     *  Set mode for the owner
     *
     *@param  mode  The new mode value
     */
    public void setOwnerMode( int mode );

    /**
     *  Format mode
     *
     *@return    Description of the Return Value
     */
    @Override
    public String toString();

    /**
     *  Check  if user has the requested mode for this resource.
     *
     *@param  user  The user
     *@param  mode  The requested mode
     *@return       true if user has the requested mode
     */
    public boolean validate(Subject user, int mode);

    public void write(VariableByteOutputStream ostream);

    public void read(VariableByteInput istream) throws IOException;
}