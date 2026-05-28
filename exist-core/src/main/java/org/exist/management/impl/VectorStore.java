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
package org.exist.management.impl;

import org.exist.storage.BrokerPool;
import org.exist.storage.vector.VectorStoreImpl;

import javax.annotation.Nullable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JMX MBean exposing operational statistics for {@code vector.dbx}.
 */
public class VectorStore implements VectorStoreMXBean {

    private static final String STORAGE_BACKEND = "vector.dbx";

    private final String instanceId;
    @Nullable
    private final VectorStoreImpl store;
    private final Path vectorFile;

    public VectorStore(final BrokerPool pool, @Nullable final VectorStoreImpl store, final Path dataDir) {
        this.instanceId = pool.getId();
        this.store = store;
        this.vectorFile = dataDir.resolve(VectorStoreImpl.FILE_NAME);
    }

    public static String getAllInstancesQuery() {
        return getName("*");
    }

    private static String getName(final String instanceId) {
        return "org.exist.management." + instanceId + ":type=VectorStore";
    }

    @Override
    public ObjectName getName() throws MalformedObjectNameException {
        return new ObjectName(getName(instanceId));
    }

    @Override
    public String getInstanceId() {
        return instanceId;
    }

    @Override
    public boolean isAvailable() {
        return store != null;
    }

    @Override
    public String getStorageBackend() {
        return STORAGE_BACKEND;
    }

    @Override
    public boolean isEntryCountKnown() {
        return store != null;
    }

    @Override
    public String getFileName() {
        return VectorStoreImpl.FILE_NAME;
    }

    @Override
    public long getFileSize() {
        if (!Files.isRegularFile(vectorFile)) {
            return NO_FILE_SIZE;
        }
        try {
            return Files.size(vectorFile);
        } catch (final IOException e) {
            return NO_FILE_SIZE;
        }
    }

    @Override
    public long getEntryCount() {
        if (store == null) {
            return ENTRY_COUNT_UNKNOWN;
        }
        try {
            return store.getEntryCount();
        } catch (final IOException e) {
            return ENTRY_COUNT_UNKNOWN;
        }
    }

    @Override
    public short getFormatVersion() {
        return VectorStoreImpl.FILE_FORMAT_VERSION_ID;
    }

    @Override
    public void resetEntryCountCache() {
        if (store != null) {
            store.resetEntryCountCache();
        }
    }
}
