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

import org.exist.security.PermissionDeniedException;
import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class BasicPermissionsTableModelTest {
    
    @Test
    public void getMode() throws PermissionDeniedException {
        
        final int[] modes = {
            0,
            1,
            4,
            5,
            7,
            9,
            36,
            45,
            63,
            73,
            292,
            365,
            511,
            457,
            484,
            493,
            2048,
            2112,
            1024,
            1032,
            3583
        };
        
        for(final int mode : modes) {
            final UnixStylePermissionAider permission = new UnixStylePermissionAider(mode);
            final ModeDisplay modeDisplay = ModeDisplay.fromPermission(permission);
            final BasicPermissionsTableModel model = new BasicPermissionsTableModel(modeDisplay);
            final UnixStylePermissionAider updatedPermission = new UnixStylePermissionAider();
            model.getMode().writeToPermission(updatedPermission);
            assertEquals(mode, updatedPermission.getMode());
        }
    }
}
