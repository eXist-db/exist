/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing;

import org.apache.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.*;

/**
 * Manages all custom indexes registered with the database instance.
 */
public class IndexManager {

    private final static Logger LOG = Logger.getLogger(IndexManager.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "modules";
    public static final String CONFIGURATION_MODULE_ELEMENT_NAME = "module";
    public static final String INDEXER_MODULES_CLASS_ATTRIBUTE = "class";
    public static final String INDEXER_MODULES_ID_ATTRIBUTE = "id";
    
    public final static String PROPERTY_INDEXER_MODULES = "indexer.modules";

    private BrokerPool pool;

    private Map<String, Index> indexers = new HashMap<String, Index>();

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
        final Configuration.IndexModuleConfig modConf[] = (Configuration.IndexModuleConfig[])
                config.getProperty(PROPERTY_INDEXER_MODULES);
        final String dataDir = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (modConf != null) {
            for (int i = 0; i < modConf.length; i++) {
                final String className = modConf[i].getClassName();
                initIndex(pool, modConf[i].getId(), modConf[i].getConfig(), dataDir, className);
            }
        }
        // check if a structural index was configured. If not, create one based on default settings.
        AbstractIndex structural = (AbstractIndex) indexers.get(StructuralIndex.STRUCTURAL_INDEX_ID);
        if (structural == null) {
            structural = initIndex(pool, StructuralIndex.STRUCTURAL_INDEX_ID, null, dataDir, StructuralIndex.DEFAULT_CLASS);
            structural.setName(StructuralIndex.STRUCTURAL_INDEX_ID);
        }
    }

    private AbstractIndex initIndex(BrokerPool pool, String id, Element config, String dataDir, String className) throws DatabaseConfigurationException {
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
        } catch (final IllegalAccessException e) {
            LOG.warn("Exception while configuring index " + className + ": " + e.getMessage(), e);
        } catch (final InstantiationException e) {
            LOG.warn("Exception while configuring index " + className + ": " + e.getMessage(), e);
        }
        return null;
    }

    public Index registerIndex(Index index) throws DatabaseConfigurationException {
        index.open();
        indexers.put(index.getIndexId(), index);
        if (LOG.isInfoEnabled())
            {LOG.info("Registered index " + index.getClass() + " as " + index.getIndexId());}
        return index;
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
            if (indexId.equals(indexer.getIndexId()))
                {return indexer;}
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
        final List<IndexWorker> workerList = new ArrayList<IndexWorker>(indexers.size());
        for (final Iterator<Index> i = indexers.values().iterator(); i.hasNext(); ) {
            final Index index = i.next();
            final IndexWorker worker = index.getWorker(broker);
            if (worker != null)
                {workerList.add(worker);}
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