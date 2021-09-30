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
package org.exist.util.serializer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SerializerPoolTest {

    @Test
    public void exceedMaxIdle() {
        final int maxIdle = 3;
        final int initialCapacity = 0;

        final SerializerObjectFactory serializerObjectFactory = new SerializerObjectFactory();
        final SerializerPool serializerPool = new SerializerPool(serializerObjectFactory, maxIdle, initialCapacity);

        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(0, serializerPool.getNumIdle());
        assertEquals(0, serializerPool.getNumActive());

        // borrow 1
        final SAXSerializer saxSerializer1 = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        assertNotNull(saxSerializer1);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(0, serializerPool.getNumIdle());
        assertEquals(1, serializerPool.getNumActive());

        // borrow 2
        final SAXSerializer saxSerializer2 = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        assertNotNull(saxSerializer2);
        assertNotSame(saxSerializer2, saxSerializer1);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(0, serializerPool.getNumIdle());
        assertEquals(2, serializerPool.getNumActive());

        // borrow 3
        final SAXSerializer saxSerializer3 = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        assertNotNull(saxSerializer3);
        assertNotSame(saxSerializer3, saxSerializer2);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(0, serializerPool.getNumIdle());
        assertEquals(3, serializerPool.getNumActive());

        // borrow 4 -- will exceed `maxIdle`
        final SAXSerializer saxSerializer4 = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
        assertNotSame(saxSerializer4, saxSerializer3);
        assertNotNull(saxSerializer4);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(0, serializerPool.getNumIdle());
        assertEquals(4, serializerPool.getNumActive());

        // now try and return the readers...

        // return 4
        serializerPool.returnObject(saxSerializer4);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(1, serializerPool.getNumIdle());
        assertEquals(3, serializerPool.getNumActive());

        // return 3
        serializerPool.returnObject(saxSerializer3);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(2, serializerPool.getNumIdle());
        assertEquals(2, serializerPool.getNumActive());

        // return 2
        serializerPool.returnObject(saxSerializer2);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(3, serializerPool.getNumIdle());
        assertEquals(1, serializerPool.getNumActive());

        // return 1 --  will exceed `maxIdle`
        serializerPool.returnObject(saxSerializer1);
        assertEquals(maxIdle, serializerPool.getMaxIdlePerKey());
        assertEquals(maxIdle, serializerPool.getNumIdle());  // NOTE: that getNumIdle() can never exceed maxIdle
        assertEquals(0, serializerPool.getNumActive());
    }
}
