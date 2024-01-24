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

package org.exist.util.io;

import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.exist.util.Configuration;

/**
 * Generic pool for {@link ContentFile} instances used to represent RPC server data up to a defined size in memory first
 * before storing the data in the file system using the {@link TemporaryFileManager}.
 *
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public final class ContentFilePool extends GenericObjectPool<ContentFile> {
    /**
     * Variable defining the {@link ContentFile} maximum in memory stored size in bytes.
     */
    public static final String PROPERTY_IN_MEMORY_SIZE = "rpc-server.content-file.in-memory-size";
    /**
     * Variable defining the maximum mool size holding {@link ContentFile values.}
     */
    public static final String PROPERTY_POOL_SIZE = "rpc-server.content-file-pool.size";
    /**
     * Variable defining the maximum mool size holding {@link ContentFile values.}
     */
    public static final String PROPERTY_POOL_MAX_IDLE = "rpc-server.content-file-pool.max-idle";

    /**
     * Creates a new pool using the givem temporary file manager, configuration and maximum idle time.
     *
     * @param tempFileManager the temporary file manager used when need to swap out data bigger than max in memory size
     * @param config the configuration used to configure the main pool properties
     */
    public ContentFilePool(final TemporaryFileManager tempFileManager, final Configuration config) {
        super(new ContentFilePoolObjectFactory(tempFileManager, toInMemorySize(config)), toPoolConfig(config));
    }

    private static int toInMemorySize(final Configuration config) {
        return config.getInteger(PROPERTY_IN_MEMORY_SIZE, VirtualTempPath.DEFAULT_IN_MEMORY_SIZE);
    }

    private static GenericObjectPoolConfig<ContentFile> toPoolConfig(final Configuration config) {
        final GenericObjectPoolConfig<ContentFile> poolConfig = new GenericObjectPoolConfig<>();
        poolConfig.setBlockWhenExhausted(false);
        poolConfig.setLifo(true);
        poolConfig.setMaxIdle(config.getInteger(PROPERTY_POOL_MAX_IDLE));
        poolConfig.setMaxTotal(config.getInteger(PROPERTY_POOL_SIZE));
        poolConfig.setJmxNameBase("org.exist.management.exist:type=ContentFilePool");
        return poolConfig;
    }

    @Override
    public ContentFile borrowObject() {
        try {
            return super.borrowObject();
        } catch (final Exception e) {
            throw new IllegalStateException("Error while borrowing ContentFile", e);
        }
    }

    @Override
    public void returnObject(final ContentFile obj) {
        if (obj == null) {
            return;
        }
        try {
            obj.close();
            super.returnObject(obj);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while returning ContentFile", e);
        }
    }
}
