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
package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
class LongTest {

    @Test
    void testOver() {
        assertThrows(XPathException.class, () -> {
            new IntegerValue("9223372036854775808", Type.LONG);
        });
    }

    @Test
    void testPositiveLimit() throws XPathException {
        new IntegerValue("9223372036854775807", Type.LONG);
    }

    @Test
    void testNegativeLimit() throws XPathException {
        new IntegerValue("-9223372036854775808", Type.LONG);
    }

    @Test
    void testUnder() {
        assertThrows(XPathException.class, () -> {
            new IntegerValue("-9223372036854775809", Type.LONG);
        });
    }   
}
