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
package org.exist.storage.vector;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.management.AgentFactory;
import org.exist.management.impl.VectorStore;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Broker Pool Service for the Vector Store.
 */
public class VectorStoreServiceImpl implements VectorStoreService, BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(VectorStoreServiceImpl.class);

    private Path dataDir;
    private VectorStoreImpl vectorStore;

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.dataDir = (Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (dataDir == null) {
            throw new BrokerPoolServiceException("Could not determine " + BrokerPool.PROPERTY_DATA_DIR + " from the configuration");
        }
    }

    @Override
    public void prepare(final BrokerPool pool) throws BrokerPoolServiceException {
        try {
            this.vectorStore = new VectorStoreImpl(pool, dataDir);
        } catch (final DBException e) {
            throw new BrokerPoolServiceException(e);
        }
    }

    @Override
    public void startSystem(final DBBroker systemBroker, final Txn transaction) throws BrokerPoolServiceException {
        LOG.info("Opened Vector Store. file={}", dataDir.relativize(dataDir.resolve(VectorStoreImpl.FILE_NAME)));
        if (vectorStore != null) {
            try {
                AgentFactory.getInstance().addMBean(new VectorStore(systemBroker.getBrokerPool(), vectorStore, dataDir));
            } catch (final DatabaseConfigurationException e) {
                LOG.warn("Failed to register VectorStore JMX MBean: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public void shutdown() {
        if (this.vectorStore != null) {
            try {
                this.vectorStore.close();
            } catch (final IOException e) {
                LOG.error("Clean shutdown of Vector Store failed: {}", e.getMessage(), e);
            }
            this.vectorStore = null;
        }
    }

    @Override
    @Nullable
    public org.exist.storage.vector.VectorStore getVectorStore() {
        return vectorStore;
    }
}
