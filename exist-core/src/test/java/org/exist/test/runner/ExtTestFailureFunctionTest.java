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
package org.exist.test.runner;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtTestFailureFunctionTest {

    @Test
    void shortValueIsKept() {
        assertEquals("abc", ExtTestFailureFunction.abbreviate("abc"));
        final String exactlyMax = "x".repeat(ExtTestFailureFunction.MAX_VALUE_LENGTH);
        assertEquals(exactlyMax, ExtTestFailureFunction.abbreviate(exactlyMax));
    }

    @Test
    void longValueIsCutOffAndSaysHowLongItWas() {
        final String abbreviated = ExtTestFailureFunction.abbreviate("x".repeat(100_000));
        assertEquals(ExtTestFailureFunction.MAX_VALUE_LENGTH, abbreviated.length());
        assertTrue(abbreviated.endsWith("... [truncated, 100000 characters in all]"), abbreviated);
        assertTrue(abbreviated.startsWith("xxx"));
    }
}
