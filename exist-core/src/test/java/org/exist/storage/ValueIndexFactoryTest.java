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
package org.exist.storage;

import java.math.BigDecimal;
import java.nio.ByteBuffer;

import org.exist.EXistException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.exist.storage.btree.Value;
import org.exist.xquery.value.DecimalValue;
import org.junit.Ignore;
import org.junit.Test;


public class ValueIndexFactoryTest {

    @Ignore
    @Test
    public void negativeNumbersComparison() {

        // -8.6...
        final ByteBuffer data1 = encode(-8.612328);

        // 1.0
        final ByteBuffer data2 = encode(1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) <= -1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", -1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Test
    public void numbersComparison() {

        // -8.6...
        final ByteBuffer data1 = encode(8.612328);

        // 1.0
        final ByteBuffer data2 = encode(1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) >= 1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", 1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Ignore
    @Test
    public void negativeNumbersComparison2() {

        // -8.6...
        final ByteBuffer data1 = encode(8.612328);

        // 1.0
        final ByteBuffer data2 = encode(-1.0);

//        // print data
//        print(data1);
//        print(data2);

        // -8.6 < 1.0
        assertTrue(data1.compareTo(data2) >= 1);

        // -8.6 < 1.0
        assertEquals("v1 < v2", 1, new Value(data1.array()).compareTo(new Value(data2.array())));
    }

    @Test
    public void roundTripDecimal() throws EXistException {
        BigDecimal dec = new BigDecimal("123456789123456789123456789123456789.123456789123456789123456789");

        byte data[] = ValueIndexFactory.serialize(new DecimalValue(dec), 0);

        Indexable value = ValueIndexFactory.deserialize(data, 0, data.length);
        assertTrue(value instanceof DecimalValue);

        assertEquals(dec, ((DecimalValue)value).getValue());
    }
	
    private ByteBuffer encode(final double number) {
        final ByteBuffer buf = ByteBuffer.allocate(8);
        buf.putDouble(number);
        ((java.nio.Buffer) buf).flip();
        return buf;
    }
}
