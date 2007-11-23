package org.exist.fluent;

import java.io.File;
import java.util.*;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.*;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.util.*;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

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
 * Database db = Database.login("admin", null);
 * for (String name : db.getFolder("/").query().all("//user/@name").values())
 *   System.out.println("user: " + name);
 * Database.shutdown();</pre></p>
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.26 $ ($Date: 2006/09/04 06:09:05 $)
 */
public class Database {
	
	private static final Logger LOG = Logger.getLogger(Database.class);

	/**
	 * Start up the database, configured using the given config file.  This method must be
	 * called precisely once before making use of any facilities offered in this package.  The
	 * configuration file is typically called 'conf.xml' and you can find a sample one in the root
	 * directory of eXist's distribution.
	 *
	 * @param configFile the config file that specifies the database to use
	 * @throws IllegalStateException if the database has already been started
	 */
	public static void startup(File configFile) {
		try {
			if (BrokerPool.isConfigured(dbName)) throw new IllegalStateException("database already started");
			configFile = configFile.getAbsoluteFile();
			Configuration config = new Configuration(configFile.getName(), configFile.getParentFile().getAbsolutePath());
			BrokerPool.configure(dbName, 1, 2, config);
			pool = BrokerPool.getInstance(dbName);
			txManager = pool.getTransactionManager();
			ListenerManager.configureTriggerDispatcher(new Database(SecurityManager.SYSTEM_USER));
		} catch (DatabaseConfigurationException e) {
			throw new DatabaseException(e);
		} catch (EXistException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Shut down the database connection.  If the database is not started, do nothing.
	 */
	public static void shutdown() {
		if (pool != null) pool.shutdown();
		pool = null;
	}
	
	/**
	 * Ensure the database is started.  If the database is not started, start it with the
	 * given config file.  If it is already started, make sure it was started with the same
	 * config file.
	 * 
	 * @param configFile the config file that specifies the database to use
	 * @throws IllegalStateException if the database was already started with a different config file
	 */
	public static void ensureStarted(File configFile) {
		if (isStarted()) {
			String currentPath = pool.getConfiguration().getConfigFilePath();
			if (!configFile.getAbsoluteFile().equals(new File(currentPath).getAbsoluteFile()))
				throw new IllegalStateException("database already started with different configuration " + currentPath);
		} else {
			startup(configFile);
		}
	}
	
	/**
	 * Return whether the database has been started and is currently running.
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
		try {
			DBBroker broker = pool.get(SecurityManager.SYSTEM_USER);
			try {
				broker.flush();
				broker.sync(Sync.MAJOR_SYNC);
			} finally {
				pool.release(broker);
			}
		} catch (EXistException e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Login to obtain access to the database.  The password should be passed in the clear.
	 * If a user does not have a password set, you can pass in any value including <code>null</code>.
	 * Note that all newly created databases have a user <code>admin</code> with no password set.
	 *
	 * @param username the username of the user being logged in
	 * @param password the password corresponding to that user name, or <code>null</code> if none
	 * @return an instance of the database configured for access by the given user
	 * @throws DatabaseException if the user could not be logged in
	 */
	public static Database login(String username, String password) {
		User user = pool.getSecurityManager().getUser(username);
		if (user == null || !user.validate(password)) throw new DatabaseException("invalid user credentials");
		return new Database(user);
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
	static final String ROOT_PREFIX = "/db";		// should match the root prefix in NativeBroker
	private static BrokerPool pool;
	private static TransactionManager txManager;
	private static final ThreadLocal<Transaction> localTransaction = new ThreadLocal<Transaction>();
	private static final WeakHashMap<NativeBroker,Boolean> instrumentedBrokers = new WeakHashMap<NativeBroker,Boolean>();
	
	private final User user;
	private final NamespaceMap namespaceBindings;
	String defaultExportEncoding = "UTF-8";
	
	Database(User user) {
		this.user = user;
		this.namespaceBindings = new NamespaceMap();
	}
	
	Database(Database parent, NamespaceMap namespaceBindings) {
		this.user = parent.user;
		this.namespaceBindings = namespaceBindings.extend();
	}
	
	/**
	 * Set the default character encoding to be used when exporting XML files from the database.
	 * If not explicitly set, it defaults to UTF-8.
	 *
	 * @param encoding
	 */
	public void setDefaultExportEncoding(String encoding) {
		defaultExportEncoding = encoding;
	}
	
	DBBroker acquireBroker() {
		try {
			NativeBroker broker = (NativeBroker) pool.get(user);
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
		pool.release(broker);
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
			XQueryContext context = broker.getXQueryService().newContext(AccessContext.INTERNAL_PREFIX_LOOKUP);
			context.declareNamespaces(namespaceBindings.getCombinedMap());
			context.setBackwardsCompatibility(false);
			context.setStaticallyKnownDocuments(new DocumentSet(0));
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
	 * Get the document for the given absolute path.  Namespace bindings will be inherited
	 * from this database.
	 *
	 * @param path the absolute path of the desired document
	 * @return the document at the given path
	 * @throws DatabaseException if the document is not found or something else goes wrong
	 */
	public Document getDocument(String path) {
		if (path.length() == 0) throw new IllegalArgumentException("empty document path");
		if (!path.startsWith("/")) throw new IllegalArgumentException("document path not absolute");
		if (path.endsWith("/")) throw new IllegalArgumentException("document path ends with '/'");
		int i = path.lastIndexOf('/');
		assert i != -1;
		return getFolder(path.substring(0, i)).documents().get(path.substring(i+1));
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
				docs = new DocumentSet();
				base = new ValueSequence();
				for (Resource res : context) {
					QueryService qs = res.query();
					if (qs.docs != null) docs.addAll(qs.docs);
					if (qs.base != null) try {
						base.addAll(qs.base);
					} catch (XPathException e) {
						throw new DatabaseException("unexpected item type conflict", e);
					}
				}
			}
		};
	}
	
	void checkSame(Resource o) {
		// allow other resource to be a NULL, as those are safe and database-neutral
		if (!(o.database() == null || o.database().user == this.user)) throw new IllegalArgumentException("cannot combine objects from two database instances in one operation");
	}
	
	private static final WeakMultiValueHashMap<String, StaleMarker> staleMap = new WeakMultiValueHashMap<String, StaleMarker>();
	
	private static void stale(String key) {
		synchronized(staleMap) {
			for (StaleMarker value : staleMap.get(key)) value.mark();
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
		public void dropIndex(DocumentImpl doc) throws ReadOnlyException {
			stale(normalizePath(doc.getURI().getCollectionPath()));
		}
		public void removeNode(StoredNode node, NodePath currentPath, String content) {
			stale(normalizePath(((DocumentImpl) node.getOwnerDocument()).getURI().getCollectionPath()) + "#" + node.getNodeId());
		}
		public void flush() {}
		public void setDocument(DocumentImpl document) {}
		public void storeAttribute(AttrImpl node, NodePath currentPath, int indexingHint, RangeIndexSpec spec, boolean remove) {}
		public void storeText(TextImpl node, NodePath currentPath, int indexingHint) {}
		public void sync() {}
		public void printStatistics() {}
		
		public boolean close() {return true;}
		public void remove() {}
		public void closeAndRemove() {
			// TODO:  do nothing OK here?  indexes just got wiped and recreated, and this listener
			// was removed...
		}

	};
	
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
		return t == null ? new Transaction(txManager) : new Transaction(t.tx);
	}
	
	private static final WeakMultiValueHashMap<Long, NodeProxy> nodes = new WeakMultiValueHashMap<Long, NodeProxy>();
	private static final long ADDRESS_MASK = 0xFFFFFFFF0000FFFFL;
	
	private static final NodeIndexListener indexChangeListener = new NodeIndexListener() {
		@SuppressWarnings("hiding")
		private final Logger LOG = Logger.getLogger("org.exist.fluent.Database.indexChangeListener");
		
		public void nodeChanged(StoredNode node) {
			int numUpdated = 0;
			for (NodeProxy target : nodes.get(node.getInternalAddress() & ADDRESS_MASK)) {
				target.setNodeId(node.getNodeId());
				numUpdated++;
			}
			if (LOG.isDebugEnabled()) System.out.println("change nodeid at " + StorageAddress.toString(node.getInternalAddress()) + " to " + node.getNodeId() + "; updated " + numUpdated);
		}
	};
	
	static void trackNode(NodeProxy proxy) {
		if (proxy.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE) return;	// no need to track document nodes as they don't change gids
		if (proxy.getInternalAddress() == -1) {
			StoredNode node = (StoredNode) proxy.getNode();
			if (node == null) {
				LOG.error("can't load node for proxy, doc=" + proxy.getDocument().getURI().lastSegment() + ", nodeid=" + proxy.getNodeId());
				return;
			}
			proxy.setInternalAddress(node.getInternalAddress());
			assert proxy.getInternalAddress() != -1;
		}
		proxy.getDocument().getMetadata().setIndexListener(indexChangeListener);
		// this may cause duplicates in the list; try to avoid them by design,
		// or it might become a performance hit
		nodes.put(proxy.getInternalAddress() & ADDRESS_MASK, proxy);
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
		@SuppressWarnings("unchecked")
		public Iterator iterator() {return EMPTY_ITERATOR;}
	};

}
