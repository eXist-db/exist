/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import com.evolvedbinary.j8fu.fsm.AtomicFSM;
import com.evolvedbinary.j8fu.fsm.FSM;
import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import net.jcip.annotations.ThreadSafe;
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
import org.exist.repo.ClasspathHelper;
import org.exist.repo.ExistRepository;
import org.exist.scheduler.Scheduler;
import org.exist.scheduler.impl.QuartzSchedulerImpl;
import org.exist.scheduler.impl.SystemTaskJobImpl;
import org.exist.security.SecurityManager;
import org.exist.security.*;
import org.exist.security.internal.SecurityManagerImpl;
import org.exist.storage.blob.BlobStore;
import org.exist.storage.blob.BlobStoreImplService;
import org.exist.storage.blob.BlobStoreService;
import org.exist.storage.journal.JournalManager;
import org.exist.storage.lock.FileLockService;
import org.exist.storage.lock.LockManager;
import org.exist.storage.recovery.RecoveryManager;
import org.exist.storage.sync.Sync;
import org.exist.storage.sync.SyncTask;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.ShutdownListener;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.PerformanceStats;
import org.exist.xquery.PerformanceStatsService;
import org.exist.xquery.XQuery;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.nio.file.FileStore;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.evolvedbinary.j8fu.fsm.TransitionTable.transitionTable;
import static org.exist.util.ThreadUtils.nameInstanceThreadGroup;
import static org.exist.util.ThreadUtils.newInstanceThread;

/**
 * This class controls all available instances of the database.
 * Use it to configure, start and stop database instances.
 * You may have multiple instances defined, each using its own configuration.
 * To define multiple instances, pass an identification string to
 * {@link #configure(String, int, int, Configuration, Optional)}
 * and use {@link #getInstance(String)} to retrieve an instance.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@ConfigurationClass("pool")
public class BrokerPool extends BrokerPools implements BrokerPoolConstants, Database {

    private final static Logger LOG = LogManager.getLogger(BrokerPool.class);

    private final BrokerPoolServicesManager servicesManager = new BrokerPoolServicesManager();

    private StatusReporter statusReporter = null;

    private final XQuery xqueryService = new XQuery();

    private AtomicLazyVal<SaxonConfiguration> saxonConfiguration = new AtomicLazyVal<>(() -> SaxonConfiguration.loadConfiguration(this));

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

    private final int concurrencyLevel;
    private LockManager lockManager;

    /**
     * Root thread group for all threads related
     * to this instance.
     */
    private final ThreadGroup instanceThreadGroup;

    /**
     * State of the BrokerPool instance
     */
    private enum State {
        SHUTTING_DOWN_MULTI_USER_MODE,
        SHUTTING_DOWN_SYSTEM_MODE,
        SHUTDOWN,
        INITIALIZING,
        INITIALIZING_SYSTEM_MODE,
        INITIALIZING_MULTI_USER_MODE,
        OPERATIONAL
    }

    private enum Event {
        INITIALIZE,
        INITIALIZE_SYSTEM_MODE,
        INITIALIZE_MULTI_USER_MODE,
        READY,

        START_SHUTDOWN_MULTI_USER_MODE,
        START_SHUTDOWN_SYSTEM_MODE,
        FINISHED_SHUTDOWN,
    }

    @SuppressWarnings("unchecked")
    private final FSM<State, Event> status = new AtomicFSM<>(State.SHUTDOWN,
            transitionTable(State.class, Event.class)
                    .when(State.SHUTDOWN).on(Event.INITIALIZE).switchTo(State.INITIALIZING)
                    .when(State.INITIALIZING).on(Event.INITIALIZE_SYSTEM_MODE).switchTo(State.INITIALIZING_SYSTEM_MODE)
                    .when(State.INITIALIZING_SYSTEM_MODE).on(Event.INITIALIZE_MULTI_USER_MODE).switchTo(State.INITIALIZING_MULTI_USER_MODE)
                    .when(State.INITIALIZING_MULTI_USER_MODE).on(Event.READY).switchTo(State.OPERATIONAL)
                    .when(State.OPERATIONAL).on(Event.START_SHUTDOWN_MULTI_USER_MODE).switchTo(State.SHUTTING_DOWN_MULTI_USER_MODE)
                    .when(State.SHUTTING_DOWN_MULTI_USER_MODE).on(Event.START_SHUTDOWN_SYSTEM_MODE).switchTo(State.SHUTTING_DOWN_SYSTEM_MODE)
                    .when(State.SHUTTING_DOWN_SYSTEM_MODE).on(Event.FINISHED_SHUTDOWN).switchTo(State.SHUTDOWN)
            .build()
    );


    public String getStatus() {
        return status.getCurrentState().name();
    }

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
    private final AtomicBoolean readOnly = new AtomicBoolean();

    @ConfigurationFieldAsAttribute("pageSize")
    private final int pageSize;

    private FileLockService dataLock;

    /**
     * The journal manager of the database instance.
     */
    private Optional<JournalManager> journalManager = Optional.empty();

    /**
     * The transaction manager of the database instance.
     */
    private TransactionManager transactionManager = null;

    /**
     * The Blob Store of the database instance.
     */
    private BlobStoreService blobStoreService;

    /**
     * Delay (in ms) for running jobs to return when the database instance shuts down.
     */
    @ConfigurationFieldAsAttribute("wait-before-shutdown")
    private final long maxShutdownWait;

    /**
     * The scheduler for the database instance.
     */
    @ConfigurationFieldAsAttribute("scheduler")
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
     * The global notification service used to subscribe
     * to document updates.
     */
    private NotificationService notificationService = null;

    /**
     * The cache in which the database instance may store items.
     */

    private DefaultCacheManager cacheManager;

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

    private StartupTriggersManager startupTriggersManager;

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
            final Optional<Observer> statusObserver) {

        final NumberFormat nf = NumberFormat.getNumberInstance();

        this.classLoader = Thread.currentThread().getContextClassLoader();
        this.instanceName = instanceName;
        this.instanceThreadGroup = new ThreadGroup(nameInstanceThreadGroup(instanceName));

        this.maxShutdownWait = conf.getProperty(BrokerPool.PROPERTY_SHUTDOWN_DELAY, DEFAULT_MAX_SHUTDOWN_WAIT);
        LOG.info("database instance '{}' will wait  {} ms during shutdown", instanceName, nf.format(this.maxShutdownWait));

        this.recoveryEnabled = conf.getProperty(PROPERTY_RECOVERY_ENABLED, true);
        LOG.info("database instance '{}' is enabled for recovery : {}", instanceName, this.recoveryEnabled);

        this.minBrokers = conf.getProperty(PROPERTY_MIN_CONNECTIONS, minBrokers);
        this.maxBrokers = conf.getProperty(PROPERTY_MAX_CONNECTIONS, maxBrokers);
        LOG.info("database instance '{}' will have between {} and {} brokers", instanceName, nf.format(this.minBrokers), nf.format(this.maxBrokers));

        this.majorSyncPeriod = conf.getProperty(PROPERTY_SYNC_PERIOD, DEFAULT_SYNCH_PERIOD);
        LOG.info("database instance '{}' will be synchronized every {} ms", instanceName, nf.format(/*this.*/majorSyncPeriod));

        // convert from bytes to megabytes: 1024 * 1024
        this.diskSpaceMin = 1024L * 1024L * conf.getProperty(BrokerPool.DISK_SPACE_MIN_PROPERTY, DEFAULT_DISK_SPACE_MIN);

        this.pageSize = conf.getProperty(PROPERTY_PAGE_SIZE, DEFAULT_PAGE_SIZE);

        //Configuration is valid, save it
        this.conf = conf;

        this.concurrencyLevel = Math.max(maxBrokers, 2 * Runtime.getRuntime().availableProcessors());

        statusObserver.ifPresent(this.statusObservers::add);

        this.watchdog = Optional.ofNullable(System.getProperty(BrokerWatchdog.TRACE_BROKERS_PROPERTY_NAME))
                .filter(value -> "yes".equals(value))
                .map(value -> new BrokerWatchdog());
    }

    /**
     * Initializes the database instance.
     *
     * @throws EXistException
     * @throws DatabaseConfigurationException
     */
    void initialize() throws EXistException, DatabaseConfigurationException {
        try {
            _initialize();
        } catch(final Throwable e) {
            // remove that file lock we may have acquired in canReadDataDir

            if (dataLock != null && !readOnly.get()) {
                dataLock.release();
            }

            if (e instanceof EXistException existException) {
                throw existException;
            } else if(e instanceof DatabaseConfigurationException databaseConfigurationException) {
                throw databaseConfigurationException;
            } else {
                throw new EXistException(e);
            }
        }
    }

    private void _initialize() throws EXistException, DatabaseConfigurationException {
        this.lockManager = new LockManager(conf, concurrencyLevel);

        //Flag to indicate that we are initializing
        status.process(Event.INITIALIZE);

        if(LOG.isDebugEnabled()) {
            LOG.debug("initializing database instance '{}'...", instanceName);
        }

        // register core broker pool services
        this.scheduler = servicesManager.register(new QuartzSchedulerImpl(this));

        // NOTE: this must occur after the scheduler, and before any other service which requires access to the data directory
        this.dataLock = servicesManager.register(new FileLockService("dbx_dir.lck", BrokerPool.PROPERTY_DATA_DIR, NativeBroker.DEFAULT_DATA_DIR));

        this.securityManager = servicesManager.register(new SecurityManagerImpl(this));

        this.cacheManager = servicesManager.register(new DefaultCacheManager(this));
        this.xQueryPool = servicesManager.register(new XQueryPool());
        this.processMonitor = servicesManager.register(new ProcessMonitor());
        this.xqueryStats = servicesManager.register(new PerformanceStatsService());
        final XMLReaderObjectFactory xmlReaderObjectFactory = servicesManager.register(new XMLReaderObjectFactory());
        this.xmlReaderPool = servicesManager.register(new XMLReaderPool(xmlReaderObjectFactory, maxBrokers, 0));
        final int bufferSize = Optional.of(conf.getInteger(PROPERTY_COLLECTION_CACHE_SIZE))
                .filter(size -> size != -1)
                .orElse(DEFAULT_COLLECTION_BUFFER_SIZE);
        this.collectionCache = servicesManager.register(new CollectionCache());
        this.notificationService = servicesManager.register(new NotificationService());

        this.journalManager = recoveryEnabled ? Optional.of(new JournalManager()) : Optional.empty();
        journalManager.ifPresent(servicesManager::register);

        final SystemTaskManager systemTaskManager = servicesManager.register(new SystemTaskManager(this));
        this.transactionManager = servicesManager.register(new TransactionManager(this, journalManager, systemTaskManager));

        this.blobStoreService = servicesManager.register(new BlobStoreImplService());

        this.symbols = servicesManager.register(new SymbolTable());

        this.expathRepo = Optional.of(new ExistRepository());
        expathRepo.ifPresent(servicesManager::register);
        servicesManager.register(new ClasspathHelper());

        this.indexManager = servicesManager.register(new IndexManager(this));

        //Get a manager to handle further collections configuration
        this.collectionConfigurationManager = servicesManager.register(new CollectionConfigurationManager(this));

        this.startupTriggersManager = servicesManager.register(new StartupTriggersManager());

        // this is just used for unit tests
        final BrokerPoolService testBrokerPoolService = (BrokerPoolService) conf.getProperty("exist.testBrokerPoolService");
        if (testBrokerPoolService != null) {
            servicesManager.register(testBrokerPoolService);
        }

        //configure the registered services
        try {
            servicesManager.configureServices(conf);
        } catch(final BrokerPoolServiceException e) {
            throw new EXistException(e);
        }

        // calculate how much memory is reserved for caches to grow
        final Runtime rt = Runtime.getRuntime();
        final long maxMem = rt.maxMemory();
        final long minFree = maxMem / 5;
        reservedMem = cacheManager.getTotalMem() + collectionCache.getMaxCacheSize() + minFree;
        LOG.debug("Reserved memory: {}; max: {}; min: {}", reservedMem, maxMem, minFree);

        //prepare the registered services, before entering system (single-user) mode
        try {
            servicesManager.prepareServices(this);
        } catch(final BrokerPoolServiceException e) {
            throw new EXistException(e);
        }

        //setup database synchronization job
        if(majorSyncPeriod > 0) {
            final SyncTask syncTask = new SyncTask();
            syncTask.configure(conf, null);
            scheduler.createPeriodicJob(2500, new SystemTaskJobImpl(SyncTask.getJobName(), syncTask), 2500);
        }

        try {
            statusReporter = new StatusReporter(SIGNAL_STARTUP);
            statusObservers.forEach(statusReporter::addObserver);

            final Thread statusThread = newInstanceThread(this, "startup-status-reporter", statusReporter);
            statusThread.start();

            // statusReporter may have to be terminated or the thread can/will hang.
            try {
                final boolean exportOnly = conf.getProperty(PROPERTY_EXPORT_ONLY, false);

                // If the initialization fails after transactionManager has been created this method better cleans up
                // or the FileSyncThread for the journal can/will hang.
                try {

                    // Enter System Mode
                    try(final DBBroker systemBroker = get(Optional.of(securityManager.getSystemSubject()))) {

                        status.process(Event.INITIALIZE_SYSTEM_MODE);

                        if(isReadOnly()) {
                            journalManager.ifPresent(JournalManager::disableJournalling);
                        }

                        try(final Txn transaction = transactionManager.beginTransaction()) {
                            servicesManager.startPreSystemServices(systemBroker, transaction);
                            transaction.commit();
                        } catch(final BrokerPoolServiceException e) {
                            throw new EXistException(e);
                        }

                        //Run the recovery process
                        boolean recovered = false;
                        if(isRecoveryEnabled()) {
                            recovered = runRecovery(systemBroker);
                            //TODO : extract the following from this block ? What if we are not transactional ? -pb
                            if(!recovered) {
                                try {
                                    if(systemBroker.getCollection(XmldbURI.ROOT_COLLECTION_URI) == null) {
                                        final Txn txn = transactionManager.beginTransaction();
                                        try {
                                            systemBroker.getOrCreateCollection(txn, XmldbURI.ROOT_COLLECTION_URI);
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
                                initialiseSystemCollections(systemBroker);
                            } catch(final PermissionDeniedException pde) {
                                LOG.error(pde.getMessage(), pde);
                                throw new EXistException(pde.getMessage(), pde);
                            }
                        }

                        statusReporter.setStatus(SIGNAL_READINESS);

                        try(final Txn transaction = transactionManager.beginTransaction()) {
                            servicesManager.startSystemServices(systemBroker, transaction);
                            transaction.commit();
                        } catch(final BrokerPoolServiceException e) {
                            throw new EXistException(e);
                        }

                        //If necessary, launch a task to repair the DB
                        //TODO : merge this with the recovery process ?
                        if(isRecoveryEnabled() && recovered) {
                            if(!exportOnly) {
                                reportStatus("Reindexing database files...");
                                try {
                                    systemBroker.repair();
                                } catch(final PermissionDeniedException e) {
                                    LOG.warn("Error during recovery: {}", e.getMessage(), e);
                                }
                            }

                            if((Boolean) conf.getProperty(PROPERTY_RECOVERY_CHECK)) {
                                final ConsistencyCheckTask task = new ConsistencyCheckTask();
                                final Properties props = new Properties();
                                props.setProperty("backup", "no");
                                props.setProperty("output", "sanity");
                                task.configure(conf, props);
                                try (final Txn transaction = transactionManager.beginTransaction()) {
                                    task.execute(systemBroker, transaction);
                                    transaction.commit();
                                }
                            }
                        }

                        //OK : the DB is repaired; let's make a few RW operations
                        statusReporter.setStatus(SIGNAL_WRITABLE);

                        //initialize configurations watcher trigger
                        if(!exportOnly) {
                            try {
                                initialiseTriggersForCollections(systemBroker, XmldbURI.SYSTEM_COLLECTION_URI);
                            } catch(final PermissionDeniedException pde) {
                                //XXX: do not catch exception!
                                LOG.error(pde.getMessage(), pde);
                            }
                        }

                        // remove temporary docs
                        try {
                            systemBroker.cleanUpTempResources(true);
                        } catch(final PermissionDeniedException pde) {
                            LOG.error(pde.getMessage(), pde);
                        }

                        sync(systemBroker, Sync.MAJOR);

                        // we have completed all system mode operations
                        // we can now prepare those services which need
                        // system mode before entering multi-user mode
                        try(final Txn transaction = transactionManager.beginTransaction()) {
                            servicesManager.startPreMultiUserSystemServices(systemBroker, transaction);
							transaction.commit();
                        } catch(final BrokerPoolServiceException e) {
                            throw new EXistException(e);
                        }
                    }

                    //Create a default configuration file for the root collection
                    //TODO : why can't we call this from within CollectionConfigurationManager ?
                    //TODO : understand why we get a test suite failure
                    //collectionConfigurationManager.checkRootCollectionConfigCollection(broker);
                    //collectionConfigurationManager.checkRootCollectionConfig(broker);

                    //Create the minimal number of brokers required by the configuration
                    for(int i = 1; i < minBrokers; i++) {
                        createBroker();
                    }

                    status.process(Event.INITIALIZE_MULTI_USER_MODE);

                    // register some MBeans to provide access to this instance
                    AgentFactory.getInstance().initDBInstance(this);

                    if(LOG.isDebugEnabled()) {
                        LOG.debug("database instance '{}' initialized", instanceName);
                    }

                    servicesManager.startMultiUserServices(this);

                    status.process(Event.READY);

                    statusReporter.setStatus(SIGNAL_STARTED);
                } catch(final Throwable t) {
                    transactionManager.shutdown();
                    throw t;
                }
            } catch(final EXistException e) {
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
                collection.setPermissions(sysBroker, permissions);
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
     * Get the LockManager for this database instance
     *
     * @return The lock manager
     */
    public LockManager getLockManager() {
        return lockManager;
    }

    /**
     * Run a database recovery if required. This method is called once during
     * startup from {@link org.exist.storage.BrokerPool}.
     *
     * @param broker the database broker
     * @return true if recovery was run, false otherwise
     * @throws EXistException if a database error occurs
     */
    public boolean runRecovery(final DBBroker broker) throws EXistException {
        final boolean forceRestart = conf.getProperty(PROPERTY_RECOVERY_FORCE_RESTART, false);
        if(LOG.isDebugEnabled()) {
            LOG.debug("ForceRestart = {}", forceRestart);
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
     *
     * @return the classloader
     */
    public ClassLoader getClassLoader() {
        return this.classLoader;
    }

    /**
     * Whether or not the database instance is operational, i.e. initialization
     * has completed
     *
     * @return <code>true</code> if the database instance is operational
     */
    public boolean isOperational() {
        return status.getCurrentState() == State.OPERATIONAL;
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

    @Override
    public ThreadGroup getThreadGroup() {
        return instanceThreadGroup;
    }

    /**
     * Returns the number of brokers currently serving requests for the database instance.
     *
     * @return The brokers count
     * @deprecated use countActiveBrokers
     */
    //TODO : rename as getActiveBrokers ?
    @Deprecated
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

    @Override
    public BlobStore getBlobStore() {
        return blobStoreService.getBlobStore();
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
        return !readOnly.get() && recoveryEnabled;
    }

    @Override
    public boolean isReadOnly() {
        final boolean isReadOnly = readOnly.get();
        if (!isReadOnly) {
            final long freeSpace = FileUtils.measureFileStore(dataLock.getFile(), FileStore::getUsableSpace);
            if (freeSpace != -1 && freeSpace < diskSpaceMin) {
                LOG.fatal("Partition containing DATA_DIR: {} is running out of disk space [minimum: {} free: {}]. Switching eXist-db into read-only mode to prevent data loss!", dataLock.getFile().toAbsolutePath().toString(), diskSpaceMin, freeSpace);
                setReadOnly();
            }
        }
        return readOnly.get();
    }

    public void setReadOnly() {
        if (readOnly.compareAndSet(false, true)) {
            LOG.warn("Switched database into read-only mode!");
        }
    }

    public boolean isInServiceMode() {
        return inServiceMode;
    }

    @Override
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
     *
     * @deprecated Use {@link #getXmlReaderPool()} instead
     */
    @Deprecated
    public XMLReaderPool getParserPool() {
        return xmlReaderPool;
    }

    /**
     * Returns a pool in which the database instance's readers are stored.
     *
     * @return The pool
     */
    public XMLReaderPool getXmlReaderPool() {
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
     * @throws EXistException if the broker cannot be created
     */
    protected DBBroker createBroker() throws EXistException {
        //TODO : in the future, don't pass the whole configuration, just the part relevant to brokers
        final DBBroker broker = BrokerFactory.getInstance(this, this.getConfiguration());
        inactiveBrokers.push(broker);
        brokersCount++;
        broker.setId(broker.getClass().getName() + '_' + instanceName + "_" + brokersCount);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Created broker '{} for database instance '{}'", broker.getId(), instanceName);
        }
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
            sb.append(System.lineSeparator());

            for(final Entry<Thread, DBBroker> entry : activeBrokers.entrySet()) {
                sb.append(entry.getKey());
                sb.append(" = ");
                sb.append(entry.getValue());
                sb.append(System.lineSeparator());
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
                Thread.currentThread().interrupt();
                LOG.error("Interrupt detected");
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
            broker.prepare();

            //activate the broker
            activeBrokers.put(Thread.currentThread(), broker);

            if(LOG.isTraceEnabled()) {
                LOG.trace("+++ {}{}", Thread.currentThread(), Stacktrace.top(Thread.currentThread().getStackTrace(), Stacktrace.DEFAULT_STACK_TOP));
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
                    LOG.error("Broker {} is already in the inactive list!!!", broker.getId());
                    return;
                }
            }

            if(activeBrokers.remove(Thread.currentThread()) == null) {
                LOG.error("release() has been called from the wrong thread for broker {}", broker.getId());
                // Cleanup the state of activeBrokers
                for(final Entry<Thread, DBBroker> activeBroker : activeBrokers.entrySet()) {
                    if(activeBroker.getValue() == broker) {
                        final String msg = "release() has been called from '" + Thread.currentThread() + "', but occupied at '" + activeBroker.getKey() + "'.";
                        final EXistException ex = new EXistException(msg);
                        LOG.error(msg, ex);
                        activeBrokers.remove(activeBroker.getKey());
                        break;
                    }
                }
            } else {
                if(LOG.isTraceEnabled()) {
                    LOG.trace("--- {}{}", Thread.currentThread(), Stacktrace.top(Thread.currentThread().getStackTrace(), Stacktrace.DEFAULT_STACK_TOP));
                }
            }
            
            Subject lastUser = broker.popSubject();

            //guard to ensure that the broker has popped all its subjects
            if(lastUser == null || broker.getCurrentSubject() != null) {
                LOG.warn("Broker {} was returned with extraneous Subjects, cleaning...", broker.getId(), new IllegalStateException("DBBroker pushSubject/popSubject mismatch").fillInStackTrace());
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
            if(activeBrokers.isEmpty()) {
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
            if(!activeBrokers.isEmpty()) {
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
        broker.prepare();
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
     * NOTE: This method should not be called concurrently from multiple threads.
     *
     * @param broker    A broker responsible for executing the job
     * @param syncEvent One of {@link org.exist.storage.sync.Sync}
     */
    public void sync(final DBBroker broker, final Sync syncEvent) {

        /**
         * Database Systems - The Complete Book (Second edition)
         * § 17.4.1 The Undo/Redo Rules
         *
         * The constraints that an undo/redo logging system must follow are summarized by the following rule:
         *     * UR1  Before modifying any database element X on disk because of changes
         *            made by some transaction T, it is necessary that the update record
         *            <T,X,v,w> appear on disk.
         */
        journalManager.ifPresent(manager -> manager.flush(true, true));

        // sync various DBX files
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
     * Schedules a system maintenance task for the database instance. If the database is idle,
     * the task will be run immediately. Otherwise, the task will be deferred
     * until all running threads have returned.
     *
     * @param task The task
     */
    //TOUNDERSTAND (pb) : synchronized, so... "schedules" or, rather, "executes" ?
    public void triggerSystemTask(final SystemTask task) {
        final State s = status.getCurrentState();
        if(s == State.SHUTTING_DOWN_MULTI_USER_MODE || s == State.SHUTTING_DOWN_SYSTEM_MODE) {
            LOG.info("Skipping SystemTask: '{}' as database is shutting down...", task.getName());
            return;
        } else if(s == State.SHUTDOWN) {
            LOG.warn("Unable to execute SystemTask: '{}' as database is shut down!", task.getName());
            return;
        }

        transactionManager.triggerSystemTask(task);
    }

    /**
     * Shuts downs the database instance
     */
    public void shutdown() {
        shutdown(false);
    }

    /**
     * Returns true if the BrokerPool is in the
     * process of shutting down.
     *
     * @return true if the BrokerPool is shutting down.
     */
    public boolean isShuttingDown() {
        final State s = status.getCurrentState();
        return s == State.SHUTTING_DOWN_MULTI_USER_MODE
                || s == State.SHUTTING_DOWN_SYSTEM_MODE;
    }

    /**
     * Returns true if the BrokerPool is either in the
     * process of shutting down, or has already shutdown.
     *
     * @return true if the BrokerPool is shutting down or
     *     has shutdown.
     */
    public boolean isShuttingDownOrDown() {
        final State s = status.getCurrentState();
        return s == State.SHUTTING_DOWN_MULTI_USER_MODE
                || s == State.SHUTTING_DOWN_SYSTEM_MODE
                || s == State.SHUTDOWN;
    }

    /**
     * Returns true of the BrokerPool is shutdown.
     *
     * @return true if the BrokerPool is shutdown.
     */
    public boolean isShutDown() {
        return status.getCurrentState() == State.SHUTDOWN;
    }

    /**
     * Shuts downs the database instance
     *
     * @param killed <code>true</code> when the JVM is (cleanly) exiting
     */
    public void shutdown(final boolean killed) {
        shutdown(killed, BrokerPools::removeInstance);
    }

    void shutdown(final boolean killed, final Consumer<String> shutdownInstanceConsumer) {

        try {
            status.process(Event.START_SHUTDOWN_MULTI_USER_MODE);
        } catch(final IllegalStateException e) {
            // we are not operational!
            LOG.warn(e);
            return;
        }

        // notify any BrokerPoolServices that we are about to shutdown
        try {
            // instruct database services that we are about to stop multi-user mode
            servicesManager.stopMultiUserServices(this);
        } catch(final BrokerPoolServicesManagerException e) {
            for(final BrokerPoolServiceException bpse : e.getServiceExceptions()) {
                LOG.error(bpse.getMessage(), bpse);
            }
        }

        try {
            status.process(Event.START_SHUTDOWN_SYSTEM_MODE);
        } catch(final IllegalStateException e) {
            // we are not in SHUTTING_DOWN_MULTI_USER_MODE!
            LOG.warn(e);
            return;
        }

        try {
            LOG.info("Database is shutting down ...");

            processMonitor.stopRunningJobs();

            //Shutdown the scheduler
            scheduler.shutdown(true);

            try {
                statusReporter = new StatusReporter(SIGNAL_SHUTDOWN);
                statusObservers.forEach(statusReporter::addObserver);

                synchronized (this) {
                    final Thread statusThread = newInstanceThread(this, "shutdown-status-reporter", statusReporter);
                    statusThread.start();

                    // DW: only in debug mode
                    if (LOG.isDebugEnabled()) {
                        notificationService.debug();
                    }

                    //Notify all running tasks that we are shutting down

                    //Notify all running XQueries that we are shutting down
                    processMonitor.killAll(500);

                    if (isRecoveryEnabled()) {
                        journalManager.ifPresent(jm -> jm.flush(true, true));
                    }

                    final long waitStart = System.currentTimeMillis();
                    //Are there active brokers ?
                    if (!activeBrokers.isEmpty()) {
                        printSystemInfo();
                        LOG.info("Waiting {}ms for remaining threads to shut down...", maxShutdownWait);
                        while (!activeBrokers.isEmpty()) {
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

                    try {
                        if (broker != null) {
                            broker.prepare();
                            broker.pushSubject(securityManager.getSystemSubject());
                        }

                        try {
                            // instruct all database services to stop
                            servicesManager.stopSystemServices(broker);
                        } catch(final BrokerPoolServicesManagerException e) {
                           for(final BrokerPoolServiceException bpse : e.getServiceExceptions()) {
                               LOG.error(bpse.getMessage(), bpse);
                           }
                        }

                        //TOUNDERSTAND (pb) : shutdown() is called on only *one* broker ?
                        // WM: yes, the database files are shared, so only one broker is needed to close them for all
                        broker.shutdown();

                    } finally {
                        if(broker != null) {
                            broker.popSubject();
                        }
                    }

                    collectionCache.invalidateAll();

                    // final notification to database services to shutdown
                    servicesManager.shutdown();

                    // remove all remaining inactive brokers as we have shutdown now and no longer need those
                    inactiveBrokers.clear();

                    // deregister JMX MBeans
                    AgentFactory.getInstance().closeDBInstance(this);

                    //Clear the living instances container
                    shutdownInstanceConsumer.accept(instanceName);
                    if (!readOnly.get()) {
                        // release the lock on the data directory
                        dataLock.release();
                    }

                    //clearing additional resources, like ThreadLocal
                    clearThreadLocals();

                    LOG.info("shutdown complete !");

                    if (shutdownListener != null) {
                        shutdownListener.shutdown(instanceName, instancesCount());
                    }
                }
            } finally {
                // clear instance variables, just to be sure they will be garbage collected
                // the test suite restarts the db a few hundred times
                Configurator.clear(this);
                transactionManager = null;
                collectionCache = null;
                xQueryPool = null;
                processMonitor = null;
                collectionConfigurationManager = null;
                notificationService = null;
                indexManager = null;
                xmlReaderPool = null;
                shutdownListener = null;
                securityManager = null;

                if (lockManager != null) {
                    lockManager.getLockTable().shutdown();
                    lockManager = null;
                }

                notificationService = null;
                statusObservers.clear();
                startupTriggersManager = null;
                statusReporter.terminate();
                statusReporter = null;

//                instanceThreadGroup.destroy();
            }
        } finally {
            status.process(Event.FINISHED_SHUTDOWN);
        }
    }

    public void addStatusObserver(final Observer statusObserver) {
        this.statusObservers.add(statusObserver);
    }

    public boolean removeStatusObserver(final Observer statusObserver) {
        return this.statusObservers.remove(statusObserver);
    }

    private void clearThreadLocals() {
        for (final Thread thread : Thread.getAllStackTraces().keySet()) {
            try {
                cleanThreadLocalsForThread(thread);
            } catch (final EXistException ex) {
                if (LOG.isDebugEnabled()) {
                    LOG.warn("Could not clear ThreadLocals for thread: {}", thread.getName());
                }
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

            final String s = sout.toString();
            LOG.info(s);
            System.err.println(s);
        } catch(final IOException e) {
            LOG.error(e);
        }
    }

    @ThreadSafe
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

        public void terminate() {
            this.terminate = true;
            synchronized(this) {
                this.notifyAll();
            }
        }

        @Override
        public void run() {
            while(!terminate) {
                synchronized(this) {
                    try {
                        wait(500);
                    } catch(final InterruptedException e) {
                        // nothing to do
                    }
                    this.setChanged();
                    this.notifyObservers(status);
                }
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

    public net.sf.saxon.Configuration getSaxonConfiguration() {
        return saxonConfiguration.get().getConfiguration();
    }

    public net.sf.saxon.s9api.Processor getSaxonProcessor() {
        return saxonConfiguration.get().getProcessor();
    }

    /**
     * Represents a change involving {@link BrokerPool#inactiveBrokers}
     * or {@link BrokerPool#activeBrokers} or {@link DBBroker#getReferenceCount}
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

