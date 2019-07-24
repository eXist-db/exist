/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.storage.BrokerPool;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Manages all custom indexes registered with the database instance.
 */
@ThreadSafe
public class IndexManager implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(IndexManager.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "modules";
    public static final String CONFIGURATION_MODULE_ELEMENT_NAME = "module";
    public static final String INDEXER_MODULES_CLASS_ATTRIBUTE = "class";
    public static final String INDEXER_MODULES_ID_ATTRIBUTE = "id";

    public final static String PROPERTY_INDEXER_MODULES = "indexer.modules";

    private final BrokerPool pool;

    private final Map<String, Index> indexers = new ConcurrentHashMap<>();

    private Configuration.IndexModuleConfig modConfigs[];
    private Path dataDir;

    private AtomicLong configurationTimestamp = new AtomicLong(System.currentTimeMillis());

    /**
     * @param pool   the BrokerPool representing the current database instance
     */
    public IndexManager(final BrokerPool pool) {
        this.pool = pool;
    }

    private void configurationChanged() {
        while (true) {
            long prev = configurationTimestamp.get();
            long now = System.currentTimeMillis();

            if(now > prev && configurationTimestamp.compareAndSet(prev, now)) {
                return;
            }
        }
    }

    /**
     * Get the timestamp of when the index manager's configuration was last
     * updated.
     *
     * @return the timestamp of when the index managers configuration was
     *      last updated.
     */
    public long getConfigurationTimestamp() {
        return configurationTimestamp.get();
    }

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        this.modConfigs = (Configuration.IndexModuleConfig[])
                configuration.getProperty(PROPERTY_INDEXER_MODULES);
        this.dataDir = (Path) configuration.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        configurationChanged();
    }

    /**
     * Registers the indexes specified in
     * the global configuration object, i.e. in the :
     * <pre>
     * &lt;modules&gt;
     *   &lt;module id="foo" class="bar" foo1="bar1" ... /&gt;
     * &lt;/modules&gt;
     * </pre>
     * section of the configuration file.
     */
    @Override
    public void prepare(final BrokerPool brokerPool) throws BrokerPoolServiceException {
        try {
            if (modConfigs != null) {
                for (final Configuration.IndexModuleConfig modConfig : modConfigs) {
                    final String className = modConfig.getClassName();
                    initIndex(pool, modConfig.getId(), modConfig.getConfig(), dataDir, className);
                }
            }
            // check if a structural index was configured. If not, create one based on default settings.
            AbstractIndex structural = (AbstractIndex) indexers.get(StructuralIndex.STRUCTURAL_INDEX_ID);
            if (structural == null) {
                structural = initIndex(pool, StructuralIndex.STRUCTURAL_INDEX_ID, null, dataDir, StructuralIndex.DEFAULT_CLASS);
                if (structural != null) {
                    structural.setName(StructuralIndex.STRUCTURAL_INDEX_ID);
                }
            }
        } catch(final DatabaseConfigurationException e) {
            throw new BrokerPoolServiceException(e);
        } finally {
            configurationChanged();
        }
    }

    private AbstractIndex initIndex(final BrokerPool pool, final String id, final Element config, final Path dataDir, final String className) throws DatabaseConfigurationException {
        try {
            final Class<?> clazz = Class.forName(className);
            if (!AbstractIndex.class.isAssignableFrom(clazz)) {
                throw new DatabaseConfigurationException("Class " + className + " does not implement " +
                        AbstractIndex.class.getName());
            }
            final AbstractIndex index = (AbstractIndex) clazz.newInstance();
            index.configure(pool, dataDir, config);
            index.open();
            indexers.put(id, index);
            if (LOG.isInfoEnabled()) {
                LOG.info("Registered index " + className + " as " + id);
            }
            return index;
        } catch (final ClassNotFoundException e) {
            LOG.warn("Class " + className + " not found. Cannot configure index.");
        } catch (final IllegalAccessException | InstantiationException e) {
            LOG.warn("Exception while configuring index " + className + ": " + e.getMessage(), e);
        }
        return null;
    }

    public Index registerIndex(final Index index) throws DatabaseConfigurationException {
        index.open();
        indexers.put(index.getIndexId(), index);
        if (LOG.isInfoEnabled()) {
            LOG.info("Registered index " + index.getClass() + " as " + index.getIndexId());
        }

        configurationChanged();

        return index;
    }

    public void unregisterIndex(final Index index) throws DBException {
        indexers.remove(index.getIndexId(), index);
        index.close();
        if (LOG.isInfoEnabled()) {
            LOG.info("Unregistered index " + index.getClass() + " as " + index.getIndexId());
        }

        configurationChanged();
    }

    /**
     * Returns the {@link org.exist.storage.BrokerPool} on with this IndexManager operates.
     *
     * @return the broker pool
     */
    public BrokerPool getBrokerPool() {
        return pool;
    }

    /**
     * Returns an iterator over the registered indexes.
     *
     * @return the iterator
     */
    protected Iterator<Index> iterator() {
        return indexers.values().iterator();
    }

    /**
     * Returns the index registered with the provided ID.
     *
     * @param indexId the ID
     * @return the index
     */
    public synchronized Index getIndexById(final String indexId) {
        return indexers.values().stream()
                .filter(indexer -> indexer.getIndexId().equals(indexId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Returns the index registered with the provided human-readable name.
     *
     * @param indexName the name
     * @return the index
     */
    public synchronized Index getIndexByName(final String indexName) {
        return indexers.get(indexName);
    }

    /**
     * Returns a set of IndexWorkers, one for each registered index. The
     * returned IndexWorkers are used by the DBBroker instances to perform the
     * actual indexing work.
     *
     * @return set of IndexWorkers
     */
    synchronized List<IndexWorker> getWorkers(final DBBroker broker) {
        return indexers.values().stream()
                .map(index -> index.getWorker(broker))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Shutdowns all registered indexes by calling {@link org.exist.indexing.Index#close()}
     * on them.
     *
     * @param systemBroker The broker that will perform the operation
     * @throws BrokerPoolServiceException in case of an error in the BrookerPoolService
     */
    @Override
    public void stop(final DBBroker systemBroker) throws BrokerPoolServiceException {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index index = i.next();
            try {
                index.close();
            } catch(final DBException e) {
                throw new BrokerPoolServiceException(e);
            }
        }
    }

    /**
     * Call indexes to flush all data to disk.
     *
     * @throws DBException in case of an eXist-db error
     */
    public void sync() throws DBException {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index index = i.next();
            index.sync();
        }
    }

    /**
     * Physically destroy the registered indexes by calling {@link org.exist.indexing.Index#remove()}
     * on them.
     *
     * @throws DBException in case of an eXist-db error
     */
    public void removeIndexes() throws DBException {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index index = i.next();
            index.remove();
        }
    }

    /**
     * Reopens the registered index in case they have been closed by a previous operation
     * such as {@link org.exist.indexing.Index#close()} by calling {@link org.exist.indexing.Index#open()}
     * on them.
     *
     * @throws DatabaseConfigurationException in cse of an database configuration error
     */
    public void reopenIndexes() throws DatabaseConfigurationException {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index index = i.next();
            index.open();
        }
    }

    public void backupToArchive(final RawDataBackup backup) throws IOException {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index index = i.next();
            if (index instanceof RawBackupSupport) {
                ((RawBackupSupport) index).backupToArchive(backup);
            }
        }
    }
}
