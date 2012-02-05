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
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class SimpleACLPermissionAider extends UnixStylePermissionAider implements ACLPermission {

    private final List<ACEAider> aces = new ArrayList<ACEAider>();

    public SimpleACLPermissionAider() {
        super();
    }

    public SimpleACLPermissionAider(int mode) {
        super(mode);
    }

    public SimpleACLPermissionAider(String user, String group, int mode) {
        super(user, group, mode);
    }

    @Override
    public short getVersion() {
        return SimpleACLPermission.VERSION;
    }

    @Override
    public void addACE(ACE_ACCESS_TYPE access_type, ACE_TARGET target, String who, int mode) throws PermissionDeniedException {
        //TODO validate()
        aces.add(new ACEAider(access_type, target, who, mode));
    }

    @Override
    public int getACECount() {
        return aces.size();
    }

    @Override
    public ACE_ACCESS_TYPE getACEAccessType(int index) {
        return aces.get(index).getAccessType();
    }

    @Override
    public ACE_TARGET getACETarget(int index) {
        return aces.get(index).getTarget();
    }

    @Override
    public String getACEWho(int index) {
        return aces.get(index).getWho();
    }

    @Override
    public int getACEMode(int index) {
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


}