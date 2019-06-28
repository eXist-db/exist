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
 *  $Id: UnixStylePermission.java 14502 2011-05-23 10:12:51Z deliriumsky $
 */
package org.exist.security;

import java.io.IOException;
import java.util.Arrays;

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import static org.exist.security.PermissionRequired.IS_DBA;
import static org.exist.security.PermissionRequired.IS_OWNER;
import static org.exist.security.PermissionRequired.ACL_WRITE;

/**
 * A simple ACL (Access Control List) implementation
 * which extends UnixStylePermission with additional
 * ACEs (Access Control Entries).
 *
 * everyone has READ_ACL
 * WRITE access implies WRITE_ACL
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class SimpleACLPermission extends UnixStylePermission implements ACLPermission {

    public static final short VERSION = 1;

    private static final int MAX_ACL_LENGTH = 255; //restrict to sizeof 1 byte

    private int acl[] = new int[0];

    public SimpleACLPermission(final SecurityManager sm) {
        super(sm);
    }

    public SimpleACLPermission(final SecurityManager sm, final long vector) {
        super(sm, vector);
    }

    public SimpleACLPermission(final SecurityManager sm, final int ownerId, final int groupId, final int mode) {
        super(sm, ownerId, groupId, mode);
    }

    public void addUserACE(final ACE_ACCESS_TYPE access_type, final int userId, final int mode) throws PermissionDeniedException {
        addACE(access_type, ACE_TARGET.USER, userId, mode);
    }

    public void addGroupACE(final ACE_ACCESS_TYPE access_type, final int groupId, final int mode) throws PermissionDeniedException {
        addACE(access_type, ACE_TARGET.GROUP, groupId, mode);
    }

    @Override
    public void addACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String name, final String modeStr) throws PermissionDeniedException {
        addACE(access_type, target, lookupTargetId(target, name), modeStrToMode(modeStr));
    }

    @Override
    public void addACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String name, final int mode) throws PermissionDeniedException {
        addACE(access_type, target, lookupTargetId(target, name), mode);
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    private void addACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final int id, final int mode) throws PermissionDeniedException {
        if (acl.length >= MAX_ACL_LENGTH) {
            throw new PermissionDeniedException("Maximum of " + MAX_ACL_LENGTH + " ACEs has been reached.");
        }

        final int newAcl[] = new int[acl.length + 1];
        System.arraycopy(acl, 0, newAcl, 0, acl.length);
        newAcl[newAcl.length - 1] = encodeAsACE(access_type, target, id, mode);
        this.acl = newAcl;
    }

    public void insertUserACE(final int index, final ACE_ACCESS_TYPE access_type, final int userId, final int mode) throws PermissionDeniedException {
        insertACE(index, access_type, ACE_TARGET.USER, userId, mode);
    }

    public void insertGroupACE(final int index, final ACE_ACCESS_TYPE access_type, final int groupId, final int mode) throws PermissionDeniedException {
        insertACE(index, access_type, ACE_TARGET.GROUP, groupId, mode);
    }

    @Override
    public void insertACE(final int index, final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String name, final String modeStr) throws PermissionDeniedException {
        insertACE(index, access_type, target, lookupTargetId(target, name), modeStrToMode(modeStr));
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    private void insertACE(final int index, final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final int id, final int mode) throws PermissionDeniedException {
        if (acl.length >= MAX_ACL_LENGTH) {
            throw new PermissionDeniedException("Maximum of " + MAX_ACL_LENGTH + " ACEs has been reached.");
        }

        if (index < 0 || (acl.length > 0 && acl.length <= index)) {
            throw new PermissionDeniedException("No Such ACE index " + index + " in ACL.");
        }

        final int newAcl[] = new int[acl.length + 1];
        System.arraycopy(acl, 0, newAcl, 0, index);
        newAcl[index] = encodeAsACE(access_type, target, id, mode);
        if (acl.length > 0) {
            System.arraycopy(acl, index, newAcl, index + 1, newAcl.length - index - 1);
        }
        this.acl = newAcl;
    }

    private int modeStrToMode(final String modeStr) throws PermissionDeniedException {
        if (modeStr == null || modeStr.length() == 0 || modeStr.length() > 3) {
            throw new PermissionDeniedException("Invalid mode string '" + modeStr + "'");
        }

        int mode = 0;
        for (final char c : modeStr.toCharArray()) {
            switch (c) {
                case READ_CHAR:
                    mode |= READ;
                    break;
                case WRITE_CHAR:
                    mode |= WRITE;
                    break;
                case EXECUTE_CHAR:
                    mode |= EXECUTE;
                    break;
                case UNSET_CHAR:
                    break;
                default:
                    throw new PermissionDeniedException("Unknown char '" + c + "' in mode string '" + modeStr + "'");
            }
        }
        return mode;
    }

    private int lookupTargetId(final ACE_TARGET target, final String targetName) throws PermissionDeniedException {
        final int id;
        if (target == ACE_TARGET.USER) {
            final Account account = sm.getAccount(targetName);
            if (account == null) {
                throw new PermissionDeniedException("User Account for username '" + targetName + "' is unknown.");
            }
            id = account.getId();
        } else if (target == ACE_TARGET.GROUP) {
            final Group group = sm.getGroup(targetName);
            if (group == null) {
                throw new PermissionDeniedException("User Group for groupname '" + targetName + "' is unknown.");
            }
            id = group.getId();
        } else {
            throw new PermissionDeniedException("Unknown ACE_TARGET type");
        }
        return id;
    }

    /**
     * should return max of 29 bits - e.g. The maximum numeric value - 536870911
     * exact encoding is [target(3),id(20),mode(3),access_type(3)]
     */
    private int encodeAsACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, int id, int mode) {
        //ensure mode is just 3 bits max (rwu) - TODO(AR) maybe error if not 20 bits
        mode = mode & 7;

        //makes sure id is only 20 bits max - TODO(AR) maybe error if not 20 bits
        id = id & 1048575;

        return (target.getVal() << 26) | (id << 6) | (mode << 3) | access_type.getVal();
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    @Override
    public void removeACE(final int index) throws PermissionDeniedException {

        if (index < 0 || index >= acl.length) {
            throw new PermissionDeniedException("ACL Entry does not exist");
        }

        final int newAcl[] = new int[acl.length - 1];
        System.arraycopy(acl, 0, newAcl, 0, index);
        System.arraycopy(acl, index + 1, newAcl, index, newAcl.length - index);
        this.acl = newAcl;
    }

    @Override
    public void modifyACE(final int index, final ACE_ACCESS_TYPE access_type, final String modeStr) throws PermissionDeniedException {
        modifyACE(index, access_type, modeStrToMode(modeStr));
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    @Override
    public void modifyACE(final int index, final ACE_ACCESS_TYPE access_type, final int mode) throws PermissionDeniedException {

        if (index < 0 || index >= acl.length) {
            throw new PermissionDeniedException("ACL Entry does not exist");
        }

        final int ace = acl[index];
        acl[index] = ((ace >>> 6) << 6) | (mode << 3) | access_type.getVal();
    }

    /**
     * Clears all ACE's
     */
    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    @Override
    public void clear() {
        acl = new int[0];
    }

    public int getACEId(final int index) {
        return (acl[index] >>> 6) & 1048575;
    }

    /**
     * Convenience method for getting the name of the user or group
     * of which this ace is applied to
     */
    @Override
    public String getACEWho(final int index) {
        switch (getACETarget(index)) {
            case USER:
                return sm.getAccount(getACEId(index)).getName();
            case GROUP:
                return sm.getGroup(getACEId(index)).getName();
            default:
                return null;
        }
    }

    @Override
    public int getACEMode(final int index) {
        return (acl[index] >>> 3) & 7;
    }

    public String getACEModeString(final int index) {
        final int aceMode = getACEMode(index);

        final char ch[] = new char[]{
                (aceMode & READ) != READ ? UNSET_CHAR : READ_CHAR,
                (aceMode & WRITE) != WRITE ? UNSET_CHAR : WRITE_CHAR,
                (aceMode & EXECUTE) != EXECUTE ? UNSET_CHAR : EXECUTE_CHAR
        };
        return String.valueOf(ch);
    }

    @Override
    public ACE_TARGET getACETarget(final int index) {
        return ACE_TARGET.fromVal(acl[index] >>> 26);
    }

    @Override
    public ACE_ACCESS_TYPE getACEAccessType(final int index) {
        return ACE_ACCESS_TYPE.fromVal(acl[index] & 7);
    }

    @Override
    public int getACECount() {
        return acl.length;
    }

    @Override
    public void read(final VariableByteInput istream) throws IOException {
        super.read(istream);
        final int aclLength = istream.read();
        acl = new int[aclLength];
        for (int i = 0; i < aclLength; i++) {
            acl[i] = istream.readInt();
        }
    }

    @Override
    public void write(final VariableByteOutputStream ostream) throws IOException {
        super.write(ostream);
        ostream.write(acl.length);
        for (final int ace : acl) {
            ostream.writeInt(ace);
        }
    }

    /**
     * Evaluation order is -
     *
     * 1) ACL ACEs are evaluated first
     * 2) Classic Unix Style Permissions are evaluated second
     *
     * The first match is considered the authority
     */
    @Override
    public boolean validate(final Subject user, final int mode) {

        //group dba has full access
        if (user.hasDbaRole()) {
            return true;
        }

        final int userId = user.getId();
        final int userGroupIds[] = user.getGroupIds();

        /*
         * START EXTENDED ACL VALIDATION.
         *
         * exact encoding is [target(3),id(20),mode(3),access_type(3)]
         */

        //check ACL
        for (final int ace : acl) {

            final int aceTarget = ace >>> 26;
            final int id = (ace >>> 6) & 1048575;
            final int aceMode = (ace >>> 3) & 7;
            final int accessType = ace & 7;

            if ((aceTarget & ACE_TARGET.USER.getVal()) == ACE_TARGET.USER.getVal()) {
                //check for a user
                if (id == userId && (aceMode & mode) == mode) {
                    return (accessType == ACE_ACCESS_TYPE.ALLOWED.getVal());
                }
            } else if ((aceTarget & ACE_TARGET.GROUP.getVal()) == ACE_TARGET.GROUP.getVal()) {
                //check for a group
                for (final int userGroupId : userGroupIds) {
                    if (userGroupId == id && (aceMode & mode) == mode) {
                        return (accessType == ACE_ACCESS_TYPE.ALLOWED.getVal());
                    }
                }
            }
        }

        /*
         *   END EXTENDED ACL VALIDATION
         */


        /*
         * FALLBACK to UNIX STYLE VALIDATION
         */

        //check owner
        if (userId == (vector >>> 32)) {                     //check owner
            return (mode & ((vector >>> 28) & 7)) == mode;  //check owner mode
        }

        //check group

        final int groupId = (int) ((vector >>> 8) & 1048575);
        for (final int userGroupId : userGroupIds) {
            if (userGroupId == groupId) {
                return (mode & ((vector >>> 4) & 7)) == mode;
            }
        }

        //check other
        if ((mode & (vector & 7)) == mode) {
            return true;
        }

        /*
         * END FALLBACK to UNIX STYLE VALIDATION
         */

        return false;
    }

    @Override
    public short getVersion() {
        return VERSION;
    }

    @Override
    public boolean isCurrentSubjectCanWriteACL() {
        return validate(getCurrentSubject(), WRITE);
    }

    @Override
    public SimpleACLPermission copy() {
        final SimpleACLPermission prm = new SimpleACLPermission(sm, vector);

        prm.acl = new int[acl.length];
        System.arraycopy(acl, 0, prm.acl, 0, acl.length);

        return prm;
    }

    /**
     * Determines if this permisisons ACL is equal to that
     * of another permissions ACL.
     *
     * @param other the other ACL to check equality against.
     *
     * @return true if the ACLs are equal
     */
    public boolean equalsAcl(final SimpleACLPermission other) {
        if (other == null || other.getACECount() != getACECount()) {
            return false;
        }

        for (int i = 0; i < getACECount(); i++) {

            if(getACEAccessType(i) != other.getACEAccessType(i)
                    || getACETarget(i) != other.getACETarget(i)
                    || (!getACEWho(i).equals(other.getACEWho(i)))
                    || getACEMode(i) != other.getACEMode(i)) {
                return false;
            }
        }

        return true;
    }

    @PermissionRequired(user = IS_DBA | IS_OWNER, mode = ACL_WRITE)
    public void copyAclOf(final SimpleACLPermission simpleACLPermission) {
        this.acl = Arrays.copyOf(simpleACLPermission.acl, simpleACLPermission.acl.length);
    }

    @Override
    public boolean aclEquals(final ACLPermission other) {
        if (other == null) {
            return false;
        }

        if (other instanceof SimpleACLPermission) {
            // optimisation for when both are the same type
            return Arrays.equals(acl, ((SimpleACLPermission) other).acl);
        } else {
            if (getACECount() != other.getACECount()) {
                return false;
            }

            for (int i = 0; i < getACECount(); i++) {
                if (getACEAccessType(i) != other.getACEAccessType(i)
                        || getACETarget(i) != other.getACETarget(i)
                        || (!getACEWho(i).equals(other.getACEWho(i)))
                        || getACEMode(i) != other.getACEMode(i)) {
                    return false;
                }
            }

            return true;
        }
    }
}
