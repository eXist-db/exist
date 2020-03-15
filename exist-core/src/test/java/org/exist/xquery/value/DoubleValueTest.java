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
import org.junit.Test;

import static com.ibm.icu.impl.Assert.fail;
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

    @Test(expected=XPathException.class)
    public void toJavaObject_int_lowerBound() throws XPathException {
        final double value = -2147483649d;  // NOTE: this is out of bounds for an XDM xs:int, so should generate an error
        final DoubleValue doubleValue = new DoubleValue(value);

        doubleValue.toJavaObject(int.class);

        fail("xs:double value is out of bounds for xs:int");
    }

    @Test(expected=XPathException.class)
    public void toJavaObject_int_upperBound() throws XPathException {
        final double value = 2147483649d;  // NOTE: this is out of bounds for an XDM xs:int, so should generate an error
        final DoubleValue doubleValue = new DoubleValue(value);

        doubleValue.toJavaObject(int.class);

        fail("xs:double value is out of bounds for xs:int");
    }

    @Test
    public void toJavaObject_int() throws XPathException {
        final double value = -2147483648d;
        final DoubleValue doubleValue = new DoubleValue(value);

        final int i = doubleValue.toJavaObject(int.class);

        assertEquals(-2147483648, i);

    }

    @Test
    public void toJavaObject_long() throws XPathException {
        final double value = -2147483649d;
        final DoubleValue doubleValue = new DoubleValue(value);

        final long l = doubleValue.toJavaObject(long.class);

        assertEquals(-2147483649l, l);

    }
}
