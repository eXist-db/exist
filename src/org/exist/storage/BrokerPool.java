/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.storage;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.CollectionCache;
import org.exist.security.User;
import org.exist.storage.sync.Sync;
import org.exist.storage.sync.SyncDaemon;
import org.exist.util.Configuration;
import org.exist.util.Lock;
import org.exist.util.ReentrantReadWriteLock;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.ShutdownListener;

/**
 * This class controls all available instances of the database.
 * Use it to configure, start and stop database instances. You may
 * have multiple instances defined, each using its own configuration,
 * database directory etc.. To define multiple instances, pass an
 * identification string to the static method configure() and use
 * getInstance(id) to retrieve an instance.
 * 
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class BrokerPool {

	private final static Logger LOG = Logger.getLogger(BrokerPool.class);
	
	private final static TreeMap instances = new TreeMap();
	
	private static boolean registerShutdownHook = true;
	
	private final static ShutdownThread shutdownThread = new ShutdownThread();
	
	//	size of the internal buffer for collection objects
	public final static int COLLECTION_BUFFER_SIZE = 128;
	
	public final static String DEFAULT_INSTANCE = "exist";
	
	public final static long MAX_SHUTDOWN_WAIT = 45000;

	/**
	 * Should a shutdown hook be registered with the JVM? If set to true, method
	 * {@link #configure(String, int, int, Configuration)} will register a shutdown thread
	 * which takes care to shut down the database if the application receives a kill or term
	 * signal. However, this is unnecessary if the calling application has already registered
	 * a shutdown hook.
	 *  
	 * @param register
	 */
	public final static void setRegisterShutdownHook(boolean register) {
		registerShutdownHook = register;
	}
	
	public final static void configure(int minBrokers, int maxBrokers, Configuration config)
		throws EXistException {
		configure(DEFAULT_INSTANCE, minBrokers, maxBrokers, config);
	}

	/**
	 *  Configure a new BrokerPool instance. Call this before calling getInstance().
	 *
	 *@param  id The name to identify this database instance. You may have more
	 *	than one instance with different configurations.
	 *@param  minBrokers Minimum number of database brokers to start during initialization.
	 *@param  maxBrokers Maximum number of database brokers available to handle requests.
	 *@param  config The configuration object used by this instance.
	 *@exception  EXistException thrown if initialization fails.
	 */
	public final static void configure(
		String id,
		int minBrokers,
		int maxBrokers,
		Configuration config)
		throws EXistException {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance == null) {
			LOG.debug("configuring database instance '" + id + "' ...");
			instance = new BrokerPool(id, minBrokers, maxBrokers, config);
			instances.put(id, instance);
			if(instances.size() == 1) {
				if(registerShutdownHook) {
					LOG.debug("registering shutdown hook");
					try {
						Runtime.getRuntime().addShutdownHook(shutdownThread);
					} catch(IllegalArgumentException e) {
						LOG.debug("shutdown hook already registered");
					}
				}
			}
		} else
			LOG.warn("instance with id " + id + " already configured");
	}

	public final static boolean isConfigured(String id) {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance == null)
			return false;
		return instance.isInstanceConfigured();
	}

	public final static boolean isConfigured() {
		return isConfigured(DEFAULT_INSTANCE);
	}

	/**
	 *  Singleton method. Get the BrokerPool for a specified database instance.
	 *
	 *@return        The instance.
	 *@exception  EXistException  thrown if the instance has not been configured.
	 */
	public final static BrokerPool getInstance(String id) throws EXistException {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance != null)
			return instance;
		else
			throw new EXistException("instance with id " + id + " has not been configured yet");
	}

	public final static BrokerPool getInstance() throws EXistException {
		return getInstance(DEFAULT_INSTANCE);
	}

	public final static Iterator getInstances() {
		return instances.values().iterator();
	}

	/**
	 *  Shutdown running brokers. After calling this method, the BrokerPool is
	 *  no longer configured. You have to configure it again by calling
	 *  configure().
	 */
	public final static void stop(String id) throws EXistException {
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance != null) {
			instance.shutdown();
		} else
			throw new EXistException("instance with id " + " is not available");
	}

	public final static void stop() throws EXistException {
		stop(DEFAULT_INSTANCE);
	}

	public final static void stopAll(boolean killed) {
		Vector tmpInstances = new Vector();
		for (Iterator i = instances.values().iterator(); i.hasNext();) {
			tmpInstances.add(i.next());
		}
		BrokerPool instance;
		for (Iterator i = tmpInstances.iterator(); i.hasNext();) {
			instance = (BrokerPool) i.next();
			if (instance.conf != null)
				instance.shutdown(killed);
		}
		instances.clear();
	}

	private int max = 15;
	private int min = 1;
	protected Configuration conf = null;
	private int brokers = 0;
	
	private Stack pool = new Stack();
	private Map threads = new HashMap();
	private String instanceId;
	private boolean syncRequired = false;
	private int syncEvent = 0;
	private boolean initializing = true;
	
	/**
	 * During shutdown: max. time to wait (in ms.) for running jobs to return
	 */
	private long maxShutdownWait = MAX_SHUTDOWN_WAIT;
	
	/**
	 * The security manager for this database instance.
	 */
	private org.exist.security.SecurityManager secManager = null;
	
	/**
	 * SyncDaemon is a daemon thread which periodically triggers a cache sync.
	 */
	private SyncDaemon syncDaemon;
	
	/**
	 * ShutdownListener will be notified when the database instance shuts down.
	 */
	private ShutdownListener shutdownListener = null;
	
	// --------------- Global pools: --------------------------
	
	/**
	 * The global pool for compiled XQuery expressions.
	 */
	private XQueryPool xqueryCache;
	
	private XQueryMonitor monitor;
	
	/**
	 * The global collection cache.
	 */
	protected CollectionCache collectionsCache;
	
	/**
	 * Global pool for SAX XMLReader instances.
	 */
	protected XMLReaderPool xmlReaderPool;
	
	private Lock globalXUpdateLock = new ReentrantReadWriteLock("xupdate");

	/**
	 *  Constructor for the BrokerPool object
	 *
	 *@exception  EXistException  Description of the Exception
	 */
	public BrokerPool(String id, int minBrokers, int maxBrokers, Configuration config)
		throws EXistException {
		instanceId = id;
		min = minBrokers;
		max = maxBrokers;
		Integer minInt = (Integer) config.getProperty("db-connection.pool.min");
		Integer maxInt = (Integer) config.getProperty("db-connection.pool.max"); 
		Long syncInt = (Long) config.getProperty("db-connection.pool.sync-period");
		Long maxWaitInt = (Long) config.getProperty("db-connection.pool.shutdown-wait");
		if (minInt != null)
			min = minInt.intValue();
		if (maxInt != null)
			max = maxInt.intValue();
		long syncPeriod = 120000;
		if (syncInt != null)
			syncPeriod = syncInt.longValue();
		if (maxWaitInt != null) {
			maxShutdownWait = maxWaitInt.longValue();
			LOG.info("Max. wait during shutdown: " + maxShutdownWait);
		}
		LOG.info("Instances: min = " + min + "; max = " + max + "; sync = " + syncPeriod);
		syncDaemon = new SyncDaemon();
		if (syncPeriod > 0)
			syncDaemon.executePeriodically(1000, new Sync(this, syncPeriod), false);
		conf = config;
		xqueryCache = new XQueryPool();
		monitor = new XQueryMonitor();
		collectionsCache = new CollectionCache(COLLECTION_BUFFER_SIZE);
		xmlReaderPool = new XMLReaderPool(new XMLReaderObjectFactory(this), 5, 0);
		initialize();
	}

	/**
	 *  Number of active Brokers in this pool.
	 *
	 *@return    Description of the Return Value
	 */
	public int active() {
		return max - pool.size();
	}

	/**
	 *  Number of available Brokers for the current database instance.
	 */
	public int available() {
		return pool.size();
	}

	/**
	 * Returns the configuration object for this database
	 * instance.
	 */
	public Configuration getConfiguration() {
		return conf;
	}

	/**
	 * Returns the global collections cache. Collection objects
	 * are shared within one database instance.
	 * 
	 * @return
	 */
	public CollectionCache getCollectionsCache() {
		return collectionsCache;
	}
	
	public XMLReaderPool getParserPool() {
		return xmlReaderPool;
	}
	
	protected DBBroker createBroker() throws EXistException {
		DBBroker broker = BrokerFactory.getInstance(this, conf);
		//Thread.dumpStack();
		LOG.debug(
			"database " + instanceId + ": created new instance of " + broker.getClass().getName());
		pool.push(broker);
		brokers++;
		broker.setId(broker.getClass().getName() + '_' + brokers);
		return broker;
	}

	/**
	 *  Get a DBBroker instance from the pool.
	 */
	public DBBroker get() throws EXistException {
		if (!isInstanceConfigured())
			throw new EXistException("database instance is not available");
		DBBroker broker = (DBBroker)threads.get(Thread.currentThread());
		if(broker != null) {
			// the thread already holds a reference to a broker object
			broker.incReferenceCount();
			return broker;
		}
		synchronized(this) {
			if (pool.isEmpty()) {
				if (brokers < max)
					createBroker();
				else
					while (pool.isEmpty()) {
						LOG.debug("waiting for broker instance to become available");
						try {
							this.wait();
						} catch (InterruptedException e) {
						}
					}
			}
			broker = (DBBroker) pool.pop();
			threads.put(Thread.currentThread(), broker);
			broker.incReferenceCount();
			this.notifyAll();
			return broker;
		}
	}

	/**
	 * Get a DBBroker instance and set its current user to user.
	 *  
	 * @param user
	 * @return
	 * @throws EXistException
	 */
	public DBBroker get(User user) throws EXistException {
		DBBroker broker = get();
		broker.setUser(user);
		return broker;
	}
	
	/**
	 *  Returns the security manager responsible for this pool
	 *
	 *@return    The securityManager value
	 */
	public org.exist.security.SecurityManager getSecurityManager() {
		return secManager;
	}

	/**
	 * Reload the security manager. This method is called whenever the
	 * users.xml file has been changed.
	 * 
	 * @param broker
	 */
	public void reloadSecurityManager(DBBroker broker) {
		LOG.debug("reloading security manager");
		secManager = new org.exist.security.SecurityManager(this, broker);
	}

	public SyncDaemon getSyncDaemon() {
	    return syncDaemon;
	}
	
	/**
	 *  Initialize the current instance.
	 *
	 *@exception  EXistException  Description of the Exception
	 */
	protected void initialize() throws EXistException {
		LOG.debug("initializing database " + instanceId);
		initializing = true;
		// create a first broker to initialize the security manager
		createBroker();
		DBBroker broker = (DBBroker) pool.peek();
		broker.cleanUp();
		secManager = new org.exist.security.SecurityManager(this, broker);
		initializing = false;
		
		// now create remaining brokers
		for (int i = 1; i < min; i++)
			createBroker();
		LOG.debug("database engine " + instanceId + " initialized.");
	}

	protected boolean isInitializing() {
		return initializing;
	}
	
	/**
	 *  Release a DBBroker instance into the pool.
	 *	If all active instances are in the pool (i.e.
	 * 	the database is currently not used), release
	 *  will call sync() to flush unwritten buffers 
	 *  to the disk. 
	 * 
	 *@param  broker  Description of the Parameter
	 */
	public void release(DBBroker broker) {
		if (broker == null)
			return;
		broker.decReferenceCount();
		if(broker.getReferenceCount() > 0) {
			// broker still has references. Keep it
			return;  
		}
		synchronized (this) {
		    threads.remove(Thread.currentThread());
			pool.push(broker);
			if (syncRequired && threads.size() == 0) {
				sync(broker, syncEvent);
				syncRequired = false;
			}
			this.notifyAll();
		}
	}

	/**
	 * Write buffers to disk. release() calls this
	 * method after a specified period of time
	 * to flush buffers.
	 * 
	 * @param broker
	 */
	public void sync(DBBroker broker, int syncEvent) {
		broker.sync(syncEvent);
	}

	public synchronized void shutdown() {
		shutdown(false);
	}
	
	/**  Shutdown all brokers. */
	public synchronized void shutdown(boolean killed) {
		syncDaemon.shutDown();
		monitor.killAll(500);
		long waitStart = System.currentTimeMillis();
		while (threads.size() > 0) {
			try {
				this.wait(1000);
			} catch (InterruptedException e) {
			}
			if(System.currentTimeMillis() - waitStart > maxShutdownWait) {
				LOG.debug("Not all threads returned. Forcing shutdown ...");
				break;
			}
		}
		LOG.debug("calling shutdown ...");
		DBBroker broker = null;
		if(pool.isEmpty())
			try {
				broker = createBroker();
			} catch (EXistException e) {
				LOG.warn("could not create instance for shutdown. Giving up.");
			}
		else
			broker = (DBBroker)pool.peek();
		broker.shutdown();
		LOG.debug("shutdown!");
		conf = null;
		instances.remove(instanceId);
		if(instances.size() == 0 && !killed) {
			LOG.debug("removing shutdown hook");
			Runtime.getRuntime().removeShutdownHook(shutdownThread);
		}
		if (shutdownListener != null)
			shutdownListener.shutdown(instanceId, instances.size());
	}

	/**
		 *  Returns maximum of concurrent Brokers.
		 *
		 *@return    The max value
		 */
	public int getMax() {
		return max;
	}

	public String getId() {
		return instanceId;
	}

	/**
	 *  Has this BrokerPool been configured?
	 *
	 *@return    The configured value
	 */
	public final boolean isInstanceConfigured() {
		return conf != null;
	}

	public void triggerSync(int event) {
		synchronized (this) {
			if(pool.size() == 0)
				return;
			if (pool.size() == brokers) {
				DBBroker broker = (DBBroker) pool.peek();
				sync(broker, syncEvent);
				syncRequired = false;
			} else {
				syncEvent = event;
				syncRequired = true;
			}
		}
	}

	public void registerShutdownListener(ShutdownListener listener) {
		shutdownListener = listener;
	}

	/**
	 * Returns the global XQuery pool for this database instance.
	 * 
	 * @return
	 */
	public XQueryPool getXQueryPool() {
	    return xqueryCache;
	}
	
	public XQueryMonitor getXQueryMonitor() {
		return monitor;
	}
	
	/**
	 * Returns the global update lock for this database instance.
	 * This lock is used by XUpdate operations to avoid that
	 * concurrent XUpdate requests modify the database until all
	 * document locks have been correctly set.
	 *  
	 * @return
	 */
	public Lock getGlobalUpdateLock() {
	    return globalXUpdateLock;
	}
	
	protected static class ShutdownThread extends Thread {

		/**  Constructor for the ShutdownThread object */
		public ShutdownThread() {
			super();
		}

		/**  Main processing method for the ShutdownThread object */
		public void run() {
			LOG.debug("shutdown forced");
			BrokerPool.stopAll(true);
		}
	}

}
