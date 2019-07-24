/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.security;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.internal.RealmImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import static org.exist.security.PermissionRequired.*;

/**
 * Manages the permissions assigned to a resource. This includes
 * the user who owns the resource, the owner group and the permissions
 * for owner, group and others.
 *
 * Permissions are encoded into a 52 bit vector with the following convention -
 *
 * [userId(20),setUid(1),userMode(rwx)(3),groupId(20),setGid(1),groupMode(rwx)(3),sticky(1),otherMode(rwx)(3)]
 * @see UnixStylePermission#encodeAsBitVector(int, int, int) for more details
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class UnixStylePermission extends AbstractUnixStylePermission implements Permission {

    public final static Logger LOG = LogManager.getLogger(SecurityManager.class);

    protected final SecurityManager sm;

    protected long vector = encodeAsBitVector(RealmImpl.SYSTEM_ACCOUNT_ID, RealmImpl.DBA_GROUP_ID, 0);

    public UnixStylePermission(final SecurityManager sm) {
    	if(sm == null) {
            throw new IllegalArgumentException("Security manager can't be null");
        }
    	this.sm = sm;
    }
    
    protected UnixStylePermission(final SecurityManager sm, final long vector) {
        if(sm == null) {
            throw new IllegalArgumentException("Security manager can't be null");
        }
        this.sm = sm;
        this.vector = vector;
    }
    
    /**
     * Construct a permission with given user, group and permissions
     * @param sm the security manager
     * @param ownerId the owner
     * @param groupId id of the group
     * @param mode mode for the resource.

     */
    public UnixStylePermission(final SecurityManager sm, final int ownerId, final int groupId, final int mode) {
        this(sm);
        this.vector = encodeAsBitVector(ownerId, groupId, mode);
    }

    /**
     *  Gets the user who owns this resource
     *
     * @return The owner value
     */
    @Override
    public Account getOwner() {
        int id = getOwnerId();
        Account account = sm.getAccount(id);
        if (account == null) {
            LOG.fatal(
                "Detected a Security Database corruption. Could not find account for id: {}.",
                id
            );
            return sm.getSystemSubject();
        }
        return account;
    }

    private int getOwnerId() {
        return (int)(vector >>> 32);
    }

    /**
     * Set the owner passed as User object
     *
     * @param account The new owner value
     */
    @Override
    public void setOwner(Account account) {
    	
        //assume SYSTEM identity if user gets lost due to a database corruption - WTF???
        //TODO this should eventually be replaced with a PermissionDeniedException
    	if(account == null) {
            account = sm.getSystemSubject();
    	}
        
        final int accountId = account.getId();
        if(accountId != getOwnerId()) {
            setOwnerId(accountId);
        }
    }

    @Override
    public void setOwner(final int id) {
        Account account = sm.getAccount(id);
        
        //assume SYSTEM identity if user gets lost due to a database corruption - WTF???
        //TODO this should eventually be replaced with a PermissionDeniedException
        if(account == null) {
            account = sm.getSystemSubject();
        }
        final int accountId = account.getId();
     
        if(accountId != getOwnerId()) {
            setOwnerId(accountId);
        }
    }

    /**
     * Set the owner
     *
     * @param name The new owner value
     */
    @Override
    public void setOwner(final String name) {
    	final Account account = sm.getAccount(name);
    	if(account != null){
            final int accountId = account.getId();
            if(accountId != getOwnerId()) {
                setOwnerId(accountId);
            }
        }
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    private void setOwnerId(@PermissionRequired(user = IS_DBA | NOT_POSIX_CHOWN_RESTRICTED) final int ownerId) {
        this.vector =
            ((long)ownerId << 32) | //left shift new ownerId into position
            (vector & 4294967295L); //extract everything from current permission except ownerId
    }

    /**
     * Gets the group 
     *
     * @return The group value
     */
    @Override
    public Group getGroup() {
        int id = getGroupId();
        Group group = sm.getGroup(id);
        if (group == null) {
            LOG.fatal(
                "Detected a Security Database corruption. Could not find group for id: {}.",
                id
            );
            return sm.getDBAGroup();
        }
        return group;
    }

    private int getGroupId() {
        return (int)((vector >>> 8) & 1048575);
    }
    
    /**
     * Set the owner group
     *
     * @param groupName The new group value
     */
    @Override
    public void setGroup(final String groupName) {
        final Group group = sm.getGroup(groupName);
        if(group != null) {
            setGroupId(group.getId());
        }
    }

    @Override
    public void setGroup(final Group group) {
    	if(group != null){
            setGroupId(group.getId());
        }
    }
    
    @Override
    public void setGroup(final int id) {
        Group group = sm.getGroup(id);
        if(group == null){
            group = sm.getDBAGroup(); //TODO is this needed?
        }
        setGroupId(group.getId());
    }
    
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    private void setGroupId(@PermissionRequired(user = IS_DBA | IS_MEMBER | NOT_POSIX_CHOWN_RESTRICTED) final int groupId) {
        /*
        This function wrapper is really just used as a place
        to focus PermissionRequired checks for several public
        functions
        */
        _setGroupId(groupId);
    }
    
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setGroupFrom(@PermissionRequired(mode = IS_SET_GID) final Permission other) {
        _setGroupId(other.getGroup().getId());
    }
    
    private void _setGroupId(final int groupId) {
        this.vector =
            ((vector >>> 28) << 28) | //current ownerId and ownerMode, mask rest
            (groupId << 8) |          //left shift new groupId into positon
            (vector & 255);            //current groupMode and otherMode
    }
    
    /**
     * Get the mode
     *
     * @return The mode
     */
    @Override
    public int getMode() {
        return (int)
            ((((vector >>> 31) & 1) << 11) |    //setUid
            (((vector >>> 7) & 1) << 10) |      //setGid
            (((vector >>> 3) & 1) << 9) |       //sticky
            ((((vector >>> 28) & 7) << 6) |     //userPerm
            (((vector >>> 4) & 7) << 3) |       //groupPerm
            (vector & 7)));                     //otherPerm
    }

    /**
     * Set the mode
     *
     * @param mode The new mode value
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    final public void setMode(final int mode) { 
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
    public void setSetUid(final boolean setUid) {
        this.vector = (((vector >>> 32) << 1 | (setUid ? 1 : 0)) << 31) | (vector & 2147483647);
    }

    @Override
    public boolean isSetGid() {
        return ((vector >>> 7) & 1) == 1;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setSetGid(final boolean setGid) {
        this.vector = (((vector >>> 8) << 1 | (setGid ? 1 : 0)) << 7) | (vector & 127);
    }

    @Override
    public boolean isSticky() {
        return ((vector >>> 3) & 1) == 1;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER)
    @Override
    public void setSticky(final boolean sticky) {
        this.vector = (((vector >>> 4) << 1 | (sticky ? 1 : 0)) << 3) | (vector & 7);
    }
    
    /**
     * Get the active mode for the owner
     *
     * @return The mode value
     */
    @Override
    public int getOwnerMode() {
        return (int) ((vector >>> 28) & 7);
    }

    /**
     * Set mode for the owner
     *
     * @param mode The new owner mode value
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
     * Get the mode for group
     *
     * @return The mode value
     */
    @Override
    public int getGroupMode() {
        return (int)((vector >>> 4) & 7);
    }

    /**
     * Sets mode for group
     *
     * @param  mode The new mode value
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
     * Get the mode for others
     *
     * @return The mode value
     */
    @Override
    public int getOtherMode() {
        return (int)(vector & 7);
    }

    /**
     * Set mode for others
     *
     * @param mode The new other mode value
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
     * Format mode
     *
     * @return the mode formatted as a string e.g. 'rwxrwxrwx'
     */
    @Override
    public String toString() {
        final char ch[] = new char[] {
            (vector & (READ << 28)) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & (WRITE << 28)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1L << 31)) == 0 ? ((vector & (EXECUTE << 28)) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((vector & (EXECUTE << 28)) == 0 ? SETUID_CHAR_NO_EXEC : SETUID_CHAR),
            
            (vector & (READ << 4)) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & (WRITE << 4)) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1 << 7)) == 0 ? ((vector & (EXECUTE << 4)) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((vector & (EXECUTE << 4)) == 0 ? SETGID_CHAR_NO_EXEC : SETGID_CHAR),

            (vector & READ) == 0 ? UNSET_CHAR : READ_CHAR,
            (vector & WRITE) == 0 ? UNSET_CHAR : WRITE_CHAR,
            (vector & (1 << 3)) == 0 ? ((vector & EXECUTE) == 0 ? UNSET_CHAR : EXECUTE_CHAR) : ((vector & EXECUTE) == 0 ? STICKY_CHAR_NO_EXEC : STICKY_CHAR)
        };
        return String.valueOf(ch);
    }

    /**
     * Check if user has the requested mode for this resource.
     *
     * @param user The user
     * @param mode The requested mode
     * @return true if user has the requested mode
     */
    @Override
    public boolean validate(final Subject user, final int mode) {

        //group dba has full access
        if(user.hasDbaRole()) {
            return true;
        }

        //check owner
        if(user.getId() == (vector >>> 32)) {                   //check owner
            return (mode & ((vector >>> 28) & 7)) == mode;      //check owner mode
        }

        //check group
        final int userGroupIds[] = user.getGroupIds();
        final int groupId = (int)((vector >>> 8) & 1048575);
        for(final int userGroupId : userGroupIds) {
            if(userGroupId == groupId) {
                return (mode & ((vector >>> 4) & 7)) == mode;
            }
        }

        //check other
        if((mode & (vector & 7)) == mode) {
            return true;
        }

        return false;
    }

    @Override
    public void read(final VariableByteInput istream) throws IOException {
        this.vector = istream.readLong();
    }

    @Override
    public void write(final VariableByteOutputStream ostream) throws IOException {
        ostream.writeLong(vector);
    }

    protected final long getVector() {
        return vector;
    }

    /**
     * should return max of 52 bits - e.g. The maximum numeric value - 4503599627370495
     * exact encoding is [userId(20),setUid(1),userMode(rwx)(3),groupId(20),setGid(1),groupMode(rwx)(3),sticky(1),otherMode(rwx)(3)]
     * @param userId id of the user
     * @param groupId id of the group
     * @param mode mode for the resource.
     * @return the encoded bit vector
     */
    protected final long encodeAsBitVector(int userId, int groupId, int mode) {

        //makes sure mode is only 12 bits max - TODO maybe error if not 10 bits
        mode = mode & 4095;

        //makes sure userId is only 20 bits max - TODO maybe error if not 20 bits
        userId = userId & 1048575;

        //makes sure groupId is only 20 bits max - TODO maybe error if not 20 bits
        groupId = groupId & 1048575;

        final int setUid = (mode >>> 11) & 1;
        final int setGid = (mode >>> 10) & 1;
        final int sticky = (mode >>> 9) & 1;

        final int userPerm = (mode >>> 6) & 7;
        final int groupPerm = (mode >>> 3) & 7;
        final int otherPerm = mode & 7;

        return ((long)userId << 32) | ((long)setUid << 31) | (userPerm << 28) | (groupId << 8) | (setGid << 7) | (groupPerm << 4) | (sticky << 3) | otherPerm;
    }

    protected Subject getCurrentSubject() {
        return sm.getDatabase().getActiveBroker().getCurrentSubject();
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
        return isCurrentSubjectInGroup(getGroupId());
    }
    
    @Override
    public boolean isCurrentSubjectInGroup(final int groupId) {
        for(final int currentSubjectGroupId : getCurrentSubject().getGroupIds()) {
            if(currentSubjectGroupId == groupId) {
                return true;
            }
        }
        return false;
    }

    @Override
    public UnixStylePermission copy() {
        return new UnixStylePermission(sm, vector);
    }

    @Override
    public boolean isPosixChownRestricted() {
        return sm.getDatabase().getConfiguration().getProperty(DBBroker.POSIX_CHOWN_RESTRICTED_PROPERTY, true);
    }
}