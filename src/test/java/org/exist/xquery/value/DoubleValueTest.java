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

package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class DoubleValueTest {

    @Test
    public void convertToInteger() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        final IntegerValue integerValue = (IntegerValue)doubleValue.convertTo(Type.INTEGER);

        assertEquals(2147483647, integerValue.getInt());
        assertEquals(-2147483649l, integerValue.getLong());
    }

    @Test
    public void getInt() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        assertEquals(2147483647, doubleValue.getInt());
    }

    @Test
    public void getLong() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        assertEquals(-2147483649l, doubleValue.getLong());
    }

    @Test
    public void toJavaObject_int() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        final int i = doubleValue.toJavaObject(int.class);

        assertEquals(2147483647, i);     //TODO(AR) is this correct?

    }

    @Test
    public void toJavaObject_long() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        final long l = doubleValue.toJavaObject(long.class);

        assertEquals(-2147483649l, l);

    }
}
