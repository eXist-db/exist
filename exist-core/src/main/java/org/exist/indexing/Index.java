/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Element;

import java.nio.file.Path;

/**
 * Represents an arbitrary index structure that can be used by eXist. This is the
 * main interface to be registered with the database instance. It provides methods
 * to configure, open and close the index. These methods will be called by the main
 * database instance during startup/shutdown. They don't need to be synchronized.
 */
public interface Index extends AutoCloseable {
   
    /**
     * Returns an id which uniquely identifies this index.  This is usually the class name. 
     * @return a unique name identifying this index.
     */
    String getIndexId();

    /**
     * Returns a human-readable name which uniquely identifies this index. This is configured by the user
     * @return a unique name identifying this index.
     */
    String getIndexName();

    /**
     * Returns the {@link org.exist.storage.BrokerPool} on with this Index operates.
     * 
     * @return the broker pool
     */
    BrokerPool getBrokerPool();

	/**
     * Configure the index and all resources associated with it. This method
     * is called while the database instance is initializing and receives the
     * <pre>&lt;module id="foo" class="bar"/&gt;</pre>
     * section of the configuration file.
     *
     * @param pool the BrokerPool representing the current database instance.
     * @param dataDir the main data directory where eXist stores its files (if relevant).
     * @param config the module element which configures this index, as found in conf.xml
     * @throws DatabaseConfigurationException in case of an database configuration error
     */
    void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException;

    /**
     * Opens the index for writing and reading. Will be called during initialization, but also
     * if the database has to be restarted.
     *
     * @throws DatabaseConfigurationException in case of an database configuration error
     */
    void open() throws DatabaseConfigurationException;

    /**
     * Closes the index and all associated resources.
     *
     * @throws DBException in case of an eXist-db error
     */
    @Override
    void close() throws DBException;

    /**
     * Sync the index. This method should make sure that all index contents are written to disk.
     * It will be called during checkpoint events and the system relies on the index to materialize
     * all data.
     *
     * @throws DBException in case of an eXist-db error
     */
    void sync() throws DBException;

    /**
     * Closes the index and removes it completely, including all resources and files
     * associated to it. This method is called during database repair before the
     * db contents are re-indexed.
     * @throws DBException in case of an eXist-db error
     */
    void remove() throws DBException;

    /**
     * Returns a new IndexWorker, which is used to access the index in a multi-threaded
     * environment.
     *
     * Every database instance has a number of
     * {@link org.exist.storage.DBBroker} objects. All operations on the db
     * have to go through one of these brokers. Each DBBroker retrieves an
     * IndexWorker for every index by calling this method.
     *
     * @param broker The DBBroker that owns this worker
     * @return a new IndexWorker that can be used for concurrent access to the index.
     */
    IndexWorker getWorker(DBBroker broker);

    /**
     * Convenience method that allows to check index consistency.
     * 
     * @param broker the broker that will perform the operation.
     * @return whether or not the index is in a consistent state. 
     * The definition of "consistency" is left to the user.
     */
    boolean checkIndex(DBBroker broker);

    /**
     * Returns the underlying btree class for btree-based indexes or null for
     * other indexes.
     *
     * @return the underlying btree or null if not available
     */
    BTree getStorage();
}
