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
public class ShortValueTest {

    @Test
    public void serializeDeserializeMin() throws XPathException {
        serializeDeserialize(Short.MIN_VALUE);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        serializeDeserialize(Short.MAX_VALUE);
    }

    private void serializeDeserialize(final short s) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue shortValue1 = new IntegerValue(s, Type.SHORT);
        assertEquals(Type.SHORT, shortValue1.getType());
        assertEquals(s, shortValue1.getValue());
        assertEquals(s, shortValue1.getLong());
        assertEquals(s, shortValue1.getInt());
        shortValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue shortValue2 = IntegerValue.deserialize(buffer, Type.SHORT);
        assertEquals(Type.SHORT, shortValue2.getType());

        assertEquals(shortValue1, shortValue2);
        assertEquals(shortValue1.getValue(), shortValue2.getValue());
        assertEquals(shortValue1.getLong(), shortValue2.getLong());
        assertEquals(shortValue1.getInt(), shortValue2.getInt());
    }
}
