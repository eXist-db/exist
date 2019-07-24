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
	
    int DEFAULT_COLLECTION_PERM = 0777;
    int DEFAULT_RESOURCE_PERM = 0666;
    int DEFAULT_UMASK = 022;
    
    int DEFAULT_SYSTEM_COLLECTION_PERM = 0755;
    int DEFAULT_SYSTSEM_RESOURCE_PERM = 0770;
    
    int DEFAULT_SYSTEM_ETC_COLLECTION_PERM = 0755;
    int DEFAULT_SYSTEM_SECURITY_COLLECTION_PERM = 0770;

    int DEFAULT_TEMPORARY_COLLECTION_PERM = 0771;
    int DEFAULT_TEMPORARY_DOCUMENT_PERM = 0771;

    int SET_UID = 04;
    int SET_GID = 02;
    int STICKY = 01;

    int READ = 04;
    int WRITE = 02;
    int EXECUTE = 01;
	
    String USER_STRING = "user";
    String GROUP_STRING = "group";
    String OTHER_STRING = "other";

    String READ_STRING = "read";
    String WRITE_STRING = "write";
    String EXECUTE_STRING = "execute";

    char SETUID_CHAR = 's';
    char SETUID_CHAR_NO_EXEC = 'S';
    char SETGID_CHAR = 's';
    char SETGID_CHAR_NO_EXEC = 'S';
    char STICKY_CHAR = 't';
    char STICKY_CHAR_NO_EXEC = 'T';
    char READ_CHAR = 'r';
    char WRITE_CHAR = 'w';
    char EXECUTE_CHAR = 'x';
    char UNSET_CHAR = '-';

    char ALL_CHAR = 'a';
    char USER_CHAR = 'u';
    char GROUP_CHAR = 'g';
    char OTHER_CHAR = 'o';
    
    int getGroupMode();

    /**
     * Gets the user who owns this resource
     *
     * @return The owner value
     */
    Account getOwner();

    /**
     * Gets the group 
     *
     * @return The group value
     */
    Group getGroup();

    /**
     * Get the mode
     *
     * @return The mode value
     */
    int getMode();

    /**
     * Get the active mode for others
     *
     * @return The mode value
     */
    int getOtherMode();

    /**
     * Get the active mode for the owner
     *
     * @return The mode value
     */
    int getOwnerMode();
    
    /**
     * Set the owner group by group id
     *
     * @param  id  The group id
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setGroup(int id) throws PermissionDeniedException;

    /**
     * Set the owner group
     *
     * @param  group  The group value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setGroup(Group group) throws PermissionDeniedException;

    /**
     * Set the owner group
     *
     * @param  name The group's name
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setGroup(String name) throws PermissionDeniedException;

    /**
     * Set the owner group
     * 
     * This is used to set the owner group
     * of this permission to the same
     * as the owner group of the <i>other</i>
     * permission.
     * 
     * This is typically used in setGID situations.
     * 
     * @param other Another permissions object
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setGroupFrom(Permission other) throws PermissionDeniedException;
    
    /**
     * Sets mode for group
     *
     * @param  perm  The new group mode value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setGroupMode(int perm) throws PermissionDeniedException;

    /**
     * Set the owner passed as account id
     *
     * @param  id  The new owner id
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setOwner(int id) throws PermissionDeniedException;

    /**
     * Set the owner passed as User object
     *
     * @param  user  The new owner value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setOwner(Account user) throws PermissionDeniedException;


    /**
     * Set the owner
     *
     * @param  user  The new owner value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setOwner(String user) throws PermissionDeniedException;

    /**
     * Set mode using a string.
     * 
     * The string can either be in one of three formats:
     *  
     * 1) Unix Symbolic format as given to 'chmod' on Unix/Linux
     * 2) eXist Symbolic format as described in @see org.exist.security.AbstractUnixStylePermission#setExistSymbolicMode(java.lang.String)
     * 3) Simple Symbolic format e.g. "rwxr-xr-x"
     * 
     * The eXist symbolic format should be avoided
     * in new applications as it is deprecated
     * 
     * @param modeStr The new mode
     * @throws PermissionDeniedException is user has not sufficient rights
     * @throws SyntaxException  Description of the Exception
     */
    void setMode(String modeStr) throws SyntaxException, PermissionDeniedException;

    /**
     *  Set mode
     *
     * @param  mode  The new mode value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setMode(int mode) throws PermissionDeniedException;

    /**
     *  Set mode for others
     *
     * @param  perm  The new mode value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setOtherMode(int perm) throws PermissionDeniedException;

    /**
     *  Set mode for the owner
     *
     * @param  other  The new mode value
     * @throws PermissionDeniedException is user has not sufficient rights
     */
    void setOwnerMode(int other) throws PermissionDeniedException;

    boolean isSetUid();
    boolean isSetGid();
    boolean isSticky();

    void setSetUid(boolean setUid) throws PermissionDeniedException;
    void setSetGid(boolean setGid) throws PermissionDeniedException;
    void setSticky(boolean sticky) throws PermissionDeniedException;

    /**
     *  Check  if user has the requested mode for this resource.
     *
     *@param  user  The user
     *@param  mode  The requested mode
     *@return       true if user has the requested mode
     */
    boolean validate(Subject user, int mode);

    void write(VariableByteOutputStream ostream) throws IOException;

    void read(VariableByteInput istream) throws IOException;

    boolean isCurrentSubjectDBA();

    boolean isCurrentSubjectOwner();

    boolean isCurrentSubjectInGroup();

    boolean isCurrentSubjectInGroup(int groupId);

    boolean isPosixChownRestricted();

    Permission copy();
}
