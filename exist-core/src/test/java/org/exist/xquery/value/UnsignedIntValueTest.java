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
public class UnsignedIntValueTest {

    private final String UNSIGNED_INT_MIN_VALUE = "0";
    private final String UNSIGNED_INT_MAX_VALUE = "4294967295";

    @Test
    public void serializeDeserializeMin() throws XPathException {
        serializeDeserialize(UNSIGNED_INT_MIN_VALUE);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        serializeDeserialize(UNSIGNED_INT_MAX_VALUE);
    }

    private void serializeDeserialize(final String unsignedInt) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue unsignedIntValue1 = new IntegerValue(unsignedInt, Type.UNSIGNED_INT);
        assertEquals(Type.UNSIGNED_INT, unsignedIntValue1.getType());
        assertEquals(unsignedInt, unsignedIntValue1.toString());
        unsignedIntValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue unsignedIntValue2 = IntegerValue.deserialize(buffer, Type.UNSIGNED_INT);
        assertEquals(Type.UNSIGNED_INT, unsignedIntValue2.getType());

        assertEquals(unsignedIntValue1, unsignedIntValue2);
        assertEquals(unsignedIntValue1.getValue(), unsignedIntValue2.getValue());
        assertEquals(unsignedIntValue1.getLong(), unsignedIntValue2.getLong());
        assertEquals(unsignedIntValue1.getInt(), unsignedIntValue2.getInt());
    }
}
