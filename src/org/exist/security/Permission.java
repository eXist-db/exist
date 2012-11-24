/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2011 The eXist-db Project
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
	
    public final static int DEFAULT_COLLECTION_PERM = 0777;
    public final static int DEFAULT_RESOURCE_PERM = 0666;
    public final static int DEFAULT_UMASK = 022;
    
    public final static int DEFAULT_SYSTEM_COLLECTION_PERM = 0755;
    public final static int DEFAULT_SYSTSEM_RESOURCE_PERM = 0770;
    
    public final static int DEFAULT_SYSTEM_ETC_COLLECTION_PERM = 0755;
    public final static int DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM = 0770;
    
    public final static int DEFAULT_TEMPORARY_DOCUMENT_PERM = 0771;

    public final static int SET_UID = 4;
    public final static int SET_GID = 2;
    public final static int STICKY = 1;

    public final static int READ = 4;
    public final static int WRITE = 2;
    public final static int EXECUTE = 1;
	
    public final static String USER_STRING = "user";
    public final static String GROUP_STRING = "group";
    public final static String OTHER_STRING = "other";

    public final static String READ_STRING = "read";
    public final static String WRITE_STRING = "write";
    public final static String EXECUTE_STRING = "execute";

    public final static char SETUID_CHAR = 's';
    public final static char SETGID_CHAR = 's';
    public final static char STICKY_CHAR = 't';
    public final static char READ_CHAR = 'r';
    public final static char WRITE_CHAR = 'w';
    public final static char EXECUTE_CHAR = 'x';
    public final static char UNSET_CHAR = '-';

    public final static char ALL_CHAR = 'a';
    public final static char USER_CHAR = 'u';
    public final static char GROUP_CHAR = 'g';
    public final static char OTHER_CHAR = 'o';
    
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
    public void setGroup(int id) throws PermissionDeniedException;

    /**
     * Set the owner group
     *
     * @param  group  The group value
     */
    public void setGroup(Group group) throws PermissionDeniedException;

    /**
     * Set the owner group
     *
     * @param  name The group's name
     */
    public void setGroup(String name) throws PermissionDeniedException;

    /**
     * Sets mode for group
     *
     * @param  perm  The new group mode value
     */
    public void setGroupMode(int perm) throws PermissionDeniedException;

    /**
     * Set the owner passed as account id
     *
     * @param  id  The new owner id
     */
    public void setOwner(int id) throws PermissionDeniedException;

    /**
     * Set the owner passed as User object
     *
     * @param  user  The new owner value
     */
    public void setOwner(Account user) throws PermissionDeniedException;

    /**
     * Set the owner
     *
     * @param  user  The new owner value
     */
    public void setOwner(String user) throws PermissionDeniedException;

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
     *@param  str                  The new mode
     *@exception  SyntaxException  Description of the Exception
     *
     * @deprecated Setting permissions via string is not very efficient!
     */
    @Deprecated
    public void setMode(String str) throws SyntaxException, PermissionDeniedException;

    /**
     *  Set mode
     *
     *@param  mode  The new mode value
     */
    public void setMode(int mode) throws PermissionDeniedException;

    /**
     *  Set mode for others
     *
     *@param  perm  The new mode value
     */
    public void setOtherMode(int perm) throws PermissionDeniedException;

    /**
     *  Set mode for the owner
     *
     *@param  other  The new mode value
     */
    public void setOwnerMode(int other) throws PermissionDeniedException;

    public boolean isSetUid();
    public boolean isSetGid();
    public boolean isSticky();

    public void setSetUid(boolean setUid) throws PermissionDeniedException;
    public void setSetGid(boolean setGid) throws PermissionDeniedException;
    public void setSticky(boolean sticky) throws PermissionDeniedException;

    /**
     *  Check  if user has the requested mode for this resource.
     *
     *@param  user  The user
     *@param  mode  The requested mode
     *@return       true if user has the requested mode
     */
    public boolean validate(Subject user, int mode);

    public void write(VariableByteOutputStream ostream) throws IOException;

    public void read(VariableByteInput istream) throws IOException;

    public boolean isCurrentSubjectDBA();

    public boolean isCurrentSubjectOwner();

    public boolean isCurrentSubjectInGroup();

    public boolean isCurrentSubjectInGroup(int groupId);
}
