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
public class LongValueTest {

    @Test
    public void serializeDeserializeMin() throws XPathException {
        serializeDeserialize(Long.MIN_VALUE);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        serializeDeserialize(Long.MAX_VALUE);
    }

    private void serializeDeserialize(final long l) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(IntegerValue.MAX_SERIALIZED_SIZE);
        final IntegerValue longValue1 = new IntegerValue(l, Type.LONG);
        assertEquals(Type.LONG, longValue1.getType());
        assertEquals(l, longValue1.getValue());
        assertEquals(l, longValue1.getLong());
        longValue1.serialize(buffer);

        assertTrue(buffer.position() >= IntegerValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= IntegerValue.MAX_SERIALIZED_SIZE);
        assertEquals(IntegerValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final IntegerValue longValue2 = IntegerValue.deserialize(buffer, Type.LONG);
        assertEquals(Type.LONG, longValue2.getType());

        assertEquals(longValue1, longValue2);
        assertEquals(longValue1.getValue(), longValue2.getValue());
        assertEquals(longValue1.getLong(), longValue2.getLong());
    }
}
