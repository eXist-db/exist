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

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.TreeMap;
import java.util.Vector;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.indexing.IndexManager;
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
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.ReadOnlyException;
import org.exist.util.SingleInstanceConfiguration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.util.XMLReaderPool;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmldb.XmldbURI;
/**
 * This class controls all available instances of the database.
 * Use it to configure, start and stop database instances. 
 * You may have multiple instances defined, each using its own configuration. 
 * To define multiple instances, pass an identification string to {@link #configure(String, int, int, Configuration)}
 * and use {@link #getInstance(String)} to retrieve an instance.
 *
 *@author  Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
//TODO : in the future, separate the design between the Map of DBInstances and their non static implementation 
public class BrokerPool {

	private final static Logger LOG = Logger.getLogger(BrokerPool.class);
	
	private final static TreeMap instances = new TreeMap();
	
	/**
	 * The name of a default database instance for those who are too lazy to provide parameters ;-). 
	 */	
	public final static String DEFAULT_INSTANCE_NAME = "exist";		

	public static final String CONFIGURATION_ELEMENT_NAME = "pool";

	//Various configuration property keys (set by the configuration manager)
	public static final String PROPERTY_DATA_DIR = "db-connection.data-dir";
	public final static String PROPERTY_MIN_CONNECTIONS = "db-connection.pool.min";
	public final static String PROPERTY_MAX_CONNECTIONS = "db-connection.pool.max";
	public final static String PROPERTY_SYNC_PERIOD = "db-connection.pool.sync-period";
	public final static String PROPERTY_SHUTDOWN_DELAY = "wait-before-shutdown";
	public final static String PROPERTY_COLLECTION_CACHE_SIZE = "db-connection.collection-cache-size";
	public final static String PROPERTY_SECURITY_CLASS = "db-connection.security.class";
	
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
	public final int DEFAULT_COLLECTION_BUFFER_SIZE = 128;
	
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
	private Stack inactiveBrokers = new Stack();
	
	/**
	 * The number of active brokers for the database instance 
	 */	
	private Map activeBrokers = new HashMap();
	
	/** The configuration object for the database instance
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
    
	/**
	 * The system maintenance tasks of the database instance.
	 */
    //TODO : maybe not the most appropriate container...
    // WM: yes, only used in initialization. Don't need a synchronized collection here
    private List systemTasks = new ArrayList();
    //TODO : remove when SystemTask has a getPeriodicity() method
    private Vector systemTasksPeriods = new Vector();
	
	/**
	 * The pending system maintenance tasks of the database instance.
	 */
	private Stack waitingSystemTasks = new Stack();

	/**
	 * The cache in which the database instance may store items.
	 */	
	
	private DefaultCacheManager cacheManager;

    private CollectionCacheManager collectionCacheMgr;

    /**
	 * The pool in which the database instance's <strong>compiled</strong> XQueries are stored.
	 */
	private XQueryPool xQueryPool;
	
	/**
	 * The monitor in which the database instance's strong>running</strong> XQueries are managed.
	 */
	private XQueryMonitor xQueryMonitor;

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
		//TODO : sanity check : the synch period should be reasonible
		LOG.info("database instance '" + instanceName + "' will be synchronized every " + nf.format(/*this.*/majorSyncPeriod) + " ms");

		//TODO : move this to initialize ?
		scheduler = new Scheduler(this, conf);

		aLong = (Long) conf.getProperty("db-connection.pool.shutdown-wait");		
		if (aLong != null) {
			this.maxShutdownWait = aLong.longValue();			
		}
		//TODO : sanity check : the shutdown period should be reasonible
		LOG.info("database instance '" + instanceName + "' will wait  " + nf.format(this.maxShutdownWait) + " ms during shutdown");

		aBoolean = (Boolean) conf.getProperty("db-connection.recovery.enabled");
		if (aBoolean != null) {
			this.transactionsEnabled = aBoolean.booleanValue();
        }
		LOG.info("database instance '" + instanceName + "' is enabled for transactions : " + this.transactionsEnabled);
		
		//How ugly : needs refactoring...
		Configuration.SystemTaskConfig systemTasksConfigs[] = (Configuration.SystemTaskConfig[]) conf.getProperty("db-connection.system-task-config");
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
		
		//TODO : since we need one :-( (see above)	
		this.isReadOnly = !canReadDataDir(conf);
		LOG.debug("isReadOnly: " + isReadOnly);
		//Configuration is valid, save it
		this.conf = conf;
		
		//TODO : in the future, we should implement an Initializable interface
		initialize();
		
		//setup any configured jobs for the scheduler
		scheduler.setupConfiguredJobs(conf);
		
		//TODO : move this to initialize ?
        if (majorSyncPeriod > 0) {
        	//TODO : why not automatically register Sync in system tasks ?
            scheduler.createPeriodicJob(2500, new Sync(), false);
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
        
		//REFACTOR : construct then configure
        cacheManager = new DefaultCacheManager(conf);

        //REFACTOR : construct then configure
        xQueryPool = new XQueryPool(conf);
        //REFACTOR : construct then... configure
        xQueryMonitor = new XQueryMonitor();
        //REFACTOR : construct then... configure
        xmlReaderPool = new XMLReaderPool(new XMLReaderObjectFactory(this), 5, 0);
        //REFACTOR : construct then... configure
        int bufferSize = conf.getInteger(PROPERTY_COLLECTION_CACHE_SIZE);
        if(bufferSize == -1)
        	bufferSize = DEFAULT_COLLECTION_BUFFER_SIZE;
        collectionCache = new CollectionCache(this, bufferSize, 0.001);
        collectionCacheMgr = new CollectionCacheManager(conf, collectionCache);
        
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

        indexManager = new IndexManager(this, conf);
        
        //TODO : replace the following code by get()/release() statements ?
        // WM: I would rather tend to keep this broker reserved as a system broker.
        // create a first broker to initialize the security manager
		createBroker();
		//TODO : this broker is *not* marked as active and *might* be reused by another process ! Is it intended ?
        // at this stage, the database is still single-threaded, so reusing the broker later is not a problem.
		DBBroker broker = (DBBroker) inactiveBrokers.peek();
        
        if (broker.isReadOnly()) {
            transactionManager.setEnabled(false);
            isReadOnly = true;
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
		collectionConfigurationManager = new CollectionConfigurationManager(broker);
        //If necessary, launch a task to repair the DB
        //TODO : merge this with the recovery process ?
        if (recovered) {
            try {
                broker.setUser(SecurityManager.SYSTEM_USER);
                broker.repair();
            } catch (PermissionDeniedException e) {
                LOG.warn("Error during recovery: " + e.getMessage(), e);
            }
        }

        //OK : the DB is repaired; let's make a few RW operations
		
        // remove temporary docs
		broker.cleanUpTempResources(true);

		//Create a default configuration file for the root collection
		//TODO : why can't we call this from within CollectionConfigurationManager ?
		//TODO : understand why we get a test suite failure
        //collectionConfigurationManager.checkRootCollectionConfigCollection(broker);
        //collectionConfigurationManager.checkRootCollectionConfig(broker);		

		//Schedule the system tasks	            
	    for (int i = 0; i < systemTasks.size(); i++) {
	    	//TODO : remove first argument when SystemTask has a getPeriodicity() method
	        initSystemTask((SingleInstanceConfiguration.SystemTaskConfig) systemTasksPeriods.get(i), (SystemTask)systemTasks.get(i));
	    }		
		systemTasksPeriods = null;

		//Create the minimal number of brokers required by the configuration 
		for (int i = 1; i < minBrokers; i++)
			createBroker();        
        
        if (LOG.isDebugEnabled())
            LOG.debug("database instance '" + instanceName + "' initialized");
	}
	    
	//TODO : remove the period argument when SystemTask has a getPeriodicity() method
	//TODO : make it protected ?
    private void initSystemTask(SingleInstanceConfiguration.SystemTaskConfig config, SystemTask task) throws EXistException {
        try {
            if (config.getCronExpr() == null) {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() + " every " +
                        config.getPeriod() + " ms");
                scheduler.createPeriodicJob(config.getPeriod(), new SystemTaskJob(task), false);
            } else {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() +
                        " with cron expression: " + config.getCronExpr());
                scheduler.createCronJob(config.getCronExpr(), new SystemTaskJob(task));
            }
        } catch (Exception e) {
			LOG.warn(e.getMessage(), e);
            throw new EXistException("Failed to initialize system maintenance task: " + e.getMessage());
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
    public XQueryMonitor getXQueryMonitor() {
    	return xQueryMonitor;
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
		if (!isInstanceConfigured())			
			throw new EXistException("database instance '" + instanceName + "' is not available");
		
		//Try to get an active broker
		DBBroker broker = (DBBroker)activeBrokers.get(Thread.currentThread());
		//Use it...
		//TOUNDERSTAND (pb) : why not pop a broker from the inactive ones rather than maintaining reference counters ?
        // WM: a thread may call this more than once in the sequence of operations, i.e. calls to get/release can
        // be nested. Returning a new broker every time would lead to a deadlock condition if two threads have
        // to wait for a broker to become available. We thus use reference counts and return
        // the same broker instance for each thread.
		if(broker != null) {
			//increase its number of uses
			broker.incReferenceCount();
			return broker;
			//TODO : share the code with what is below (including notifyAll) ?
            // WM: notifyAll is not necessary if we don't have to wait for a broker.
		}
		//No active broker : get one ASAP
		synchronized(this) {
            while (serviceModeUser != null && !user.equals(serviceModeUser)) {
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
			broker = (DBBroker) inactiveBrokers.pop();			
			//activate the broker
			activeBrokers.put(Thread.currentThread(), broker);
			broker.incReferenceCount();
            broker.setUser(user);
            //Inform the other threads that we have a new-comer
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
		//TOUNDERSTAND (pb) : why maintain reference counters rather than pushing the brokers to the stack ?
		//TODO : first check that the broker is active ! If not, return immediately.
		broker.decReferenceCount();
		if(broker.getReferenceCount() > 0) {
			//it is still in use and thus can't be marked as inactive
			return;  
		}		
		//Broker is no more used : inactivate it
		synchronized (this) {
		    activeBrokers.remove(Thread.currentThread());
			inactiveBrokers.push(broker);
			//If the database is now idle, do some useful stuff
			if(activeBrokers.size() == 0) {
				//TODO : use a "clean" dedicated method (we have some below) ?
				if (syncRequired) {
					//Note that the broker is not yet really inactive ;-)
					sync(broker, syncEvent);
					this.syncRequired = false;
                    this.checkpoint = false;
				}				
                processWaitingTasks(broker);
                if (serviceModeUser != null && !broker.getUser().equals(serviceModeUser)) {
                    inServiceMode = true;
                }
            }
			//Inform the other threads that someone is gone
			this.notifyAll();
		}
	}

    public void enterServiceMode(User user) throws PermissionDeniedException {
        if (!user.hasDbaRole())
            throw new PermissionDeniedException("Only users of group dba can switch the db to service mode");
        serviceModeUser = user;
        synchronized (this) {
            if (activeBrokers.size() != 0) {
                while(!inServiceMode) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
        inServiceMode = true;
        DBBroker broker = (DBBroker) inactiveBrokers.peek();
        checkpoint = true;
        sync(broker, Sync.MAJOR_SYNC);
        checkpoint = false;
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
	private void sync(DBBroker broker, int syncEvent) {
		broker.sync(syncEvent);
		User user = broker.getUser();
		//TODO : strange that it is set *after* the sunc method has been called.
		broker.setUser(SecurityManager.SYSTEM_USER);
        if (status != SHUTDOWN)
            broker.cleanUpTempResources();
        if (syncEvent == Sync.MAJOR_SYNC){
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
        } else
            cacheManager.checkDistribution();
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
				DBBroker broker = (DBBroker) inactiveBrokers.peek();
				//Do the synchonization job
				sync(broker, syncEvent);
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
     * Executes a system maintenance task for the database instance. The database will be stopped
     * during its execution (TODO : how ?).
     * @param broker A broker responsible for executing the task 
     * @param task The task
     */
	//TODO : rename as executeSystemTask ?
	//TOUNDERSTAND (pb) : *not* synchronized, so... "executes" or, rather, "schedules" ?
	// WM: no other brokers will be running when this method is called, so there's no need to synchronize.
    //TOUNDERSTAND (pb) : why do we need a broker here ? Why not get and release one when we're done ?
    // WM: get/release may lead to deadlock!
	//TODO : make it protected ?
    private void runSystemTask(DBBroker broker, SystemTask task) {
    	try {
    		//Flush everything
    		//TOUNDERSTAND (pb) : are we sure that this sync will be executed (see comments above) ?
            // WM: tried to fix it
    		sync(broker, Sync.MAJOR_SYNC);
            LOG.debug("Running system maintenance task: " + task.getClass().getName());
    		task.execute(broker);
    	} catch(EXistException e) {
    		LOG.warn("System maintenance task reported error: " + e.getMessage(), e);
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
    	synchronized(this) {
    		//Are there available brokers ?
    	    // TOUNDERSTAND (pb) : the trigger is ignored !
            // WM: yes, commented out
//    		if(inactiveBrokers.size() == 0)
//    			return;
			//TODO : check task and throw an exception if inaccurate
			//Is the database instance idle ?    		
    		if(inactiveBrokers.size() == brokersCount) {
    			//Borrow a broker
    			//TODO : this broker is *not* marked as active and may be reused by another process !
                // WM: No other broker will be running at this point
    			//TODO : use get() then release the broker ? WM: deadlock risk here!
    			DBBroker broker = (DBBroker) inactiveBrokers.peek();
    			//Do the job
    			runSystemTask(broker, task);
    		} else
    			//Put the task into the queue
                waitingSystemTasks.push(task);
    	}
    }
    
    /**
     * Executes waiting system maintenance tasks for the database instance.
     * @param broker A broker responsible for executing the task
     */
    //TOUNDERSTAND (pb) : *not* synchronized, so... "executes" or, rather, "schedules" ?
    //TOUNDERSTAND (pb) : why do we need a broker here ? Why not get and release one when we're done ?
    // WM: same as above: no other broker is active while we are calling this 
    //TODO : make it protected ?
    private void processWaitingTasks(DBBroker broker) {
        while (!waitingSystemTasks.isEmpty()) {
            SystemTask task = (SystemTask) waitingSystemTasks.pop();
            runSystemTask(broker, task);
        }
    }   

	/**
	 * Shuts downs the database instance
	 */
	public synchronized void shutdown() {
		shutdown(false);
	}
	
	/**
	 * Shuts downs the database instance
	 * @param killed <code>true</code> when the JVM is (cleanly) exiting
	 */
	public synchronized void shutdown(boolean killed) {
        if (status == SHUTDOWN)
            // we are already shut down
            return;
        
        status = SHUTDOWN;
        
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
		}

		//Notify all running XQueries that we are shutting down
		xQueryMonitor.killAll(500);
		//TODO : close other objects using varying methods ? set them to null ?
		//cacheManager.something();
		//xQueryPool.something();
		//collectionConfigurationManager.something();
		//collectionCache.something();
		//xmlReaderPool.close();

		long waitStart = System.currentTimeMillis();
		//Are there active brokers ?
		while (activeBrokers.size() > 0) {
			try {
				//Wait until they become inactive...
				this.wait(1000);
			} catch (InterruptedException e) {
			}
			//...or force the shutdown
			if(System.currentTimeMillis() - waitStart > maxShutdownWait) {
				LOG.warn("Not all threads returned. Forcing shutdown ...");
				break;
			}
		}
		LOG.debug("calling shutdown ...");

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
			broker = (DBBroker)inactiveBrokers.peek();

        //TOUNDERSTAND (pb) : shutdown() is called on only *one* broker ?
        // WM: yes, the database files are shared, so only one broker is needed to close them for all
        if (broker != null) {
            broker.setUser(SecurityManager.SYSTEM_USER);
            broker.shutdown();
        }
        collectionCacheMgr.deregisterCache(collectionCache);
        
        transactionManager.shutdown();

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

        // clear instance variables, just to be sure they will be garbage collected
        // the test suite restarts the db a few hundred times
        transactionManager = null;
        collectionCache = null;
        collectionCacheMgr = null;
        xQueryPool = null;
        xQueryMonitor = null;
        collectionConfigurationManager = null;
        notificationService = null;
        indexManager = null;
        scheduler = null;
        systemTasks = null;
        systemTasksPeriods = null;
        waitingSystemTasks = null;
        xmlReaderPool = null;
        shutdownListener = null;
        securityManager = null;
        notificationService = null;

        status = OPERATING;
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
}
