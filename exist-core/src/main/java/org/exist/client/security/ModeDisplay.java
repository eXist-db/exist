/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.client.security;

import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.UnixStylePermissionAider;

/**
 * Used for Tri-State display of a permission mode
 * when multiple items are selected.
 *
 * If a value is true, then all items are set.
 * If a value is false, then all items are unset.
 * If a value is null, then some items are set and some are unset, none will be changed.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ModeDisplay {
    public Boolean ownerRead;
    public Boolean ownerWrite;
    public Boolean ownerExecute;
    public Boolean setUid;

    public Boolean groupRead;
    public Boolean groupWrite;
    public Boolean groupExecute;
    public Boolean setGid;

    public Boolean otherRead;
    public Boolean otherWrite;
    public Boolean otherExecute;
    public Boolean sticky;

    public static ModeDisplay fromPermission(final Permission permission) {
        final ModeDisplay modeDisplay = new ModeDisplay();

        final int ownerMode = permission.getOwnerMode();
        modeDisplay.ownerRead = (ownerMode & Permission.READ) == Permission.READ;
        modeDisplay.ownerWrite = (ownerMode & Permission.WRITE) == Permission.WRITE;
        modeDisplay.ownerExecute = (ownerMode & Permission.EXECUTE) == Permission.EXECUTE;

        final int groupMode = permission.getGroupMode();
        modeDisplay.groupRead = (groupMode & Permission.READ) == Permission.READ;
        modeDisplay.groupWrite = (groupMode & Permission.WRITE) == Permission.WRITE;
        modeDisplay.groupExecute = (groupMode & Permission.EXECUTE) == Permission.EXECUTE;

        final int otherMode = permission.getOtherMode();
        modeDisplay.otherRead = (otherMode & Permission.READ) == Permission.READ;
        modeDisplay.otherWrite = (otherMode & Permission.WRITE) == Permission.WRITE;
        modeDisplay.otherExecute = (otherMode & Permission.EXECUTE) == Permission.EXECUTE;

        modeDisplay.setUid = permission.isSetUid();
        modeDisplay.setGid = permission.isSetGid();
        modeDisplay.sticky = permission.isSticky();

        return modeDisplay;
    }

    public void writeToPermission(final Permission newMode) throws PermissionDeniedException {

        // NOTE: we only modify those mode bits which are not null (i.e. TristateState.INDETERMINATE)

        if (ownerRead != null) {
            if (ownerRead) {
                newMode.setOwnerMode(newMode.getOwnerMode() | Permission.READ);
            } else {
                newMode.setOwnerMode(newMode.getOwnerMode() & ~Permission.READ);
            }
        }

        if (ownerWrite != null) {
            if (ownerWrite) {
                newMode.setOwnerMode(newMode.getOwnerMode() | Permission.WRITE);
            } else {
                newMode.setOwnerMode(newMode.getOwnerMode() & ~Permission.WRITE);
            }
        }

        if (ownerExecute != null) {
            if (ownerExecute) {
                newMode.setOwnerMode(newMode.getOwnerMode() | Permission.EXECUTE);
            } else {
                newMode.setOwnerMode(newMode.getOwnerMode() & ~Permission.EXECUTE);
            }
        }

        if (groupRead != null) {
            if (groupRead) {
                newMode.setGroupMode(newMode.getGroupMode() | Permission.READ);
            } else {
                newMode.setGroupMode(newMode.getGroupMode() & ~Permission.READ);
            }
        }

        if (groupWrite != null) {
            if (groupWrite) {
                newMode.setGroupMode(newMode.getGroupMode() | Permission.WRITE);
            } else {
                newMode.setGroupMode(newMode.getGroupMode() & ~Permission.WRITE);
            }
        }

        if (groupExecute != null) {
            if (groupExecute) {
                newMode.setGroupMode(newMode.getGroupMode() | Permission.EXECUTE);
            } else {
                newMode.setGroupMode(newMode.getGroupMode() & ~Permission.EXECUTE);
            }
        }

        if (otherRead != null) {
            if (otherRead) {
                newMode.setOtherMode(newMode.getOtherMode() | Permission.READ);
            } else {
                newMode.setOtherMode(newMode.getOtherMode() & ~Permission.READ);
            }
        }

        if (otherWrite != null) {
            if (otherWrite) {
                newMode.setOtherMode(newMode.getOtherMode() | Permission.WRITE);
            } else {
                newMode.setOtherMode(newMode.getOtherMode() & ~Permission.WRITE);
            }
        }

        if (otherExecute != null) {
            if (otherExecute) {
                newMode.setOtherMode(newMode.getOtherMode() | Permission.EXECUTE);
            } else {
                newMode.setOtherMode(newMode.getOtherMode() & ~Permission.EXECUTE);
            }
        }

        if (setUid != null) {
            newMode.setSetUid(setUid);
        }

        if (setGid != null) {
            newMode.setSetGid(setGid);
        }

        if (sticky != null) {
            newMode.setSticky(sticky);
        }
    }
}
