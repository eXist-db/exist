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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.Database;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.collections.Collection;
import org.exist.collections.Collection.SubCollectionEntry;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.*;
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
import org.exist.util.crypto.digest.DigestType;
import org.exist.util.crypto.digest.MessageDigest;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.TerminatedException;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

/**
 * This is the base class for all database backends. All the basic database
 * operations like storing, removing or index access are provided by subclasses
 * of this class.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface DBBroker extends AutoCloseable {

	// Matching types
    int MATCH_EXACT 		= 0;
    int MATCH_REGEXP 		= 1;
    int MATCH_WILDCARDS 	= 2;  // no longer used!
    int MATCH_CONTAINS 		= 3;
    int MATCH_STARTSWITH 	= 4;
    int MATCH_ENDSWITH 		= 5;
	
	//TODO : move elsewhere
    String CONFIGURATION_ELEMENT_NAME = "xupdate";
    
    //TODO : move elsewhere
    String XUPDATE_FRAGMENTATION_FACTOR_ATTRIBUTE = "allowed-fragmentation";

    //TODO : move elsewhere
    String PROPERTY_XUPDATE_FRAGMENTATION_FACTOR = "xupdate.fragmentation";

    //TODO : move elsewhere
    String XUPDATE_CONSISTENCY_CHECKS_ATTRIBUTE = "enable-consistency-checks";

    //TODO : move elsewhere
    String PROPERTY_XUPDATE_CONSISTENCY_CHECKS = "xupdate.consistency-checks";

    String POSIX_CHOWN_RESTRICTED_ATTRIBUTE = "posix-chown-restricted";
    String POSIX_CHOWN_RESTRICTED_PROPERTY = "db-connection.posix-chown-restricted";
    String PRESERVE_ON_COPY_ATTRIBUTE = "preserve-on-copy";
    String PRESERVE_ON_COPY_PROPERTY = "db-connection.preserve-on-copy";

    String getId();

    void setId(String id);

    /**
     * Prepares the broker for (re-)use,
     * when (re-)leased from BrokerPool.
     */
    void prepare();

    /**
     * Change the state that the broker performs actions as
     *
     * @param subject The new state for the broker to perform actions as
     */
    void pushSubject(final Subject subject);

    /**
     * Restore the previous state for the broker to perform actions as
     *
     * @return The state which has been popped
     */
    Subject popSubject();

    /**
     * The state that is currently using this DBBroker object
     * 
     * @return The current state that the broker is executing as
     */
    Subject getCurrentSubject();

    /**
     * Logs the details of all state changes
     *
     * Used for tracing privilege escalation/de-escalation
     * during the lifetime of an active broker
     *
     * @throws IllegalStateException if TRACE level logging is not enabled
     */
    void traceSubjectChanges();

    /**
     * Clears the details of all state changes
     *
     * Used for tracing privilege escalation/de-escalation
     * during the lifetime of an active broker
     *
     * @throws IllegalStateException if TRACE level logging is not enabled
     */
    void clearSubjectChangesTrace();

    IndexController getIndexController();

    ElementIndex getElementIndex();

    StructuralIndex getStructuralIndex();

    /**
     * Flush all data that has not been written before.
     */
    void flush();

    /** Remove all observers */
    void clearContentLoadingObservers();

    /**
     * Observer Design Pattern: add an observer.
     *
     * @param observer the observer
     */
    void addContentLoadingObserver(ContentLoadingObserver observer);

    /**
     * Observer Design Pattern: remove an observer.
     *
     * @param observer the observer
     */
    void removeContentLoadingObserver(ContentLoadingObserver observer);

    /**
     * Adds all the documents in the database to the specified DocumentSet.
     *
     * WARNING: This is an incredibly expensive operation as it requires recursing through the Collection hierarchy and
     * accessing every document.
     *
     * @param docs a (possibly empty) document set to which the found documents are added.
     * @return all XML resources as MutableDocumentSet
     * @throws PermissionDeniedException when one collection can not be accessed
     * @throws LockException when one collection is locked
     */
    MutableDocumentSet getAllXMLResources(MutableDocumentSet docs) throws PermissionDeniedException, LockException;

    void getResourcesFailsafe(Txn transaction, BTreeCallback callback, boolean fullScan) throws TerminatedException;

    void getCollectionsFailsafe(Txn transaction, BTreeCallback callback) throws TerminatedException;

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
     * use #openCollection(XmldbURI, LockMode) instead.
     *
     * @param uri The Collection's path
     * @return the Collection, or null if no Collection matches the path
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    @Nullable @EnsureUnlocked Collection getCollection(XmldbURI uri) throws PermissionDeniedException;

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
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    @Nullable @EnsureLocked Collection openCollection(XmldbURI uri,
            LockMode lockMode) throws PermissionDeniedException;

    List<String> findCollectionsMatching(String regexp);

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
    @EnsureUnlocked Collection getOrCreateCollection(Txn transaction, XmldbURI uri)
        throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Returns the database collection identified by the specified path. If the
     * collection does not yet exist, it is created - including all ancestors.
     * The path should be absolute, e.g. /db/shakespeare.
     *
     * @param transaction The transaction, which registers the acquired write locks. The locks should be released on commit/abort.
     * @param uri The collection's URI
     * @param creationAttributes the attributes to use if the collection needs to be created, the first item is a Permission (or null for default), the second item is a Creation Date.
     * @return The collection or <code>null</code> if no collection matches the path
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws IOException If an error occurs whilst reading (get) or writing (create) a Collection to disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     */
    Collection getOrCreateCollection(Txn transaction, XmldbURI uri, Optional<Tuple2<Permission, Long>> creationAttributes)
            throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Stores a document.
     * Since the process is dependent on the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction The database transaction
     * @param name        The name (without path) of the document
     * @param source      The source of the content for the new document to store
     * @param mimeType    The mimeType of the document to store, or null if unknown.
     * @param collection  The collection to store the document into
     *
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    public abstract void storeDocument(Txn transaction, XmldbURI name, InputSource source, @Nullable MimeType mimeType, Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Stores a document.
     * Since the process is dependent on the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction       The database transaction
     * @param name              The name (without path) of the document
     * @param source            The source of the content for the new document to store
     * @param mimeType          The mimeType of the document to store, or null if unknown.
     *                          If null, application/octet-stream will be used to store a binary document.
     * @param createdDate       The created date to set for the document, or if null the date is set to 'now'
     * @param lastModifiedDate  The lastModified date to set for the document, or if null the date is set to the {@code createdDate}
     * @param permission        A specific permission to set on the document, or null for the default permission
     * @param documentType      A document type declaration, or null if absent or a binary document is being stored
     * @param xmlReader         A custom XML Reader (e.g. a HTML to XHTML converting reader), or null to use the default XML reader or if a binary document is being stored
     * @param collection        The collection to store the document into
     *
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    public abstract void storeDocument(Txn transaction, XmldbURI name, InputSource source, @Nullable MimeType mimeType, @Nullable Date createdDate, @Nullable Date lastModifiedDate, @Nullable Permission permission, @Nullable DocumentType documentType, @Nullable XMLReader xmlReader, Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Stores a document.
     * Since the process is dependent on the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction The database transaction
     * @param name        The name (without path) of the document
     * @param node        The DOM Node to store as a new document
     * @param mimeType    The mimeType of the document to store, or null if unknown.
     * @param collection  The collection to store the document into
     *
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    public abstract void storeDocument(Txn transaction, XmldbURI name, Node node, @Nullable MimeType mimeType, Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Stores a document.
     * Since the process is dependent on the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction       The database transaction
     * @param name              The name (without path) of the document
     * @param node              The DOM Node to store as a new document
     * @param mimeType          The mimeType of the document to store, or null if unknown.
     *                          If null, application/octet-stream will be used to store a binary document.
     * @param createdDate       The created date to set for the document, or if null the date is set to 'now'
     * @param lastModifiedDate  The lastModified date to set for the document, or if null the date is set to the {@code createdDate}
     * @param permission        A specific permission to set on the document, or null for the default permission
     * @param documentType      A document type declaration, or null if absent or a binary document is being stored
     * @param xmlReader         A custom XML Reader (e.g. a HTML to XHTML converting reader), or null to use the default XML reader or if a binary document is being stored
     * @param collection        The collection to store the document into
     *
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    public abstract void storeDocument(Txn transaction, XmldbURI name, Node node, @Nullable MimeType mimeType, @Nullable Date createdDate, @Nullable Date lastModifiedDate, @Nullable Permission permission, @Nullable DocumentType documentType, @Nullable XMLReader xmlReader, Collection collection) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Returns the configuration object used to initialize the current database
     * instance.
     *
     * @return the configuration
     */
    Configuration getConfiguration();

    /**
     * Return a {@link org.exist.storage.dom.NodeIterator} starting at the
     * specified node.
     * 
     * @param node the NodeHandle
     * @return NodeIterator of node.
     * @throws RuntimeException not implemented
     */
    INodeIterator getNodeIterator(NodeHandle node) throws RuntimeException;

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml.
     *
     * @param docURI the XmldbURI to the resource
     * @return the document or null if no document could be found at the
     *         specified location.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    @EnsureUnlocked Document getXMLResource(XmldbURI docURI) throws PermissionDeniedException;

    /**
     * Get a document by its file name. The document's file name is used to
     * identify a document.
     *
     * @param docURI absolute file name in the database;
     *                 name can be given with or without the leading path
     * @param accessType The access mode for the resource e.g. {@link org.exist.security.Permission#READ}
     * @return The document value or null if no document could be found
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    @EnsureUnlocked DocumentImpl getResource(XmldbURI docURI, int accessType) throws PermissionDeniedException;

    @EnsureUnlocked DocumentImpl getResourceById(int collectionId, byte resourceType, int documentId) throws PermissionDeniedException;

    /**
     * Return the document stored at the specified path. The path should be
     * absolute, e.g. /db/shakespeare/plays/hamlet.xml, with the specified lock.
     *
     * @param docURI absolute file name in the database;
     *                 name can be given with or without the leading path.
     * @param lockMode one of the modes in {@link LockMode}
     *
     * @return the document or null if no document could be found at the
     *         specified location.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    @Nullable @EnsureLocked LockedDocument getXMLResource(XmldbURI docURI, LockMode lockMode)
        throws PermissionDeniedException;

    /**
     * Get a new document id that does not yet exist within the collection.
     *
     * @param transaction the transaction
     *
     * @return next resource ID
     *
     * @throws EXistException when something went wrong
     * @throws LockException when the resource or collection is locked
     */
    int getNextResourceId(Txn transaction) throws EXistException, LockException;

    /**
     * Get the string value of the specified node.
     * 
     * If addWhitespace is set to true, an extra space character will be added
     * between adjacent elements in mixed content nodes.
     * @param node the node
     * @param addWhitespace to add whitespace or not
     *
     * @return the node value as String
     *
     * @throws RuntimeException not implemented
     */
    String getNodeValue(IStoredNode node, boolean addWhitespace);

    /**
     * Get an instance of the Serializer used for converting nodes back to XML
     * from the pool.
     *
     * After use {@link #returnSerializer(Serializer)} should always be called.
     *
     * @return the {@link Serializer}
     */
    Serializer borrowSerializer();

    /**
     * Return an instance of the Serializer used for converting nodes back to XML
     * to the pool.
     *
     * The {@code serializer} should have been obtained by {@link #borrowSerializer()}
     *
     * @param serializer the {@link Serializer}
     */
    void returnSerializer(Serializer serializer);

    /**
     * Get's a new Serializer.
     *
     * @return a serializer
     *
     * @deprecated Use {@link #borrowSerializer()} and {@link #returnSerializer(Serializer)} instead. Will be removed in eXist-db 6.0.0
     */
    @Deprecated
    Serializer getSerializer();

    /**
     * Get's a new Serializer.
     *
     * @return a serializer
     *
     * @deprecated Use {@link #borrowSerializer()} and {@link #returnSerializer(Serializer)} instead. Will be removed in eXist-db 6.0.0
     */
    @Deprecated
    Serializer newSerializer();

    /**
     * Get's a new Serializer.
     *
     * @param chainOfReceivers the receivers to set on the serializer.
     * @return a serializer
     *
     * @deprecated Use {@link #borrowSerializer()} and {@link #returnSerializer(Serializer)} instead. Will be removed in eXist-db 6.0.0
     */
    @Deprecated
    Serializer newSerializer(final List<String> chainOfReceivers);

    NativeValueIndex getValueIndex();

    /**
     * Get a node with given owner document and id from the database.
     * 
     * @param doc the document the node belongs to
     * @param nodeId the node's unique identifier
     *
     * @return the IStoredNode
     */
    IStoredNode objectWith(@EnsureLocked(mode=LockMode.READ_LOCK) Document doc, NodeId nodeId);

    IStoredNode objectWith(NodeProxy p);

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
    boolean removeCollection(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection collection)
            throws PermissionDeniedException, IOException, TriggerException;

    /**
     * Removes the resources entry from the Collection store on disk.
     *
     * @param transaction The database transaction.
     * @param document The document to remove.
     */
    void removeResourceMetadata(final Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl document);

    /**
     * Remove a document from the database.
     *
     * @param tx the transaction
     * @param doc the document
     *
     * @throws IOException If an error occurs whilst removing the Collection from disk
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    void removeResource(Txn tx, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc)
            throws IOException, PermissionDeniedException;

    /**
     * Remove a XML document from the database.
     *
     * NOTE Should never be called directly,
     * only for use from {@link Collection#removeXMLResource(Txn, DBBroker, XmldbURI)}
     * or {@link DBBroker}.
     *
     * @param transaction the transaction
     * @param document the document
     *
     * @throws IOException If an error occurs whilst removing the Collection from disk
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    void removeXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl document)
            throws PermissionDeniedException, IOException;

    /**
     * Remove a XML document from the database.
     *
     * NOTE Should never be called directly,
     * only for use from {@link Collection#removeXMLResource(Txn, DBBroker, XmldbURI)}
     * or {@link DBBroker}.
     *
     * @param transaction the transaction
     * @param document the document
     * @param freeDocId true, if the document ID can be freed
     *
     * @throws IOException If an error occurs whilst removing the Collection from disk
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     */
    void removeXMLResource(Txn transaction,
        @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl document, boolean freeDocId) throws PermissionDeniedException, IOException;

    enum IndexMode {
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
     * @param transaction the transaction
     * @param collectionUri The URI of the Collection to reindex
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst reindexing the Collection on disk
     */
    void reindexCollection(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK, type=LockType.COLLECTION) XmldbURI collectionUri)
            throws PermissionDeniedException, IOException, LockException;

    void reindexXMLResource(final Txn txn,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl doc);

    void reindexXMLResource(final Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) final DocumentImpl doc, final IndexMode mode);

    /**
     * Repair indexes. Should delete all secondary indexes and rebuild them.
     * This method will be called after the recovery run has completed.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst repairing indexes the database
     */
    void repair() throws PermissionDeniedException, IOException, LockException;

    /**
     * Repair core indexes (dom, collections ...). This method is called immediately
     * after recovery and before {@link #repair()}.
     */
    void repairPrimary();

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
    void saveCollection(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection collection)
            throws IOException;

    void closeDocument();

    /**
     * Shut down the database instance. All open files, jdbc connections etc.
     * should be closed.
     */
    void shutdown();

    /**
     * Store a node into the database. This method is called by the parser to
     * write a node to the storage backend.
     * 
     * @param transaction the current transaction
     * @param node the node to be stored
     * @param currentPath
     *            path expression which points to this node's element-parent or
     *            to itself if it is an element.
     * @param indexSpec the IndexSpec
     * @param <T> the return type
     */
    <T extends IStoredNode> void storeNode(Txn transaction, IStoredNode<T> node, NodePath currentPath, IndexSpec indexSpec);

    <T extends IStoredNode> void endElement(final IStoredNode<T> node, NodePath currentPath, String content);

    /**
     * Update indexes for the given element node. This method is called when the indexer
     * encounters a closing element tag. It updates any range indexes defined on the
     * element value and adds the element id to the structural index.
     *
     * @param node        the current element node
     * @param currentPath node path leading to the element
     * @param content     contains the string value of the element. Needed if a range index
     *                    is defined on it.
     */
    <T extends IStoredNode> void endElement(final IStoredNode<T> node, NodePath currentPath, String content, boolean remove);

    /**
     * Store a document (descriptor) into the database.
     *
     * @param transaction the current transaction
     * @param doc
     *            the document's metadata to store.
     */
    void storeXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc);

    void storeMetadata(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc) throws TriggerException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     *
     * @param transaction the current transaction
     * @param blob
     *            the binary document descriptor
     * @param data
     *            the document binary data
     *
     * @throws IOException If an error occurs whilst writing the binary resource to disk
     */
    @Deprecated
    void storeBinaryResource(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) BinaryDocument blob, byte[] data) throws IOException;

    /**
     * Stores the given data under the given binary resource descriptor
     * (BinaryDocument).
     *
     * @param transaction the current transaction
     * @param blob
     *            the binary document descriptor
     * @param is
     *            the document binary data as input stream
     *
     * @throws IOException If an error occurs whilst writing the binary resource to disk
     */
    void storeBinaryResource(Txn transaction,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) BinaryDocument blob, InputStream is) throws IOException;

    void getCollectionResources(Collection.InternalAccess collectionInternalAccess);

    /**
     * @deprecated use {@link #readBinaryResource(Txn, BinaryDocument, OutputStream)}
     * @param blob
     *            the binary document descriptor
     * @param os the OutputStream to use
     * @throws IOException If an error occurs whilst reading the binary resource from disk
     */
    @Deprecated
    void readBinaryResource(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob,
        final OutputStream os) throws IOException;

    void readBinaryResource(final Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob,
        final OutputStream os) throws IOException;

    /**
     * @deprecated use {@link #withBinaryFile(Txn, BinaryDocument, Function)}
     * @param blob
     *            the binary document descriptor
     * @return the path to the binary document
     * @throws IOException If an error occurs whilst reading the binary resource from disk
     */
    @Deprecated
    Path getBinaryFile(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob) throws IOException;

    /**
     * Perform an operation with a {@link Path} reference to the BLOB file
     * backing a Binary Document.
     *
     * NOTE: Use of this method should be avoided where possible. It only
     * exists for integration with tools which can only
     * work with File Paths and where making a copy of the file is not
     * necessary.
     *
     * WARNING: The provided {@link Path} MUST ONLY be used for
     * READ operations, any WRITE/DELETE operation will corrupt the
     * integrity of the blob store.
     *
     * Consider if you really need to use this method. It is likely you could
     * instead use {@link #getBinaryResource(Txn, BinaryDocument)} and make a
     * copy of the data to a temporary file.
     *
     * Note that any resources associated with the BLOB file
     * may not be released until the {@code fnFile} has finished executing.
     *
     * USE WITH CAUTION!
     *
     * @param transaction the current database transaction.
     * @param binaryDocument the binary document to retrieve the backing BLOB file for.
     * @param <T> the type of the return value
     * @param fnFile a function which performs a read-only operation on the BLOB file.
     *     The Path will be null if the Blob does not exist in the Blob Store.
     *     If you wish to handle exceptions in your function you should consider
     *     {@link com.evolvedbinary.j8fu.Try} or similar.
     * @return T
     *
     * @throws IOException if an error occurs whilst retrieving the BLOB file.
     */
    <T> T withBinaryFile(final Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument binaryDocument,
            final Function<Path, T> fnFile) throws IOException;

    /**
     * @deprecated use {@link #getBinaryResource(Txn, BinaryDocument)}
     * @param blob
     *            the binary document descriptor
     * @return the InputStream
     * @throws IOException if an error occurs whilst retrieving the binary resource.
     */
    @Deprecated
	InputStream getBinaryResource(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob)
           throws IOException;

    InputStream getBinaryResource(final Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob)
            throws IOException;

    /**
     * @deprecated use {@link BinaryDocument#getContentLength()}
     * @param blob
     *            the binary document descriptor
     * @return the size of the binary resource
     * @throws IOException if an error occurs whilst retrieving the binary resource.
     */
    @Deprecated
    long getBinaryResourceSize(@EnsureLocked(mode=LockMode.READ_LOCK) final BinaryDocument blob)
           throws IOException;

    /**
     * Get the Digest of the content of a Binary Document.
     *
     * @param transaction the database transaction
     * @param binaryDocument the binary document
     * @param digestType the type of the digest
     *
     * @return the message digest of the content of the Binary Document
     * @throws IOException if an error occurs whilst retrieving the digest.
     */
    MessageDigest getBinaryResourceContentDigest(final Txn transaction,
            final BinaryDocument binaryDocument, final DigestType digestType) throws IOException;
    
    /**
     * Completely delete this binary document (descriptor and binary data).
     *
     * @param transaction The current transaction
     * @param blob the binary document descriptor
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws IOException if an error occurs whilst removing the binary resource.
     */
    void removeBinaryResource(Txn transaction,
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
	void moveCollection(Txn transaction,
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
	void moveResource(Txn transaction,
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
	 * @param destination The destination parent collection
	 * @param newName The new name of the collection
	 *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     * @throws EXistException If an internal error occurs
     *
     * @deprecated Use {@link #copyCollection(Txn, Collection, Collection, XmldbURI, PreserveType)}
	 */
	@Deprecated
	void copyCollection(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) Collection sourceCollection,
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
     * @throws EXistException If an internal error occurs
     */
    void copyCollection(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) Collection sourceCollection,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName, final PreserveType preserve)
            throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;


	/**
	 * Copy a resource to the destination collection and rename it.
	 *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK} on the
     *     `sourceDocument` and its parent Collection,
     *     and a {@link LockMode#WRITE_LOCK} on the `targetCollection`
	 *
     * @param transaction The current transaction
	 * @param sourceDocument the resource to copy
	 * @param targetCollection the destination collection
	 * @param newName the new name the resource should have in the destination collection
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     * @throws EXistException If an internal error occurs
     *
     * @deprecated Use {@link #copyResource(Txn, DocumentImpl, Collection, XmldbURI, PreserveType)}
	 */
	@Deprecated
	void copyResource(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl sourceDocument,
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
     * @param transaction The current transaction
     * @param sourceDocument the resource to copy
     * @param targetCollection the destination collection
     * @param newName the new name the resource should have in the destination collection
     * @param preserve Cause the copy process to preserve the following attributes of each source in the copy:
     *     modification time, file mode, user ID, and group ID, as allowed by permissions. Access Control Lists (ACLs)
     *     will also be preserved.
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     * @throws IOException If an error occurs whilst copying the Collection on disk
     * @throws TriggerException If a CollectionTrigger throws an exception
     * @throws EXistException If an internal error occurs
     */
    void copyResource(Txn transaction, @EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl sourceDocument,
            @EnsureLocked(mode=LockMode.WRITE_LOCK) Collection targetCollection, XmldbURI newName, final PreserveType preserve)
            throws PermissionDeniedException, LockException, IOException, TriggerException, EXistException;

	/**
	 * Defragment pages of this document. This will minimize the number of split
	 * pages.
	 *
     * @param transaction The current transaction
	 * @param doc to defrag
	 */
	void defragXMLResource(Txn transaction, @EnsureLocked(mode=LockMode.WRITE_LOCK) DocumentImpl doc);

	/**
	 * Perform a consistency check on the specified document.
	 * 
	 * This checks if the DOM tree is consistent.
	 * 
	 * @param doc the document to check the XML tree in
	 */
	void checkXMLResourceTree(@EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl doc);

	void checkXMLResourceConsistency(@EnsureLocked(mode=LockMode.READ_LOCK) DocumentImpl doc)
			throws EXistException;

	/**
	 * Sync dom and collection state data (pages) to disk. In case of
	 * {@link org.exist.storage.sync.Sync#MAJOR}, sync all states (dom,
	 * collection, text and element) to disk.
	 * 
	 * @param syncEvent the event
	 */
	void sync(Sync syncEvent);

	/**
	 * Update a node's data. To keep nodes in a correct sequential order, it is
	 * sometimes necessary to update a previous written node. Warning: don't use
	 * it for other purposes.
	 *
     * @param transaction the current transaction
	 * @param node the node to update
     * @param reindex true will trigger a reindex
     * @param <T> the type to return
	 */
	<T extends IStoredNode> void updateNode(Txn transaction, IStoredNode<T> node, boolean reindex);

	/**
	 * Is the database running read-only? Returns false by default. Storage
	 * backends should override this if they support read-only mode.
	 * 
	 * @return boolean
	 */
	boolean isReadOnly();

	BrokerPool getBrokerPool();

	Database getDatabase();

	void insertNodeAfter(Txn transaction,
			final NodeHandle previous, final IStoredNode node);
    
    void indexNode(Txn transaction, IStoredNode node, NodePath currentPath);

	void indexNode(Txn transaction, IStoredNode node);

	<T extends IStoredNode> void removeNode(Txn transaction, IStoredNode<T> node,
			NodePath currentPath, String content);

	void removeAllNodes(Txn transaction, IStoredNode node,
			NodePath currentPath, StreamListener listener);

	void endRemove(Txn transaction);

	/**
	 * Create a temporary document in the temp collection and store the supplied
	 * data.
	 * 
	 * @param doc the document to store
     * @return the temporary document
     *
     * @throws EXistException If an internal error occurs
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
	 */
	@EnsureUnlocked DocumentImpl storeTempResource(
			org.exist.dom.memtree.DocumentImpl doc) throws EXistException,
			PermissionDeniedException, LockException;
		
	/**
	 * Clean up temporary resources. Called by the sync daemon.
	 * 
	 * @param forceRemoval Should temporary resources be forcefully removed
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
	 */
	void cleanUpTempResources(boolean forceRemoval) throws PermissionDeniedException;
	
	/** Convenience method that allows to check available memory during broker-related processes.
	 * This method should eventually trigger flush() events.
	 */
	void checkAvailableMemory();

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
     *
     * @throws PermissionDeniedException If the current user does not have appropriate permissions
     * @throws LockException If an exception occurs whilst acquiring locks
     */
	MutableDocumentSet getXMLResourcesByDoctype(String doctype, MutableDocumentSet result) throws PermissionDeniedException, LockException;

	int getReferenceCount();

	void incReferenceCount();

	void decReferenceCount();

	IndexSpec getIndexConfiguration();

    IEmbeddedXMLStreamReader getXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    IEmbeddedXMLStreamReader newXMLStreamReader(NodeHandle node, boolean reportAttributes)
            throws IOException, XMLStreamException;

    void backupToArchive(RawDataBackup backup) throws IOException, EXistException;

    /**
     * Reads and populates the metadata for a sub-Collection
     *
     * The entry to read is determined by {@link SubCollectionEntry#getUri()}
     *
     * NOTE: It is assumed that the caller holds a {@link LockMode#READ_LOCK} (or better)
     * on the Collection indicated in `entry`.
     *
     * @param entry The sub-Collection entry to populate
     *
     * @throws IOException If an error occurs whilst reading (get) or writing (create) a Collection to disk
     * @throws LockException if we are unable to obtain a lock on the collections.dbx
     */
    void readCollectionEntry(SubCollectionEntry entry) throws IOException, LockException;

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
    boolean preserveOnCopy(PreserveType preserve);

    void addCurrentTransaction(Txn transaction);

    void removeCurrentTransaction(Txn transaction);

    @Nullable Txn getCurrentTransaction();

    /**
     * Gets the current transaction, or if there is no current transaction
     * for this thread (i.e. broker), then we begin a new transaction.
     *
     * The callee is *always* responsible for calling .close on the transaction
     *
     * Note - When there is an existing transaction, calling .close on the object
     * returned (e.g. ResusableTxn) from this function will only cause a minor state
     * change and not close the original transaction. That is intentional, as it will
     * eventually be closed by the creator of the original transaction (i.e. the code
     * site that began the first transaction)
     *
     * @deprecated This is a stepping-stone; Transactions should be explicitly passed
     *   around. This will be removed in the near future.
     * @return the transaction
     */
    @Deprecated
    Txn continueOrBeginTransaction();

    //TODO the object passed to the function e.g. Txn should not implement .close
    //if we are using a function passing approach like this, i.e. one point of
    //responsibility and WE HERE should be responsible for closing the transaction.
    //we could return a sub-class of Txn which is uncloseable like Txn.reuseable or similar
    //also getCurrentTransaction should then be made private
//    private <T> T transact(final Function<Txn, T> transactee) throws EXistException {
//        final Txn existing = getCurrentTransaction();
//        if(existing == null) {
//            try(final Txn txn = pool.getTransactionManager().beginTransaction()) {
//                return transactee.apply(txn);
//            }
//        } else {
//            return transactee.apply(existing);
//        }
//    }


    boolean isTriggersEnabled();

    void setTriggersEnabled(boolean triggersEnabled);

    @Override
    void close();

    /**
     * Indicates the behaviour for not preserving or
     * preserving Collection of Document attributes
     * when making a copy.
     */
    enum PreserveType {
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
