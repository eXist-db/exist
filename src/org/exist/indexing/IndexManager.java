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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;

/**
 * Manages all custom indexes registered with the database instance.
 */
public class IndexManager {

    private final static Logger LOG = LogManager.getLogger(IndexManager.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "modules";
    public static final String CONFIGURATION_MODULE_ELEMENT_NAME = "module";
    public static final String INDEXER_MODULES_CLASS_ATTRIBUTE = "class";
    public static final String INDEXER_MODULES_ID_ATTRIBUTE = "id";
    
    public final static String PROPERTY_INDEXER_MODULES = "indexer.modules";

    private BrokerPool pool;

    private Map<String, Index> indexers = new HashMap<>();

    /**
     * Constructs a new IndexManager and registers the indexes specified in
     * the global configuration object, i.e. in the :
     * <pre>
     * &lt;modules&gt;
     *   &lt;module id="foo" class="bar" foo1="bar1" ... /&gt;
     * &lt;/modules&gt;
     * </pre>
     * section of the configuration file.
     *
     * @param pool the BrokerPool representing the current database instance
     * @param config the configuration object
     * @throws DatabaseConfigurationException
     */
    public IndexManager(BrokerPool pool, Configuration config) throws DatabaseConfigurationException {
        this.pool = pool;
        final Configuration.IndexModuleConfig modConfigs[] = (Configuration.IndexModuleConfig[])
                config.getProperty(PROPERTY_INDEXER_MODULES);
        final Path dataDir = (Path) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (modConfigs != null) {
            for (Configuration.IndexModuleConfig modConfig : modConfigs) {
                final String className = modConfig.getClassName();
                initIndex(pool, modConfig.getId(), modConfig.getConfig(), dataDir, className);
            }
        }
        // check if a structural index was configured. If not, create one based on default settings.
        AbstractIndex structural = (AbstractIndex) indexers.get(StructuralIndex.STRUCTURAL_INDEX_ID);
        if (structural == null) {
            structural = initIndex(pool, StructuralIndex.STRUCTURAL_INDEX_ID, null, dataDir, StructuralIndex.DEFAULT_CLASS);
            structural.setName(StructuralIndex.STRUCTURAL_INDEX_ID);
        }
    }

    private AbstractIndex initIndex(BrokerPool pool, String id, Element config, Path dataDir, String className) throws DatabaseConfigurationException {
        try {
            final Class<?> clazz = Class.forName(className);
            if (!AbstractIndex.class.isAssignableFrom(clazz)) {
                throw new DatabaseConfigurationException("Class " + className + " does not implement " +
                        AbstractIndex.class.getName());
            }
            final AbstractIndex index = (AbstractIndex)clazz.newInstance();
            index.configure(pool, dataDir, config);
            index.open();
            indexers.put(id, index);
            if (LOG.isInfoEnabled())
                {LOG.info("Registered index " + className + " as " + id);}
            return index;
        } catch (final ClassNotFoundException e) {
            LOG.warn("Class " + className + " not found. Cannot configure index.");
        } catch (final IllegalAccessException | InstantiationException e) {
            LOG.warn("Exception while configuring index " + className + ": " + e.getMessage(), e);
        }
        return null;
    }

    public Index registerIndex(Index index) throws DatabaseConfigurationException {
        index.open();
        indexers.put(index.getIndexId(), index);
        if (LOG.isInfoEnabled()) {
            LOG.info("Registered index " + index.getClass() + " as " + index.getIndexId());
        }
        return index;
    }

    public void unregisterIndex(Index index) throws DBException {
        indexers.remove(index.getIndexId(), index);
        if (LOG.isInfoEnabled()) {
            LOG.info("Unregistered index " + index.getClass() + " as " + index.getIndexId());
        }
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
    public synchronized Index getIndexById(String indexId) {
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            final Index indexer = i.next();
            if (indexId.equals(indexer.getIndexId())) {
                return indexer;
            }
        }
        return null;
    }

    /** 
     * Returns the index registered with the provided human-readable name.
     * @param indexName the name
     * @return the index
     */    
    public synchronized Index getIndexByName(String indexName) {
        return indexers.get(indexName);
    }

    /**
     * Returns a set of IndexWorkers, one for each registered index. The
     * returned IndexWorkers are used by the DBBroker instances to perform the
     * actual indexing work.
     *
     * @return set of IndexWorkers
     */
    protected synchronized List<IndexWorker> getWorkers(DBBroker broker) {
        final List<IndexWorker> workerList = new ArrayList<>(indexers.size());
        for (final Index index : indexers.values()) {
            final IndexWorker worker = index.getWorker(broker);
            if (worker != null) {
                workerList.add(worker);
            }
        }
        return workerList;
    }

    /**
     * Shutdowns all registered indexes by calling {@link org.exist.indexing.Index#close()}
     * on them.
     *
     * @throws DBException
     */
    public void shutdown() throws DBException {
        Index index;
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            index = i.next();
            index.close();
        }
    }

    /**
     * Call indexes to flush all data to disk.
     *
     * @throws DBException
     */
    public void sync() throws DBException {
        Index index;
        for (final Iterator<Index> i = iterator(); i.hasNext(); ) {
            index = i.next();
            index.sync();
        }
    }

    /** 
     * Physically destroy the registered indexes by calling {@link org.exist.indexing.Index#remove()}
     * on them.
     * 
     * @throws DBException
     */
    public void removeIndexes() throws DBException {
        Index index;
        for (final Iterator<Index> i = iterator(); i.hasNext();) {
            index = i.next();
            index.remove();
        }
    }

    /** Reopens the registered index in case they have been closed by a previous operation 
     * such as {@link org.exist.indexing.Index#close()} by calling {@link org.exist.indexing.Index#open()}
     * on them.
     * 
     * @throws DatabaseConfigurationException
     */
    public void reopenIndexes() throws DatabaseConfigurationException {
        Index index;
        for (final Iterator<Index> i = iterator(); i.hasNext();) {
            index = i.next();
            index.open();
        }
    }

    public void backupToArchive(RawDataBackup backup) throws IOException {
        Index index;
        for (final Iterator<Index> i = iterator(); i.hasNext();) {
            index = i.next();
            if (index instanceof RawBackupSupport)
                {((RawBackupSupport)index).backupToArchive(backup);}
        }
    }
}
