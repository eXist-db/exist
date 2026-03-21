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

import org.exist.EXistException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.exist.storage.btree.Value;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.DoubleValue;
import org.junit.Test;


public class ValueIndexFactoryTest {

    @Test
    public void negativeNumbersComparison() throws EXistException {

        // -8.6...
        final byte[] data1 = encodeDouble(-8.612328);

        // 1.0
        final byte[] data2 = encodeDouble(1.0);

        // -8.6 < 1.0
        assertTrue("v1 < v2", new Value(data1).compareTo(new Value(data2)) < 0);
    }

    @Test
    public void numbersComparison() throws EXistException {

        // 8.6...
        final byte[] data1 = encodeDouble(8.612328);

        // 1.0
        final byte[] data2 = encodeDouble(1.0);

        // 8.6 > 1.0
        assertTrue("v1 > v2", new Value(data1).compareTo(new Value(data2)) > 0);
    }

    @Test
    public void negativeNumbersComparison2() throws EXistException {

        // 8.6...
        final byte[] data1 = encodeDouble(8.612328);

        // -1.0
        final byte[] data2 = encodeDouble(-1.0);

        // 8.6 > -1.0
        assertTrue("v1 > v2", new Value(data1).compareTo(new Value(data2)) > 0);
    }

    @Test
    public void roundTripDecimal() throws EXistException {
        BigDecimal dec = new BigDecimal("123456789123456789123456789123456789.123456789123456789123456789");

        byte data[] = ValueIndexFactory.serialize(new DecimalValue(dec), 0);

        Indexable value = ValueIndexFactory.deserialize(data, 0, data.length);
        assertTrue(value instanceof DecimalValue);

        assertEquals(dec, ((DecimalValue)value).getValue());
    }

    @Test
    public void roundTripDouble() throws EXistException {
        final double[] values = {-8.612328, -1.0, 0.0, 1.0, 8.612328};
        for (final double d : values) {
            final byte[] data = ValueIndexFactory.serialize(new DoubleValue(d), 0);
            final Indexable value = ValueIndexFactory.deserialize(data, 0, data.length);
            assertTrue(value instanceof DoubleValue);
            assertEquals(d, ((DoubleValue) value).getValue(), 0.0);
        }
    }

    @Test
    public void doubleOrderingComprehensive() throws EXistException {
        final double[] ordered = {-8.612328, -1.0, 0.0, 1.0, 8.612328};
        final byte[][] encoded = new byte[ordered.length][];
        for (int i = 0; i < ordered.length; i++) {
            encoded[i] = encodeDouble(ordered[i]);
        }
        for (int i = 0; i < ordered.length; i++) {
            for (int j = i + 1; j < ordered.length; j++) {
                assertTrue(ordered[i] + " < " + ordered[j],
                        new Value(encoded[i]).compareTo(new Value(encoded[j])) < 0);
            }
        }
    }

    private byte[] encodeDouble(final double number) throws EXistException {
        return ValueIndexFactory.serialize(new DoubleValue(number), 0);
    }
}
