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
public class IntValueTest {

    @Test
    public void serializeDeserializeMin() throws XPathException {
        serializeDeserialize(Integer.MIN_VALUE);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        serializeDeserialize(Integer.MAX_VALUE);
    }

    private void serializeDeserialize(final int i) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue intValue1 = new IntegerValue(i, Type.INT);
        assertEquals(Type.INT, intValue1.getType());
        assertEquals(i, intValue1.getValue());
        assertEquals(i, intValue1.getLong());
        assertEquals(i, intValue1.getInt());
        intValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue intValue2 = IntegerValue.deserialize(buffer, Type.INT);
        assertEquals(Type.INT, intValue2.getType());

        assertEquals(intValue1, intValue2);
        assertEquals(intValue1.getValue(), intValue2.getValue());
        assertEquals(intValue1.getLong(), intValue2.getLong());
        assertEquals(intValue1.getInt(), intValue2.getInt());
    }
}
