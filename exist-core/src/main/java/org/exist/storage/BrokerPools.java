/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2016 The eXist-db Project
 * http://exist-db.org
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import net.jcip.annotations.GuardedBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import com.evolvedbinary.j8fu.function.ConsumerE;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.exist.util.ThreadUtils.newGlobalThread;

/**
 * This abstract class really just contains the static
 * methods for {@link BrokerPool} to help us organise the
 * code into smaller understandable chunks and reduce the
 * complexity when understanding the concurrency
 * constraints between one and many BrokerPools
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
abstract class BrokerPools {

    private static final Logger LOG = LogManager.getLogger(BrokerPools.class);

    private static final ReadWriteLock instancesLock = new ReentrantReadWriteLock();
    @GuardedBy("instancesLock") private static final Map<String, BrokerPool> instances = new TreeMap<>();

    /**
     * The name of a default database instance
     */
    public static String DEFAULT_INSTANCE_NAME = "exist";

    // register a shutdown hook
    static {
        try {
            Runtime.getRuntime().addShutdownHook(newGlobalThread("BrokerPools.ShutdownHook", () -> {
                /**
                 * Make sure that all instances are cleanly shut down.
                 */
                LOG.info("Executing shutdown thread");
                BrokerPools.stopAll(true);
            }));
            LOG.debug("Shutdown hook registered");
        } catch(final IllegalArgumentException e) {
            LOG.warn("Shutdown hook already registered");
        }
    }

    /**
     * Creates and configures a default database instance and adds it to the available instances.
     * Call this before calling {link #getInstance()}.
     * If a default database instance already exists, the new configuration is ignored.
     *
     * @param minBrokers The minimum number of concurrent brokers for handling requests on the database instance.
     * @param maxBrokers The maximum number of concurrent brokers for handling requests on the database instance.
     * @param config     The configuration object for the database instance
     *
     * @throws EXistException If the initialization fails.
     * @throws DatabaseConfigurationException If configuration fails.
     */
    public static void configure(final int minBrokers, final int maxBrokers, final Configuration config)
            throws EXistException, DatabaseConfigurationException {
        configure(DEFAULT_INSTANCE_NAME, minBrokers, maxBrokers, config);
    }

    /**
     * Creates and configures a default database instance and adds it to the available instances.
     * Call this before calling {link #getInstance()}.
     * If a default database instance already exists, the new configuration is ignored.
     *
     * @param minBrokers The minimum number of concurrent brokers for handling requests on the database instance.
     * @param maxBrokers The maximum number of concurrent brokers for handling requests on the database instance.
     * @param config     The configuration object for the database instance
     * @param statusObserver    Observes the status of this database instance
     *
     * @throws EXistException If the initialization fails.
     * @throws DatabaseConfigurationException If configuration fails.
     */
    public static void configure(final int minBrokers, final int maxBrokers, final Configuration config,
            final Optional<Observer> statusObserver)  throws EXistException, DatabaseConfigurationException {
        configure(DEFAULT_INSTANCE_NAME, minBrokers, maxBrokers, config, statusObserver);
    }

    /**
     * Creates and configures a database instance and adds it to the pool.
     * Call this before calling {link #getInstance()}.
     * If a database instance with the same name already exists, the new configuration is ignored.
     *
     * @param instanceName A <strong>unique</strong> name for the database instance.
     *                     It is possible to have more than one database instance (with different configurations
     *                     for example).
     * @param minBrokers   The minimum number of concurrent brokers for handling requests on the database instance.
     * @param maxBrokers   The maximum number of concurrent brokers for handling requests on the database instance.
     * @param config       The configuration object for the database instance
     *
     * @throws EXistException If the initialization fails.
     */
    public static void configure(final String instanceName, final int minBrokers, final int maxBrokers,
            final Configuration config) throws EXistException {
        configure(instanceName, minBrokers, maxBrokers, config, Optional.empty());
    }

    /**
     * Creates and configures a database instance and adds it to the pool.
     * Call this before calling {link #getInstance()}.
     * If a database instance with the same name already exists, the new configuration is ignored.
     *
     * @param instanceName A <strong>unique</strong> name for the database instance.
     *                     It is possible to have more than one database instance (with different configurations
     *                     for example).
     * @param minBrokers        The minimum number of concurrent brokers for handling requests on the database instance.
     * @param maxBrokers        The maximum number of concurrent brokers for handling requests on the database instance.
     * @param config            The configuration object for the database instance
     * @param statusObserver    Observes the status of this database instance
     *
     * @throws EXistException If the initialization fails.
     */
    public static void configure(final String instanceName, final int minBrokers, final int maxBrokers,
            final Configuration config, final Optional<Observer> statusObserver) throws EXistException {

        // optimize for read-concurrency as instances are configured (created) once and used many times
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(instancesLock, LockMode.READ_LOCK)) {
            if (instances.containsKey(instanceName)) {
                LOG.warn("Database instance '" + instanceName + "' is already configured");
                return;
            }
        }

        // fallback to probably having to create a new BrokerPool instance
        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(instancesLock, LockMode.WRITE_LOCK)) {
            // check again, as another thread may have preempted us since we released the read-lock
            if (instances.containsKey(instanceName)) {
                LOG.warn("Database instance '" + instanceName + "' is already configured");
                return;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Configuring database instance '" + instanceName + "'...");
            }

            try {
                //Create the instance
                final BrokerPool instance = new BrokerPool(instanceName, minBrokers, maxBrokers, config, statusObserver);

                //initialize it!
                instance.initialize();

                //Add it to the list
                instances.put(instanceName, instance);
            } catch(final Throwable e) {
                // Catch all possible issues and report.
                LOG.error("Unable to initialize database instance '" + instanceName + "': " + e.getMessage(), e);
                final EXistException ee;
                if(e instanceof EXistException) {
                    ee = (EXistException)e;
                } else {
                    ee = new EXistException(e);
                }
                throw ee;
            }
        }
    }

    /**
     * Returns whether or not the default database instance is configured.
     *
     * @return <code>true</code> if it is configured
     */
    public static boolean isConfigured() {
        return isConfigured(DEFAULT_INSTANCE_NAME);
    }

    /**
     * Returns whether or not a database instance is configured.
     *
     * @param instanceName The name of the database instance
     * @return <code>true</code> if it is configured
     */
    public static boolean isConfigured(final String instanceName) {
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(instancesLock, LockMode.READ_LOCK)) {
            final BrokerPool instance = instances.get(instanceName);
            if (instance == null) {
                return false;
            } else {
                return instance.isInstanceConfigured();
            }
        }
    }

    /**
     * Returns the broker pool for the default database instance (if it is configured).
     *
     * @return The broker pool of the default database instance
     *
     * @throws EXistException If the instance is not available (not created, stopped or not configured)
     */
    public static BrokerPool getInstance() throws EXistException {
        return getInstance(DEFAULT_INSTANCE_NAME);
    }

    /**
     * Returns a broker pool for a database instance.
     *
     * @param instanceName The name of the database instance
     * @return The broker pool
     *
     * @throws EXistException If the instance is not available (not created, stopped or not configured)
     */
    public static BrokerPool getInstance(final String instanceName) throws EXistException {
        //Check if there is a database instance with the same id
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(instancesLock, LockMode.READ_LOCK)) {
            final BrokerPool instance = instances.get(instanceName);
            if (instance != null) {
                //TODO : call isConfigured(id) and throw an EXistException if relevant ?
                return instance;
            } else {
                throw new EXistException("Database instance '" + instanceName + "' is not available");
            }
        }
    }

    static void removeInstance(final String instanceName) {
        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(instancesLock, LockMode.WRITE_LOCK)) {
            instances.remove(instanceName);
        }
    }

    public static <E extends Exception> void readInstances(final ConsumerE<BrokerPool, E> reader) throws E {
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(instancesLock, LockMode.READ_LOCK)) {
            for (final BrokerPool instance : instances.values()) {
                reader.accept(instance);
            }
        }
    }

    static int instancesCount() {
        try(final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(instancesLock, LockMode.READ_LOCK)) {
            return instances.size();
        }
    }

    /**
     * Stops all the database instances. After calling this method, the database instances are
     * no longer configured.
     *
     * @param killed <code>true</code> when invoked by an exiting JVM
     */
    public static void stopAll(final boolean killed) {
        try(final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(instancesLock, LockMode.WRITE_LOCK)) {
            for (final BrokerPool instance : instances.values()) {
                if (instance.isInstanceConfigured()) {
                    //Shut it down
                    instance.shutdown(killed);
                }
            }

            // Clear the living instances container : they are all sentenced to death...
            assert(instances.size() == 0); // should have all been removed by BrokerPool#shutdown(boolean)
            instances.clear();
        }
    }
}
