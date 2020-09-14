/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
