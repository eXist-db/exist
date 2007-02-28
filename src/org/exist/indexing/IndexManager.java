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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.DBException;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.SingleInstanceConfiguration;

/**
 * Manages all custom indexes registered with the database instance.
 */
public class IndexManager {

    private final static Logger LOG = Logger.getLogger(IndexManager.class);

    private BrokerPool pool;

    private Map indexers = new HashMap();

    /**
     * Constructs a new IndexManager and registers the indexes specified in
     * the global configuration object.
     *
     * @param pool the BrokerPool representing the current database instance
     * @param config the configuration object
     * @throws DatabaseConfigurationException
     */
    public IndexManager(BrokerPool pool, Configuration config) throws DatabaseConfigurationException {
        this.pool = pool;
        Configuration.IndexModuleConfig modConf[] = (Configuration.IndexModuleConfig[])
                config.getProperty("indexer.modules");
        String dataDir = (String) config.getProperty("db-connection.data-dir");

        if (modConf != null) {
            for (int i = 0; i < modConf.length; i++) {
                String className = modConf[i].getClassName();
                try {
                    Class clazz = Class.forName(className);
                    if (!Index.class.isAssignableFrom(clazz)) {
                        throw new DatabaseConfigurationException("Class " + className + " does not implement " +
                                Index.class.getName());
                    }
                    Index index = (Index) clazz.newInstance();
                    index.open(pool, dataDir, modConf[i].getConfig());
                    indexers.put(modConf[i].getId(), index);
                    if (LOG.isInfoEnabled())
                        LOG.info("Registered index " + className + " as " + modConf[i].getId());
                } catch (ClassNotFoundException e) {
                    throw new DatabaseConfigurationException("Class " + className + " not found. Cannot configure index.");
                } catch (IllegalAccessException e) {
                    throw new DatabaseConfigurationException("Exception while configuring index " + className + ": " +
                            e.getMessage());
                } catch (InstantiationException e) {
                    throw new DatabaseConfigurationException("Exception while configuring index " + className + ": " +
                            e.getMessage());
                }
            }
        }
    }

    public Iterator iterator() {
        return indexers.values().iterator();
    }

    /**
     * Returns a set of IndexWorkers, one for each registered index. The
     * returned IndexWorkers are used by the DBBroker instances to do the
     * actual work.
     *
     * @return
     */
    public synchronized IndexWorker[] getWorkers() {
        final IndexWorker workers[] = new IndexWorker[indexers.size()];
        Index index;
        int j = 0;
        for (Iterator i = indexers.values().iterator(); i.hasNext(); j++) {
            index = (Index) i.next();
            workers[j] = index.getWorker();
        }
        return workers;
    }

    /**
     * Shutdown all registered indexes by calling {@link org.exist.indexing.Index#close()}
     * on them.
     *
     * @throws DBException
     */
    public void shutdown() throws DBException {
        Index index;
        for (Iterator i = iterator(); i.hasNext(); ) {
            index = (Index) i.next();
            index.close();
        }
    }
}
