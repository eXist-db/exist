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
package org.exist.start;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MainTest {

    /**
     * Regression test for the 7.0.0-beta3 macOS dock-launch failure
     * (NegativeArraySizeException: -1). The appbundler-generated macOS
     * app launches the JVM with zero arguments; previously stripFirstElement
     * would unconditionally allocate `new String[args.length - 1]` and
     * throw on the empty input.
     */
    @Test
    public void stripFirstElementOnEmptyArrayReturnsEmpty() {
        final String[] result = Main.stripFirstElement(new String[0]);
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void stripFirstElementOnSingleElementReturnsEmpty() {
        final String[] result = Main.stripFirstElement(new String[]{"jetty"});
        assertNotNull(result);
        assertEquals(0, result.length);
    }

    @Test
    public void stripFirstElementOnMultipleElementsDropsFirst() {
        final String[] result = Main.stripFirstElement(new String[]{"jetty", "a", "b"});
        assertArrayEquals(new String[]{"a", "b"}, result);
    }
}
