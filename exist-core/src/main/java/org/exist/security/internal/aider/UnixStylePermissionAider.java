/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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

import org.exist.security.*;

import org.exist.security.SecurityManager;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.util.SyntaxException;

/**
 * Unix style permission details.
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class UnixStylePermissionAider extends AbstractUnixStylePermission implements PermissionAider {

    //owner, default to DBA
    private Account owner;
    private Group ownerGroup;
    private int mode;

    public UnixStylePermissionAider() {
    	owner = new UserAider(SecurityManager.DBA_USER);
    }


    /**
     * Construct a Permission with given mode
     *
     * @param  mode  The mode
     */
    public UnixStylePermissionAider(final int mode) {
    	this();
        this.mode = mode;
    }

    
    /**
     * Construct a permission with given user, group and mode
     *
     * @param user name of the owner
     * @param group name of the group
     * @param mode mode for the resource.
     */
    public UnixStylePermissionAider(final String user, final String group, final int mode) {
        this.owner = new UserAider(user);
        this.ownerGroup = new GroupAider(group);
        this.mode = mode;
    }

    @Override
    public boolean isSetGid() {
        return ((mode >>> 9) & SET_GID) == SET_GID;
    }

    @Override
    public boolean isSetUid() {
        return ((mode >>> 9) & SET_UID) == SET_UID;
    }

    @Override
    public boolean isSticky() {
        return ((mode >>> 9) & STICKY) == STICKY;
    }

    @Override
    public void setSetUid(final boolean setUid) {
        if(setUid) {
            this.mode = mode | (SET_UID << 9);
        } else {
            this.mode = mode & (~(SET_UID << 9));
        }
    }

    @Override
    public void setSetGid(final boolean setGid) {
        if(setGid) {
            this.mode = mode | (SET_GID << 9);
        } else {
            this.mode = mode & (~(SET_GID << 9));
        }
    }

    @Override
    public void setSticky(final boolean sticky) {
        if(sticky) {
            this.mode = mode | (STICKY << 9);
        } else {
            this.mode = mode & (~(STICKY << 9));
        }
    }

    /**
     * Get the active mode for group
     *
     * @return The group mode value
     */
    @Override
    public int getGroupMode() {
        return (mode & 0x38) >> 3;
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
     * Get the mode
     *
     * @return    The mode value
     */
    @Override
    public int getMode() {
        return mode;
    }

    /**
     * Get the active mode for others
     *
     * @return    The other mode value
     */
    @Override
    public int getOtherMode() {
        return mode & 0x7;
    }

    /**
     * Get the active mode for the owner
     *
     * @return    The user mode value
     */
    @Override
    public int getOwnerMode() {
        return (mode & 0x1c0) >> 6;
    }

    /**
     * Set the owner group
     *
     * @param  group  The group value
     */
    @Override
    public void setGroup(final Group group) {
        this.ownerGroup = group;
    }

    /**
     * Set the owner group
     *
     * @param  group  The group name
     */
    @Override
    public void setGroup(final String group) {
        this.ownerGroup = new GroupAider(group);
    }
    
    @Override
    public void setGroupFrom(Permission other) throws PermissionDeniedException {
        this.ownerGroup = new GroupAider(other.getGroup().getName());
    }

    /**
     *  Sets mode for group
     *
     *@param  groupMode  The new group mode value
     */
    @Override
    public void setGroupMode(final int groupMode) {
        this.mode |= (groupMode << 3);
    }

    /**
     *  Set the owner passed as User object
     *
     *@param  user  The new owner value
     */
    @Override
    public void setOwner(final Account user) {
        this.owner = user;
    }

    /**
     *  Set the owner
     *
     *@param  user  The new owner value
     */
    @Override
    public void setOwner(final String user) {
        this.owner = new UserAider(user);
    }

    /**
     *  Set mode
     *
     *@param  mode  The new mode value
     */
    @Override
    public void setMode(final int mode) {
        this.mode = mode;
    }

    /**
     *  Set mode for others
     *
     *@param  otherMode  The new public mode value
     */
    @Override
    public void setOtherMode(final int otherMode) {
        this.mode |= otherMode ;
    }

    /**
     *  Set mode for the owner
     *
     *@param  ownerMode  The new owner mode value
     */
    @Override
    public void setOwnerMode(final int ownerMode) {
        this.mode |= (ownerMode << 6);
    }


    /**
     *  Format mode
     *
     *@return    Description of the Return Value
     */
    @Override
    public String toString() {

        final char ch[] = new char[] {
            (mode & (READ << 6)) == 0 ? UNSET_CHAR : READ_CHAR,
            (mode & (WRITE << 6)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (mode & (SET_UID << 9)) == 0 ? ((mode & (EXECUTE << 6)) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((mode & (EXECUTE << 6)) == 0 ? SETUID_CHAR_NO_EXEC : SETUID_CHAR),
            
            (mode & (READ << 3)) == 0 ? UNSET_CHAR : READ_CHAR,
            (mode & (WRITE << 3)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (mode & (SET_GID << 9)) == 0 ? ((mode & (EXECUTE << 3)) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((mode & (EXECUTE << 3)) == 0 ? SETGID_CHAR_NO_EXEC : SETGID_CHAR),

            (mode & READ) == 0 ? UNSET_CHAR : READ_CHAR,
            (mode & WRITE) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (mode & (STICKY << 9)) == 0 ? ((mode & EXECUTE) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((mode & EXECUTE) == 0 ? STICKY_CHAR_NO_EXEC : STICKY_CHAR)
        };
        return String.valueOf(ch);
    }

    public static UnixStylePermissionAider fromString(final String modeStr) throws SyntaxException {
        if(modeStr == null || !(modeStr.length() == 9 || modeStr.length() == 12)){
            throw new SyntaxException("Invalid Permission String '" + modeStr + "'");
        }

        int mode = 0;
        for(int i = 0; i < modeStr.length(); i=i+3) {
            for(final char c : modeStr.substring(i, i + 3).toCharArray()) {
                switch(c) {
                    case READ_CHAR:
                        mode |= (READ << (6 - i));
                        break;
                    case WRITE_CHAR:
                        mode |= (WRITE << (6 - i));
                        break;
                    case EXECUTE_CHAR:
                        mode |= (EXECUTE << (6 - i));
                        break;
                    case SETUID_CHAR | SETGID_CHAR:
                        if(i == 0) {
                            mode |= (SET_UID << 9);
                        } else if(i == 3) {
                            mode |= (SET_GID << 9);
                        }
                        mode |= (EXECUTE << (6 - i));
                        break;
                    case SETUID_CHAR_NO_EXEC | SETGID_CHAR_NO_EXEC:
                        if(i == 0) {
                            mode |= (SET_UID << 9);
                        } else if(i == 3) {
                            mode |= (SET_GID << 9);
                        }
                        break;
                    case STICKY_CHAR:
                        mode |= (STICKY << 9);
                        mode |= (EXECUTE << (6 - i));
                        break;
                    case STICKY_CHAR_NO_EXEC:
                        mode |= (STICKY << 9);
                        break;
                    case UNSET_CHAR:
                        break;
                    default:
                        throw new SyntaxException("Unknown char '" + c + "' in mode string '" + modeStr + "'");
                }
            }
        }
        return new UnixStylePermissionAider(mode);
    }
    
    @Override
    public boolean validate(final Subject user, final int mode) {
    	throw new UnsupportedOperationException("Validation of Permission Aider is unsupported");
    }

    @Override
    public void setGroup(final int id) {
        ownerGroup = new GroupAider(id);
    }

    @Override
    public void setOwner(final int id) {
        owner = new UserAider(id);
    }

    @Override
    public void write(final VariableByteOutputStream ostream) {
       throw new UnsupportedOperationException("Serialization of permission Aider is unsupported");
    }

    @Override
    public void read(final VariableByteInput istream) throws IOException {
        throw new UnsupportedOperationException("De-Serialization of permission Aider is unsupported");
    }

    @Override
    public boolean isCurrentSubjectDBA() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isCurrentSubjectOwner() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isCurrentSubjectInGroup() {
        throw new UnsupportedOperationException("Not supported yet.");
    }
    
    @Override
    public boolean isCurrentSubjectInGroup(final int groupId) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public boolean isPosixChownRestricted() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public Permission copy() {
        throw new UnsupportedOperationException();
    }
}
