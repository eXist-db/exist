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

package org.exist.util;

import org.easymock.EasyMock;
import org.junit.Test;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertFalse;

public class LeasableTest {


    @Test
    public void fromCloseable() {
        AutoCloseable autoCloseable = EasyMock.mock(AutoCloseable.class);

        Leasable<AutoCloseable> leasable = Leasable.fromCloseable(autoCloseable);

        assertFalse(leasable.isLeased());
        assertFalse(leasable.isClosed());

        Leasable<AutoCloseable>.Lease lease1 = leasable.lease();
        assertTrue(leasable.isLeased());

        Leasable<AutoCloseable>.Lease lease2 = leasable.lease();

        assertFalse(lease1.isClosed());
        lease1.close();
        assertTrue(lease1.isClosed());

        assertTrue(leasable.isLeased());
        assertFalse(leasable.isClosed());

        lease2.close();
        assertFalse(leasable.isLeased());
        assertTrue(leasable.isClosed());
    }
}
