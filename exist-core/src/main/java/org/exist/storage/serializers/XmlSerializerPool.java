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
package org.exist.storage.serializers;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;

import javax.annotation.Nullable;

public class XmlSerializerPool extends GenericObjectPool<Serializer> {
    public XmlSerializerPool(final DBBroker broker, final Configuration config, final int maxIdle) {
        super(new XmlSerializerPoolObjectFactory(broker, config), toConfig(broker.getId(), maxIdle));
    }

    private static GenericObjectPoolConfig<Serializer> toConfig(@Nullable final String brokerId, final int maxIdle) {
        final GenericObjectPoolConfig<Serializer> config = new GenericObjectPoolConfig<>();
        config.setBlockWhenExhausted(false);
        config.setLifo(true);
        config.setMaxIdle(maxIdle);
        config.setMaxTotal(-1);            // TODO(AR) is this the best way to allow us to temporarily exceed the size of the pool?
        final String poolName = brokerId == null ? "" : "pool." + brokerId;
        config.setJmxNameBase("org.exist.management.exist:type=XmlSerializerPool,name=" + poolName);
        return config;
    }

    @Override
    public Serializer borrowObject() {
        try {
            return super.borrowObject();
        } catch (final Exception e) {
            throw new IllegalStateException("Error while borrowing serializer: " + e.getMessage());
        }
    }

    @Override
    public void returnObject(final Serializer obj) {
        if (obj == null) {
            return;
        }

        try {
            super.returnObject(obj);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while returning serializer: " + e.getMessage());
        }
    }
}
