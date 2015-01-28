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
package org.exist.storage;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection;
import org.exist.collections.Collection.SubCollectionEntry;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.*;
import org.exist.indexing.Index;
import org.exist.indexing.IndexController;
import org.exist.indexing.StreamListener;
import org.exist.indexing.StructuralIndex;
import org.exist.numbering.NodeId;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.dom.INodeIterator;
import org.exist.storage.lock.EnsureLocked;
import org.exist.storage.lock.EnsureUnlocked;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.Lock.LockType;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.Document;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;

/**
 * This is the base class for all database backends. All the basic database
 * operations like storing, removing or index access are provided by subclasses
 * of this class.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class DBBroker extends Observable implements AutoCloseable {

	// Matching types
	public final static int MATCH_EXACT 		= 0;
	public final static int MATCH_REGEXP 		= 1;
	public final static int MATCH_WILDCARDS 	= 2;
	public final static int MATCH_CONTAINS 		= 3;
	public final static int MATCH_STARTSWITH 	= 4;
	public final static int MATCH_ENDSWITH 		= 5;
	
	//TODO : move elsewhere
	public static final String CONFIGURATION_ELEMENT_NAME = "xupdate";
    
    //TODO : move elsewhere
    public final static String XUPDATE_FRAGMENTATION_FACTOR_ATTRIBUTE = "allowed-fragmentation";

    //TODO : move elsewhere
    public final static String PROPERTY_XUPDATE_FRAGMENTATION_FACTOR = "xupdate.fragmentation";

    //TODO : move elsewhere
    public final static String XUPDATE_CONSISTENCY_CHECKS_ATTRIBUTE = "enable-consistency-checks";

    //TODO : move elsewhere
    public final static String PROPERTY_XUPDATE_CONSISTENCY_CHECKS = "xupdate.consistency-checks";

    public static final String POSIX_CHOWN_RESTRICTED_ATTRIBUTE = "posix-chown-restricted";
    public static final String POSIX_CHOWN_RESTRICTED_PROPERTY = "db-connection.posix-chown-restricted";
    public static final String PRESERVE_ON_COPY_ATTRIBUTE = "preserve-on-copy";
    public static final String PRESERVE_ON_COPY_PROPERTY = "db-connection.preserve-on-copy";

    protected final static Logger LOG = LogManager.getLogger(DBBroker.class);

    protected boolean caseSensitive = true;

    protected Configuration config;

    protected BrokerPool pool;

    private Deque<Subject> subject = new ArrayDeque<>();

    /**
     * Used when TRACE level logging is enabled
     * to provide a history of {@link Subject} state
     * changes
     *
     * This can be written to a log file by calling
     * {@link DBBroker#traceSubjectChanges()}
     */
    private TraceableStateChanges<Subject, TraceableSubjectChange.Change> subjectChangeTrace = LOG.isTraceEnabled() ? new TraceableStateChanges<>() : null;

    private int referenceCount = 0;

    protected String id;

    private final TimestampedReference<IndexController> indexController = new TimestampedReference<>();

    private final PreserveType preserveOnCopy;

    public DBBroker(final BrokerPool pool, final Configuration config) {
        this.config = config;
        final Boolean temp = (Boolean) config.getProperty(NativeValueIndex.PROPERTY_INDEX_CASE_SENSITIVE);
        if (temp != null) {
            caseSensitive = temp.booleanValue();
        }
        this.pool = pool;
        this.preserveOnCopy = config.getProperty(PRESERVE_ON_COPY_PROPERTY, PreserveType.NO_PRESERVE);
    }

    /**
     * Prepares the broker for (re-)use,
     * when (re-)leased from BrokerPool.
     */
    public void prepare() {
        /**
         * Index modules should always be re-loaded in case
         * {@link org.exist.indexing.IndexManager#registerIndex(Index)} or
         * {@link org.exist.indexing.IndexManager#unregisterIndex(Index)}
         * has been called since the previous lease of this broker.
         */
        loadIndexModules();
    }

    /**
     * Loads the index modules via an IndexController
     */
    protected void loadIndexModules() {
        indexController.setIfExpiredOrNull(getBrokerPool().getIndexManager().getConfigurationTimestamp(), () -> new IndexController(this));
    }

    /**
     * Change the state that the broker performs actions as
     *
     * @param subject The new state for the broker to perform actions as
     */
    public void pushSubject(final Subject subject) {
        if(LOG.isTraceEnabled()) {
            subjectChangeTrace.add(TraceableSubjectChange.push(subject, getId()));
        }
        this.subject.addFirst(subject);
    }

    /**
     * Restore the previous state for the broker to perform actions as
     *
     * @return The state which has been popped
     */
    public Subject popSubject() {
        final Subject subject = this.subject.removeFirst();
        if(LOG.isTraceEnabled()) {
            subjectChangeTrace.add(TraceableSubjectChange.pop(subject, getId()));
        }
        return subject;
    }

    /**
     * The state that is currently using this DBBroker object
     * 
     * @return The current state that the broker is executing as
     */
    public Subject getCurrentSubject() {
        return subject.peekFirst();
    }

    /**
     * Logs the details of all state changes
     *
     * Used for tracing privilege escalation/de-escalation
     * during the lifetime of an active broker
     *
     * @throws IllegalStateException if TRACE level logging is not enabled
     */
    public void traceSubjectChanges() {
        subjectChangeTrace.logTrace(LOG);
    }

    /**
     * Clears the details of all state changes
     *
     * Used for tracing privilege escalation/de-escalation
     * during the lifetime of an active broker
     *
     * @throws IllegalStateException if TRACE level logging is not enabled
     */
    public void clearSubjectChangesTrace() {
        if(!LOG.isTraceEnabled()) {
            throw new IllegalStateException("This is only enabled at TRACE level logging");
        }

        subjectChangeTrace.clear();
    }

    public IndexController getIndexController() {
        return indexController.get();
    }

    public abstract ElementIndex getElementIndex();

    public abstract StructuralIndex getStructuralIndex();

    /** Flush all data that has not been written before. */
    public void flush() {
        // do nothing
    }

    /** Observer Design Pattern: List of ContentLoadingObserver objects */
    protected List<ContentLoadingObserver> contentLoadingObservers = new ArrayList<ContentLoadingObserver>();	

    /** Remove all observers */
    public void clearContentLoadingObservers() {
        contentLoadingObservers.clear();
    }    

    /** Observer Design Pattern: add an observer. */
    public void addContentLoadingObserver(ContentLoadingObserver observer) {
        if (!contentLoadingObservers.contains(observer))
            {contentLoadingObservers.add(observer);}
    }

    /** Observer Design Pattern: remove an observer. */
    public void removeContentLoadingObserver(ContentLoadingObserver observer) {
        if (contentLoadingObservers.contains(observer))
            {contentLoadingObservers.remove(observer);}
    }

    /**
     * Adds all the documents in the database to the specified DocumentSet.
     *
     * WARNING: This is an incredibly expensive operation as it requires recursing through the Collection hierarchy and
     * accessing every document.
     *
     * @param docs a (possibly empty) document set to which the found documents are added.
     */
    public abstract MutableDocumentSet getAllXMLResources(MutableDocumentSet docs) throws PermissionDeniedException, LockException;

    public abstract void getResourcesFailsafe(BTreeCallback callback, boolean fullScan) throws TerminatedException;

    public abstract void getCollectionsFailsafe(BTreeCallback callback) throws TerminatedException;

    /**
     * Gets a database Collection.
     *
     * The Collection is identified by its absolute path, e.g. /db/shakespeare.
     * The returned Collection will NOT HAVE a lock.
     *
     * The caller should take care to release any associated resource by
     * calling {@link Collection#close()}
     *
     * In general, accessing Collections without a lock provides no consistency guarantees.
     * This function should only be used where estimated reads are needed, no writes should
     * be performed on a Collection retrieved by this function.
     * If you are uncertain whether this function is safe for you to use, you should always
     * use {@link #openCollection(XmldbURI, LockMode)} instead.
     * 
     * @return the Collection, or null if no Collection matches the path
     */
    @Nullable @EnsureUnlocked public abstract Collection getCollection(XmldbURI uri) throws PermissionDeniedException;

    /**
     * Open a Collection for reading or writing.
     *
     * The Collection is identified by its absolute path, e.g. /db/shakespeare.
     * It will be loaded and locked according to the lockMode argument.
     * 
     * The caller should take care to release the Collection lock properly by
     * calling {@link Collection#close()}
     * 
     * @param uri The Collection's path
     * @param lockMode the mode for locking the Collection, as specified in {@link LockMode}
     *
     * @return the Collection, or null if no Collection matches the path
     */
    @Nullable @EnsureLocked public abstract Collection openCollection(XmldbURI uri,
            LockMode lockMode) throws PermissionDeniedException;

    public abstract List<String> findCollectionsMatching(String regexp);

    /**
     * Gets the database Collection identified by the specified path.
     * If the Collection does not yet exist, it is created - including all ancestors.
     * The Collection is identified by its absolute path, e.g. /db/shakespeare.
     * The returned Collection will NOT HAVE a lock.
     *
     * The caller should take care to release any associated resource by
     * calling {@link Collection#close()}
     * 
     * @param transaction The current transaction
     * @param uri The Collection's URI
     *
     * @return The existing or created Collection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws IOException If an error occurs whilst reading (get) or writing (create) a Collection to disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     */
    public abstract @EnsureUnlocked Collection getOrCreateCollection(Txn transaction, XmldbURI uri)
        throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Returns the database collection identified by the specified path. If the
     * collection does not yet exist, it is created - including all ancestors.
     * The path should be absolute, e.g. /db/shakespeare.
     *
     * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
     * @param uri The collection's URI
     * @param creationAttributes the attributes to use if the collection needs to be created.
     * @return The collection or <code>null</code> if no collection matches the path
     * @throws PermissionDeniedException
     * @throws IOException
     * @throws TriggerException
     */
    public abstract Collection getOrCreateCollection(Txn transaction, XmldbURI uri, Optional<Tuple2<Permission, Long>> creationAttributes)
            throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Returns the configuration object used to initialize the current database
     * instance.
     */
    public Configuration getConfiguration() {
        return config;
    }

    /**
     * Return a {@link org.exist.storage.dom.NodeIterator} starting at the
     * specified node.
     * 
     * @param node
     * @return NodeIterator of node.
     */
    public INodeIterator getNodeIterator(NodeHandle node) {
        throw new RuntimeException("not implemented for this storage backend");
    }

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml.
     * 
     * @return the document or null if no document could be found at the
     *         specified location.
     * 
     * public abstract Document getXMLResource(String path) throws
     * PermissionDeniedException;
     */
    public abstract @EnsureUnlocked Document getXMLResource(XmldbURI docURI) throws PermissionDeniedException;

    /**
     * Get a document by its file name. The document's file name is used to
     * identify a document.
     *
     * @param docURI absolute file name in the database;
     *                 name can be given with or without the leading path /db/shakespeare.
     * @param accessType The access mode for the resource e.g. {@link org.exist.security.Permission#READ}
     * @return The document value or null if no document could be found
     */
    public abstract @EnsureUnlocked DocumentImpl getResource(XmldbURI docURI, int accessType) throws PermissionDeniedException;

    public abstract @EnsureUnlocked DocumentImpl getResourceById(int collectionId, byte resourceType, int documentId) throws PermissionDeniedException;

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml, with the specified lock.
     * 
     * @return the document or null if no document could be found at the
     *         specified location.
     */
    @Nullable @EnsureLocked public abstract LockedDocument getXMLResource(XmldbURI docURI, LockMode lockMode)
        throws PermissionDeniedException;

    /**
     * Get a new document id that does not yet exist within the collection.
     * @throws EXistException 
     */
    public abstract int getNextResourceId(Txn transaction) throws EXistException, LockException;

    /**
     * Get the string value of the specified node.
     * 
     * If addWhitespace is set to true, an extra space character will be added
     * between adjacent elements in mixed content nodes.
     */
    public String getNodeValue(IStoredNode node, boolean addWhitespace) {
        throw new RuntimeException("not implemented for this storage backend");
    }

    /**
     * Get an instance of the Serializer used for converting nodes back to XML.
     * Subclasses of DBBroker may have specialized subclasses of Serializer to
     * convert a node into an XML-string
     */
    public abstract Serializer getSerializer();

    public abstract NativeValueIndex getValueIndex();

    public abstract Serializer newSerializer();

    public abstract Serializer newSerializer(List<String> chainOfReceivers);

    /**
     * Get a node with given owner document and id from the database.
     * 
     * @param doc
     *            the document the node belongs to
     * @param nodeId
     *            the node's unique identifier
     */
    public abstract IStoredNode objectWith(@EnsureLocked(mode=LockMode.READ_LOCK) Document doc, NodeId nodeId);

    public abstract IStoredNode objectWith(NodeProxy p);

    /**
     * Remove the Collection and all of its sub-Collections from the database.
     *
     * @param transaction The current transaction
     * @param collection The Collection to remove from the database
     *
     * @return true if the Collection was removed, false otherwise
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws IOException If an error occurs whilst removing the Collection from disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     */
    public abstract boolean removeCollection(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection collection)
            throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Remove a document from the database.
     *
     */
    public abstract void removeResource(Txn tx, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc)
            throws IOException, PermissionDeniedException;

    /**
     * Remove a XML document from the database.
     *
     * NOTE Should never be called directly,
     * only for use from {@link Collection#removeXMLResource(Txn, DBBroker, XmldbURI)}
     * or {@link DBBroker}.
     *
     */
    public void removeXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl document)
            throws PermissionDeniedException, IOException {
        removeXMLResource(transaction, document, true);
    }

    /**
     * Remove a XML document from the database.
     *
     * NOTE Should never be called directly,
     * only for use from {@link Collection#removeXMLResource(Txn, DBBroker, XmldbURI)}
     * or {@link DBBroker}.
     *
     */
    public abstract void removeXMLResource(Txn transaction,
        @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl document, boolean freeDocId) throws PermissionDeniedException, IOException;

    public enum IndexMode {
        STORE,
        REPAIR,
        REMOVE
    }

    /**
     * Reindex a Collection and its descendants
     *
     * NOTE: Read locks will be taken in a top-down, left-right manner
     *     on Collections as they are indexed
     *
     * @param collectionUri The URI of the Collection to reindex
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst reindexing the Collection on disk
     */
    public abstract void reindexCollection(@EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) XmldbURI collectionUri)
            throws PermissionDeniedException, IOException, LockException;

    public abstract void reindexXMLResource(final Txn txn,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl doc);

    public abstract void reindexXMLResource(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl doc, final IndexMode mode);

    /**
     * Repair indexes. Should delete all secondary indexes and rebuild them.
     * This method will be called after the recovery run has completed.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst repairing indexes the database
     */
    public abstract void repair() throws PermissionDeniedException, IOException, LockException;

    /**
     * Repair core indexes (dom, collections ...). This method is called immediately
     * after recovery and before {@link #repair()}.
     */
    public abstract void repairPrimary();

    /**
     * Saves the specified Collection to disk. Collections are usually cached in
     * memory. If a Collection is modified, this method needs to be called to make
     * the changes persistent.
     *
     * Note: adding or removing a document to a Collection does not require a save. However,
     * modifying a Collection's metadata or adding or removing a sub-Collection does require
     * a save.
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#WRITE_LOCK} on the Collection
     *
     * @param transaction The current transaction
     * @param collection The Collection to persist
     *
     * @throws IOException If an error occurs whilst writing the Collection to disk
     */
    public abstract void saveCollection(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection collection)
            throws IOException;

    public abstract void closeDocument();

    /**
     * Shut down the database instance. All open files, jdbc connections etc.
     * should be closed.
     */
    public abstract void shutdown();

    /**
     * Store a node into the database. This method is called by the parser to
     * write a node to the storage backend.
     * 
     * @param node
     *            the node to be stored
     * @param currentPath
     *            path expression which points to this node's element-parent or
     *            to itself if it is an element.
     */
    public abstract <T extends IStoredNode> void storeNode(Txn transaction, IStoredNode<T> node, NodePath currentPath,IndexSpec indexSpec);

    public <T extends IStoredNode> void endElement(final IStoredNode<T> node, NodePath currentPath, String content) {
        endElement(node, currentPath, content, false);
    }

    public abstract <T extends IStoredNode> void endElement(final IStoredNode<T> node, NodePath currentPath, String content, boolean remove);

    /**
     * Store a document (descriptor) into the database.
     * 
     * @param doc
     *            the document's metadata to store.
     */
    public abstract void storeXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc);

    public abstract void storeMetadata(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc) throws TriggerException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     * 
     * @param blob
     *            the binary document descriptor
     * @param data
     *            the document binary data
     */
    @Deprecated
    public abstract void storeBinaryResource(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) BinaryDocument blob, byte[] data) throws IOException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     * 
     * @param blob
     *            the binary document descriptor
     * @param is
     *            the document binary data as input stream
     */
    public abstract void storeBinaryResource(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) BinaryDocument blob, InputStream is) throws IOException;

    public abstract void getCollectionResources(Collection.InternalAccess collectionInternalAccess);

    public abstract void readBinaryResource(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob,
        final OutputStream os) throws IOException;

    public abstract Path getBinaryFile(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob) throws IOException;

	public abstract InputStream getBinaryResource(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob)
           throws IOException;

    public abstract long getBinaryResourceSize(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob)
           throws IOException;
    
    /**
     * Completely delete this binary document (descriptor and binary data).
     * 
     * @param blob
     *            the binary document descriptor
     * @throws PermissionDeniedException
     *             if you don't have the right to do this
     */
    public abstract void removeBinaryResource(Txn transaction,
        @EnsureLocked(mode=LockMode.WRITE_LOCK) BinaryDocument blob) throws PermissionDeniedException,IOException;

	/**
	 * Move a collection and all its sub-Collections to another Collection and
	 * rename it. Moving a collection just modifies the collection path and all
	 * resource paths. The data itself remains in place.
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#WRITE_LOCK} on both the
     *     `sourceCollection` and the `targetCollection`
	 *
     * @param transaction The current transaction
	 * @param sourceCollection The Collection to move
	 * @param targetCollection The target Collection to move the sourceCollection into
	 * @param newName The new name the sourceCollection should have in the targetCollection
	 *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst moving the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
	 */
	public abstract void moveCollection(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName)
            throws PermissionDeniedException, LockException, IOException, TriggerException;

	/**
	 * Move a resource to the target Collection and rename it.
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#WRITE_LOCK} on the
     *     `sourceDocument` and its parent Collection, and the `targetCollection`
	 *
     * @param transaction The current transaction
     * @param sourceDocument The document to move
     * @param targetCollection The target Collection to move the sourceDocument into
     * @param newName The new name the sourceDocument should have in the targetCollection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst moving the Document on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
	 */
	public abstract void moveResource(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl sourceDocument,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName)
			throws PermissionDeniedException, LockException, IOException, TriggerException;

	/**
	 * Copy a collection to the destination collection and rename it.
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK}
     *     `sourceCollection` and a {@link LockMode#WRITE_LOCK} on the `targetCollection`
	 *
	 * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
	 * @param sourceCollection The origin collection
	 * @param targetCollection The destination parent collection
	 * @param newName The new name of the collection
	 *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     *
     * @deprecated Use {@link #copyCollection(Txn, Collection, Collection, XmldbURI, PreserveType)}
	 */
	@Deprecated
	public abstract void copyCollection(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) Collection sourceCollection,
			@EnsureLocked(mode=LockMode.WRITE_LOCK) Collection destination, XmldbURI newName)
			throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;

    /**
     * Copy a collection to the destination collection and rename it.
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK}
     *     `sourceCollection` and a {@link LockMode#WRITE_LOCK} on the `targetCollection`
	 *
     * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
     * @param sourceCollection The origin collection
     * @param targetCollection The destination parent collection
     * @param newName The new name of the collection
     * @param preserve Cause the copy process to preserve the following attributes of each source in the copy:
     *     modification time, file mode, user ID, and group ID, as allowed by permissions. Access Control Lists (ACLs)
     *     will also be preserved.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     */
    public abstract void copyCollection(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName, final PreserveType preserve)
            throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;


	/**
	 * Copy a resource to the destination collection and rename it.
	 *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK} on the
     *     `sourceDocument` and its parent Collection,
     *     and a {@link LockMode#WRITE_LOCK} on the `targetCollection`
	 *
	 * @param sourceDocumet the resource to copy
	 * @param targetCollection the destination collection
	 * @param newName the new name the resource should have in the destination collection
     *
	 * @throws PermissionDeniedException
	 * @throws LockException
	 * @throws EXistException
     *
     * @deprecated Use {@link #copyResource(Txn, DocumentImpl, Collection, XmldbURI, PreserveType)}
	 */
	@Deprecated
	public abstract void copyResource(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl sourceDocument,
			@EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName)
            throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;

    /**
     * Copy a resource to the destination collection and rename it.
     *
	 *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK} on the
     *     `sourceDocument` and its parent Collection,
     *     and a {@link LockMode#WRITE_LOCK} on the `targetCollection`
     *
     * @param sourceDocument the resource to copy
     * @param targetCollection the destination collection
     * @param newName the new name the resource should have in the destination collection
     * @param preserve Cause the copy process to preserve the following attributes of each source in the copy:
     *     modification time, file mode, user ID, and group ID, as allowed by permissions. Access Control Lists (ACLs)
     *     will also be preserved.
     *
     * @throws PermissionDeniedException
     * @throws LockException
     * @throws EXistException
     */
    public abstract void copyResource(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl sourceDocument,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName, final PreserveType preserve)
            throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;

	/**
	 * Defragment pages of this document. This will minimize the number of split
	 * pages.
	 * 
	 * @param doc
	 *            to defrag
	 */
	public abstract void defragXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc);

	/**
	 * Perform a consistency check on the specified document.
	 * 
	 * This checks if the DOM tree is consistent.
	 * 
	 * @param doc
	 */
	public abstract void checkXMLResourceTree(@EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl doc);

	public abstract void checkXMLResourceConsistency(@EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl doc)
			throws EXistException;

	/**
	 * Sync dom and collection state data (pages) to disk. In case of
	 * {@link org.exist.storage.sync.Sync#MAJOR}, sync all states (dom,
	 * collection, text and element) to disk.
	 * 
	 * @param syncEvent
	 */
	public abstract void sync(Sync syncEvent);

	/**
	 * Update a node's data. To keep nodes in a correct sequential order, it is
	 * sometimes necessary to update a previous written node. Warning: don't use
	 * it for other purposes.
	 * 
	 * @param node
	 *            Description of the Parameter
	 */
	public abstract <T extends IStoredNode> void updateNode(Txn transaction, IStoredNode<T> node, boolean reindex);

	/**
	 * Is the database running read-only? Returns false by default. Storage
	 * backends should override this if they support read-only mode.
	 * 
	 * @return boolean
	 */
	public boolean isReadOnly() {
		return false;
	}

	public BrokerPool getBrokerPool() {
		return pool;
	}

	public Database getDatabase() {
		return pool;
	}

	public abstract void insertNodeAfter(Txn transaction,
			final NodeHandle previous, final IStoredNode node);
    
    public abstract void indexNode(Txn transaction, IStoredNode node, NodePath currentPath);    

	public void indexNode(Txn transaction, IStoredNode node) {
		indexNode(transaction, node, null);
	}

	public abstract <T extends IStoredNode> void removeNode(Txn transaction, IStoredNode<T> node,
			NodePath currentPath, String content);

	public abstract void removeAllNodes(Txn transaction, IStoredNode node,
			NodePath currentPath, StreamListener listener);

	public abstract void endRemove(Txn transaction);

	/**
	 * Create a temporary document in the temp collection and store the supplied
	 * data.
	 * 
	 * @param doc
	 * @throws EXistException
	 * @throws PermissionDeniedException
	 * @throws LockException
	 */
	public abstract @EnsureUnlocked DocumentImpl storeTempResource(
			org.exist.dom.memtree.DocumentImpl doc) throws EXistException,
			PermissionDeniedException, LockException;
		
	/**
	 * Clean up temporary resources. Called by the sync daemon.
	 * 
	 * @param forceRemoval Should temporary resources be forcefully removed
	 */
	public abstract void cleanUpTempResources(boolean forceRemoval) throws PermissionDeniedException;
	
	/** Convenience method that allows to check available memory during broker-related processes.
	 * This method should eventually trigger flush() events.
	 */
	public abstract void checkAvailableMemory();

    /**
     * Get all the documents in this database matching the given
     * document-type's name.
     *
     * WARNING: This is an incredibly expensive operation as it requires recursing through the Collection hierarchy and
     * accessing every document.
     *
     * @param doctype The doctype to match documents against
     * @param result a (possibly empty) document set to which the found documents are added.
     *
     * @return The result
     */
	public abstract MutableDocumentSet getXMLResourcesByDoctype(String doctype, MutableDocumentSet result) throws PermissionDeniedException, LockException;

	public int getReferenceCount() {
		return referenceCount;
	}

	public void incReferenceCount() {
		++referenceCount;
	}

	public void decReferenceCount() {
		--referenceCount;
	}

	public abstract IndexSpec getIndexConfiguration();

	public void setId(String id) {
		this.id = id;
	}

	public String getId() {
		return id;
	}

	@Override
	public String toString() {
		return id;
	}

    public abstract IEmbeddedXMLStreamReader getXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    public abstract IEmbeddedXMLStreamReader newXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    public abstract void backupToArchive(RawDataBackup backup) throws IOException, EXistException;

    /**
     * Reads and populates the metadata for a sub-Collection
     *
     * The entry to read is determined by {@link SubCollectionEntry#uri}
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK} (or better)
     * on the Collection indicated in `entry`.
     *
     * @param entry The sub-Collection entry to populate
     *
     * @throws IOException If an error occurs whilst reading (get) or writing (create) a Collection to disk
     * @throws LockException if we are unable to obtain a lock on the collections.dbx
     */
    public abstract void readCollectionEntry(SubCollectionEntry entry) throws IOException, LockException;

    /**
     * Determines if Collection or Document attributes be preserved on copy,
     * by comparing the argument with the global system settings.
     *
     * Returns true if either:
     *     1.) The {@code preserve} argument is {@link PreserveType#PRESERVE}.
     *     2.) The {@code preserve} argument is {@link PreserveType#DEFAULT},
     *         and the global system setting is {@link PreserveType#PRESERVE}.
     *
     * @param preserve The call-specific preserve flag.
     *
     * @return true if attributes should be preserved.
     */
    public boolean preserveOnCopy(final PreserveType preserve) {
        Objects.requireNonNull(preserve);

        return PreserveType.PRESERVE == preserve ||
                (PreserveType.DEFAULT == preserve && PreserveType.PRESERVE == this.preserveOnCopy);
    }

    @Override
    public void close() {
        pool.release(this);
    }

    /**
     * @deprecated Use {@link DBBroker#close()}
     */
    @Deprecated
    public void release() {
        pool.release(this);
    }

    public final static String PROP_DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS = "exist.disable-single-thread-overlapping-transaction-checks";
    private final static boolean DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS = Boolean.valueOf(System.getProperty(PROP_DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS, "false"));
    private Txn currentTransaction = null;
    public synchronized void setCurrentTransaction(final Txn transaction) {
        if (DISABLE_SINGLE_THREAD_OVERLAPPING_TRANSACTION_CHECKS) {
            currentTransaction = transaction;
        } else {
            if (currentTransaction == null ^ transaction == null) {
                currentTransaction = transaction;
            } else {
                throw new IllegalStateException("Broker already has a transaction set");
            }
        }
    }

    public synchronized Txn getCurrentTransaction() {
        return currentTransaction;
    }

    /**
     * Represents a {@link Subject} change
     * made to a broker
     *
     * Used for tracing subject changes
     */
    private static class TraceableSubjectChange extends TraceableStateChange<Subject, TraceableSubjectChange.Change> {
        private final String id;

        public enum Change {
            PUSH,
            POP
        }

        private TraceableSubjectChange(final Change change, final Subject subject, final String id) {
            super(change, subject);
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public String describeState() {
            return getState().getName();
        }

        final static TraceableSubjectChange push(final Subject subject, final String id) {
            return new TraceableSubjectChange(Change.PUSH, subject, id);
        }

        final static TraceableSubjectChange pop(final Subject subject, final String id) {
            return new TraceableSubjectChange(Change.POP, subject, id);
        }
    }

    /**
     * Indicates the behaviour for not preserving or
     * preserving Collection of Document attributes
     * when making a copy.
     */
    public enum PreserveType {
        /**
         * Implies whatever the default is,
         * as configured in conf.xml: /exist/db-connection/@preserve-on-copy
         */
        DEFAULT,

        /**
         * Collection or Document attributes are not preserved
         * when making a copy.
         */
        NO_PRESERVE,

        /**
         * Collection or Document attributes are preserved
         * when making a copy.
         */
        PRESERVE
    }
}

