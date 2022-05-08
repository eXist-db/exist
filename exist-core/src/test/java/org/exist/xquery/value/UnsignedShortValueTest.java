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

import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class UnsignedShortValueTest {

    private final String UNSIGNED_SHORT_MIN_VALUE = "0";
    private final String UNSIGNED_SHORT_MAX_VALUE = "65535";

    @Test
    public void serializeDeserializeMin() throws XPathException {
        serializeDeserialize(UNSIGNED_SHORT_MIN_VALUE);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        serializeDeserialize(UNSIGNED_SHORT_MAX_VALUE);
    }

    private void serializeDeserialize(final String unsignedShort) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue unsignedShortValue1 = new IntegerValue(unsignedShort, Type.UNSIGNED_SHORT);
        assertEquals(Type.UNSIGNED_SHORT, unsignedShortValue1.getType());
        assertEquals(unsignedShort, unsignedShortValue1.toString());
        unsignedShortValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue unsignedShortValue2 = IntegerValue.deserialize(buffer, Type.UNSIGNED_SHORT);
        assertEquals(Type.UNSIGNED_SHORT, unsignedShortValue2.getType());

        assertEquals(unsignedShortValue1, unsignedShortValue2);
        assertEquals(unsignedShortValue1.getValue(), unsignedShortValue2.getValue());
        assertEquals(unsignedShortValue1.getLong(), unsignedShortValue2.getLong());
        assertEquals(unsignedShortValue1.getInt(), unsignedShortValue2.getInt());
    }
}
