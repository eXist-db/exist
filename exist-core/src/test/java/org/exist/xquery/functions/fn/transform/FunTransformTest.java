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
package org.exist.xquery.functions.fn.transform;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

public class FunTransformTest {

    @Test
    void versionNumbers() throws Transform.PendingException {

        Options.XSLTVersion version1 = new Options.XSLTVersion(1, 0);
        Options.XSLTVersion version2 = new Options.XSLTVersion(2, 0);
        Options.XSLTVersion version3 = new Options.XSLTVersion(3, 0);
        Options.XSLTVersion version31 = new Options.XSLTVersion(3, 1);
        assertNotEquals(version1, version2);
        assertNotEquals(version1, version3);
        assertNotEquals(version2, version3);
        assertNotEquals(version3, version31);
        assertEquals(version3, Options.XSLTVersion.fromDecimal(new BigDecimal("3.0")));
        assertNotEquals(version3, Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
        assertEquals(version31, Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
        assertEquals(Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")), Options.XSLTVersion.fromDecimal(new BigDecimal("3.1")));
    }

    @Test void badVersionNumber() throws Transform.PendingException {

        assertThrows(Transform.PendingException.class, () -> {
            Options.XSLTVersion version311 = Options.XSLTVersion.fromDecimal(new BigDecimal("3.11"));
        });
    }
}
