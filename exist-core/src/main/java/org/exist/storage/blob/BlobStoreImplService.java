/*
 * Copyright (C) 2018 Adam Retter
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.blob;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.crypto.digest.DigestType;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Broker Pool Service for the de-duplicating
 * Blob Store, see {@link BlobStoreImpl}.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BlobStoreImplService implements BlobStoreService, BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(BlobStoreImplService.class);

    private static final String BLOB_STORE_PERSISTENT_FILE_NAME = "blob.dbx";
    private static final String BLOB_STORE_DIR_NAME = "blob";

    private Path persistentFile;
    private Path dataDir;
    private Path blobDir;
    private BlobStore blobStore;

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.dataDir = (Path)configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (dataDir == null) {
            throw new BrokerPoolServiceException("Could not determine " + BrokerPool.PROPERTY_DATA_DIR + " from the configuration");
        }

        this.persistentFile = dataDir.resolve(BLOB_STORE_PERSISTENT_FILE_NAME);
        this.blobDir = dataDir.resolve(BLOB_STORE_DIR_NAME);
    }

    @Override
    public void prepare(final BrokerPool pool) {
        this.blobStore = new BlobStoreImpl(pool, persistentFile, blobDir, DigestType.BLAKE_256);
    }

    @Override
    public void startSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        try {
            this.blobStore.open();
            LOG.info("Opened de-duplicating Blob Store v" + BlobStoreImpl.BLOB_STORE_VERSION + ". metadata=" + dataDir.relativize(persistentFile) + ", store=" + dataDir.relativize(blobDir) + "/");
        } catch (final IOException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void shutdown() {
        if (this.blobStore != null) {
            try {
                this.blobStore.close();
            } catch (final IOException e) {
                LOG.error("Clean shutdown of Blob Store failed: " + e);
            }
        }
    }

    @Override
    @Nullable public BlobStore getBlobStore() {
        return blobStore;
    }
}
