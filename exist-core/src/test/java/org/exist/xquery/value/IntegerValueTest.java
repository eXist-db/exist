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
import org.junit.Ignore;
import org.junit.Test;

import java.math.BigInteger;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class IntegerValueTest {

    private final String LONG_LONG_MIN_VALUE = "-170141183460469231731687303715884105728";
    private final String LONG_LONG_MAX_VALUE = "170141183460469231731687303715884105727";

    @Test
    public void serializeDeserializeMin32Bit() throws XPathException {
        serializeDeserialize(BigInteger.valueOf(Integer.MIN_VALUE));
    }

    @Test
    public void serializeDeserializeMax32Bit() throws XPathException {
        serializeDeserialize(BigInteger.valueOf(Integer.MAX_VALUE));
    }

    @Test
    public void serializeDeserializeMin64Bit() throws XPathException {
        serializeDeserialize(BigInteger.valueOf(Long.MIN_VALUE));
    }

    @Test
    public void serializeDeserializeMax64Bit() throws XPathException {
        serializeDeserialize(BigInteger.valueOf(Long.MAX_VALUE));
    }

    @Ignore("See https://github.com/eXist-db/exist/issues/4382")
    @Test
    public void serializeDeserializeMin128Bit() throws XPathException {
        serializeDeserialize(new BigInteger(LONG_LONG_MIN_VALUE));
    }

    @Ignore("See https://github.com/eXist-db/exist/issues/4382")
    @Test
    public void serializeDeserializeMax128Bit() throws XPathException {
        serializeDeserialize(new BigInteger(LONG_LONG_MAX_VALUE));
    }

    private void serializeDeserialize(final BigInteger bi) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue integerValue1 = new IntegerValue(bi, Type.INTEGER);
        assertEquals(Type.INTEGER, integerValue1.getType());
        assertEquals(bi, integerValue1.toJavaObject(BigInteger.class));
        integerValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue integerValue2 = IntegerValue.deserialize(buffer, Type.INTEGER);
        assertEquals(Type.INTEGER, integerValue2.getType());

        assertEquals(integerValue1, integerValue2);
        assertEquals(bi, integerValue2.toJavaObject(BigInteger.class));
    }
}
