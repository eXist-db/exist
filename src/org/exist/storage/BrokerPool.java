/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.storage;


import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.debuggee.Debuggee;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.SymbolTable;
import org.exist.indexing.IndexManager;
import org.exist.management.AgentFactory;
import org.exist.numbering.DLNFactory;
import org.exist.numbering.NodeIdFactory;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.SystemTaskJob;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.storage.btree.DBException;
import org.exist.storage.lock.FileLock;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ReentrantReadWriteLock;
import org.exist.storage.sync.Sync;
import org.exist.storage.sync.SyncTask;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.ReadOnlyException;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.PerformanceStats;
/**
 * This class controls all available instances of the database.
 * Use it to configure, start and stop database instances. 
 * You may have multiple instances defined, each using its own configuration. 
 * To define multiple instances, pass an identification string to {@link #configure(String, int, int, Configuration)}
 * and use {@link #getInstance(String)} to retrieve an instance.
 *
 *@author  Wolfgang Meier <wolfgang@exist-db.org>
 *@author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
//TODO : in the future, separate the design between the Map of DBInstances and their non static implementation 
public class BrokerPool extends Observable {

	private final static Logger LOG = Logger.getLogger(BrokerPool.class);
	
	private final static TreeMap instances = new TreeMap();

    public final static String SIGNAL_STARTUP = "startup";
    public final static String SIGNAL_SHUTDOWN = "shutdown";
    
	/**
	 * The name of a default database instance for those who are too lazy to provide parameters ;-). 
	 */	
	public final static String DEFAULT_INSTANCE_NAME = "exist";		
	public static final String CONFIGURATION_CONNECTION_ELEMENT_NAME = "db-connection";
	public static final String CONFIGURATION_POOL_ELEMENT_NAME = "pool";
	public static final String CONFIGURATION_SECURITY_ELEMENT_NAME = "security";
	public static final String CONFIGURATION_RECOVERY_ELEMENT_NAME = "recovery";
	public static final String DATA_DIR_ATTRIBUTE = "files";
	//TODO : move elsewhere ?
	public final static String RECOVERY_ENABLED_ATTRIBUTE = "enabled";
    public final static String RECOVERY_POST_RECOVERY_CHECK = "consistency-check";
	//TODO : move elsewhere ?
	public final static String COLLECTION_CACHE_SIZE_ATTRIBUTE = "collectionCacheSize";
	public final static String MIN_CONNECTIONS_ATTRIBUTE = "min";
	public final static String MAX_CONNECTIONS_ATTRIBUTE = "max";
	public final static String SYNC_PERIOD_ATTRIBUTE = "sync-period";
	public final static String SHUTDOWN_DELAY_ATTRIBUTE = "wait-before-shutdown";
    public final static String NODES_BUFFER_ATTRIBUTE = "nodesBuffer";
	
	//Various configuration property keys (set by the configuration manager)
	public static final String PROPERTY_DATA_DIR = "db-connection.data-dir";
	public final static String PROPERTY_MIN_CONNECTIONS = "db-connection.pool.min";
	public final static String PROPERTY_MAX_CONNECTIONS = "db-connection.pool.max";
	public final static String PROPERTY_SYNC_PERIOD = "db-connection.pool.sync-period";
	public final static String PROPERTY_SHUTDOWN_DELAY = "wait-before-shutdown";
	//TODO : move elsewhere ?
	public final static String PROPERTY_COLLECTION_CACHE_SIZE = "db-connection.collection-cache-size";
	//TODO : move elsewhere ? Get fully qualified class name ?
	public final static String DEFAULT_SECURITY_CLASS = "org.exist.security.XMLSecurityManager";
	public final static String PROPERTY_SECURITY_CLASS = "db-connection.security.class";
	public final static String PROPERTY_RECOVERY_ENABLED = "db-connection.recovery.enabled";
    public final static String PROPERTY_RECOVERY_CHECK = "db-connection.recovery.consistency-check";
	public final static String PROPERTY_SYSTEM_TASK_CONFIG = "db-connection.system-task-config";
    public static final String PROPERTY_NODES_BUFFER = "db-connection.nodes-buffer";
    public static final String PROPERTY_EXPORT_ONLY = "db-connection.emergency";
    public static final String DOC_ID_MODE_ATTRIBUTE = "doc-ids";
	
	public static final String DOC_ID_MODE_PROPERTY = "db-connection.doc-ids.mode";
	
	//TODO : inline the class ? or... make it configurable ?
    // WM: inline. I don't think users need to be able to overwrite this.
    // They can register their own shutdown hooks any time.
	private final static Thread shutdownHook = new Thread() {
	    /**
	     * Make sure that all instances are cleanly shut down.
	     */     
	    public void run() {
	        LOG.info("Executing shutdown thread");
	        BrokerPool.stopAll(true);
	    }
    };
	
	//TODO : make this defaut value configurable ? useless if we have a registerShutdownHook(Thread aThread) method (null = deregister)
	private static boolean registerShutdownHook = true;

    private static Observer statusObserver = null;

    /**
     * Whether of not the JVM should run the shutdown thread.
	 * @param register <code>true</code> if the JVM should run the thread
	 */
    //TODO : rename as activateShutdownHook ? or registerShutdownHook(Thread aThread)
    // WM: it is probably not necessary to allow users to register their own hook. This method
    // is only used once, by class org.exist.JettyStart, which registers its own hook.
	public final static void setRegisterShutdownHook(boolean register) {
		/*
		 * TODO : call Runtime.getRuntime().removeShutdownHook or Runtime.getRuntime().registerShutdownHook 
		 * depending of the value of register
		 * Since Java doesn't provide a convenient way to know if a shutdown hook has been registrered, 
		 * we may have to catch IllegalArgumentException
		 */
		//TODO : check that the JVM is not shutting down
		registerShutdownHook = register;
	}
	
	//TODO : make it non-static since every database instance may have its own policy.
	//TODO : make a defaut value that could be overwritten by the configuration
    // WM: this is only used by junit tests to test the recovery process.
    /**
     * For testing only: triggers a database corruption by disabling the page caches. The effect is
     * similar to a sudden power loss or the jvm being killed. The flag is used by some
     * junit tests to test the recovery process.
     */
	public static boolean FORCE_CORRUPTION = false;  	
	
	/**
	 *  Creates and configures a default database instance and adds it to the pool. 
	 *  Call this before calling {link #getInstance()}. 
	 * If a default database instance already exists, the new configuration is ignored.
	 * @param minBrokers The minimum number of concurrent brokers for handling requests on the database instance.
	 * @param maxBrokers The maximum number of concurrent brokers for handling requests on the database instance.
	 * @param config The configuration object for the database instance
	 * @throws EXistException
	 *@exception  EXistException If the initialization fails.	
	 */
	//TODO : in the future, we should implement a Configurable interface	
	public final static void configure(int minBrokers, int maxBrokers, Configuration config)
            throws EXistException, DatabaseConfigurationException {
		configure(DEFAULT_INSTANCE_NAME, minBrokers, maxBrokers, config);
	}

	/**
	 *  Creates and configures a database instance and adds it to the pool. 
	 *  Call this before calling {link #getInstance()}. 
	 * If a database instance with the same name already exists, the new configuration is ignored.
	 * @param instanceName A <strong>unique</strong> name for the database instance. 
	 * It is possible to have more than one database instance (with different configurations for example).
	 * @param minBrokers The minimum number of concurrent brokers for handling requests on the database instance.
	 * @param maxBrokers The maximum number of concurrent brokers for handling requests on the database instance.
	 * @param config The configuration object for the database instance
	 * @throws EXistException If the initialization fails.	
	 */
	//TODO : in the future, we should implement a Configurable interface	
	public final static void configure(
		String instanceName,
		int minBrokers,
		int maxBrokers,
		Configuration config)
            throws EXistException, DatabaseConfigurationException {
		//Check if there is a database instance in the pool with the same id
		BrokerPool instance = (BrokerPool) instances.get(instanceName);
		if (instance == null) {
			LOG.debug("configuring database instance '" + instanceName + "'...");
			
            try {
                //Create the instance
                instance = new BrokerPool(instanceName, minBrokers, maxBrokers, config);

                //Add it to the pool
                instances.put(instanceName, instance);

                //We now have at least an instance...
                if(instances.size() == 1) {
                    //... so a ShutdownHook may be interesting
                    if(registerShutdownHook) {
                        try {
                            //... currently an eXist-specific one. TODO : make it configurable ?
                            Runtime.getRuntime().addShutdownHook(shutdownHook);
                            LOG.debug("shutdown hook registered");
                            
                        } catch(IllegalArgumentException e) {
                            LOG.warn("shutdown hook already registered");
                        }
                    }
                }
                
            } catch (Throwable ex){
                // Catch all possible issues and report.
                LOG.error("Unable to initialialize database instance '" + instanceName
                        + "': "+ex.getMessage(), ex);
            }

		//TODO : throw an exception here rather than silently ignore an *explicit* parameter ?
        // WM: maybe throw an exception. Users can check if a db is already configured.
		} else
			LOG.warn("database instance '" + instanceName + "' is already configured");
	}
	
	/** Returns whether or not the default database instance is configured.
	 * @return <code>true</code> if it is configured
	 */
	//TODO : in the future, we should implement a Configurable interface
	public final static boolean isConfigured() {
		return isConfigured(DEFAULT_INSTANCE_NAME);
	}	

	/** Returns whether or not a database instance is configured.
	 * @param id The name of the database instance
	 * @return <code>true</code> if it is configured
	 */
	//TODO : in the future, we should implement a Configurable interface	
	public final static boolean isConfigured(String id) {
		//Check if there is a database instance in the pool with the same id
		BrokerPool instance = (BrokerPool) instances.get(id);
		//No : it *can't* be configured
		if (instance == null)
			return false;
		//Yes : it *may* be configured
		return instance.isInstanceConfigured();
	}

	/**Returns a broker pool for the default database instance.
	 * @return The broker pool
	 * @throws EXistException If the database instance is not available (not created, stopped or not configured)
	 */
	public final static BrokerPool getInstance() throws EXistException {
		return getInstance(DEFAULT_INSTANCE_NAME);
	}	

	/**Returns a broker pool for a database instance.
	 * @param instanceName The name of the database instance
	 * @return The broker pool
	 * @throws EXistException If the instance is not available (not created, stopped or not configured)
	 */
	public final static BrokerPool getInstance(String instanceName) throws EXistException {
		//Check if there is a database instance in the pool with the same id
        BrokerPool instance = (BrokerPool) instances.get(instanceName);
        if (instance != null)
        	//TODO : call isConfigured(id) and throw an EXistException if relevant ?
        	return instance;
        else        	
        	throw new EXistException("database instance '" + instanceName + "' is not available");
    }

	/** Returns an iterator over the database instances.
	 * @return The iterator
	 */
	public final static Iterator getInstances() {
		return instances.values().iterator();
	}

	/** Stops the default database instance. After calling this method, it is
	 *  no longer configured.
	 * @throws EXistException If the default database instance is not available (not created, stopped or not configured) 
	 */
	public final static void stop() throws EXistException {
		stop(DEFAULT_INSTANCE_NAME);
	}

	/** Stops the given database instance. After calling this method, it is
	 *  no longer configured.
	 * @param id The name of the database instance
	 * @throws EXistException If the database instance is not available (not created, stopped or not configured)
	 */
	public final static void stop(String id) throws EXistException {		
		BrokerPool instance = (BrokerPool) instances.get(id);
		if (instance == null)
			throw new EXistException("database instance '" + id + "' is not available");
		instance.shutdown();	
	}

	/** Stops all the database instances. After calling this method, the database instances are
	 *  no longer configured.
	 * @param killed <code>true</code> when invoked by an exiting JVM
	 */
	public final static void stopAll(boolean killed) {
		//Create a temporary vector
		Vector tmpInstances = new Vector();
		for (Iterator i = instances.values().iterator(); i.hasNext();) {
			//and feed it with the living database instances
			tmpInstances.add(i.next());
		}
		BrokerPool instance;
		//Iterate over the living database instances
		for (Iterator i = tmpInstances.iterator(); i.hasNext();) {
			instance = (BrokerPool) i.next();
			if (instance.conf != null)
				//Shut them down
				instance.shutdown(killed);
		}
		//Clear the living instances container : they are all sentenced to death...
		instances.clear();
	}

    public static void registerStatusObserver(Observer observer) {
        statusObserver = observer;
        LOG.debug("registering observer: " + observer.getClass().getName());
    }

	/* END OF STATIC IMPLEMENTATION */
	
    /**
	 * Default values
	 */	
	//TODO : make them static when we have 2 classes
	private final int DEFAULT_MIN_BROKERS = 1;
	private final int DEFAULT_MAX_BROKERS = 15;
    public final long DEFAULT_SYNCH_PERIOD = 120000;
    public final long DEFAULT_MAX_SHUTDOWN_WAIT = 45000;
	//TODO : move this default setting to org.exist.collections.CollectionCache ? 
	public final int DEFAULT_COLLECTION_BUFFER_SIZE = 512;

    public static final String PROPERTY_PAGE_SIZE = "db-connection.page-size";
    public static final int DEFAULT_PAGE_SIZE = 4096;
    
    /**
     * <code>true</code> if the database instance is able to handle transactions. 
     */    
    private boolean transactionsEnabled;   	

	/**
	 * The name of the database instance
	 */
	private String instanceName;

    private final static int OPERATING = 0;
    private final static int INITIALIZING = 1;
    private final static int SHUTDOWN = 2;
    
    private int status = OPERATING;
    
	/**
	 * The number of brokers for the database instance 
	 */
	private int brokersCount = 0;
	
	/**
	 * The minimal number of brokers for the database instance 
	 */
	private int minBrokers;
	
	/**
	 * The maximal number of brokers for the database instance 
	 */
	private int maxBrokers;

	/**
	 * The number of inactive brokers for the database instance 
	 */	
	private Stack<DBBroker> inactiveBrokers = new Stack();
	
	/**
	 * The number of active brokers for the database instance 
	 */	
	private Map<Thread, DBBroker> activeBrokers = new HashMap<Thread, DBBroker>();
		
	/**
     * The configuration object for the database instance
     */
	protected Configuration conf = null;    

	/**
	 * <code>true</code> if a cache synchronization event is scheduled
	 */
	//TODO : rename as syncScheduled ?
	//TODO : alternatively, delete this member and create a Sync.NOSYNC event
	private boolean syncRequired = false;	
	
	/**
	 * The kind of scheduled cache synchronization event. 
	 * One of {@link org.exist.storage.sync.Sync#MAJOR_SYNC} or {@link org.exist.storage.sync.Sync#MINOR_SYNC}
	 */
	private int syncEvent = 0;	
	
    private boolean checkpoint = false;
	
    /**
     * <code>true</code> if the database instance is running in read-only mode.
     */
    //TODO : this should be computed by the DBrokers depending of their configuration/capabilities
    //TODO : for now, this member is used for recovery management
    private boolean isReadOnly;    

    private int pageSize;
    
    private FileLock dataLock;
    
    /**
     * The transaction manager of the database instance.
     */
    private TransactionManager transactionManager = null;
   
	/**
	 * Delay (in ms) for running jobs to return when the database instance shuts down.
	 */
	private long maxShutdownWait;

	/**
	 * The scheduler for the database instance.
	 */
	private Scheduler scheduler;

    /**
     * Manages pluggable index structures. 
     */
    private IndexManager indexManager;

    /**
     * Global symbol table used to encode element and attribute qnames.
     */
    private SymbolTable symbols;
    
    /**
	 * Cache synchronization on the database instance.
	 */
	private long majorSyncPeriod = DEFAULT_SYNCH_PERIOD;		//the period after which a major sync should occur		
	private long lastMajorSync = System.currentTimeMillis();	//time the last major sync occurred
    
	/**
	 * The listener that is notified when the database instance shuts down.
	 */
	private ShutdownListener shutdownListener = null;

	/**
     * The security manager of the database instance. 
     */
    private SecurityManager securityManager = null;	
    
    /**
     * The global notification service used to subscribe
     * to document updates.
     */
    private NotificationService notificationService = null;

    private long nextSystemStatus = System.currentTimeMillis();

    /**
	 * The cache in which the database instance may store items.
	 */	
	
	private DefaultCacheManager cacheManager;

    private CollectionCacheManager collectionCacheMgr;

    private long reservedMem;

    /**
	 * The pool in which the database instance's <strong>compiled</strong> XQueries are stored.
	 */
	private XQueryPool xQueryPool;
	
	/**
	 * The monitor in which the database instance's strong>running</strong> XQueries are managed.
	 */
	private ProcessMonitor processMonitor;

    /**
     * Global performance stats to gather function execution statistics
     * from all queries running on this database instance.
     */
    private PerformanceStats xqueryStats;

    /**
     * The global manager for accessing collection configuration files from the database instance.
     */
	private CollectionConfigurationManager collectionConfigurationManager = null;				
	
	/**
     * The cache in which the database instance's collections are stored.
     */
	protected CollectionCache collectionCache;
	
	/**
	 * The pool in which the database instance's readers are stored.
	 */
	protected XMLReaderPool xmlReaderPool;

    private NodeIdFactory nodeFactory = new DLNFactory();

    //TODO : is another value possible ? If no, make it static
    // WM: no, we need one lock per database instance. Otherwise we would lock another database.
	private Lock globalXUpdateLock = new ReentrantReadWriteLock("xupdate");

    private User serviceModeUser = null;
    private boolean inServiceMode = false;

    //the time that the database was started
    private final Calendar startupTime = Calendar.getInstance();

    /** Creates and configures the database instance.
	 * @param instanceName A name for the database instance.
	 * @param minBrokers The minimum number of concurrent brokers for handling requests on the database instance.
	 * @param maxBrokers The maximum number of concurrent brokers for handling requests on the database instance.
	 * @param conf The configuration object for the database instance
	 * @throws EXistException If the initialization fails.
    */
	//TODO : Then write a configure(int minBrokers, int maxBrokers, Configuration conf) method
	private BrokerPool(String instanceName, int minBrokers, int maxBrokers, Configuration conf)
            throws EXistException, DatabaseConfigurationException {
		
		Integer anInteger;
		Long aLong;
		Boolean aBoolean;
		NumberFormat nf = NumberFormat.getNumberInstance();

        if (statusObserver != null)
            addObserver(statusObserver);
        
		//TODO : ensure that the instance name is unique ?
        //WM: needs to be done in the configure method.
		this.instanceName = instanceName;
		
		//TODO : find a nice way to (re)set the default values
		//TODO : create static final members for configuration keys
		this.minBrokers = DEFAULT_MIN_BROKERS;
		this.maxBrokers = DEFAULT_MAX_BROKERS;
		this.maxShutdownWait = DEFAULT_MAX_SHUTDOWN_WAIT;
		//TODO : read from configuration
		this.transactionsEnabled = true;
		
		this.minBrokers = minBrokers;
		this.maxBrokers = maxBrokers;
		/*
		 * strange enough, the settings provided by the constructor may be overriden
		 * by the ones *explicitely* provided by the constructor
		 * TODO : consider a private constructor BrokerPool(String instanceName) then configure(int minBrokers, int maxBrokers, Configuration config)
		 */		
		anInteger = (Integer) conf.getProperty(PROPERTY_MIN_CONNECTIONS);
		if (anInteger != null)
			this.minBrokers = anInteger.intValue();		
		anInteger = (Integer) conf.getProperty(PROPERTY_MAX_CONNECTIONS); 
		if (anInteger != null)
			this.maxBrokers = anInteger.intValue();		
		//TODO : sanity check : minBrokers shall be lesser than or equal to maxBrokers
		//TODO : sanity check : minBrokers shall be positive
		LOG.info("database instance '" + instanceName + "' will have between " + nf.format(this.minBrokers) + " and " + nf.format(this.maxBrokers) + " brokers");
		
		//TODO : use the periodicity of a SystemTask (see below)
		aLong = (Long) conf.getProperty(PROPERTY_SYNC_PERIOD);
		if (aLong != null)
			/*this.*/majorSyncPeriod = aLong.longValue();
		//TODO : sanity check : the synch period should be reasonable
		LOG.info("database instance '" + instanceName + "' will be synchronized every " + nf.format(/*this.*/majorSyncPeriod) + " ms");

		aLong = (Long) conf.getProperty(BrokerPool.PROPERTY_SHUTDOWN_DELAY);		
		if (aLong != null) {
			this.maxShutdownWait = aLong.longValue();			
		}
		//TODO : sanity check : the shutdown period should be reasonable
		LOG.info("database instance '" + instanceName + "' will wait  " + nf.format(this.maxShutdownWait) + " ms during shutdown");

		aBoolean = (Boolean) conf.getProperty(PROPERTY_RECOVERY_ENABLED);
		if (aBoolean != null) {
			this.transactionsEnabled = aBoolean.booleanValue();
        }
		LOG.info("database instance '" + instanceName + "' is enabled for transactions : " + this.transactionsEnabled);

		pageSize = conf.getInteger(PROPERTY_PAGE_SIZE);
		if (pageSize < 0)
			pageSize = DEFAULT_PAGE_SIZE;

/* TODO: start -adam- remove OLD SystemTask initialization */
		
		//How ugly : needs refactoring...
/*		Configuration.SystemTaskConfig systemTasksConfigs[] = (Configuration.SystemTaskConfig[]) conf.getProperty(BrokerPool.PROPERTY_SYSTEM_TASK_CONFIG);
		if (systemTasksConfigs != null) {
	        for (int i = 0; i < systemTasksConfigs.length; i++) {
	        	try {
		            Class clazz = Class.forName(systemTasksConfigs[i].getClassName());
		            SystemTask task = (SystemTask) clazz.newInstance();	
		            if (!(task instanceof SystemTask))
		            	//TODO : shall we ignore the exception ?
		            	throw new EXistException("'" + task.getClass().getName() + "' is not an instance of org.exist.storage.SystemTask");
		            task.configure(conf, systemTasksConfigs[i].getProperties());
		            systemTasks.add(task);
		            //TODO : remove when SystemTask has a getPeriodicity() method
		            systemTasksPeriods.add(systemTasksConfigs[i]);
		            LOG.info("added system task instance '" + task.getClass().getName() + "' to be executed every " +  nf.format(systemTasksConfigs[i].getPeriod()) + " ms");
	        	}
	        	catch (ClassNotFoundException e) {
	        		//TODO : shall we ignore the exception ?
	        		throw new EXistException("system task class '" + systemTasksConfigs[i].getClassName() + "' not found");
	        	}
	        	catch (InstantiationException e) {
	        		//TODO : shall we ignore the exception ?
	        		throw new EXistException("system task '" + systemTasksConfigs[i].getClassName() + "' can not be instantiated");
	        	}
	        	catch (IllegalAccessException e) {
	        		//TODO : shall we ignore the exception ?
	        		throw new EXistException("system task '" + systemTasksConfigs[i].getClassName() + "' can not be accessed");
	        	}
	        }
			//TODO : why not add a default Sync task here if there is no instanceof Sync in systemTasks ?
		}		
*/
/* TODO: end -adam- remove OLD SystemTask initialization */		
		
		//TODO : move this to initialize ? (cant as we need it for FileLockHeartBeat)
		scheduler = new Scheduler(this, conf);
		
		//TODO : since we need one :-( (see above)	
		this.isReadOnly = !canReadDataDir(conf);
		LOG.debug("isReadOnly: " + isReadOnly);
		//Configuration is valid, save it
		this.conf = conf;
		
		//TODO : in the future, we should implement an Initializable interface
		initialize();
		
		//TODO : move this to initialize ?
		//setup database synchronization job
        if (majorSyncPeriod > 0) {
        	//TODO : why not automatically register Sync in system tasks ?
//            scheduler.createPeriodicJob(2500, new Sync(), 2500);
            SyncTask syncTask = new SyncTask();
            scheduler.createPeriodicJob(2500, new SystemTaskJob(SyncTask.getJobName(), syncTask), 2500);
        }
	}
	
	//TODO : create a canReadJournalDir() method in the *relevant* class. The two directories may be different.
    protected boolean canReadDataDir(Configuration conf) throws EXistException {
        String dataDir = (String) conf.getProperty(PROPERTY_DATA_DIR);
        if (dataDir == null) 
        	dataDir = "data"; //TODO : DEFAULT_DATA_DIR

        File dir = new File(dataDir);        
        if (!dir.exists()) {
            try {
            	//TODO : shall we force the creation ? use a parameter to decide ? 
            	LOG.info("Data directory '" + dir.getAbsolutePath() + "' does not exist. Creating one ...");
                dir.mkdirs();                
            } catch (SecurityException e) {            	
                LOG.info("Cannot create data directory '" + dir.getAbsolutePath() + "'. Switching to read-only mode.");                
                return false;
            }
        }
        
    	//Save it for further use.
        //TODO : "data-dir" has sense for *native* brokers
    	conf.setProperty(PROPERTY_DATA_DIR, dataDir);
    	if (!dir.canWrite()) {            
            LOG.info("Cannot write to data directory: " + dir.getAbsolutePath() + ". Switching to read-only mode.");
            return false;
        }
        
    	// try to acquire lock on the data dir
        dataLock = new FileLock(this, dir, "dbx_dir.lck");
        
        try {
            boolean locked = dataLock.tryLock();
            if (!locked) {
                throw new EXistException("The database directory seems to be locked by another " +
                        "database instance. Found a valid lock file: " + dataLock.getFile());
            }
        } catch (ReadOnlyException e) {
            LOG.info(e.getMessage() + ". Switching to read-only mode!!!");
            return false;
        }
        return true;
    }
    
    protected SecurityManager newSecurityManager() 
    {
       try {
          Class smClass = (Class)conf.getProperty(PROPERTY_SECURITY_CLASS);
          return (SecurityManager)smClass.newInstance();
       } catch (Throwable ex) {
          LOG.warn("Exception while instantiating security manager class.", ex);
       }
       return null;
    }

    /**Initializes the database instance.
	 * @throws EXistException
	 */
	protected void initialize() throws EXistException, DatabaseConfigurationException {
        if (LOG.isDebugEnabled())
            LOG.debug("initializing database instance '" + instanceName + "'...");
        
        //Flag to indicate that we are initializing
        status = INITIALIZING;

        signalSystemStatus(SIGNAL_STARTUP);

        boolean exportOnly = (Boolean) conf.getProperty(PROPERTY_EXPORT_ONLY, false);
        		
		//REFACTOR : construct then configure
        cacheManager = new DefaultCacheManager(this);

        //REFACTOR : construct then configure
        xQueryPool = new XQueryPool(conf);
        //REFACTOR : construct then... configure
        processMonitor = new ProcessMonitor();
        xqueryStats = new PerformanceStats(this);

        //REFACTOR : construct then... configure
        xmlReaderPool = new XMLReaderPool(conf, new XMLReaderObjectFactory(this), 5, 0);
        //REFACTOR : construct then... configure
        int bufferSize = conf.getInteger(PROPERTY_COLLECTION_CACHE_SIZE);
        if(bufferSize == -1)
        	bufferSize = DEFAULT_COLLECTION_BUFFER_SIZE;
        collectionCache = new CollectionCache(this, bufferSize, 0.0001);
        collectionCacheMgr = new CollectionCacheManager(this, collectionCache);

        // compute how much memory should be reserved for caches to grow
        Runtime rt = Runtime.getRuntime();
        long maxMem = rt.maxMemory();
        long minFree = maxMem / 5;
        reservedMem = cacheManager.getTotalMem() + collectionCacheMgr.getMaxTotal() + minFree;
        LOG.debug("Reserved memory: " + reservedMem + "; max: " + maxMem + "; min: " + minFree);
        notificationService = new NotificationService();

        //REFACTOR : construct then... configure
        //TODO : journal directory *may* be different from BrokerPool.PROPERTY_DATA_DIR
        transactionManager = new TransactionManager(this, new File((String) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR)), isTransactional());		
        try {
            transactionManager.initialize();
        } catch (ReadOnlyException e) {
            LOG.warn(e.getMessage() + ". Switching to read-only mode!!!");
            isReadOnly = true;
        }

        symbols = new SymbolTable(this, conf);
        if (!symbols.getFile().canWrite()) {
            LOG.warn("Cannot write to " + symbols.getFile().getName() + ". Switching to read-only mode.");
            isReadOnly = true;
        }
        
        indexManager = new IndexManager(this, conf);

        //TODO : replace the following code by get()/release() statements ?
        // WM: I would rather tend to keep this broker reserved as a system broker.
        // create a first broker to initialize the security manager
		createBroker();
		//TODO : this broker is *not* marked as active and *might* be reused by another process ! Is it intended ?
        // at this stage, the database is still single-threaded, so reusing the broker later is not a problem.
		DBBroker broker = inactiveBrokers.peek();
        
        if (isReadOnly()) {
            transactionManager.setEnabled(false);
        }
        
		//Run the recovery process
        //TODO : assume 
        boolean recovered = false;
		if (isTransactional()) {
			recovered = transactionManager.runRecovery(broker);
            //TODO : extract the following from this block ? What if we ware not transactional ? -pb 
            if (!recovered) {
            	if (broker.getCollection(XmldbURI.ROOT_COLLECTION_URI) == null) {
            		Txn txn = transactionManager.beginTransaction();
            		try {
            			//TODO : use a root collection final member
            			broker.getOrCreateCollection(txn, XmldbURI.ROOT_COLLECTION_URI);
            			transactionManager.commit(txn);
            		} catch (IOException e) {
            			transactionManager.abort(txn);
	        		} catch (PermissionDeniedException e) {
	        			transactionManager.abort(txn);
	        		}
            	}
            }
        }

        /* initialise required collections if they dont exist yet */
		if (!exportOnly)
			initialiseSystemCollections(broker);

        //TODO : from there, rethink the sequence of calls.
        // WM: attention: a small change in the sequence of calls can break
        // either normal startup or recovery.
        
        //create the security manager
		//TODO : why only the first broker has a security manager ? Global or attached to each broker ?
        // WM: there's only one security manager per BrokerPool, but it needs a DBBroker instance to read
        // the system collection.
		SecurityManager localSecurityManager = newSecurityManager();
		securityManager = null;
		localSecurityManager.attach(this, broker);
		securityManager = localSecurityManager;
		status = OPERATING;
		//have to do this after initializing = false
		// so that the policies collection is saved
		if(securityManager.isXACMLEnabled())
			securityManager.getPDP().initializePolicyCollection();
		//Get a manager to handle further collectios configuration
        try {
            collectionConfigurationManager = new CollectionConfigurationManager(broker);
        } catch (Exception e) {
            LOG.error("Found an error while initializing database: " + e.getMessage(), e);
        }
        //If necessary, launch a task to repair the DB
        //TODO : merge this with the recovery process ?
        if (recovered) {
            try {
                broker.setUser(SecurityManager.SYSTEM_USER);
                broker.repair();
            } catch (PermissionDeniedException e) {
                LOG.warn("Error during recovery: " + e.getMessage(), e);
            }
            if (((Boolean)conf.getProperty(PROPERTY_RECOVERY_CHECK)).booleanValue()) {
                ConsistencyCheckTask task = new ConsistencyCheckTask();
                Properties props = new Properties();
                props.setProperty("backup", "no");
                props.setProperty("output", "sanity");
                task.configure(conf, props);
                task.execute(broker);
            }
        }

        //OK : the DB is repaired; let's make a few RW operations
		
        // remove temporary docs
		broker.cleanUpTempResources(true);

        sync(broker, Sync.MAJOR_SYNC);
        
		//Create a default configuration file for the root collection
		//TODO : why can't we call this from within CollectionConfigurationManager ?
		//TODO : understand why we get a test suite failure
        //collectionConfigurationManager.checkRootCollectionConfigCollection(broker);
        //collectionConfigurationManager.checkRootCollectionConfig(broker);		


/* TODO: start adam */
		
		//Schedule the system tasks	            
	    /*for (int i = 0; i < systemTasks.size(); i++) {
	    	//TODO : remove first argument when SystemTask has a getPeriodicity() method
	        initSystemTask((SingleInstanceConfiguration.SystemTaskConfig) systemTasksPeriods.get(i), (SystemTask)systemTasks.get(i));
	    }		
		systemTasksPeriods = null;*/
		
/* TODO: end adam */		

		//Create the minimal number of brokers required by the configuration 
		for (int i = 1; i < minBrokers; i++)
			createBroker();        

        // register some MBeans to provide access to this instance
        AgentFactory.getInstance().initDBInstance(this);
        
        if (LOG.isDebugEnabled())
            LOG.debug("database instance '" + instanceName + "' initialized");
        
		
        //setup any configured jobs
        //scheduler.setupConfiguredJobs();
        
        //execute any startup jobs
        //scheduler.executeStartupJobs();
        
        scheduler.run();
	}
	    
	//TODO : remove the period argument when SystemTask has a getPeriodicity() method
	//TODO : make it protected ?
    /*private void initSystemTask(SingleInstanceConfiguration.SystemTaskConfig config, SystemTask task) throws EXistException {
        try {
            if (config.getCronExpr() == null) {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() + " every " +
                        config.getPeriod() + " ms");
                scheduler.createPeriodicJob(config.getPeriod(), new SystemTaskJob(task), config.getPeriod());
            } else {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() +
                        " with cron expression: " + config.getCronExpr());
                scheduler.createCronJob(config.getCronExpr(), new SystemTaskJob(task));
            }
        } catch (Exception e) {
			LOG.warn(e.getMessage(), e);
            throw new EXistException("Failed to initialize system maintenance task: " + e.getMessage());
        }
    }*/

     /**
     * Initialise system collections, if it doesnt exist yet
     *
     * @param sysBroker The system broker from before the brokerpool is populated
     * @param sysCollectionUri XmldbURI of the collection to create
     * @param permissions The persmissions to set on the created collection
     */
    private void initialiseSystemCollection(DBBroker sysBroker, XmldbURI sysCollectionUri, int permissions) throws EXistException {
        //create /db/system
        Collection sysCollection = sysBroker.getCollection(sysCollectionUri);
        if (sysCollection == null)
        {
            TransactionManager transact = getTransactionManager();
            Txn txn = transact.beginTransaction();
            try {
                sysCollection = sysBroker.getOrCreateCollection(txn, sysCollectionUri);
                if (sysCollection == null)
                    throw new IOException("Could not create system collection: " + sysCollectionUri);
                sysCollection.setPermissions(permissions);
                sysBroker.saveCollection(txn, sysCollection);

                transact.commit(txn);
            } catch (Exception e) {
                transact.abort(txn);
                e.printStackTrace();
                String msg = "Initialisation of system collections failed: " + e.getMessage();
                LOG.error(msg, e);
                throw new EXistException(msg, e);
            }
        }
    }

    /**
     * Initialise required system collections, if they dont exist yet
     *
     * @param sysBroker - The system broker from before the brokerpool is populated
     *
     * @throws EXistException If a system collection cannot be created
     */
    private void initialiseSystemCollections(DBBroker sysBroker) throws EXistException
    {
        //create /db/system
        initialiseSystemCollection(sysBroker, XmldbURI.SYSTEM_COLLECTION_URI, 0770);
    }

    public long getReservedMem() {
        return reservedMem - cacheManager.getSizeInBytes();
    }

    public int getPageSize() {
        return pageSize;
    }

    public void signalSystemStatus(String signal) {
        if (System.currentTimeMillis() > nextSystemStatus) {
            setChanged();
            notifyObservers(signal);
            nextSystemStatus = System.currentTimeMillis() + 10000;
        }
    }

    /**
	 * Whether or not the database instance is being initialized. 
	 * 
	 * @return <code>true</code> is the database instance is being initialized
	 */
	//	TODO : let's be positive and rename it as isInitialized ? 
	public boolean isInitializing() {
		return status == INITIALIZING;
	}

    /** Returns the database instance's name.
     * @return The id
     */
	//TODO : rename getInstanceName
    public String getId() {
    	return instanceName;
    }    

	/**
	 *  Returns the number of brokers currently serving requests for the database instance. 
	 *
	 *@return The brokers count
	 */
    //TODO : rename as getActiveBrokers ?
	public int active() {
		return activeBrokers.size();
	}

    public Map<Thread, DBBroker> getActiveBrokers() {
        return new HashMap<Thread, DBBroker>(activeBrokers);
    }

    /**
	 * Returns the number of inactive brokers for the database instance.
	 *@return The brokers count
	 */
	//TODO : rename as getInactiveBrokers ?
	public int available() {
		return inactiveBrokers.size();
	}
	
	//TODO : getMin() method ?
	
	/**
	 *  Returns the maximal number of brokers for the database instance.
	 *
	 *@return The brokers count
	 */
	//TODO : rename as getMaxBrokers ?
	public int getMax() {
		return maxBrokers;
	}	
	 
	/**
	 * Returns whether the database instance has been configured.
	 *
	 *@return <code>true</code> if the datbase instance is configured
	 */
	public final boolean isInstanceConfigured() {
		return conf != null;
	}	
	
	/**
	 * Returns the configuration object for the database instance.
	 *@return The configuration
	 */
	public Configuration getConfiguration() {
		return conf;
	}	

    //TODO : rename as setShutdwonListener ?
	public void registerShutdownListener(ShutdownListener listener) {
		//TODO : check that we are not shutting down
		shutdownListener = listener;
	}

    public NodeIdFactory getNodeFactory() {
        return nodeFactory;
    }

     /**
     *  Returns the database instance's security manager
     *
     *@return    The security manager
     */   
    public SecurityManager getSecurityManager() {
    	return securityManager;
    }
    
    
    /** Returns the Scheduler
     * @return The scheduler
     */
    public Scheduler getScheduler() {
    	return scheduler;
    }

    public SymbolTable getSymbols() {
        return symbols;
    }
    
    public NotificationService getNotificationService() {
    	return notificationService;
    }
    
    /**
     * Returns whether transactions can be handled by the database instance.
     * 
     * @return <code>true</code> if transactions can be handled
     */
    public boolean isTransactional() {
    	//TODO : confusion between dataDir and a so-called "journalDir" !
        return !isReadOnly && transactionsEnabled;
    }
	
    public boolean isReadOnly() {
        return isReadOnly;
    }

    public void setReadOnly() {
    	isReadOnly = true;
    }
    
    public boolean isInServiceMode() {
        return inServiceMode;
    }

    public TransactionManager getTransactionManager() {
        return this.transactionManager;
    }
    
    /** 
     * Returns a manager for accessing the database instance's collection configuration files.
     * @return The manager
     */
    public CollectionConfigurationManager getConfigurationManager() {
        return collectionConfigurationManager;
    }        

    /**
     * Returns a cache in which the database instance's collections are stored.
     * 
     * @return The cache
	 */
    //TODO : rename as getCollectionCache ?
	public CollectionCache getCollectionsCache() {		
		return collectionCache;
	}
	
	/**
     * Returns a cache in which the database instance's may store items.
     * 
     * @return The cache
	 */	
    public DefaultCacheManager getCacheManager() {
        return cacheManager;
    }

    public CollectionCacheManager getCollectionCacheMgr() {
        return collectionCacheMgr;
    }

    /**
     * Returns the index manager which handles all additional indexes not
     * being part of the database core.
     * 
     * @return The IndexManager
     */
    public IndexManager getIndexManager() {
        return indexManager;
    }

    /**
     * Returns a pool in which the database instance's <strong>compiled</strong> XQueries are stored.
     * 
     * @return The pool
     */
    public XQueryPool getXQueryPool() {
        return xQueryPool;
    }

    /**
     * Returns a monitor in which the database instance's <strong>running</strong> XQueries are managed.
     * 
     * @return The monitor
     */
    public ProcessMonitor getProcessMonitor() {
    	return processMonitor;
    }

    /**
     * Returns the global profiler used to gather execution statistics
     * from all XQueries running on this db instance.
     *
     * @return the profiler
     */
    public PerformanceStats getPerformanceStats() {
        return xqueryStats;
    }

	/**
     * Returns a pool in which the database instance's readers are stored.
     * 
     * @return The pool
	 */
	public XMLReaderPool getParserPool() {
		return xmlReaderPool;
	}    
    
    /**
     * Returns the global update lock for the database instance.
     * This lock is used by XUpdate operations to avoid that
     * concurrent XUpdate requests modify the database until all
     * document locks have been correctly set.
     *  
     * @return The global lock
     */
	//TODO : rename as getUpdateLock ?
    public Lock getGlobalUpdateLock() {
        return globalXUpdateLock;
    } 
    
    /** Creates an inactive broker for the database instance.
     * @return The broker
     * @throws EXistException
     */
    protected DBBroker createBroker() throws EXistException {
    	//TODO : in the future, don't pass the whole configuration, just the part relevant to brokers
		DBBroker broker = BrokerFactory.getInstance(this, this.getConfiguration());
		inactiveBrokers.push(broker);
		brokersCount++;
		broker.setId(broker.getClass().getName() + '_' + instanceName + "_" + brokersCount);
		LOG.debug(
			"created broker '" + broker.getId() + " for database instance '" + instanceName + "'");		
		return broker;
	}

	/** Returns an active broker for the database instance.
	 * @return The broker
	 * @throws EXistException If the instance is not available (stopped or not configured)
	 */
    //TODO : rename as getBroker ? getInstance (when refactored) ?
	public DBBroker get(User user) throws EXistException {
		if (!isInstanceConfigured()) {		
			throw new EXistException("database instance '" + instanceName + "' is not available");
		}

		synchronized(this) {
			//Try to get an active broker
			DBBroker broker = activeBrokers.get(Thread.currentThread());
			//Use it...
			//TOUNDERSTAND (pb) : why not pop a broker from the inactive ones rather than maintaining reference counters ?
	        // WM: a thread may call this more than once in the sequence of operations, i.e. calls to get/release can
	        // be nested. Returning a new broker every time would lead to a deadlock condition if two threads have
	        // to wait for a broker to become available. We thus use reference counts and return
	        // the same broker instance for each thread.
			if(broker != null) {
				//increase its number of uses
				broker.incReferenceCount();
	            if (user != null)
	                broker.setUser(user);
	            return broker;
				//TODO : share the code with what is below (including notifyAll) ?
	            // WM: notifyAll is not necessary if we don't have to wait for a broker.
			}
			
			//No active broker : get one ASAP
		
            while (serviceModeUser != null && user != null && !user.equals(serviceModeUser)) {
                try {
                    LOG.debug("Db instance is in service mode. Waiting for db to become available again ...");
                    wait();
                } catch (InterruptedException e) {
                }
            }
            //Are there any available brokers ?
			if (inactiveBrokers.isEmpty()) {
				//There are no available brokers. If allowed... 
				if (brokersCount < maxBrokers)
					//... create one
					createBroker();
				else
					//... or wait until there is one available
					while (inactiveBrokers.isEmpty()) {
						LOG.debug("waiting for a broker to become available");
						try {
							this.wait();
						} catch (InterruptedException e) {
						}
					}
			}
			broker = inactiveBrokers.pop();
			//activate the broker
			activeBrokers.put(Thread.currentThread(), broker);
			broker.incReferenceCount();
            if (user != null)
                broker.setUser(user);
            //Inform the other threads that we have a new-comer
            // TODO: do they really need to be informed here???????
            this.notifyAll();
			return broker;
		}
	}

	/**
	 * Releases a broker for the database instance. If it is no more used, make if invactive.
	 * If there are pending system maintenance tasks,
	 * the method will block until these tasks have finished. 
	 * 
	 *@param  broker  The broker to be released
	 */
	//TODO : rename as releaseBroker ? releaseInstance (when refactored) ?
	public void release(DBBroker broker) {
		//TODO : Is this test accurate ?
        // might be null as release() is often called within a finally block
		if (broker == null)
			return;
		
		synchronized (this) {
			//TOUNDERSTAND (pb) : why maintain reference counters rather than pushing the brokers to the stack ?
			//TODO : first check that the broker is active ! If not, return immediately.
			broker.decReferenceCount();
			if(broker.getReferenceCount() > 0) {
				//it is still in use and thus can't be marked as inactive
				return;  
			}
			
	        //Broker is no more used : inactivate it
		
            for (DBBroker inactiveBroker : inactiveBrokers) {
                if (broker == inactiveBroker) {
                    LOG.error("Broker is already in the inactive list!!!");
                    return;
                }
            }
			
	        if (activeBrokers.remove(Thread.currentThread())==null) {
	             LOG.error("release() has been called from the wrong thread for broker "+broker.getId());
	             // Cleanup the state of activeBrokers
	             for (Thread t : activeBrokers.keySet()) {
	                if (activeBrokers.get(t)==broker) {
	                     activeBrokers.remove(t);
	                     break;
	                }
	             }
	         }
			inactiveBrokers.push(broker);
			//If the database is now idle, do some useful stuff
			if(activeBrokers.isEmpty()) {
				//TODO : use a "clean" dedicated method (we have some below) ?
				if (syncRequired) {
					//Note that the broker is not yet really inactive ;-)
					sync(broker, syncEvent);
					this.syncRequired = false;
                    this.checkpoint = false;
				}				
                if (serviceModeUser != null && !broker.getUser().equals(serviceModeUser)) {
                    inServiceMode = true;
                }
            }
			//Inform the other threads that someone is gone
			this.notifyAll();
		}
	}

    public DBBroker enterServiceMode(User user) throws PermissionDeniedException {
        if (!user.hasDbaRole())
            throw new PermissionDeniedException("Only users of group dba can switch the db to service mode");
        serviceModeUser = user;
        synchronized (this) {
            if (!activeBrokers.isEmpty()) {
                while(!inServiceMode) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        inServiceMode = true;
        DBBroker broker = inactiveBrokers.peek();
        checkpoint = true;
        sync(broker, Sync.MAJOR_SYNC);
        checkpoint = false;
        // Return a broker that can be used to perform system tasks
        return broker;
    }

    public void exitServiceMode(User user) throws PermissionDeniedException {
        if (!user.equals(serviceModeUser))
            throw new PermissionDeniedException("The db has been locked by a different user");
        serviceModeUser = null;
        inServiceMode = false;
        synchronized (this) {
            this.notifyAll();
        }
    }

    /**
	 * Reloads the security manager of the database instance. This method is 
         * called for example when the <code>users.xml</code> file has been changed.
	 * 
	 * @param broker A broker responsible for executing the job
         *
         *  TOUNDERSTAND (pb) : why do we need a broker here ? Why not get and 
         *  release one when we're done?
         *  WM: this is called from the Collection.store() methods to signal 
         *  that /db/system/users.xml has changed.
         *  A broker is already available in these methods, so we use it here.
         */
	public void reloadSecurityManager(DBBroker broker) {
		securityManager = newSecurityManager();
		securityManager.attach(this, broker);
		LOG.debug("Security manager reloaded");
	}

	public long getMajorSyncPeriod()
	{
		return majorSyncPeriod;
	}
	
	public long getLastMajorSync()
	{
		return lastMajorSync;
	}
	
    /**
     * Executes a waiting cache synchronization for the database instance.
	 * @param broker A broker responsible for executing the job 
	 * @param syncEvent One of {@link org.exist.storage.sync.Sync#MINOR_SYNC} or {@link org.exist.storage.sync.Sync#MINOR_SYNC}
	 */
	//TODO : rename as runSync ? executeSync ?
	//TOUNDERSTAND (pb) : *not* synchronized, so... "executes" or, rather, "schedules" ? "executes" (WM)
	//TOUNDERSTAND (pb) : why do we need a broker here ? Why not get and release one when we're done ?
    // WM: the method will always be under control of the BrokerPool. It is guaranteed that no
    // other brokers are active when it is called. That's why we don't need to synchronize here.
	//TODO : make it protected ?
	public void sync(DBBroker broker, int syncEvent) {
		broker.sync(syncEvent);
		User user = broker.getUser();
		//TODO : strange that it is set *after* the sunc method has been called.
		broker.setUser(SecurityManager.SYSTEM_USER);
        if (status != SHUTDOWN)
            broker.cleanUpTempResources();
        if (syncEvent == Sync.MAJOR_SYNC) {
            try {
                if (!FORCE_CORRUPTION)
                    transactionManager.checkpoint(checkpoint);
            } catch (TransactionException e) {
                LOG.warn(e.getMessage(), e);
            }
            cacheManager.checkCaches();
            lastMajorSync = System.currentTimeMillis();
            if (LOG.isDebugEnabled())
            	notificationService.debug();
        } else {
            cacheManager.checkDistribution();
        }
        //TODO : touch this.syncEvent and syncRequired ?
	
        //After setting the SYSTEM_USER above we must change back to the DEFAULT User to prevent a security problem
        //broker.setUser(User.DEFAULT);
        broker.setUser(user);
	}
	
	/**
	 * Schedules a cache synchronization for the database instance. If the database instance is idle,
	 * the cache synchronization will be run immediately. Otherwise, the task will be deffered 
	 * until all running threads have returned.
	 * @param syncEvent One of {@link org.exist.storage.sync.Sync#MINOR_SYNC} or 
         * {@link org.exist.storage.sync.Sync#MINOR_SYNC}   
	 */
	public void triggerSync(int syncEvent) {
		//TOUNDERSTAND (pb) : synchronized, so... "schedules" or, rather, "executes" ? "schedules" (WM)
        if (status == SHUTDOWN)
            return;
        LOG.debug("Triggering sync: " + syncEvent);
        synchronized (this) {
			//Are there available brokers ?
		    // TOUNDERSTAND (pb) : the trigger is ignored !
            // WM: yes, it seems wrong!!
//			if(inactiveBrokers.size() == 0)
//				return;
			//TODO : switch on syncEvent and throw an exception if it is inaccurate ?
			//Is the database instance idle ?
			if (inactiveBrokers.size() == brokersCount) {
				//Borrow a broker
				//TODO : this broker is *not* marked as active and may be reused by another process !
                // No other brokers are running at this time, so there's no risk.
				//TODO : use get() then release the broker ?
                // No, might lead to a deadlock.
				DBBroker broker = inactiveBrokers.pop();
				//Do the synchonization job
				sync(broker, syncEvent);
            inactiveBrokers.push(broker);
				syncRequired = false;
			} else {
				//Put the synchonization job into the queue
				//TODO : check that we don't replace high priority Sync.MAJOR_SYNC by a lesser priority sync !
				this.syncEvent = syncEvent;
				syncRequired = true;
			}
		}
	}
   
	/**
	 * Schedules a system maintenance task for the database instance. If the database is idle,
	 * the task will be run immediately. Otherwise, the task will be deffered 
	 * until all running threads have returned.   
     * @param task The task
     */
    //TOUNDERSTAND (pb) : synchronized, so... "schedules" or, rather, "executes" ?
    public void triggerSystemTask(SystemTask task) {    	
    	transactionManager.triggerSystemTask(task);
    }
    
	/**
	 * Shuts downs the database instance
	 */
	public void shutdown() {
		shutdown(false);
	}
	
	public boolean isShuttingDown()
	{
		return(status == SHUTDOWN);
	}
	
	/**
	 * Shuts downs the database instance
	 * @param killed <code>true</code> when the JVM is (cleanly) exiting
	 */
	public void shutdown(boolean killed) {
        if (status == SHUTDOWN)
            // we are already shut down
            return;

        LOG.info("Database is shutting down ...");

        status = SHUTDOWN;
        processMonitor.stopRunningJobs();
        java.util.concurrent.locks.Lock lock = transactionManager.getLock();
        try {
            // wait for currently running system tasks before we shutdown
            // they will have a lock on the transactionManager
            lock.lock();

            synchronized (this) {

                // release transaction log to allow remaining brokers to complete
                // their job
                lock.unlock();

                notificationService.debug();

                //Notify all running tasks that we are shutting down

                //Shutdown the scheduler
                scheduler.shutdown(false); 	//asynchronous

                while(!scheduler.isShutdown()) 	//wait for shutdown
                {
                    try
                    {
                        wait(250);
                    }
                    catch(InterruptedException e) {}
                    signalSystemStatus(SIGNAL_SHUTDOWN);
                }

                //Notify all running XQueries that we are shutting down
                processMonitor.killAll(500);
                //TODO : close other objects using varying methods ? set them to null ?
                //cacheManager.something();
                //xQueryPool.something();
                //collectionConfigurationManager.something();
                //collectionCache.something();
                //xmlReaderPool.close();

                if (isTransactional())
                    transactionManager.getJournal().flushToLog(true, true);

                boolean hangingThreads = false;
                long waitStart = System.currentTimeMillis();
                //Are there active brokers ?
                if (!activeBrokers.isEmpty()) {
                    LOG.info("Waiting " + maxShutdownWait + "ms for remaining threads to shut down...");
                    while (!activeBrokers.isEmpty()) {
                        try {
                            //Wait until they become inactive...
                            this.wait(1000);
                        } catch (InterruptedException e) {
                        }
                        signalSystemStatus(SIGNAL_SHUTDOWN);
                        //...or force the shutdown
                        if(maxShutdownWait > -1 && System.currentTimeMillis() - waitStart > maxShutdownWait) {
                            LOG.warn("Not all threads returned. Forcing shutdown ...");
                            hangingThreads = true;
                            break;
                        }
                    }
                }
                LOG.debug("Calling shutdown ...");
                
                // closing down external indexes
                try {
                    indexManager.shutdown();
                } catch (DBException e) {
                    LOG.warn("Error during index shutdown: " + e.getMessage(), e);
                }

                //TODO : replace the following code by get()/release() statements ?
                // WM: deadlock risk if not all brokers returned properly.
                DBBroker broker = null;
                if (inactiveBrokers.isEmpty())
                    try {
                        broker = createBroker();
                    } catch (EXistException e) {
                        LOG.warn("could not create instance for shutdown. Giving up.");
                    }
                else
                    //TODO : this broker is *not* marked as active and may be reused by another process !
                    //TODO : use get() then release the broker ?
                    // WM: deadlock risk if not all brokers returned properly.
                    broker = inactiveBrokers.peek();

                //TOUNDERSTAND (pb) : shutdown() is called on only *one* broker ?
                // WM: yes, the database files are shared, so only one broker is needed to close them for all
                if (broker != null) {
                    broker.setUser(SecurityManager.SYSTEM_USER);
                    broker.shutdown();
                }
                collectionCacheMgr.deregisterCache(collectionCache);

                signalSystemStatus(SIGNAL_SHUTDOWN);

                transactionManager.shutdown();
//                if (hangingThreads)
//                    // do not write a checkpoint if some threads did not return before shutdown
//                    // there might be dirty transactions
//                    transactionManager.shutdown(false);
//                else
//                    transactionManager.shutdown(true);

                // deregister JMX MBeans
                AgentFactory.getInstance().closeDBInstance(this);

                //Invalidate the configuration
                conf = null;
                //Clear the living instances container
                instances.remove(instanceName);

                if (!isReadOnly)
                    // release the lock on the data directory
                    dataLock.release();

                LOG.info("shutdown complete !");

                //Last instance closes the house...
                //TOUNDERSTAND (pb) : !killed or, rather, killed ?
                // TODO: WM: check usage of killed!
                if(instances.size() == 0 && !killed) {
                    LOG.debug("removing shutdown hook");
                    Runtime.getRuntime().removeShutdownHook(shutdownHook);
                }
                if (shutdownListener != null)
                    shutdownListener.shutdown(instanceName, instances.size());
            }
        } finally {
            // clear instance variables, just to be sure they will be garbage collected
            // the test suite restarts the db a few hundred times
            transactionManager = null;
            collectionCache = null;
            collectionCacheMgr = null;
            xQueryPool = null;
            processMonitor = null;
            collectionConfigurationManager = null;
            notificationService = null;
            indexManager = null;
            scheduler = null;
            xmlReaderPool = null;
            shutdownListener = null;
            securityManager = null;
            notificationService = null;
        }
	}

	//TODO : move this elsewhere
    public void triggerCheckpoint() {
        if (syncRequired)
            return;
        synchronized (this) {
            syncEvent = Sync.MAJOR_SYNC;
            syncRequired = true;
            checkpoint = true;
        }
    }
    
    private Debuggee debuggee = null;
    
    public Debuggee getDebuggee() {
    	synchronized (this) {
    		if (debuggee == null)
    			debuggee = DebuggeeFactory.getInstance(); 
    	}
    	
    	return debuggee;
    }

    public Calendar getStartupTime() {
        return startupTime;
    }
    
}
