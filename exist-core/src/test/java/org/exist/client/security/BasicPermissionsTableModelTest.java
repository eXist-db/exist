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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.client.security;

import org.exist.security.internal.aider.UnixStylePermissionAider;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BasicPermissionsTableModelTest {
    
    @Test
    public void getMode() {
        
        final int modes[] = {
            0,
            01,
            04,
            05,
            07,
            011,
            044,
            055,
            077,
            0111,
            0444,
            0555,
            0777,
            0711,
            0744,
            0755,
            04000,
            04100,
            02000,
            02010,
            06777
        };
        
        for(final int mode : modes) {
            final UnixStylePermissionAider permission = new UnixStylePermissionAider(mode);
            final BasicPermissionsTableModel model = new BasicPermissionsTableModel(permission);
            assertEquals(mode, model.getMode());
        }
    }
}
