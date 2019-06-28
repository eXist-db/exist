/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.storage.journal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LsnTest {

    @Test
    public void compareTo() {
        assertEquals(0, Lsn.LSN_INVALID.compareTo(Lsn.LSN_INVALID));
        assertEquals(-1, Lsn.LSN_INVALID.compareTo(new Lsn((short)0, 0)));
        assertEquals(1, new Lsn((short)0, 0).compareTo(Lsn.LSN_INVALID));

        assertEquals(0, new Lsn((short)0, 0).compareTo(new Lsn((short)0, 0)));

        assertEquals(0, new Lsn((short)1, 123).compareTo(new Lsn((short)1, 123)));
        assertEquals(-1, new Lsn((short)1, 123).compareTo(new Lsn((short)1, 124)));
        assertEquals(1, new Lsn((short)1, 124).compareTo(new Lsn((short)1, 122)));

        assertEquals(-1, new Lsn((short)1, 123).compareTo(new Lsn((short)2, 123)));
        assertEquals(1, new Lsn((short)2, 123).compareTo(new Lsn((short)1, 123)));

        assertEquals(-1, new Lsn((short)1, Long.MAX_VALUE).compareTo(new Lsn((short)2, Long.MIN_VALUE)));
        assertEquals(-1, new Lsn((short)1, Long.MAX_VALUE).compareTo(new Lsn((short)2, 0)));
        assertEquals(-1, new Lsn((short)1, Long.MAX_VALUE).compareTo(new Lsn((short)2, 1)));
        assertEquals(-1, new Lsn((short)1, Long.MAX_VALUE).compareTo(new Lsn((short)2, Long.MAX_VALUE)));

        assertEquals(1, new Lsn((short)2, Long.MIN_VALUE).compareTo(new Lsn((short)1, Integer.MAX_VALUE)));
        assertEquals(1, new Lsn((short)2, 0).compareTo(new Lsn((short)1, Integer.MAX_VALUE)));
        assertEquals(1, new Lsn((short)2, 1).compareTo(new Lsn((short)1, Integer.MAX_VALUE)));
        assertEquals(1, new Lsn((short)2, Long.MAX_VALUE).compareTo(new Lsn((short)1, Integer.MAX_VALUE)));
    }

    @Test
    public void equalsTo() {
        assertTrue(Lsn.LSN_INVALID.equals(Lsn.LSN_INVALID));
        assertTrue(Lsn.LSN_INVALID.equals(new Lsn((short)-1, -1)));
        assertTrue(new Lsn((short)-1, -1).equals(Lsn.LSN_INVALID));

        assertFalse(new Lsn((short)0, 0).equals(Lsn.LSN_INVALID));
        assertFalse(Lsn.LSN_INVALID.equals(new Lsn((short)0, 0)));
    }

}
