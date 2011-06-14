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
import static org.exist.security.PermissionRequired.IS_DBA;
import static org.exist.security.PermissionRequired.IS_OWNER;
import static org.exist.security.PermissionRequired.IS_MEMBER;

import org.exist.security.internal.RealmImpl;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

/**
 * Manages the permissions assigned to a resource. This includes
 * the user who owns the resource, the owner group and the permissions
 * for owner, group and others.
 *
 * Permissions are encoded into a 52 bit vector with the following convention -
 *
 * [userId(20),setUid(1),userMode(rwx)(3),groupId(20),setGid(1),groupMode(rwx)(3),sticky(1),otherMode(rwx)(3)]
 * @see UnixStylePermission.encodeAsBitVector(int, int, int) for more details
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class UnixStylePermission extends AbstractUnixStylePermission implements Permission {

    protected SecurityManager sm;

    protected long vector = encodeAsBitVector(RealmImpl.SYSTEM_ACCOUNT_ID, RealmImpl.DBA_GROUP_ID, DEFAULT_PERM);

    public UnixStylePermission(SecurityManager sm) {
    	if(sm == null) {
            throw new IllegalArgumentException("Security manager can't be null");
        }
    	this.sm = sm;
    }
    
    /**
     * Construct a permission with given user, group and permissions
     *
     * @param  invokingUser Description of the Parameter
     * @param  sm           Description of the Parameter
     * @param  user         Description of the Parameter
     * @param  group        Description of the Parameter
     * @param  mode  Description of the Parameter
     */
    public UnixStylePermission(SecurityManager sm, int ownerId, int groupId, int mode) {
        this(sm);
        vector = encodeAsBitVector(ownerId, groupId, mode);
    }

    /**
     *  Gets the user who owns this resource
     *
     * @return The owner value
     */
    @Override
    public Account getOwner() {
        return sm.getAccount(getOwnerId());
    }

    private int getOwnerId() {
        return (int)(vector >>> 32);
    }

    /**
     *  Set the owner passed as User object
     *
     *@param  account  The new owner value
     */
    @Override
    public void setOwner(Subject invokingUser, Account account) {
    	//assume SYSTEM identity if user gets lost due to a database corruption - WTF???
    	if(account == null) {
            account = sm.getSystemSubject();
    	}
        setOwnerId(account.getId());
    }

    @Override
    public void setOwner(Subject invokingUser, int id) {
        Account account = sm.getAccount(id);
        if(account == null) {
            account = sm.getSystemSubject();
        }
        setOwnerId(id);
    }

    /**
     *  Set the owner
     *
     *@param  name  The new owner value
     */
    @Override
    public void setOwner(Subject invokingUser, String name) {
    	Account account = sm.getAccount(invokingUser, name);
    	if (account != null){
            setOwnerId(account.getId());
        }
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

    @PermissionRequired(user = IS_DBA)
    private void setOwnerId(int ownerId) {
        this.vector =
            ((long)ownerId << 32) | //left shift new ownerId into position
            (vector & 4294967295L);   //extract everything from current permission except ownerId
    }

    /**
     *  Gets the group 
     *
     *@return    The group value
     */
    @Override
    public Group getGroup() {
        return sm.getGroup(getGroupId());
    }

    private int getGroupId() {
        return (int)((vector >>> 8) & 1048575);
    }
    
    /**
     *  Set the owner group
     *
     *@param  groupName  The new group value
     */
    @Override
    public void setGroup(Subject invokingUser, String groupName) {
        Group group = sm.getGroup(invokingUser, groupName);
        if(group != null) {
            setGroupId(group.getId());
        }
    }

    @Override
    public void setGroup(Subject invokingUser, Group group) {
    	if(group != null){
            setGroupId(group.getId());
        }
    }
    
    @Override
    public void setGroup(Subject invokingUser, int id) {
        Group group = sm.getGroup(id);
        if(group == null){
            group = sm.getDBAGroup(); //TODO is this needed?
        }
        setGroupId(group.getId());
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

    @PermissionRequired(user = IS_DBA | IS_OWNER, group = IS_MEMBER)
    private void setGroupId(int groupId) {
        this.vector =
            ((vector >>> 28) << 28) | //current ownerId and ownerMode, mask rest
            (groupId << 8) |          //left shift new groupId into positon
            (vector & 255);            //current groupMode and otherMode
    }

    /**
     *  Get the mode
     *
     *@return    The mode
     */
    @Override
    public int getMode() {
        return (int) ((((vector >>> 31) & 1) << 11) | (((vector >>> 7) & 1) << 10) | (((vector >>> 3) & 1) << 9) | //setUid | setGid | sticky
                ((((vector >>> 28) & 7) << 6) | (((vector >>> 4) & 7) << 3) | (vector & 7))); //userPerm | groupPerm | otherPerm
    }

    /**
     *  Set the mode
     *
     *@param  mode  The new mode value
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    final public void setMode(int mode) { 
        this.vector =
            ((vector >>> 32) << 32) |               //left shift current ownerId into position
            ((long)((mode >>> 11) & 1) << 31) |     //left shift setuid into position
            ((((mode >>> 6) & 7)) << 28) |          //left shift new ownerMode into position
            (((vector >>> 8) & 1048575) << 8) |     //left shift current groupId into position
            (((mode >>> 10) & 1) << 7) |            //left shift setgid into position
            (((mode >>> 3) & 7) << 4) |             //left shift new groupMode into position
            (((mode >>> 9) & 1) << 3) |             //left shift sticky into position
            (mode & 7);                             //new otherMode
    }
    
    @Override
    public boolean isSetUid() {
        return ((vector >>> 31) & 1) == 1;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setSetUid(boolean setUid) {
        this.vector = ((vector >>> 32) << 32) | (setUid ? 1 : 0) | (vector & 2147483647);
    }

    @Override
    public boolean isSetGid() {
        return ((vector >>> 7) & 1) == 1;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setSetGid(boolean setGid) {
        this.vector = ((vector >>> 8) << 8) | (setGid ? 1 : 0) | (vector & 127);
    }

    @Override
    public boolean isSticky() {
        return ((vector >>> 3) & 1) == 1;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setSticky(boolean sticky) {
        this.vector = ((vector >>> 4) << 4) | (sticky ? 1 : 0) | (vector & 7);
    }
    
    /**
     *  Get the active mode for the owner
     *
     *@return    The mode value
     */
    @Override
    public int getOwnerMode() {
        return (int) ((vector >>> 28) & 7);
    }

    /**
     *  Set mode for the owner
     *
     *@param  mode  The new owner mode value
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setOwnerMode(int mode) {
        mode = mode & 7; //ensure its only 3 bits

        this.vector =
            ((vector >>> 31) << 31) |  //left shift current ownerId and setuid into position
            (mode << 28) |             //left shift new ownerMode into position
            (vector & 268435455);      //extract everything else from current permission except ownerId and ownerMode
    }

    /**
     *  Get the mode for group
     *
     *@return    The mode value
     */
    @Override
    public int getGroupMode() {
        return (int)((vector >>> 4) & 7);
    }

    /**
     *  Sets mode for group
     *
     *@param  mode  The new mode value
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setGroupMode(int mode) {
        mode = mode & 7; //ensure its only 3 bits

        this.vector =
            ((vector >>> 7) << 7) | //left shift current ownerId, setuid, ownerMode, groupId and setgid into position
            (mode << 4) |           //left shift new groupMode into position
            (vector & 15);          //current sticky and otherMode
    }

    /**
     *  Get the mode for others
     *
     *@return    The mode value
     */
    @Override
    public int getOtherMode() {
        return (int)(vector & 7);
    }

    /**
     *  Set mode for others
     *
     *@param  mode  The new other mode value
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setOtherMode(int mode) {
        mode = mode & 7; //ensure its only 3 bits

        this.vector =
            ((vector >>> 3) << 3) |    //left shift current ownerId, ownerMode, groupId and groupMode into position
            mode;                      //new otherMode
    }

    /**
     *  Format mode
     *
     *@return the mode formatted as a string e.g. 'rwurwurwu'
     */
    @Override
    public String toString() {
        final char ch[] = new char[] {
            (vector & (READ << 28)) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & (WRITE << 28)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1L << 31)) == 0 ? ((vector & (UPDATE << 28)) == 0 ? UNSET_CHAR : UPDATE_CHAR) : SETUID_CHAR,
            
            (vector & (READ << 4)) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & (WRITE << 4)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1 << 7)) == 0 ? ((vector & (UPDATE << 4)) == 0 ? UNSET_CHAR : UPDATE_CHAR) : SETGID_CHAR,

            (vector & READ) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & WRITE) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1 << 3)) == 0 ? ((vector & UPDATE) == 0 ? UNSET_CHAR : UPDATE_CHAR) : STICKY_CHAR
        };
        return String.valueOf(ch);
    }

    /**
     *  Check  if user has the requested mode for this resource.
     *
     *@param  user  The user
     *@param  mode  The requested mode
     *@return       true if user has the requested mode
     */
    @Override
    public boolean validate(Subject user, int mode) {

        //group dba has full access
        if(user.hasDbaRole()) {
            return true;
        }

        //check owner
        if(user.getId() == (vector >>> 32)) {                   //check owner
            return (mode & ((vector >>> 28) & 7)) == mode;      //check owner mode
        }

        //check group
        int userGroupIds[] = user.getGroupIds();
        int groupId = (int)((vector >>> 8) & 1048575);
        for(int userGroupId : userGroupIds) {
            if(userGroupId == groupId) {
                return (mode & ((vector >>> 4) & 7)) == mode;
            }
        }

        //check other
        if((mode & (vector & 7)) == mode) {
            return true;
        }

        return false;

        /*
        if((permissionCheck & vector) == permissionCheck) { //check user and user mode
            return true;    //matched both the username and the mode
        } else if(((permissionCheck >>> 29) & (vector >>> 29)) == permissionCheck >>> 29) {
            ///prevents fall-through, i.e. if the username matches and the mode didnt then stop comparrisons
            return false;
        }

        //check group
        int userGroupIds[] = user.getGroupIds();
        for(int userGroupId : userGroupIds) {
            permissionCheck = encodeAsBitVector(0, userGroupId, mode << 3);
            if((permissionCheck & vector) == permissionCheck) { //check group and group mode
                return true;
            } else if((((permissionCheck >> 6) & 1048575) & ((vector >> 6) & 1048575)) == ((permissionCheck >> 6) & 1048575)) {
                ///prevents fall-through, i.e. if the grouname matches and the mode didnt then stop comparrisons
                return false;
            }
        }
        
        //check other
        permissionCheck = encodeAsBitVector(0, 0, mode); //check other mode
        if((permissionCheck & vector) == permissionCheck) {
            return true;
        }
        
        return false;*/
    }

    @Override
    public void read(VariableByteInput istream) throws IOException {
        this.vector = istream.readLong();
    }

    @Override
    public void write(VariableByteOutputStream ostream) {
        ostream.writeLong(vector);
    }

    protected final long getVector() {
        return vector;
    }

    /**
     * should return max of 52 bits - e.g. The maximum numeric value - 4503599627370495
     * exact encoding is [userId(20),setUid(1),userMode(rwx)(3),groupId(20),setGid(1),groupMode(rwx)(3),sticky(1),otherMode(rwx)(3)]
     */
    protected final long encodeAsBitVector(int userId, int groupId, int mode) {

        //makes sure mode is only 12 bits max - TODO maybe error if not 10 bits
        mode = mode & 4095;

        //makes sure userId is only 20 bits max - TODO maybe error if not 20 bits
        userId = userId & 1048575;

        //makes sure groupId is only 20 bits max - TODO maybe error if not 20 bits
        groupId = groupId & 1048575;

        int setUid = (mode >>> 11) & 1;
        int setGid = (mode >>> 10) & 1;
        int sticky = (mode >>> 9) & 1;

        int userPerm = (mode >>> 6) & 7;
        int groupPerm = (mode >>> 3) & 7;
        int otherPerm = mode & 7;

        return ((long)userId << 32) | ((long)setUid << 31) | (userPerm << 28) | (groupId << 8) | (setGid << 7) | (groupPerm << 4) | (sticky << 3) | otherPerm;
    }

    protected Subject getCurrentSubject() {
        return sm.getDatabase().getSubject();
    }

    @Override
    public boolean isCurrentSubjectDBA() {
        return getCurrentSubject().hasDbaRole();
    }

    @Override
    public boolean isCurrentSubjectOwner() {
        return getCurrentSubject().getId() == getOwnerId();
    }

    @Override
    public boolean isCurrentSubjectInGroup() {
        final int groupId = getGroupId();
        for(int currentSubjectGroupId : getCurrentSubject().getGroupIds()) {
            if(groupId == currentSubjectGroupId) {
                return true;
            }
        }
        return false;
    }
}