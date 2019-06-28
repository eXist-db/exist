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
package org.exist.security.internal.aider;

import java.util.ArrayList;
import java.util.List;

import org.exist.security.ACLPermission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SimpleACLPermission;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class SimpleACLPermissionAider extends UnixStylePermissionAider implements ACLPermission {

    private final List<ACEAider> aces = new ArrayList<>();

    public SimpleACLPermissionAider() {
        super();
    }

    public SimpleACLPermissionAider(final int mode) {
        super(mode);
    }

    public SimpleACLPermissionAider(final String user, final String group, final int mode) {
        super(user, group, mode);
    }

    @Override
    public short getVersion() {
        return SimpleACLPermission.VERSION;
    }

    @Override
    public void addACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String who, final int mode) throws PermissionDeniedException {
        //TODO validate()
        aces.add(new ACEAider(access_type, target, who, mode));
    }

    @Override
    public void addACE(final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String name, final String modeStr) throws PermissionDeniedException {
        addACE(access_type, target, name, modeStrToMode(modeStr));
    }

    @Override
    public void insertACE(final int index, final ACE_ACCESS_TYPE access_type, final ACE_TARGET target, final String name, final String modeStr) throws PermissionDeniedException {
        aces.add(index, new ACEAider(access_type, target, name, modeStrToMode(modeStr)));
    }

    @Override
    public void modifyACE(final int index, final ACE_ACCESS_TYPE access_type, final String modeStr) throws PermissionDeniedException {
        modifyACE(index, access_type, modeStrToMode(modeStr));
    }

    @Override
    public void modifyACE(final int index, final ACE_ACCESS_TYPE access_type, final int mode) throws PermissionDeniedException {
        final ACEAider ace = aces.get(index);
        ace.setAccessType(access_type);
        ace.setMode(mode);
    }

    @Override
    public void removeACE(final int index) throws PermissionDeniedException {
        aces.remove(index);
    }

    @Override
    public int getACECount() {
        return aces.size();
    }

    @Override
    public ACE_ACCESS_TYPE getACEAccessType(final int index) {
        return aces.get(index).getAccessType();
    }

    @Override
    public ACE_TARGET getACETarget(final int index) {
        return aces.get(index).getTarget();
    }

    @Override
    public String getACEWho(final int index) {
        return aces.get(index).getWho();
    }

    @Override
    public int getACEMode(final int index) {
        return aces.get(index).getMode();
    }

    @Override
    public void clear() throws PermissionDeniedException {
        //TODO validate()
        aces.clear();
    }

    @Override
    public boolean isCurrentSubjectCanWriteACL() {
        //TODO validate()
        return true;
    }

    @Override
    public boolean aclEquals(final ACLPermission other) {
        if (other == null) {
            return false;
        }

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
}
