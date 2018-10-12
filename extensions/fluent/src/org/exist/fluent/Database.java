package org.exist.fluent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.*;
import org.exist.collections.*;
import org.exist.collections.Collection;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.TextImpl;
import org.exist.security.*;
import org.exist.storage.*;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.*;

import static org.exist.util.ThreadUtils.newInstanceThread;

/**
 * <p>The global entry point to an embedded instance of the <a href='http://exist-db.org'>eXist </a>database.
 * The static methods on this class control the lifecycle of the database connection.  It follows that
 * there can be only one embedded database running in the JVM (or rather one per classloader, but
 * that would probably be a bit confusing).  To gain access to the contents of the database, you
 * need to acquire a handle instance by logging in.  All operations performed based on that instance
 * will be executed using the permissions of the user associated with that instance.  You can have
 * any number of instances (including multiple ones for the same user), but cannot mix resources
 * obtained from different instances.  There is no need to explicitly release instances.</p>
 * 
 * <p>Here's a short example of how to start up the database, perform a query, and shut down:
 * <pre> Database.startup(new File("conf.xml"));
 * Database db = Database.login("admin", "passwd");
 * for (String name : db.getFolder("/").query().all("//user/@name").values())
 *   System.out.println("user: " + name);
 * Database.shutdown();</pre></p>
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.26 $ ($Date: 2006/09/04 06:09:05 $)
 */
public class Database {
	
	private static final Logger LOG = LogManager.getLogger(Database.class);

	/**
	 * Start up the database, configured using the given config file.  This method must be
	 * called precisely once before making use of any facilities offered in this package.  The
	 * configuration file is typically called 'conf.xml' and you can find a sample one in the root
	 * directory of eXist's distribution.
	 *
	 * @param configFile the config file that specifies the database to use
	 * @throws IllegalStateException if the database has already been started
	 */
	public static void startup(Path configFile) {
		try {
			if (isStarted()) throw new IllegalStateException("database already started");
			configFile = configFile.toAbsolutePath();
			Configuration config = new Configuration(FileUtils.fileName(configFile), Optional.of(configFile.getParent().toAbsolutePath()));
			BrokerPool.configure(dbName, 1, 5, config);
			pool = BrokerPool.getInstance(dbName);
			txManager = pool.getTransactionManager();
			configureRootCollection(configFile);
			defragmenter.start();
			QueryService.statistics().reset();
		} catch (DatabaseConfigurationException e) {
			throw new DatabaseException(e);
		} catch (EXistException e) {
			throw new DatabaseException(e);
		}
	}

	static void configureRootCollection(Path configFile) {
		Database db = new Database(pool.getSecurityManager().getSystemSubject());
		StringBuilder configXml = new StringBuilder();
		configXml.append("<collection xmlns='http://exist-db.org/collection-config/1.0'>");
		configXml.append(ListenerManager.getTriggerConfigXml());
		{
			XMLDocument configDoc = db.getFolder("/").documents().load(Name.generate(db), Source.xml(configFile.toFile()));
			Node indexNode = configDoc.query().optional("/exist/indexer/index").node();
			if (indexNode.extant()) configXml.append(indexNode.toString());
			configDoc.delete();
		}
		configXml.append("</collection>");
		
		// If the config is already *exactly* how we want it, no need to reload and reindex.
		try {
			Node currentConfig =
				db.getFolder(XmldbURI.CONFIG_COLLECTION + Database.ROOT_PREFIX).documents()
					.get(CollectionConfiguration.DEFAULT_COLLECTION_CONFIG_FILE).xml().root();
			if (currentConfig.query().presub().single("deep-equal(., $1)", configXml.toString()).booleanValue()) return;
		} catch (DatabaseException e) {
			// fall through
		}
		
		// Now force reload and reindex so it'll pick up the new settings.
		Transaction tx = db.requireTransactionWithBroker();
		try {
			pool.getConfigurationManager().addConfiguration(tx.tx, tx.broker, tx.broker.getCollection(XmldbURI.ROOT_COLLECTION_URI), configXml.toString());
			tx.commit();
			DBBroker broker = db.acquireBroker();
			try {
				broker.reindexCollection(XmldbURI.ROOT_COLLECTION_URI);
			} finally {
				db.releaseBroker(broker);
			}
		} catch (final PermissionDeniedException | IOException | CollectionConfigurationException e) {
			throw new DatabaseException(e);
		} finally {
			tx.abortIfIncomplete();
		}
	}
	
	/**
	 * Shut down the database connection.  If the database is not started, do nothing.
	 */
	public static void shutdown() {
		if (pool == null) return;
		defragmenter.stop();
		pool.shutdown();
		pool = null;
	}
	
	/**
	 * Ensure the database is started.  If the database is not started, start it with the
	 * given config file.  If it is already started, make sure it was started with the same
	 * config file.
	 * 
	 * @param configFile the config file that specifies the database to use
	 * @throws IllegalStateException if the database was already started with a different config file
	 * 
	 * @deprecated Please use a combination of {@link #isStarted()} and {@link #startup(Path)}.
	 */
	@Deprecated public static void ensureStarted(final Path configFile) {
		if (isStarted()) {
			final Optional<Path> currentPath = pool.getConfiguration().getConfigFilePath();
			if(!currentPath.map(cp -> cp.toAbsolutePath().equals(configFile.toAbsolutePath())).orElse(false)) {
				throw new IllegalStateException("database already started with different configuration " + currentPath);
			}
		} else {
			startup(configFile);
		}
	}
	
	/**
	 * Return whether the database has been started and is currently running in this JVM.  This will
	 * be the case if {@link #startup(Path)} or {@link #ensureStarted(Path)} was previously called
	 * successfully and {@link #shutdown()} was not yet called.
	 *
	 * @return <code>true</code> if the database has been started with any configuration file
	 */
	public static boolean isStarted() {
		return BrokerPool.isConfigured(dbName);
	}
	
	/**
	 * Flush the contents of the database to disk.  This ensures that all transactions are written out
	 * and the state of the database is synced.  It shouldn't be necessary any more with the newly
	 * implemented transaction recovery and this method will probably be deprecated in the future.
	 */
	public static void flush() {
		if (!BrokerPool.isConfigured(dbName)) throw new IllegalStateException("database not started");
		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
			broker.flush();
			broker.sync(Sync.MAJOR);
		} catch (EXistException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Verify the internal consistency of the database's data structures.  Log a fatal message if the
	 * database is corrupted, as well as error-level messages for all the problems found.  If the
	 * database is corrupted, you can try using admin tools to reindex it, or back it up and restore
	 * it.  However, there's a good chance it's unrecoverable.
	 * 
	 * @return <code>true</code> if the database's internal data structures are consistent,
	 * 		<code>false</code> if the database is corrupted
	 */
	public static boolean checkConsistency() {
		synchronized(pool) {
			try {
				DBBroker broker = pool.enterServiceMode(pool.getSecurityManager().getSystemSubject());
				try {
					List<ErrorReport> errors = new ConsistencyCheck(broker, false, false).checkAll(NULL_PROGRESS_CALLBACK);
					if (errors.isEmpty()) return true;
					LOG.fatal("database corrupted");
					for (ErrorReport error : errors) LOG.error(error.toString().replace("\n", " "));
					return false;
				} finally {
					pool.exitServiceMode(pool.getSecurityManager().getSystemSubject());
				}
            } catch (TerminatedException e) {
                throw new DatabaseException(e);
			} catch (PermissionDeniedException e) {
				throw new DatabaseException(e);
			}
		}
	}
	
	private static final ConsistencyCheck.ProgressCallback NULL_PROGRESS_CALLBACK = new ConsistencyCheck.ProgressCallback() {
		public void error(ErrorReport error) {}
		public void startCollection(String path) {}
        public void startDocument(String name, int current, int count) {}
	};
	
	/**
	 * Login to obtain access to the database.  The password should be passed in the clear.
	 * If a user does not have a password set, pass in <code>null</code>.
	 * Note that all newly created databases have a user <code>admin</code> with no password set.
	 *
	 * @param username the username of the user being logged in
	 * @param password the password corresponding to that user name, or <code>null</code> if none
	 * @return an instance of the database configured for access by the given user
	 * @throws DatabaseException if the user could not be logged in
	 */
	public static Database login(String username, String password) {
		Subject user;
		try {
			user = pool.getSecurityManager().authenticate(username, password);
		} catch (AuthenticationException e) {
			throw new DatabaseException(e.getMessage(),e);
		}
		return new Database(user);
	}
	
    /**
     * Get database if was already login in current thread. 
     *
     * @return an instance of the database configured for access
     * @throws DatabaseException if the user could not be logged in
     */
	public static Database current() throws DatabaseException {
	    if (pool == null) {
            //if (isStarted()) throw new IllegalStateException("database already started");
	        try {
    	        pool = BrokerPool.getInstance(dbName);
                txManager = pool.getTransactionManager();
                //configureRootCollection(configFile);
                //defragmenter.start();
                //QueryService.statistics().reset();
	        } catch (EXistException e) {
	            throw new DatabaseException(e);
	        }
	    }
	    DBBroker broker;
	    try {
	        broker = pool.getActiveBroker();
	    } catch (Throwable e) {
	        throw new DatabaseException(e);
	    }

        return new Database( broker.getCurrentSubject() );
	}
	
	/**
	 * Remove the given listener from all trigger points on all sources.
	 *
	 * @param listener the listener to remove
	 */
	public static void remove(Listener listener) {
		ListenerManager.INSTANCE.remove(listener);
	}
	
	static String normalizePath(String path) {
		if (path.startsWith(ROOT_PREFIX)) {
			path = path.equals(ROOT_PREFIX) ? "/" : path.substring(Database.ROOT_PREFIX.length());
		}
		return path;
	}

	private static String dbName = "exist";
	public static final String ROOT_PREFIX = XmldbURI.ROOT_COLLECTION;
	private static volatile BrokerPool pool;
	private static TransactionManager txManager;
	private static final ThreadLocal<Transaction> localTransaction = new ThreadLocal<Transaction>();
	private static final WeakHashMap<NativeBroker,Boolean> instrumentedBrokers = new WeakHashMap<NativeBroker,Boolean>();
	
	private final Subject user;
	private final NamespaceMap namespaceBindings;
	String defaultCharacterEncoding = "UTF-8";
	
	Database(Subject user) {
		this.user = user;
		this.namespaceBindings = new NamespaceMap();
	}
	
	Database(Database parent, NamespaceMap namespaceBindings) {
		this.user = parent.user;
		this.namespaceBindings = namespaceBindings.extend();
	}
	
	/**
	 * @deprecated Renamed to {@link #setDefaultCharacterEncoding(String)}.
	 */
	@Deprecated public void setDefaultExportEncoding(String encoding) {
		setDefaultCharacterEncoding(encoding);
	}
	
	/**
	 * Set the default character encoding to be used when exporting XML files from the database.
	 * If not explicitly set, it defaults to UTF-8.
	 *
	 * @param encoding
	 */
	public void setDefaultCharacterEncoding(String encoding) {
		defaultCharacterEncoding = encoding;
	}
	
	DBBroker acquireBroker() {
		try {
			NativeBroker broker = (NativeBroker) pool.get(Optional.ofNullable(user));
			if (instrumentedBrokers.get(broker) == null) {
				broker.addContentLoadingObserver(contentObserver);
				instrumentedBrokers.put(broker, Boolean.TRUE);
			}
			return broker;
		} catch (EXistException e) {
			throw new DatabaseException(e);
		}
	}
	
	void releaseBroker(DBBroker broker) {
		broker.close();
	}
	
	/**
	 * Return the namespace bindings for this database instance.  They will be inherited by
	 * all resources derived from this instance.
	 *
	 * @return the namespace bindings for this database instance
	 */
	public NamespaceMap namespaceBindings() {
		return namespaceBindings;
	}
	
	private Sequence adoptInternal(Object o) {
		DBBroker broker = acquireBroker();
		try {
			XQueryContext context = new XQueryContext(broker.getBrokerPool());
			context.declareNamespaces(namespaceBindings.getCombinedMap());
			context.setBackwardsCompatibility(false);
			context.setStaticallyKnownDocuments(DocumentSet.EMPTY_DOCUMENT_SET);
			return XPathUtil.javaObjectToXPath(o, context, true);
		} catch (XPathException e) {
			throw new DatabaseException(e);
		} finally {
			releaseBroker(broker);
		}
	}
	
	public ItemList adopt(org.w3c.dom.Node node) {
		// this works for DocumentFragments too, they'll be automatically expanded
		return new ItemList(adoptInternal(node), namespaceBindings.extend(), this);
	}
	
	/**
	 * Check whether the database contains a document or a folder with the given absolute path.
	 *
	 * @param path the absolute path of the document or folder to check
	 * @return <code>true</code> if there is a document or folder at the given path, <code>false</code> otherwise
	 */
	public boolean contains(String path) {
		if (path.length() == 0) throw new IllegalArgumentException("empty path: " + path);
		if (path.equals("/")) return true;
		if (!path.startsWith("/")) throw new IllegalArgumentException("path not absolute: " + path);
		if (path.endsWith("/")) throw new IllegalArgumentException("path ends with '/': " + path);
		int i = path.lastIndexOf('/');
		assert i != -1;
		
		DBBroker broker = acquireBroker();
		try {
                    if (broker.getCollection(XmldbURI.create(path)) != null) return true;
                    String folderPath = path.substring(0, i);
                    String name = path.substring(i+1);			
                    Collection collection = broker.openCollection(XmldbURI.create(folderPath), LockMode.NO_LOCK);
                    if (collection == null) return false;
                    return collection.getDocument(broker, XmldbURI.create(name)) != null;
                } catch(PermissionDeniedException pde) {
                    throw new DatabaseException(pde.getMessage(), pde);
                } finally {
			releaseBroker(broker);
		}
	}
	
	/**
	 * Get the document for the given absolute path.  Namespace bindings will be inherited
	 * from this database.
	 *
	 * @param path the absolute path of the desired document
	 * @return the document at the given path
	 * @throws DatabaseException if the document is not found or something else goes wrong
	 */
	public Document getDocument(String path) {
		if (path.length() == 0) throw new IllegalArgumentException("empty document path: " + path);
		if (!path.startsWith("/")) throw new IllegalArgumentException("document path not absolute: " + path);
		if (path.endsWith("/")) throw new IllegalArgumentException("document path ends with '/': " + path);
		int i = path.lastIndexOf('/');
		assert i != -1;
		return getFolder(i == 0 ? "/" : path.substring(0, i)).documents().get(path.substring(i+1));
	}

	/**
	 * Get the folder for the given path.  Namespace mappings will be inherited from this
	 * database.
	 * 
	 * @param path the address of the desired collection
	 * @return a collection bound to the given path
	 * @throws DatabaseException if the path does not identify a valid collection
	 */
	public Folder getFolder(String path) {
		return new Folder(path, false, namespaceBindings.extend(), this);
	}
	
	/**
	 * Create the folder for the given path.  Namespace mappings will be inherited from this
	 * database.  If the folder does not exist, it is created along with all required ancestors.
	 * 
	 * @param path the address of the desired collection
	 * @return a collection bound to the given path
	 */
	public Folder createFolder(String path) {
		return new Folder(path, true, namespaceBindings.extend(), this);
	}
	
	/**
	 * Return a query service that runs queries over the given list of resources.
	 * The resources can be of different kinds, and come from different locations in the
	 * folder hierarchy.  The service will inherit the database's namespace bindings,
	 * rather than the bindings of any given context resource.
	 *
	 * @param context the arbitrary collection of database objects over which to query
	 * @return a query service over the given resources
	 */
	public QueryService query(Resource... context) {
		return query(Arrays.asList(context));
	}
	
	/**
	 * Return a query service that runs queries over the given list of resources.
	 * The resources can be of different kinds, and come from different locations in the
	 * folder hierarchy.  The service will inherit the database's namespace bindings,
	 * rather than the bindings of any given context resource.
	 *
	 * @param context the arbitrary collection of database objects over which to query;
	 * 	the collection is not copied, and the collection's contents are re-read every time the query is performed
	 * @return a query service over the given resources
	 */
	public QueryService query(final java.util.Collection<? extends Resource> context) {
		return new QueryService(getFolder("/")) {
			@Override void prepareContext(DBBroker broker) {
				MutableDocumentSet mdocs = new DefaultDocumentSet();
				base = new ValueSequence();
				for (Resource res : context) {
					QueryService qs = res.query();
					if (qs.docs != null) mdocs.addAll(qs.docs);
					if (qs.base != null) try {
						base.addAll(qs.base);
					} catch (XPathException e) {
						throw new DatabaseException("unexpected item type conflict", e);
					}
				}
				docs = mdocs;
			}
		};
	}
	
	/**
	 * Return a transaction for use with database operations.  If a transaction is already in progress
	 * then join it, otherwise begin a new one.  If a transaction is joined, calling <code>commit</code>
	 * or <code>abort</code> on the returned instance will have no effect; only the outermost 
	 * transaction object can do this.
	 *
	 * @return a transaction object
	 */
	static Transaction requireTransaction() {
		Transaction t = localTransaction.get();
		return t == null ? new Transaction(txManager, null) : new Transaction(t, null);
	}
	
	Transaction requireTransactionWithBroker() {
		Transaction t = localTransaction.get();
		return t == null ? new Transaction(txManager, this) : new Transaction(t, this);
	}
	
	void checkSame(Resource o) {
		// allow other resource to be a NULL, as those are safe and database-neutral
		if (!(o.database() == null || o.database().user == this.user)) throw new IllegalArgumentException("cannot combine objects from two database instances in one operation");
	}
	
	private static final WeakMultiValueHashMap<String, StaleMarker> staleMap = new WeakMultiValueHashMap<String, StaleMarker>();
	
	private static void stale(String key) {
		int updated = 0;
		synchronized(staleMap) {
			for (StaleMarker value : staleMap.get(key)) {value.mark(); updated++;}
			staleMap.remove(key);
		}
	}
	
	static void trackStale(String key, StaleMarker value) {
		staleMap.put(normalizePath(key), value);
	}
	
	private static final ContentLoadingObserver contentObserver = new ContentLoadingObserver() {
		public void dropIndex(Collection collection) {
			stale(normalizePath(collection.getURI().getCollectionPath()));
		}
		public void dropIndex(DocumentImpl doc) {
			stale(normalizePath(doc.getURI().getCollectionPath()));
		}
                @Override
		public void removeNode(NodeHandle node, NodePath currentPath, String content) {
			stale(normalizePath((node.getOwnerDocument()).getURI().getCollectionPath()) + "#" + node.getNodeId());
		}
		public void flush() {}
		public void setDocument(DocumentImpl document) {}
		public void storeAttribute(AttrImpl node, NodePath currentPath, RangeIndexSpec spec, boolean remove) {}
		public void storeText(TextImpl node, NodePath currentPath) {}
		public void sync() {}
		public void printStatistics() {}
		
		public void close() {}
		public void remove() {}
		public void closeAndRemove() {
			// TODO:  do nothing OK here?  indexes just got wiped and recreated, and this listener
			// was removed...
		}

	};
	
	static void queueDefrag(DocumentImpl doc) {
		defragmenter.queue(doc);
	}
	
	private static final Defragmenter defragmenter = new Defragmenter();
	
	private static class Defragmenter implements Runnable {
		private static final Logger LOG = LogManager.getLogger("org.exist.fluent.Database.defragmenter");
		private static final long DEFRAG_INTERVAL = 10000;  // ms
		private Set<DocumentImpl> docsToDefrag = new TreeSet<DocumentImpl>();
		private Thread thread;
		
		public void start() {
			if (thread != null) return;
			thread = newInstanceThread(pool, "fluent.database-defragmenter", this);
			thread.setPriority(Thread.NORM_PRIORITY-3);
			thread.setDaemon(true);
			thread.start();
		}
		
		public void stop() {
			if (thread == null) return;
			thread.interrupt();
			try {
				thread.join();
			} catch (InterruptedException e) {
				// oh well
			}
			thread = null;
		}
		
		public synchronized void queue(DocumentImpl doc) {
			docsToDefrag.add(doc);
		}
		
		public void run() {
			while(true) {
				try {
					Thread.sleep(DEFRAG_INTERVAL);
				} catch (InterruptedException e) {
					break;
				}
				
				// Grab copy of docsToDefrag to avoid potential deadlocks (if an executing query has a lock on
				// the document we want to defrag, we block, then if it tries to queue another document for
				// defrag it blocks, and it's deadlock time).
				Set<DocumentImpl> docsToDefragCopy;
				synchronized(this) {
					LOG.debug(new MessageFormat(
							"checking for documents to defragment, {0,choice,0#no candidates|1#1 candidate|1<{0,number,integer} candidates}")
							.format(new Object[] {docsToDefrag.size()}));
					docsToDefragCopy = docsToDefrag;
					docsToDefrag = new TreeSet<DocumentImpl>();
				}
					
				int count = 0;
				try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
					Integer fragmentationLimitObject = broker.getBrokerPool().getConfiguration().getInteger(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
					int fragmentationLimit = fragmentationLimitObject == null ? 0 : fragmentationLimitObject;
					for (Iterator<DocumentImpl> it = docsToDefragCopy.iterator(); it.hasNext(); ) {
						DocumentImpl doc = it.next();
						if (doc.getMetadata().getSplitCount() <= fragmentationLimit) {
							it.remove();
						} else {
							// Must hold write lock on doc before checking stale map to avoid race condition
							if (doc.getUpdateLock().attempt(LockMode.WRITE_LOCK)) try {
								String docPath = normalizePath(doc.getURI().getCollectionPath());
								if (!staleMap.containsKey(docPath)) {
									LOG.debug("defragmenting " + docPath);
									count++;
									Transaction tx = Database.requireTransaction();
									try {
										broker.defragXMLResource(tx.tx, doc);
										tx.commit();
										it.remove();
									} finally {
										tx.abortIfIncomplete();
									}
								}
							} finally {
								doc.getUpdateLock().release(LockMode.WRITE_LOCK);
							}
						}
					}
				} catch (EXistException e) {
					LOG.error("unable to get broker with system privileges to defragment documents", e);
				}
				
				LOG.debug(new MessageFormat(
						"defragmented {0,choice,0#0 documents|1#1 document|1<{0,number,integer} documents}, next cycle in {1,number,integer}s")
						.format(new Object[] {count, DEFRAG_INTERVAL / 1000}));
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	static <T> Iterator<T> emptyIterator() {
		return EMPTY_ITERATOR;
	}
	
	@SuppressWarnings("unchecked")
	static final Iterator EMPTY_ITERATOR = new Iterator() {
		public boolean hasNext() {return false;}
		public Object next() {throw new NoSuchElementException();}
		public void remove() {throw new UnsupportedOperationException();}
	};
	
	@SuppressWarnings("unchecked")
	static final Iterable EMPTY_ITERABLE = new Iterable() {
		public Iterator iterator() {return EMPTY_ITERATOR;}
	};

}
