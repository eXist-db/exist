/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2019 The eXist Project
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

package org.exist.util.io;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * Tests the {@link ByteArrayContent} implementation.
 *
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class ByteArrayContentTest {
    private ByteArrayContent content;

    @Before
    public void setUp() {
        content = ByteArrayContent.of("test data");
    }

    @Test(expected = NullPointerException.class)
    public void testOfNullString() {
        ByteArrayContent.of((String) null);
    }

    @Test
    public void testOfNullBytes() {
        content = ByteArrayContent.of((byte[]) null);
        assertEquals(0, content.size());
        assertArrayEquals(new byte[0], content.getBytes());
    }

    @Test
    public void testClose() {
        content.close();
        assertEquals(0, content.size());
        assertArrayEquals(new byte[0], content.getBytes());
    }

    @Test
    public void testGetBytes() {
        assertArrayEquals("test data".getBytes(), content.getBytes());
    }

    @Test
    public void testSize() {
        assertEquals(9, content.size());
    }
}
