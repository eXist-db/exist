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

package org.exist.util.io;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests the {@link ByteArrayContent} implementation.
 *
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
class ByteArrayContentTest {
    private ByteArrayContent content;

    @BeforeEach
    void setUp() {
        content = ByteArrayContent.of("test data");
    }

    @Test
    void testOfNullString() {
        assertThrows(NullPointerException.class, () ->
            ByteArrayContent.of((String) null));
    }

    @Test
    void testOfNullBytes() {
        content = ByteArrayContent.of((byte[]) null);
        assertEquals(0, content.size());
        assertArrayEquals(new byte[0], content.getBytes());
    }

    @Test
    void testClose() {
        content.close();
        assertEquals(0, content.size());
        assertArrayEquals(new byte[0], content.getBytes());
    }

    @Test
    void testGetBytes() {
        assertArrayEquals("test data".getBytes(), content.getBytes());
    }

    @Test
    void testSize() {
        assertEquals(9, content.size());
    }
}
