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
import org.exist.Database;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionCache;
import org.exist.collections.CollectionConfiguration;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.*;
import org.exist.config.ConfigurationDocumentTrigger;
import org.exist.config.Configurator;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.debuggee.Debuggee;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.persistent.SymbolTable;
import org.exist.indexing.IndexManager;
import org.exist.management.AgentFactory;
import org.exist.numbering.DLNFactory;
import org.exist.numbering.NodeIdFactory;
import org.exist.plugin.PluginsManager;
import org.exist.plugin.PluginsManagerImpl;
import org.exist.repo.ClasspathHelper;
import org.exist.repo.ExistRepository;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.impl.QuartzSchedulerImpl;
import org.exist.scheduler.impl.SystemTaskJobImpl;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.btree.DBException;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.lock.DeadlockDetection;
import org.exist.storage.lock.FileLock;
import org.exist.storage.recovery.RecoveryManager;
import org.exist.storage.sync.Sync;
import org.exist.storage.sync.SyncTask;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.Configuration.StartupTriggerConfig;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.PerformanceStats;
import org.exist.xquery.XQuery;
import org.expath.pkg.repo.PackageException;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class controls all available instances of the database.
 * Use it to configure, start and stop database instances.
 * You may have multiple instances defined, each using its own configuration.
 * To define multiple instances, pass an identification string to
 * {@link #configure(String, int, int, Configuration, Optional<Observer>)}
 * and use {@link #getInstance(String)} to retrieve an instance.
 *
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author Adam Retter <adam@exist-db.org>
 */
@ConfigurationClass("pool")
public class BrokerPool extends BrokerPools implements BrokerPoolConstants, Database {

    private final static Logger LOG = LogManager.getLogger(BrokerPool.class);

    private StatusReporter statusReporter = null;

    private final XQuery xqueryService = new XQuery();


    //TODO : make it non-static since every database instance may have its own policy.
    //TODO : make a default value that could be overwritten by the configuration
    // WM: this is only used by junit tests to test the recovery process.
    /**
     * For testing only: triggers a database corruption by disabling the page caches. The effect is
     * similar to a sudden power loss or the jvm being killed. The flag is used by some
     * junit tests to test the recovery process.
     */
    public static boolean FORCE_CORRUPTION = false;

    /**
     * <code>true</code> if the database instance is able to perform recovery.
     */
    private final boolean recoveryEnabled;

    /**
     * The name of the database instance
     */
    private final String instanceName;

    /**
     * State of the BrokerPool instance
     */
    private enum State {
        SHUTTING_DOWN,
        SHUTDOWN,
        INITIALIZING,
        OPERATIONAL
    }

    private final AtomicReference<State> status = new AtomicReference<>(State.SHUTDOWN);

    /**
     * The number of brokers for the database instance
     */
    private int brokersCount = 0;

    /**
     * The minimal number of brokers for the database instance
     */
    @ConfigurationFieldAsAttribute("min")
    private final int minBrokers;

    /**
     * The maximal number of brokers for the database instance
     */
    @ConfigurationFieldAsAttribute("max")
    private final int maxBrokers;

    /**
     * The number of inactive brokers for the database instance
     */
    private final Deque<DBBroker> inactiveBrokers = new ArrayDeque<>();

    /**
     * The number of active brokers for the database instance
     */
    private final Map<Thread, DBBroker> activeBrokers = new ConcurrentHashMap<>();


    /**
     * Used when TRACE level logging is enabled
     * to provide a history of broker leases
     */
    private final Map<String, TraceableStateChanges<TraceableBrokerLeaseChange.BrokerInfo, TraceableBrokerLeaseChange.Change>> brokerLeaseChangeTrace = LOG.isTraceEnabled() ? new HashMap<>() : null;
    private final Map<String, List<TraceableStateChanges<TraceableBrokerLeaseChange.BrokerInfo, TraceableBrokerLeaseChange.Change>>> brokerLeaseChangeTraceHistory = LOG.isTraceEnabled() ? new HashMap<>() : null;

    /**
     * The configuration object for the database instance
     */
    private final Configuration conf;

    private final ConcurrentSkipListSet<Observer> statusObservers = new ConcurrentSkipListSet<>();

    /**
     * <code>true</code> if a cache synchronization event is scheduled
     */
    //TODO : rename as syncScheduled ?
    //TODO : alternatively, delete this member and create a Sync.NOSYNC event
    private boolean syncRequired = false;

    /**
     * The kind of scheduled cache synchronization event.
     * One of {@link org.exist.storage.sync.Sync}
     */
    private Sync syncEvent = Sync.MINOR;

    private boolean checkpoint = false;

    /**
     * Indicates whether the database is operating in read-only mode
     */
    @GuardedBy("itself") private Boolean readOnly = Boolean.FALSE;

    @ConfigurationFieldAsAttribute("pageSize")
    private final int pageSize;

    private FileLock dataLock;

    /**
     * The journal manager of the database instance.
     */
    private Optional<JournalManager> journalManager = Optional.empty();

    /**
     * The transaction manager of the database instance.
     */
    private TransactionManager transactionManager = null;

    /**
     * Delay (in ms) for running jobs to return when the database instance shuts down.
     */
    @ConfigurationFieldAsAttribute("wait-before-shutdown")
    private final long maxShutdownWait;

    /**
     * The scheduler for the database instance.
     */
    @ConfigurationFieldAsAttribute("scheduler")
    private final Scheduler scheduler;

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
    @ConfigurationFieldAsAttribute("sync-period")
    private final long majorSyncPeriod;        //the period after which a major sync should occur
    private long lastMajorSync = System.currentTimeMillis();    //time the last major sync occurred

    private final long diskSpaceMin;

    /**
     * The listener that is notified when the database instance shuts down.
     */
    private ShutdownListener shutdownListener = null;

    /**
     * The security manager of the database instance.
     */
    private SecurityManager securityManager = null;

    /**
     * The plugin manager.
     */
    private PluginsManagerImpl pluginManager = null;


    /**
     * The global notification service used to subscribe
     * to document updates.
     */
    private NotificationService notificationService = null;

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
    //TODO : rename as collectionsCache ?
    private CollectionCache collectionCache;

    /**
     * The pool in which the database instance's readers are stored.
     */
    private XMLReaderPool xmlReaderPool;

    private final NodeIdFactory nodeFactory = new DLNFactory();

    private final Lock globalXUpdateLock = new ReentrantLock();

    private Subject serviceModeUser = null;
    private boolean inServiceMode = false;

    //the time that the database was started
    private final Calendar startupTime = Calendar.getInstance();

    private final Optional<BrokerWatchdog> watchdog;

    private final ClassLoader classLoader;

    private Optional<ExistRepository> expathRepo = Optional.empty();

    /**
     * Creates and configures the database instance.
     *
     * @param instanceName A name for the database instance.
     * @param minBrokers   The minimum number of concurrent brokers for handling requests on the database instance.
     * @param maxBrokers   The maximum number of concurrent brokers for handling requests on the database instance.
     * @param conf         The configuration object for the database instance
     * @param statusObserver    Observes the status of this database instance
     *
     * @throws EXistException If the initialization fails.
     */
    //TODO : Then write a configure(int minBrokers, int maxBrokers, Configuration conf) method
    BrokerPool(final String instanceName, final int minBrokers, final int maxBrokers, final Configuration conf,
            final Optional<Observer> statusObserver) throws EXistException, DatabaseConfigurationException {

        final NumberFormat nf = NumberFormat.getNumberInstance();

        this.classLoader = Thread.currentThread().getContextClassLoader();

        //TODO : ensure that the instance name is unique ?
        //WM: needs to be done in the configure method.
        this.instanceName = instanceName;

        //TODO : sanity check : the shutdown period should be reasonable
        this.maxShutdownWait = conf.getProperty(BrokerPool.PROPERTY_SHUTDOWN_DELAY, DEFAULT_MAX_SHUTDOWN_WAIT);
        LOG.info("database instance '" + instanceName + "' will wait  " + nf.format(this.maxShutdownWait) + " ms during shutdown");

        this.recoveryEnabled = conf.getProperty(PROPERTY_RECOVERY_ENABLED, true);
        LOG.info("database instance '" + instanceName + "' is enabled for recovery : " + this.recoveryEnabled);

        this.minBrokers = conf.getProperty(PROPERTY_MIN_CONNECTIONS, minBrokers);
        this.maxBrokers = conf.getProperty(PROPERTY_MAX_CONNECTIONS, maxBrokers);

        //TODO : sanity check : minBrokers shall be lesser than or equal to maxBrokers
        //TODO : sanity check : minBrokers shall be positive
        LOG.info("database instance '" + instanceName + "' will have between " + nf.format(this.minBrokers) + " and " + nf.format(this.maxBrokers) + " brokers");

        //TODO : use the periodicity of a SystemTask (see below)
        this.majorSyncPeriod = conf.getProperty(PROPERTY_SYNC_PERIOD, DEFAULT_SYNCH_PERIOD);

        //TODO : sanity check : the synch period should be reasonable
        LOG.info("database instance '" + instanceName + "' will be synchronized every " + nf.format(/*this.*/majorSyncPeriod) + " ms");

        // convert from bytes to megabytes: 1024 * 1024
        this.diskSpaceMin = 1024l * 1024l * conf.getProperty(BrokerPool.DISK_SPACE_MIN_PROPERTY, DEFAULT_DISK_SPACE_MIN);

        this.pageSize = conf.getProperty(PROPERTY_PAGE_SIZE, DEFAULT_PAGE_SIZE);

        //TODO : move this to initialize ? (cant as we need it for FileLockHeartBeat)
        this.scheduler = new QuartzSchedulerImpl(this, conf);

        if(!canReadDataDir(conf)) {
            setReadOnly();
        }

        //Configuration is valid, save it
        this.conf = conf;

        statusObserver.ifPresent(this.statusObservers::add);

        this.watchdog = Optional.ofNullable(System.getProperty("trace.brokers"))
                .filter(v -> v.equals("yes"))
                .map(v -> new BrokerWatchdog());

        //TODO : in the future, we should implement an Initializable interface
        try {
            initialize();
        } catch(final Throwable e) {
            // remove that file lock we may have acquired in canReadDataDir
            synchronized(readOnly) {
                if (dataLock != null && !readOnly) {
                    dataLock.release();
                }
            }

            if(e instanceof EXistException) {
                throw (EXistException) e;
            } else if(e instanceof DatabaseConfigurationException) {
                throw (DatabaseConfigurationException) e;
            } else {
                throw new EXistException(e);
            }
        }

        //TODO : move this to initialize ?
        //setup database synchronization job
        if(majorSyncPeriod > 0) {
            //TODO : why not automatically register Sync in system tasks ?
//            scheduler.createPeriodicJob(2500, new Sync(), 2500);
            final SyncTask syncTask = new SyncTask();
            syncTask.configure(conf, null);
            scheduler.createPeriodicJob(2500, new SystemTaskJobImpl(SyncTask.getJobName(), syncTask), 2500);
        }
    }

    //TODO : create a canReadJournalDir() method in the *relevant* class. The two directories may be different.
    private boolean canReadDataDir(final Configuration conf) throws EXistException {
        final Path dataDir = Optional.ofNullable((Path) conf.getProperty(PROPERTY_DATA_DIR))
                .orElse(Paths.get(NativeBroker.DEFAULT_DATA_DIR));

        if(!Files.exists(dataDir)) {
            try {
                //TODO : shall we force the creation ? use a parameter to decide ?
                LOG.info("Data directory '" + dataDir.toAbsolutePath().toString() + "' does not exist. Creating one ...");
                Files.createDirectories(dataDir);
            } catch(final SecurityException | IOException e) {
                throw new EXistException("Cannot create data directory '" + dataDir.toAbsolutePath().toString() + "'", e);
            }
        }

        //Save it for further use.
        conf.setProperty(PROPERTY_DATA_DIR, dataDir);
        if(!Files.isWritable(dataDir)) {
            LOG.warn("Cannot write to data directory: " + dataDir.toAbsolutePath().toString());
            return false;
        }

        // try to acquire lock on the data dir
        dataLock = new FileLock(this, dataDir.resolve("dbx_dir.lck"));

        try {
            final boolean locked = dataLock.tryLock();
            if(!locked) {
                throw new EXistException("The database directory seems to be locked by another " +
                    "database instance. Found a valid lock file: " + dataLock.getFile());
            }
        } catch(final ReadOnlyException e) {
            LOG.warn(e);
            return false;
        }

        return true;
    }

    /**
     * Initializes the database instance.
     *
     * @throws EXistException
     */
    private void initialize() throws EXistException, DatabaseConfigurationException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("initializing database instance '" + instanceName + "'...");
        }

        //Flag to indicate that we are initializing
        if(!status.compareAndSet(State.SHUTDOWN, State.INITIALIZING)) {
            throw new IllegalStateException("Database is already initialized");
        }

        // Don't allow two threads to do a race on this. May be irrelevant as this is only called
        // from the constructor right now.
        synchronized(this) {
            try {
                statusReporter = new StatusReporter(SIGNAL_STARTUP);
                statusObservers.forEach(statusReporter::addObserver);

                final Thread statusThread = new Thread(statusReporter);
                statusThread.start();

                // statusReporter may have to be terminated or the thread can/will hang.
                try {
                    final boolean exportOnly = conf.getProperty(PROPERTY_EXPORT_ONLY, false);

                    //create the security manager
                    securityManager = new SecurityManagerImpl(this);

                    //REFACTOR : construct then configure
                    cacheManager = new DefaultCacheManager(this);

                    //REFACTOR : construct then configure
                    xQueryPool = new XQueryPool(conf);
                    //REFACTOR : construct then... configure
                    processMonitor = new ProcessMonitor(maxShutdownWait);
                    xqueryStats = new PerformanceStats(this);

                    //REFACTOR : construct then... configure
                    xmlReaderPool = new XMLReaderPool(conf, new XMLReaderObjectFactory(this), 5, 0);

                    //REFACTOR : construct then... configure
                    int bufferSize = conf.getInteger(PROPERTY_COLLECTION_CACHE_SIZE);
                    if(bufferSize == -1) {
                        bufferSize = DEFAULT_COLLECTION_BUFFER_SIZE;
                    }
                    collectionCache = new CollectionCache(this, bufferSize, 0.0001);
                    collectionCacheMgr = new CollectionCacheManager(this, collectionCache);

                    // compute how much memory should be reserved for caches to grow
                    final Runtime rt = Runtime.getRuntime();
                    final long maxMem = rt.maxMemory();
                    final long minFree = maxMem / 5;
                    reservedMem = cacheManager.getTotalMem() + collectionCacheMgr.getMaxTotal() + minFree;
                    LOG.debug("Reserved memory: " + reservedMem + "; max: " + maxMem + "; min: " + minFree);

                    notificationService = new NotificationService();

                    //REFACTOR : construct then... configure
                    //TODO : journal directory *may* be different from BrokerPool.PROPERTY_DATA_DIR
                    journalManager = recoveryEnabled ? Optional.of(new JournalManager(
                            this,
                            (Path)conf.getProperty(BrokerPool.PROPERTY_DATA_DIR),
                            conf.getProperty(PROPERTY_RECOVERY_GROUP_COMMIT, false)
                    )) : Optional.empty();
                    if(journalManager.isPresent()) {
                        try {
                            journalManager.get().initialize();
                        } catch (final ReadOnlyException e) {
                            LOG.warn(e);
                            setReadOnly();
                        }
                    }

                    transactionManager = new TransactionManager(this, journalManager);
                    transactionManager.initialize();

                    // If the initialization fails after transactionManager has been created this method better cleans up
                    // or the FileSyncThread for the journal can/will hang.
                    try {
                        symbols = new SymbolTable(conf);
                        if(!Files.isWritable(symbols.getFile())) {
                            LOG.warn("Symbols table is not writable: " + symbols.getFile().toAbsolutePath().toString());
                            setReadOnly();
                        }

                        try {
                            // initialize EXPath repository so indexManager and
                            // startup triggers can access it
                            expathRepo = Optional.ofNullable(ExistRepository.getRepository(this.conf));
                        } catch(final PackageException e) {
                            LOG.error("Failed to initialize expath repository: " + e.getMessage() + " - " +
                                     "indexing apps and the package manager may not work.");
                        }
                        ClasspathHelper.updateClasspath(this);

                        indexManager = new IndexManager(this, conf);

                        //TODO : replace the following code by get()/release() statements ?
                        // WM: I would rather tend to keep this broker reserved as a system broker.
                        // create a first broker to initialize the security manager
                        //createBroker();
                        //TODO : this broker is *not* marked as active and *might* be reused by another process ! Is it intended ?
                        // at this stage, the database is still single-threaded, so reusing the broker later is not a problem.
                        //DBBroker broker = inactiveBrokers.peek();
                        // dmitriy: Security issue: better to use proper get()/release() way, because of sub-processes (SecurityManager as example)
                        try(final DBBroker broker = get(Optional.of(securityManager.getSystemSubject()))) {

                            if(isReadOnly()) {
                                journalManager.ifPresent(JournalManager::disableJournalling);
                            }

                            //Run the recovery process
                            //TODO : assume
                            boolean recovered = false;
                            if(isRecoveryEnabled()) {
                                recovered = runRecovery(broker);
                                //TODO : extract the following from this block ? What if we ware not transactional ? -pb
                                if(!recovered) {
                                    try {
                                        if(broker.getCollection(XmldbURI.ROOT_COLLECTION_URI) == null) {
                                            final Txn txn = transactionManager.beginTransaction();
                                            try {
                                                //TODO : use a root collection final member
                                                broker.getOrCreateCollection(txn, XmldbURI.ROOT_COLLECTION_URI);
                                                transactionManager.commit(txn);
                                            } catch(final IOException | TriggerException | PermissionDeniedException e) {
                                                transactionManager.abort(txn);
                                            } finally {
                                                transactionManager.close(txn);
                                            }
                                        }
                                    } catch(final PermissionDeniedException pde) {
                                        LOG.fatal(pde.getMessage(), pde);
                                    }
                                }
                            }

        					/* initialise required collections if they don't exist yet */
                            if(!exportOnly) {
                                try {
                                    initialiseSystemCollections(broker);
                                } catch(final PermissionDeniedException pde) {
                                    LOG.error(pde.getMessage(), pde);
                                    throw new EXistException(pde.getMessage(), pde);
                                }
                            }

                            //create the plugin manager
                            pluginManager = new PluginsManagerImpl(this, broker);

                            //TODO : from there, rethink the sequence of calls.
                            // WM: attention: a small change in the sequence of calls can break
                            // either normal startup or recovery.

                            status.set(State.OPERATIONAL);

                            statusReporter.setStatus(SIGNAL_READINESS);

                            //Get a manager to handle further collections configuration
                            initCollectionConfigurationManager(broker);

                            //wake-up the plugins manager
                            pluginManager.start(broker);

                            //wake-up the security manager
                            securityManager.attach(broker);

                            //If necessary, launch a task to repair the DB
                            //TODO : merge this with the recovery process ?
                            //XXX: don't do if READONLY mode
                            if(isRecoveryEnabled() && recovered) {
                                if(!exportOnly) {
                                    reportStatus("Reindexing database files...");
                                    try {
                                        broker.repair();
                                    } catch(final PermissionDeniedException e) {
                                        LOG.warn("Error during recovery: " + e.getMessage(), e);
                                    }
                                }

                                if(((Boolean) conf.getProperty(PROPERTY_RECOVERY_CHECK)).booleanValue()) {
                                    final ConsistencyCheckTask task = new ConsistencyCheckTask();
                                    final Properties props = new Properties();
                                    props.setProperty("backup", "no");
                                    props.setProperty("output", "sanity");
                                    task.configure(conf, props);
                                    task.execute(broker);
                                }
                            }

                            //OK : the DB is repaired; let's make a few RW operations
                            statusReporter.setStatus(SIGNAL_WRITABLE);

                            //initialize configurations watcher trigger
                            if(!exportOnly) {
                                try {
                                    initialiseTriggersForCollections(broker, XmldbURI.SYSTEM_COLLECTION_URI);
                                } catch(final PermissionDeniedException pde) {
                                    //XXX: do not catch exception!
                                    LOG.error(pde.getMessage(), pde);
                                }
                            }

                            // remove temporary docs
                            try {
                                broker.cleanUpTempResources(true);
                            } catch(final PermissionDeniedException pde) {
                                LOG.error(pde.getMessage(), pde);
                            }

                            sync(broker, Sync.MAJOR);

                            //TODO(AR) remove this!
                            //require to allow access by BrokerPool.getInstance();
                            //instances.put(instanceName, this);

                            callStartupTriggers((List<StartupTriggerConfig>) conf.getProperty(BrokerPool.PROPERTY_STARTUP_TRIGGERS), broker);
                        }

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
                        for(int i = 1; i < minBrokers; i++) {
                            createBroker();
                        }

                        // register some MBeans to provide access to this instance
                        AgentFactory.getInstance().initDBInstance(this);

                        if(LOG.isDebugEnabled()) {
                            LOG.debug("database instance '" + instanceName + "' initialized");
                        }

                        //setup any configured jobs
                        //scheduler.setupConfiguredJobs();

                        //execute any startup jobs
                        //scheduler.executeStartupJobs();

                        scheduler.run();

                        statusReporter.setStatus(SIGNAL_STARTED);
                    } catch(final Throwable t) {
                        transactionManager.shutdown();
                        throw t;
                    }
                } catch(final EXistException | DatabaseConfigurationException e) {
                    throw e;
                } catch(final Throwable t) {
                    throw new EXistException(t.getMessage(), t);
                }
            } finally {
                if(statusReporter != null) {
                    statusReporter.terminate();
                    statusReporter = null;
                }
            }
        }
    }

    //TODO : remove the period argument when SystemTask has a getPeriodicity() method
    //TODO : make it protected ?
    /*private void initSystemTask(SingleInstanceConfiguration.SystemTaskConfig config, SystemTask task) throws EXistException {
        try {
            if (config.getCronExpr() == null) {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() + " every " +
                        config.getPeriod() + " ms");
                scheduler.createPeriodicJob(config.getPeriod(), new SystemTaskJobImpl(task), config.getPeriod());
            } else {
                LOG.debug("Scheduling system maintenance task " + task.getClass().getName() +
                        " with cron expression: " + config.getCronExpr());
                scheduler.createCronJob(config.getCronExpr(), new SystemTaskJobImpl(task));
            }
        } catch (Exception e) {
			LOG.warn(e.getMessage(), e);
            throw new EXistException("Failed to initialize system maintenance task: " + e.getMessage());
        }
    }*/

    private void callStartupTriggers(final List<StartupTriggerConfig> startupTriggerConfigs, final DBBroker sysBroker) {
        if(startupTriggerConfigs == null) {
            return;
        }

        for(final StartupTriggerConfig startupTriggerConfig : startupTriggerConfigs) {
            try {
                final Class<StartupTrigger> clazz = (Class<StartupTrigger>) Class.forName(startupTriggerConfig.getClazz());
                final StartupTrigger startupTrigger = clazz.newInstance();
                startupTrigger.execute(sysBroker, startupTriggerConfig.getParams());
            } catch(final ClassNotFoundException | IllegalAccessException | InstantiationException e) {
                LOG.error("Could not call StartupTrigger class: " + startupTriggerConfig + ". SKIPPING! " + e.getMessage(), e);
            } catch(final RuntimeException re) {
                LOG.warn("StartupTrigger threw RuntimeException: " + re.getMessage() + ". IGNORING!", re);
            }
        }
        // trigger a checkpoint after processing all startup triggers
        checkpoint = true;
        triggerSync(Sync.MAJOR);
    }

    /**
     * Initialise system collections, if it doesn't exist yet
     *
     * @param sysBroker        The system broker from before the brokerpool is populated
     * @param sysCollectionUri XmldbURI of the collection to create
     * @param permissions      The permissions to set on the created collection
     */
    private void initialiseSystemCollection(final DBBroker sysBroker, final XmldbURI sysCollectionUri, final int permissions) throws EXistException, PermissionDeniedException {
        Collection collection = sysBroker.getCollection(sysCollectionUri);
        if(collection == null) {
            final TransactionManager transact = getTransactionManager();
            try(final Txn txn = transact.beginTransaction()) {
                collection = sysBroker.getOrCreateCollection(txn, sysCollectionUri);
                if(collection == null) {
                    throw new IOException("Could not create system collection: " + sysCollectionUri);
                }
                collection.setPermissions(permissions);
                sysBroker.saveCollection(txn, collection);

                transact.commit(txn);
            } catch(final Exception e) {
                e.printStackTrace();
                final String msg = "Initialisation of system collections failed: " + e.getMessage();
                LOG.error(msg, e);
                throw new EXistException(msg, e);
            }
        }
    }

    /**
     * Initialize required system collections, if they don't exist yet
     *
     * @param broker - The system broker from before the brokerpool is populated
     * @throws EXistException If a system collection cannot be created
     */
    private void initialiseSystemCollections(final DBBroker broker) throws EXistException, PermissionDeniedException {
        //create /db/system
        initialiseSystemCollection(broker, XmldbURI.SYSTEM_COLLECTION_URI, Permission.DEFAULT_SYSTEM_COLLECTION_PERM);
    }

    private void initialiseTriggersForCollections(final DBBroker broker, final XmldbURI uri) throws EXistException, PermissionDeniedException {
        final Collection collection = broker.getCollection(uri);

        //initialize configurations watcher trigger
        if(collection != null) {
            final CollectionConfigurationManager manager = getConfigurationManager();
            final CollectionConfiguration collConf = manager.getOrCreateCollectionConfiguration(broker, collection);

            final DocumentTriggerProxy triggerProxy = new DocumentTriggerProxy(ConfigurationDocumentTrigger.class); //, collection.getURI());
            collConf.documentTriggers().add(triggerProxy);
        }
    }

    /**
     * Run a database recovery if required. This method is called once during
     * startup from {@link org.exist.storage.BrokerPool}.
     *
     * @param broker
     * @throws EXistException
     */
    public boolean runRecovery(final DBBroker broker) throws EXistException {
        final boolean forceRestart = conf.getProperty(PROPERTY_RECOVERY_FORCE_RESTART, false);
        if(LOG.isDebugEnabled()) {
            LOG.debug("ForceRestart = " + forceRestart);
        }
        if(journalManager.isPresent()) {
            final RecoveryManager recovery = new RecoveryManager(broker, journalManager.get(), forceRestart);
            return recovery.recover();
        } else {
            throw new IllegalStateException("Cannot run recovery without a JournalManager");
        }
    }

    public long getReservedMem() {
        return reservedMem - cacheManager.getCurrentSize();
    }

    public int getPageSize() {
        return pageSize;
    }

    /**
     * Returns the class loader used when this BrokerPool was configured.
     */
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    /**
     * Whether or not the database instance is being initialized.
     *
     * @return <code>true</code> is the database instance is being initialized
     */
    //TODO : let's be positive and rename it as isInitialized ? 
    public boolean isInitializing() {
        return status.get() == State.INITIALIZING;
    }

    /**
     * Returns the database instance's name.
     *
     * @return The id
     */
    //TODO : rename getInstanceName
    public String getId() {
        return instanceName;
    }

    /**
     * Returns the number of brokers currently serving requests for the database instance.
     *
     * @return The brokers count
     * @deprecated use countActiveBrokers
     */
    //TODO : rename as getActiveBrokers ?
    public int active() {
        return activeBrokers.size();
    }

    /**
     * Returns the number of brokers currently serving requests for the database instance.
     *
     * @return The active brokers count.
     */
    @Override
    public int countActiveBrokers() {
        return activeBrokers.size();
    }

    public Map<Thread, DBBroker> getActiveBrokers() {
        return new HashMap<>(activeBrokers);
    }

    /**
     * Returns the number of inactive brokers for the database instance.
     *
     * @return The brokers count
     */
    //TODO : rename as getInactiveBrokers ?
    public int available() {
        return inactiveBrokers.size();
    }

    //TODO : getMin() method ?

    /**
     * Returns the maximal number of brokers for the database instance.
     *
     * @return The brokers count
     */
    //TODO : rename as getMaxBrokers ?
    public int getMax() {
        return maxBrokers;
    }

    public int total() {
        return brokersCount;
    }

    /**
     * Returns whether the database instance has been configured.
     *
     * @return <code>true</code> if the datbase instance is configured
     */
    public final boolean isInstanceConfigured() {
        return conf != null;
    }

    /**
     * Returns the configuration object for the database instance.
     *
     * @return The configuration
     */
    public Configuration getConfiguration() {
        return conf;
    }

    public Optional<ExistRepository> getExpathRepo() {
        return expathRepo;
    }

    //TODO : rename as setShutdwonListener ?
    public void registerShutdownListener(final ShutdownListener listener) {
        //TODO : check that we are not shutting down
        shutdownListener = listener;
    }

    public NodeIdFactory getNodeFactory() {
        return nodeFactory;
    }

    /**
     * Returns the database instance's security manager
     *
     * @return The security manager
     */
    public SecurityManager getSecurityManager() {
        return securityManager;
    }


    /**
     * Returns the Scheduler
     *
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
    public boolean isRecoveryEnabled() {
        synchronized(readOnly) {
            return !readOnly && recoveryEnabled;
        }
    }

    @Override
    public boolean isReadOnly() {
        synchronized(readOnly) {
            if(!readOnly) {
                final long freeSpace = FileUtils.measureFileStore(dataLock.getFile(), FileStore::getUsableSpace);
                if (freeSpace < diskSpaceMin) {
                    LOG.fatal("Partition containing DATA_DIR: " + dataLock.getFile().toAbsolutePath().toString() + " is running out of disk space. " +
                            "Switching eXist-db to read only to prevent data loss!");
                    setReadOnly();
                }
            }
            return readOnly;
        }
    }

    public void setReadOnly() {
        LOG.warn("Switching database into read-only mode!");
        synchronized (readOnly) {
            readOnly = true;
        }
    }

    public boolean isInServiceMode() {
        return inServiceMode;
    }

    public Optional<JournalManager> getJournalManager() {
        return journalManager;
    }

    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    /**
     * Returns a manager for accessing the database instance's collection configuration files.
     *
     * @return The manager
     */
    @Override
    public CollectionConfigurationManager getConfigurationManager() {
        return collectionConfigurationManager;
    }

    public void initCollectionConfigurationManager(final DBBroker broker) {
        if(collectionConfigurationManager == null) {
            try {
                collectionConfigurationManager = new CollectionConfigurationManager(broker);
            } catch(final Exception e) {
                LOG.error("Found an error while initializing database: " + e.getMessage(), e);
            }
        }
    }


    /**
     * Returns a cache in which the database instance's collections are stored.
     *
     * @return The cache
     */
    public CollectionCache getCollectionsCache() {
        return collectionCache;
    }

    /**
     * Returns a cache in which the database instance's may store items.
     *
     * @return The cache
     */
    @Override
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
    @Override
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
     * Retuns the XQuery Service
     *
     * @return The XQuery service
     */
    public XQuery getXQueryService() {
        return xqueryService;
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

    /**
     * Creates an inactive broker for the database instance.
     *
     * @return The broker
     * @throws EXistException
     */
    protected DBBroker createBroker() throws EXistException {
        //TODO : in the future, don't pass the whole configuration, just the part relevant to brokers
        final DBBroker broker = BrokerFactory.getInstance(this, this.getConfiguration());
        inactiveBrokers.push(broker);
        brokersCount++;
        broker.setId(broker.getClass().getName() + '_' + instanceName + "_" + brokersCount);
        LOG.debug(
            "created broker '" + broker.getId() + " for database instance '" + instanceName + "'");
        return broker;
    }

    /**
     * Get active broker for current thread
     *
     * Note - If you call getActiveBroker() you must not call
     * release on both the returned active broker and the original
     * lease from {@link BrokerPool#getBroker()} or {@link BrokerPool#get(Optional)}
     * otherwise release will have been called more than get!
     *
     * @return Database broker
     * @throws RuntimeException NO broker available for current thread.
     */
    public DBBroker getActiveBroker() { //throws EXistException {
        //synchronized(this) {
        //Try to get an active broker
        final DBBroker broker = activeBrokers.get(Thread.currentThread());
        if(broker == null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Broker was not obtained for thread '");
            sb.append(Thread.currentThread());
            sb.append("'.");
            sb.append(System.getProperty("line.separator"));

            for(final Entry<Thread, DBBroker> entry : activeBrokers.entrySet()) {
                sb.append(entry.getKey());
                sb.append(" = ");
                sb.append(entry.getValue());
                sb.append(System.getProperty("line.separator"));
            }

            LOG.debug(sb.toString());
            throw new RuntimeException(sb.toString());
        }
        return broker;
        //}
    }

    public DBBroker authenticate(final String username, final Object credentials) throws AuthenticationException {
        final Subject subject = getSecurityManager().authenticate(username, credentials);

        try {
            return get(Optional.ofNullable(subject));
        } catch(final Exception e) {
            throw new AuthenticationException(AuthenticationException.UNNOWN_EXCEPTION, e);
        }
    }

    /**
     * Returns an active broker for the database instance.
     *
     * The current user will be inherited by this broker
     *
     * @return The broker
     */
    public DBBroker getBroker() throws EXistException {
        return get(Optional.empty());
    }

    /**
     * Returns an active broker for the database instance.
     *
     * @param subject Optionally a subject to set on the broker, if a user is not provided then the
     *                current user assigned to the broker will be re-used
     * @return The broker
     * @throws EXistException If the instance is not available (stopped or not configured)
     */
    //TODO : rename as getBroker ? getInstance (when refactored) ?
    public DBBroker get(final Optional<Subject> subject) throws EXistException {
        Objects.requireNonNull(subject, "Subject cannot be null, use BrokerPool#getBroker() instead");

        if(!isInstanceConfigured()) {
            throw new EXistException("database instance '" + instanceName + "' is not available");
        }

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
            broker.pushSubject(subject.orElseGet(broker::getCurrentSubject));

            if(LOG.isTraceEnabled()) {
                if(!brokerLeaseChangeTrace.containsKey(broker.getId())) {
                    brokerLeaseChangeTrace.put(broker.getId(), new TraceableStateChanges<>());
                }
                brokerLeaseChangeTrace.get(broker.getId()).add(TraceableBrokerLeaseChange.get(new TraceableBrokerLeaseChange.BrokerInfo(broker.getId(), broker.getReferenceCount())));
            }

            return broker;
            //TODO : share the code with what is below (including notifyAll) ?
            // WM: notifyAll is not necessary if we don't have to wait for a broker.
        }

        //No active broker : get one ASAP

        while(serviceModeUser != null && subject.isPresent() && !subject.equals(Optional.ofNullable(serviceModeUser))) {
            try {
                LOG.debug("Db instance is in service mode. Waiting for db to become available again ...");
                wait();
            } catch(final InterruptedException e) {
            }
        }

        synchronized(this) {
            //Are there any available brokers ?
            if(inactiveBrokers.isEmpty()) {
                //There are no available brokers. If allowed...
                if(brokersCount < maxBrokers)
                //... create one
                {
                    createBroker();
                } else
                    //... or wait until there is one available
                    while(inactiveBrokers.isEmpty()) {
                        LOG.debug("waiting for a broker to become available");
                        try {
                            this.wait();
                        } catch(final InterruptedException e) {
                            //nothing to be done!
                        }
                    }
            }
            broker = inactiveBrokers.pop();
            //activate the broker
            activeBrokers.put(Thread.currentThread(), broker);

            if(LOG.isTraceEnabled()) {
                LOG.trace("+++ " + Thread.currentThread() + Stacktrace.top(Thread.currentThread().getStackTrace(), Stacktrace.DEFAULT_STACK_TOP));
            }

            if(watchdog.isPresent()) {
                watchdog.get().add(broker);
            }

            broker.incReferenceCount();

            broker.pushSubject(subject.orElseGet(securityManager::getGuestSubject));

            if(LOG.isTraceEnabled()) {
                if(!brokerLeaseChangeTrace.containsKey(broker.getId())) {
                    brokerLeaseChangeTrace.put(broker.getId(), new TraceableStateChanges<>());
                }
                brokerLeaseChangeTrace.get(broker.getId()).add(TraceableBrokerLeaseChange.get(new TraceableBrokerLeaseChange.BrokerInfo(broker.getId(), broker.getReferenceCount())));
            }

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
     * NOTE - this is intentionally package-private, it is only meant to be
     * called internally and from {@link DBBroker#close()}
     *
     * @param broker The broker to be released
     */
    //TODO : rename as releaseBroker ? releaseInstance (when refactored) ?
    void release(final DBBroker broker) {
        Objects.requireNonNull(broker, "Cannot release nothing");

        if(LOG.isTraceEnabled()) {
            if(!brokerLeaseChangeTrace.containsKey(broker.getId())) {
                brokerLeaseChangeTrace.put(broker.getId(), new TraceableStateChanges<>());
            }
            brokerLeaseChangeTrace.get(broker.getId()).add(TraceableBrokerLeaseChange.release(new TraceableBrokerLeaseChange.BrokerInfo(broker.getId(), broker.getReferenceCount())));
        }

        //first check that the broker is active ! If not, return immediately.
        broker.decReferenceCount();
        if(broker.getReferenceCount() > 0) {
            broker.popSubject();
            //it is still in use and thus can't be marked as inactive
            return;
        }

        synchronized(this) {
            //Broker is no more used : inactivate it
            for(final DBBroker inactiveBroker : inactiveBrokers) {
                if(broker == inactiveBroker) {
                    LOG.error("Broker " + broker.getId() + " is already in the inactive list!!!");
                    return;
                }
            }

            if(activeBrokers.remove(Thread.currentThread()) == null) {
                LOG.error("release() has been called from the wrong thread for broker " + broker.getId());
                // Cleanup the state of activeBrokers
                for(final Entry<Thread, DBBroker> activeBroker : activeBrokers.entrySet()) {
                    if(activeBroker.getValue() == broker) {
                        final EXistException ex = new EXistException();
                        LOG.error("release() has been called from '" + Thread.currentThread() + "', but occupied at '" + activeBroker.getKey() + "'.", ex);
                        activeBrokers.remove(activeBroker.getKey());
                        break;
                    }
                }
            } else {
                if(LOG.isTraceEnabled()) {
                    LOG.trace("--- " + Thread.currentThread() + Stacktrace.top(Thread.currentThread().getStackTrace(), Stacktrace.DEFAULT_STACK_TOP));
                }
            }
            
            Subject lastUser = broker.popSubject();

            //guard to ensure that the broker has popped all its subjects
            if(lastUser == null || broker.getCurrentSubject() != null) {
                LOG.warn("Broker " + broker.getId() + " was returned with extraneous Subjects, cleaning...", new IllegalStateException("DBBroker pushSubject/popSubject mismatch").fillInStackTrace());
                if(LOG.isTraceEnabled()) {
                    broker.traceSubjectChanges();
                }

                //cleanup any remaining erroneous subjects
                while(broker.getCurrentSubject() != null) {
                    lastUser = broker.popSubject();
                }
            }

            inactiveBrokers.push(broker);
            watchdog.ifPresent(wd -> wd.remove(broker));

            if(LOG.isTraceEnabled()) {
                if(!brokerLeaseChangeTraceHistory.containsKey(broker.getId())) {
                    brokerLeaseChangeTraceHistory.put(broker.getId(), new ArrayList<>());
                }
                try {
                    brokerLeaseChangeTraceHistory.get(broker.getId()).add((TraceableStateChanges<TraceableBrokerLeaseChange.BrokerInfo, TraceableBrokerLeaseChange.Change>) brokerLeaseChangeTrace.get(broker.getId()).clone());
                    brokerLeaseChangeTrace.get(broker.getId()).clear();
                } catch(final CloneNotSupportedException e) {
                    LOG.error(e);
                }

                broker.clearSubjectChangesTrace();
            }

            //If the database is now idle, do some useful stuff
            if(activeBrokers.size() == 0) {
                //TODO : use a "clean" dedicated method (we have some below) ?
                if(syncRequired) {
                    //Note that the broker is not yet really inactive ;-)
                    sync(broker, syncEvent);
                    this.syncRequired = false;
                    this.checkpoint = false;
                }
                if(serviceModeUser != null && !lastUser.equals(serviceModeUser)) {
                    inServiceMode = true;
                }
            }
            //Inform the other threads that someone is gone
            this.notifyAll();
        }
    }

    public DBBroker enterServiceMode(final Subject user) throws PermissionDeniedException {
        if(!user.hasDbaRole()) {
            throw new PermissionDeniedException("Only users of group dba can switch the db to service mode");
        }

        serviceModeUser = user;
        synchronized(this) {
            if(activeBrokers.size() != 0) {
                while(!inServiceMode) {
                    try {
                        wait();
                    } catch(final InterruptedException e) {
                        //nothing to be done
                    }
                }
            }
        }

        inServiceMode = true;
        final DBBroker broker = inactiveBrokers.peek();
        checkpoint = true;
        sync(broker, Sync.MAJOR);
        checkpoint = false;
        // Return a broker that can be used to perform system tasks
        return broker;
    }

    public void exitServiceMode(final Subject user) throws PermissionDeniedException {
        if(!user.equals(serviceModeUser)) {
            throw new PermissionDeniedException("The db has been locked by a different user");
        }
        serviceModeUser = null;
        inServiceMode = false;
        synchronized(this) {
            this.notifyAll();
        }
    }

    public void reportStatus(final String message) {
        if(statusReporter != null) {
            statusReporter.setStatus(message);
        }
    }

    public long getMajorSyncPeriod() {
        return majorSyncPeriod;
    }

    public long getLastMajorSync() {
        return lastMajorSync;
    }

    /**
     * Executes a waiting cache synchronization for the database instance.
     *
     * @param broker    A broker responsible for executing the job
     * @param syncEvent One of {@link org.exist.storage.sync.Sync}
     */
    //TODO : rename as runSync ? executeSync ?
    //TOUNDERSTAND (pb) : *not* synchronized, so... "executes" or, rather, "schedules" ? "executes" (WM)
    //TOUNDERSTAND (pb) : why do we need a broker here ? Why not get and release one when we're done ?
    // WM: the method will always be under control of the BrokerPool. It is guaranteed that no
    // other brokers are active when it is called. That's why we don't need to synchronize here.
    //TODO : make it protected ?
    public void sync(final DBBroker broker, final Sync syncEvent) {
        broker.sync(syncEvent);

        //TODO : strange that it is set *after* the sunc method has been called.
        try {
            broker.pushSubject(securityManager.getSystemSubject());

            if (syncEvent == Sync.MAJOR) {
                LOG.debug("Major sync");
                try {
                    if (!FORCE_CORRUPTION) {
                        transactionManager.checkpoint(checkpoint);
                    }
                } catch (final TransactionException e) {
                    LOG.warn(e.getMessage(), e);
                }
                cacheManager.checkCaches();

                if (pluginManager != null) {
                    pluginManager.sync(broker);
                }

                lastMajorSync = System.currentTimeMillis();
                if (LOG.isDebugEnabled()) {
                    notificationService.debug();
                }
            } else {
                cacheManager.checkDistribution();
//            LOG.debug("Minor sync");
            }
            //TODO : touch this.syncEvent and syncRequired ?
        } finally {
            broker.popSubject();
        }
    }

    /**
     * Schedules a cache synchronization for the database instance. If the database instance is idle,
     * the cache synchronization will be run immediately. Otherwise, the task will be deferred
     * until all running threads have returned.
     *
     * @param syncEvent One of {@link org.exist.storage.sync.Sync}
     */
    public void triggerSync(final Sync syncEvent) {
        //TOUNDERSTAND (pb) : synchronized, so... "schedules" or, rather, "executes" ? "schedules" (WM)
        final State s = status.get();
        if(s == State.SHUTDOWN || s == State.SHUTTING_DOWN) {
            return;
        }

        LOG.debug("Triggering sync: " + syncEvent);
        synchronized(this) {
            //Are there available brokers ?
            // TOUNDERSTAND (pb) : the trigger is ignored !
            // WM: yes, it seems wrong!!
//			if(inactiveBrokers.size() == 0)
//				return;
            //TODO : switch on syncEvent and throw an exception if it is inaccurate ?
            //Is the database instance idle ?
            if(inactiveBrokers.size() == brokersCount) {
                //Borrow a broker
                //TODO : this broker is *not* marked as active and may be reused by another process !
                // No other brokers are running at this time, so there's no risk.
                //TODO : use get() then release the broker ?
                // No, might lead to a deadlock.
                final DBBroker broker = inactiveBrokers.pop();
                //Do the synchronization job
                sync(broker, syncEvent);
                inactiveBrokers.push(broker);
                syncRequired = false;
            } else {
                //Put the synchronization job into the queue
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
     *
     * @param task The task
     */
    //TOUNDERSTAND (pb) : synchronized, so... "schedules" or, rather, "executes" ?
    public void triggerSystemTask(final SystemTask task) {
        transactionManager.triggerSystemTask(task);
    }

    /**
     * Shuts downs the database instance
     */
    public void shutdown() {
        shutdown(false);
    }

    public boolean isShuttingDown() {
        return status.get() == State.SHUTTING_DOWN;
    }

    /**
     * Shuts downs the database instance
     *
     * @param killed <code>true</code> when the JVM is (cleanly) exiting
     */
    public void shutdown(final boolean killed) {
        if(!status.compareAndSet(State.OPERATIONAL, State.SHUTTING_DOWN)) {
            // we are not operational!
            return;
        }

        try {
            LOG.info("Database is shutting down ...");

            processMonitor.stopRunningJobs();

            //Shutdown the scheduler
            scheduler.shutdown(true);

            final java.util.concurrent.locks.Lock lock = transactionManager.getLock();
            try {
                // wait for currently running system tasks before we shutdown
                // they will have a lock on the transactionManager
                lock.lock();

                synchronized (this) {
                    // these may be used and set by other threads for the same or some other purpose
                    // (unlikely). Take no chances.
                    statusReporter = new StatusReporter(SIGNAL_SHUTDOWN);
                    statusObservers.forEach(statusReporter::addObserver);

                    final Thread statusThread = new Thread(statusReporter);
                    statusThread.start();

                    // release transaction log to allow remaining brokers to complete
                    // their job
                    lock.unlock();

                    // DW: only in debug mode
                    if (LOG.isDebugEnabled()) {
                        notificationService.debug();
                    }

                    //Notify all running tasks that we are shutting down

                    //Notify all running XQueries that we are shutting down
                    processMonitor.killAll(500);
                    //TODO : close other objects using varying methods ? set them to null ?
                    //cacheManager.something();
                    //xQueryPool.something();
                    //collectionConfigurationManager.something();
                    //collectionCache.something();
                    //xmlReaderPool.close();

                    if (isRecoveryEnabled()) {
                        journalManager.ifPresent(jm -> jm.flush(true, true));
                    }

                    final long waitStart = System.currentTimeMillis();
                    //Are there active brokers ?
                    if (activeBrokers.size() > 0) {
                        printSystemInfo();
                        LOG.info("Waiting " + maxShutdownWait + "ms for remaining threads to shut down...");
                        while (activeBrokers.size() > 0) {
                            try {
                                //Wait until they become inactive...
                                this.wait(1000);
                            } catch (final InterruptedException e) {
                                //nothing to be done
                            }

                            //...or force the shutdown
                            if (maxShutdownWait > -1 && System.currentTimeMillis() - waitStart > maxShutdownWait) {
                                LOG.warn("Not all threads returned. Forcing shutdown ...");
                                break;
                            }
                        }
                    }
                    LOG.debug("Calling shutdown ...");

                    if (pluginManager != null)
                        try {
                            pluginManager.stop(null);
                        } catch (final EXistException e) {
                            LOG.warn("Error during plugin manager shutdown: " + e.getMessage(), e);
                        }

                    // closing down external indexes
                    try {
                        indexManager.shutdown();
                    } catch (final DBException e) {
                        LOG.warn("Error during index shutdown: " + e.getMessage(), e);
                    }

                    //TODO : replace the following code by get()/release() statements ?
                    // WM: deadlock risk if not all brokers returned properly.
                    DBBroker broker = null;
                    if (inactiveBrokers.isEmpty())
                        try {
                            broker = createBroker();
                        } catch (final EXistException e) {
                            LOG.warn("could not create instance for shutdown. Giving up.");
                        }
                    else
                    //TODO : this broker is *not* marked as active and may be reused by another process !
                    //TODO : use get() then release the broker ?
                    // WM: deadlock risk if not all brokers returned properly.
                    //TODO: always createBroker? -dmitriy
                    {
                        broker = inactiveBrokers.peek();
                    }

                    //TOUNDERSTAND (pb) : shutdown() is called on only *one* broker ?
                    // WM: yes, the database files are shared, so only one broker is needed to close them for all
                    if (broker != null) {
                        broker.pushSubject(securityManager.getSystemSubject());
                        broker.shutdown();
                    }
                    collectionCacheMgr.deregisterCache(collectionCache);

                    // do not write a checkpoint if some threads did not return before shutdown
                    // there might be dirty transactions
                    transactionManager.shutdown();

                    // deregister JMX MBeans
                    AgentFactory.getInstance().closeDBInstance(this);

                    //Clear the living instances container
                    removeInstance(instanceName);

                    synchronized (readOnly) {
                        if (!readOnly) {
                            // release the lock on the data directory
                            dataLock.release();
                        }
                    }

                    //clearing additional resources, like ThreadLocal
                    clearThreadLocals();

                    LOG.info("shutdown complete !");

                    if (shutdownListener != null) {
                        shutdownListener.shutdown(instanceName, instancesCount());
                    }

                    statusReporter.terminate();
                    statusReporter = null;
                }
            } finally {
                // clear instance variables, just to be sure they will be garbage collected
                // the test suite restarts the db a few hundred times
                Configurator.clear(this);
                transactionManager = null;
                collectionCache = null;
                collectionCacheMgr = null;
                xQueryPool = null;
                processMonitor = null;
                collectionConfigurationManager = null;
                notificationService = null;
                indexManager = null;
                xmlReaderPool = null;
                shutdownListener = null;
                securityManager = null;
                notificationService = null;
                statusObservers.clear();
            }
        } finally {
            status.set(State.SHUTDOWN);
        }
    }

    public void addStatusObserver(final Observer statusObserver) {
        this.statusObservers.add(statusObserver);
    }

    public boolean removeStatusObserver(final Observer statusObserver) {
        return this.statusObservers.remove(statusObserver);
    }

    private void clearThreadLocals() {
        for (final Thread thread : Thread.getAllStackTraces().keySet()){
            try {
                cleanThreadLocalsForThread(thread);
            } catch (final EXistException ex) {
                LOG.warn("Could not clear ThreadLocals for thread: " + thread.getName());
            }
        }
    }

    private void cleanThreadLocalsForThread(final Thread thread) throws EXistException {
        try {
            // Get a reference to the thread locals table of the current thread
            final Field threadLocalsField = Thread.class.getDeclaredField("threadLocals");
            threadLocalsField.setAccessible(true);
            final Object threadLocalTable = threadLocalsField.get(thread);

            // Get a reference to the array holding the thread local variables inside the
            // ThreadLocalMap of the current thread
            final Class threadLocalMapClass = Class.forName("java.lang.ThreadLocal$ThreadLocalMap");
            final Field tableField = threadLocalMapClass.getDeclaredField("table");
            tableField.setAccessible(true);
            final Object table = tableField.get(threadLocalTable);

            // The key to the ThreadLocalMap is a WeakReference object. The referent field of this object
            // is a reference to the actual ThreadLocal variable
            final Field referentField = Reference.class.getDeclaredField("referent");
            referentField.setAccessible(true);

            for (int i = 0; i < Array.getLength(table); i++) {
                // Each entry in the table array of ThreadLocalMap is an Entry object
                // representing the thread local reference and its value
                final Object entry = Array.get(table, i);
                if (entry != null) {
                    // Get a reference to the thread local object and remove it from the table
                    final ThreadLocal threadLocal = (ThreadLocal)referentField.get(entry);
                    threadLocal.remove();
                }
            }
        } catch(final Exception e) {
            // We will tolerate an exception here and just log it
            throw new EXistException(e);
        }
    }

    public Optional<BrokerWatchdog> getWatchdog() {
        return watchdog;
    }

    //TODO : move this elsewhere
    public void triggerCheckpoint() {
        if(syncRequired) {
            return;
        }
        synchronized(this) {
            syncEvent = Sync.MAJOR;
            syncRequired = true;
            checkpoint = true;
        }
    }

    private Debuggee debuggee = null;

    public Debuggee getDebuggee() {
        synchronized(this) {
            if(debuggee == null) {
                debuggee = DebuggeeFactory.getInstance();
            }
        }

        return debuggee;
    }

    public Calendar getStartupTime() {
        return startupTime;
    }

    public void printSystemInfo() {
        try(final StringWriter sout = new StringWriter();
            final PrintWriter writer = new PrintWriter(sout)) {

            writer.println("SYSTEM INFO");
            writer.format("Database instance: %s\n", getId());
            writer.println("-------------------------------------------------------------------");
            watchdog.ifPresent(wd -> wd.dump(writer));
            DeadlockDetection.debug(writer);

            final String s = sout.toString();
            LOG.info(s);
            System.err.println(s);
        } catch(final IOException e) {
            LOG.error(e);
        }
    }

    private static class StatusReporter extends Observable implements Runnable {

        private String status;
        private volatile boolean terminate = false;

        public StatusReporter(final String status) {
            this.status = status;
        }

        public synchronized void setStatus(final String status) {
            this.status = status;
            this.setChanged();
            this.notifyObservers(status);
        }

        public synchronized void terminate() {
            this.terminate = true;
            this.notifyAll();
        }

        public void run() {
            while(!terminate) {
                synchronized(this) {
                    try {
                        wait(500);
                    } catch(final InterruptedException e) {
                        // nothing to do
                    }
                }
                this.setChanged();
                this.notifyObservers(status);
            }
        }
    }

    @Override
    public Path getStoragePlace() {
        return (Path)conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);
    }

    private final List<TriggerProxy<? extends DocumentTrigger>> documentTriggers = new ArrayList<>();
    private final List<TriggerProxy<? extends CollectionTrigger>> collectionTriggers = new ArrayList<>();

    @Override
    public List<TriggerProxy<? extends DocumentTrigger>> getDocumentTriggers() {
        return documentTriggers;
    }

    @Override
    public List<TriggerProxy<? extends CollectionTrigger>> getCollectionTriggers() {
        return collectionTriggers;
    }

    @Override
    public void registerDocumentTrigger(final Class<? extends DocumentTrigger> clazz) {
        documentTriggers.add(new DocumentTriggerProxy(clazz));
    }

    @Override
    public void registerCollectionTrigger(final Class<? extends CollectionTrigger> clazz) {
        collectionTriggers.add(new CollectionTriggerProxy(clazz));
    }

    public PluginsManager getPluginsManager() {
        return pluginManager;
    }

    protected MetaStorage metaStorage = null;

    public MetaStorage getMetaStorage() {
        return metaStorage;
    }

    /**
     * Represents a change involving {@link BrokerPool#inactiveBrokers}
     * or {@link BrokerPool#activeBrokers} or {@link DBBroker#referenceCount}
     *
     * Used for tracing broker leases
     */
    private static class TraceableBrokerLeaseChange extends TraceableStateChange<TraceableBrokerLeaseChange.BrokerInfo, TraceableBrokerLeaseChange.Change> {
        public enum Change {
            GET,
            RELEASE
        }

        public static class BrokerInfo {
            final String id;
            final int referenceCount;

            public BrokerInfo(final String id, final int referenceCount) {
                this.id = id;
                this.referenceCount = referenceCount;
            }
        }

        private TraceableBrokerLeaseChange(final Change change, final BrokerInfo brokerInfo) {
            super(change, brokerInfo);
        }

        @Override
        public String getId() {
            return getState().id;
        }

        @Override
        public String describeState() {
            return Integer.toString(getState().referenceCount);
        }

        static TraceableBrokerLeaseChange get(final BrokerInfo brokerInfo) {
            return new TraceableBrokerLeaseChange(Change.GET, brokerInfo);
        }

        static TraceableBrokerLeaseChange release(final BrokerInfo brokerInfo) {
            return new TraceableBrokerLeaseChange(Change.RELEASE, brokerInfo);
        }
    }
}

