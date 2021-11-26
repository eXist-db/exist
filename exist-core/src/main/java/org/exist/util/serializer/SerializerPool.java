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

import net.jcip.annotations.ThreadSafe;
import org.apache.commons.pool2.KeyedPooledObjectFactory;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;

@ThreadSafe
public class SerializerPool extends GenericKeyedObjectPool<Class<?>, Object> {

    private static final SerializerPool instance = new SerializerPool(new SerializerObjectFactory(), 10, 1);

    public static SerializerPool getInstance() {
        return instance;
    }

    /**
     * Constructs a Serializer Pool.
     *
     * @param factory the serializer object factory
     * @param maxIdle the maximum number of idle instances in the pool
     * @param initSize initial size of the pool (this specifies the size of the container, it does not cause the pool to be pre-populated.) (unused)
     */
    public SerializerPool(final KeyedPooledObjectFactory<Class<?>, Object> factory, final int maxIdle, @Deprecated final int initSize) {
        super(factory, toConfig(maxIdle));
    }

    private static GenericKeyedObjectPoolConfig toConfig(final int maxIdle) {
        final GenericKeyedObjectPoolConfig<Object> config = new GenericKeyedObjectPoolConfig<>();
        config.setBlockWhenExhausted(false);
        config.setLifo(true);
        config.setMaxIdlePerKey(maxIdle);
        config.setMaxTotalPerKey(-1);       // TODO(AR) is this the best way to allow us to temporarily exceed the size of the pool?
        config.setJmxNameBase("org.exist.management.exist:type=SerializerPool,name=");
        return config;
    }

    @Override
    public Object borrowObject(final Class<?> key) {
        try {
            return super.borrowObject(key);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while borrowing " + key.getSimpleName() + ": " + e.getMessage());
        }
    }

    public void returnObject(final Object obj) {
        if (obj == null) {
            return;
        }

        try {
            super.returnObject(obj.getClass(), obj);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while returning "+ obj.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }
}
