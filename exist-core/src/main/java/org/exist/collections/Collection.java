package org.exist.collections;

import org.exist.EXistException;
import org.exist.Resource;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.*;
import org.exist.storage.cache.Cacheable;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;

/**
 * Represents a Collection in the database. A collection maintains a list of
 * child Collections and documents, and provides the methods to store/remove resources.
 *
 * Collections are shared between {@link org.exist.storage.DBBroker} instances. The caller
 * is responsible to lock/unlock the collection. Call {@link DBBroker#openCollection(XmldbURI, LockMode)}
 * to get a collection with a read or write lock and {@link #release(LockMode)} to release the lock.
 */
public interface Collection extends Resource, Comparable<Collection>, Cacheable {

    /**
     * The length in bytes of the Collection ID
     */
    int LENGTH_COLLECTION_ID = 4; //sizeof int

    /**
     * The ID of an unknown Collection
     */
    int UNKNOWN_COLLECTION_ID = -1;

    /**
     * Get's the lock for this Collection
     * <p>
     * Note - this does not actually acquire the lock
     * for that you must subsequently call {@link Lock#acquire(LockMode)}
     *
     * @return The lock for the Collection
     */
    Lock getLock();

    /**
     * Closes the Collection, i.e. releases the lock held by
     * the current thread.
     * <p>
     * This is a shortcut for {@code getLock().release(LockMode)}
     *
     * @param mode The mode of the Lock to release
     */
    void release(LockMode mode);

    /**
     * Get the internal id.
     *
     * @return The id of the Collection
     */
    int getId();

    /**
     * Set the internal id
     *
     * @param id The id of the Collection
     */
    void setId(int id);

    /**
     * Set the internal storage address of the Collection data
     *
     * @param address The internal storage address
     */
    void setAddress(long address);

    /**
     * Gets the internal storage address of the Collection data
     *
     * @return The internal storage address
     */
    long getAddress();

    /**
     * Get the URI path of the Collection
     *
     * @return The URI path of the Collection
     */
    XmldbURI getURI();

    /**
     * Set the URI path of the Collection
     *
     * @param path The URI path of the Collection
     */
    void setPath(XmldbURI path);

    /**
     * Get the metadata of the Collection
     *
     * @return The Collection metadata
     */
    CollectionMetadata getMetadata();

    /**
     * Get the Collection permissions
     *
     * @return The permissions of this Collection
     */
    Permission getPermissions();

    /**
     * Get the Collection permissions (without locking)
     *
     * @return The permissions of this Collection
     */
    Permission getPermissionsNoLock();

    /**
     * Get the mode of the Collection permissions
     *
     * @param mode The unix like mode of the Collection permissions
     */
    void setPermissions(int mode) throws LockException, PermissionDeniedException;

    /**
     * Set the mode of the Collection permissions
     *
     * @param mode The unix like mode of the Collection permissions
     */
    @Deprecated
    void setPermissions(String mode) throws SyntaxException, LockException, PermissionDeniedException;

    /**
     * Set permissions for the collection.
     *
     * @param permissions the permissions to set on the Collection
     *
     * @deprecated This function is considered a security problem
     * and should be removed, move code to copyOf or Constructor
     */
    @Deprecated
    void setPermissions(Permission permissions) throws LockException;

    /**
     * Gets the creation timestamp of this Collection
     *
     * @return timestamp the creation timestamp in milliseconds
     *
     * @deprecated Use {@link #getMetadata()} {@link CollectionMetadata#getCreated()}
     */
    @Deprecated
    long getCreationTime();

    /**
     * Sets the creation timestamp of this Collection
     *
     * @param timestamp the creation timestamp in milliseconds
     */
    void setCreationTime(long timestamp);

    /**
     * Get the Collection Configuration of this Collection
     *
     * @param broker The database broker
     */
    @Nullable CollectionConfiguration getConfiguration(DBBroker broker);

    /**
     * Get the index configuration for this collection
     *
     * @param broker The database broker
     */
    IndexSpec getIndexConfiguration(DBBroker broker);

    /**
     * Get the index configuration for a node path of this collection
     *
     * @param broker The database broker
     * @param nodePath The node path to get the index configuration for
     *
     * @return The index configuration
     */
    GeneralRangeIndexSpec getIndexByPathConfiguration(DBBroker broker, NodePath nodePath);

    /**
     * Get the index configuration for a node name of this collection
     *
     * @param broker The database broker
     * @param nodeName The node name to get the index configuration for
     *
     * @return The index configuration
     */
    QNameRangeIndexSpec getIndexByQNameConfiguration(final DBBroker broker, final QName nodeName);

    /**
     * Returns true if this is a temporary collection. By default,
     * the temporary collection is in /db/system/temp.
     *
     * @return true if the collection is temporary, false otherwise
     */
    boolean isTempCollection();

    /**
     * Are Triggers enabled for this Collection
     *
     * @return true of Triggers are enabled
     */
    boolean isTriggersEnabled();

    /**
     * Enables/Disables Triggers for this collection
     *
     * @param enabled true if Triggers should be enabled for this Collection
     */
    void setTriggersEnabled(final boolean enabled);

    /**
     * Returns the estimated amount of memory used by this collection
     * and its documents. This information is required by the
     * {@link org.exist.storage.CollectionCacheManager} to be able
     * to resize the caches.
     *
     * @return estimated amount of memory in bytes
     */
    int getMemorySizeNoLock();

    /**
     * Get the parent Collection.
     *
     * @return The parent Collection of this Collection
     * or null if this is the root Collection (i.e. /db).
     */
    XmldbURI getParentURI();

    /**
     * Set user-defined Reader
     *
     * @param reader The XML reader
     */
    void setReader(XMLReader reader);

    /**
     * Determines if this Collection has any documents, or child Collections
     *
     * @param broker The database broker
     * @return true if the collection is empty, false otherwise
     */
    boolean isEmpty(DBBroker broker) throws PermissionDeniedException;

    /**
     * Returns the number of documents in this Collection
     *
     * @param broker The database broker
     * @return The number of documents in the Collection, or -1 if the collection could not be locked
     */
    int getDocumentCount(DBBroker broker) throws PermissionDeniedException;

    /**
     * Returns the number of documents in this Collection
     *
     * @param broker The database broker
     * @return The number of documents in the Collection
     * @deprecated Use {@link #getDocumentCount(DBBroker)}
     */
    @Deprecated
    int getDocumentCountNoLock(DBBroker broker) throws PermissionDeniedException;

    /**
     * Return the number of child Collections within this Collection.
     *
     * @param broker The database broker
     * @return The childCollectionCount value
     */
    int getChildCollectionCount(DBBroker broker) throws PermissionDeniedException;

    /**
     * Check if the Collection has a child document
     *
     * @param broker The database broker
     * @param name   the name (without path) of the document
     * @return true when the collection has the document, false otherwise
     */
    boolean hasDocument(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Check if the collection has a child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     * @return true if the child Collection exists, false otherwise
     */
    boolean hasChildCollection(DBBroker broker, XmldbURI name) throws PermissionDeniedException, LockException;

    /**
     * Check if the collection has a child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     * @return true if the child Collection exists, false otherwise
     * @deprecated Use {@link #hasChildCollection(DBBroker, XmldbURI)} instead
     */
    @Deprecated
    boolean hasChildCollectionNoLock(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Add a new child Collection to this Collection
     *
     * @param broker The database broker
     * @param child  The child Collection to add to this Collection
     * @param isNew  Whether the Child Collection is a newly created Collection
     */
    void addCollection(DBBroker broker, Collection child, boolean isNew)
            throws PermissionDeniedException, LockException;

    /**
     * Get the Document and child Collection
     * entries of this Collection
     *
     * @param broker The database broker
     * @return A list of entries in this Collection
     */
    List<CollectionEntry> getEntries(DBBroker broker)
            throws PermissionDeniedException, LockException;

    /**
     * Get the entry for a child Collection
     *
     * @param broker The database broker
     * @param name   The name of the child Collection
     * @return The child Collection entry
     */
    CollectionEntry getChildCollectionEntry(DBBroker broker, String name) throws PermissionDeniedException;

    /**
     * Get the entry for a resource
     *
     * @param broker The database broker
     * @param name   The name of the resource
     * @return The resource entry
     */
    CollectionEntry getResourceEntry(DBBroker broker, String name)
            throws PermissionDeniedException, LockException;

    /**
     * Update the specified child Collection
     *
     * @param broker The database broker
     * @param child  The child Collection to update
     */
    void update(DBBroker broker, Collection child) throws PermissionDeniedException, LockException;

    /**
     * Add a document to the collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         The document to add to the Collection
     */
    void addDocument(Txn transaction, DBBroker broker, DocumentImpl doc)
            throws PermissionDeniedException, LockException;

    /**
     * Removes the document from the internal list of resources, but
     * doesn't delete the document object itself.
     *
     * @param broker The database broker
     * @param doc    The document to unlink from the Collection
     */
    void unlinkDocument(DBBroker broker, DocumentImpl doc) throws PermissionDeniedException, LockException;

    /**
     * Return an iterator over all child Collections
     * <p>
     * The list of child Collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @param broker The database broker
     * @return An iterator over the child Collections
     */
    Iterator<XmldbURI> collectionIterator(DBBroker broker) throws PermissionDeniedException, LockException;

    /**
     * Return an iterator over all child Collections.
     * <p>
     * The list of child Collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @param broker The database broker
     * @return An iterator over the child Collections
     * @deprecated The creation of the stable iterator may
     * throw an {@link java.lang.IndexOutOfBoundsException},
     * use {@link #collectionIterator(DBBroker)} instead
     */
    @Deprecated
    Iterator<XmldbURI> collectionIteratorNoLock(DBBroker broker) throws PermissionDeniedException;

    /**
     * Returns an iterator on the documents in this Collection
     *
     * @param broker The database broker
     * @return A iterator of all the documents in the Collection.
     */
    Iterator<DocumentImpl> iterator(DBBroker broker) throws PermissionDeniedException, LockException;

    /**
     * Returns an iterator on the documents in this Collection
     *
     * @param broker The database broker
     * @return A iterator of all the documents in the Collection.
     * @deprecated This is not an atomic operation and
     * so there are no guarantees about which docs will be available to
     * the iterator. Use {@link #iterator(DBBroker)} instead
     */
    @Deprecated
    Iterator<DocumentImpl> iteratorNoLock(DBBroker broker) throws PermissionDeniedException;


    //TODO(AR) it is unlikely we need to pass the user as a parameter, fix this...

    /**
     * Return the Collections below this Collection
     *
     * @param broker The database broker
     * @param user   The user that is performing the operation
     * @return The List of descendant Collections
     */
    List<Collection> getDescendants(DBBroker broker, Subject user) throws PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @return The mutable document set provided in {@param docs}
     */
    MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive)
            throws PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @param lockMap   A map that receives the locks we have taken on documents
     * @return The mutable document set provided in {@param docs}
     */
    MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive,
                               LockedDocumentMap lockMap) throws PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @param lockMap   A map that receives the locks we have taken on documents
     * @param lockType  The type of lock to acquire on the documents
     * @return The mutable document set provided in {@param docs}
     */
    DocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive, LockedDocumentMap lockMap,
                        LockMode lockType) throws LockException, PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker The database broker
     * @param docs   A mutable document set which receives the documents
     * @return The mutable document set provided in {@param docs}
     */
    DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs) throws PermissionDeniedException, LockException;

    /**
     * Gets all of the documents from the Collection (without locking)
     *
     * @param broker The database broker
     * @param docs   A mutable document set which receives the documents
     * @return The mutable document set provided in {@param docs}
     * @deprecated This is not an atomic operation and
     * so there are no guarantees about which docs will be added to
     * the document set. Use {@link #getDocuments(DBBroker, MutableDocumentSet)}
     * instead
     */
    @Deprecated
    DocumentSet getDocumentsNoLock(DBBroker broker, MutableDocumentSet docs);

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker   The database broker
     * @param docs     A mutable document set which receives the documents
     * @param lockMap  A map that receives the locks we have taken on documents
     * @param lockType The type of lock to acquire on the documents
     * @return The mutable document set provided in {@param docs}
     */
    DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs, LockedDocumentMap lockMap, LockMode lockType)
            throws LockException, PermissionDeniedException;

    /**
     * Get a child resource as identified by name. This method doesn't put
     * a lock on the document nor does it recognize locks held by other threads.
     * There's no guarantee that the document still exists when accessing it.
     *
     * @param broker The database broker
     * @param name   The name of the document (without collection path)
     * @return the document or null if it doesn't exist
     */
    DocumentImpl getDocument(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Retrieve a child resource after putting a read lock on it.
     * With this method, access to the received document object is safe.
     *
     * @param broker The database broker
     * @param name   The name of the document (without collection path)
     * @return The locked document or null if it doesn't exist
     * @deprecated Use getDocumentWithLock(DBBroker broker, XmldbURI uri, int lockMode)
     */
    @Deprecated
    DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI name)
            throws LockException, PermissionDeniedException;

    /**
     * Retrieve a child resource after putting a lock on it.
     * With this method, access to the received document object is safe.
     *
     * @param broker   The database broker
     * @param name     The name of the document (without collection path)
     * @param lockMode The mode of the lock to acquire
     * @return The locked document or null if it doesn't exist
     */
    DocumentImpl getDocumentWithLock(DBBroker broker, XmldbURI name, LockMode lockMode)
            throws LockException, PermissionDeniedException;

    /**
     * Get a child resource as identified by path. This method doesn't put
     * a lock on the document nor does it recognize locks held by other threads.
     * There's no guarantee that the document still exists when accessing it.
     *
     * @param broker  The database broker
     * @param rawPath The path of the document
     * @return the document or null if it doesn't exist
     * @deprecated Use {@link #getDocument(DBBroker, XmldbURI)} instead
     */
    @Deprecated
    DocumentImpl getDocumentNoLock(DBBroker broker, String rawPath) throws PermissionDeniedException;

    /**
     * Release any locks held on the document
     *
     * @param doc The document to release locks on
     * @deprecated Use {@link #releaseDocument(DocumentImpl, LockMode)} instead
     */
    @Deprecated
    void releaseDocument(DocumentImpl doc);

    /**
     * Release any locks held on the document
     *
     * @param doc  The document to release locks on
     * @param mode The lock mode to release
     */
    void releaseDocument(DocumentImpl doc, LockMode mode);

    /**
     * Remove the specified child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     */
    void removeCollection(DBBroker broker, XmldbURI name) throws LockException, PermissionDeniedException;

    /**
     * Removes a document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         The document to remove
     */
    void removeResource(Txn transaction, DBBroker broker, DocumentImpl doc)
            throws PermissionDeniedException, LockException, IOException, TriggerException;

    /**
     * Remove an XML document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     */
    void removeXMLResource(Txn transaction, DBBroker broker, XmldbURI name)
            throws PermissionDeniedException, TriggerException, LockException, IOException;

    /**
     * Remove a Binary document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     */
    void removeBinaryResource(Txn transaction, DBBroker broker, XmldbURI name)
            throws PermissionDeniedException, LockException, TriggerException;

    /**
     * Remove a Binary document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         the document to remove
     */
    void removeBinaryResource(Txn transaction, DBBroker broker, DocumentImpl doc)
            throws PermissionDeniedException, LockException, TriggerException;

    /**
     * Validates an XML document and prepares it for further storage.
     * Launches prepare and postValidate triggers.
     * Since the process is dependent from the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param source      The source of the document to store
     * @return An {@link IndexInfo} with a write lock on the document
     */
    IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI name, InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Validates an XML document and prepares it for further storage.
     * Launches prepare and postValidate triggers.
     * Since the process is dependent from the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param data        The data of the document to store
     * @return An {@link IndexInfo} with a write lock on the document
     */
    IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI name, String data)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Validates an XML document and prepares it for further storage.
     * Launches prepare and postValidate triggers.
     * Since the process is dependent from the collection configuration,
     * the collection acquires a write lock during the process.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param node        The document node of the document to store
     * @return An {@link IndexInfo} with a write lock on the document
     */
    IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI name, Node node)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Stores an XML document into the Collection
     * <p>
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, InputSource)} should have been called previously in order
     * to acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param source      The source of the document to store
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Stores an XML document into the Collection
     * <p>
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, String)} should have been called previously in order to
     * acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param data        The data of the document to store
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, String data)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Stores an XML document into the Collection
     * <p>
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, Node)} should have been called previously in order to
     * acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param node        The document node of the document to store
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, Node node)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Creates a Binary Document object
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     *
     * @return The Binary Document object
     */
    BinaryDocument validateBinaryResource(Txn transaction, DBBroker broker, XmldbURI name)
            throws PermissionDeniedException, LockException, TriggerException, IOException;

    /**
     * Store a binary document into the Collection (streaming)
     *
     * Locks the collection while the resource is being saved. Triggers will be called after the collection
     * has been unlocked while keeping a lock on the resource to prevent modification.
     *
     * Callers should not lock the collection before calling this method as this may lead to deadlocks.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param is          The content for the document
     * @param mimeType    The Internet Media Type of the document
     * @param size        The size in bytes of the document
     * @param created     The created timestamp of the document
     * @param modified    The modified timestamp of the document
     *
     * @return The stored Binary Document object
     */
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, XmldbURI name, InputStream is, String mimeType,
            long size, Date created, Date modified) throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException;

    /**
     * Store a binary document into the Collection
     *
     * Locks the collection while the resource is being saved. Triggers will be called after the collection
     * has been unlocked while keeping a lock on the resource to prevent modification.
     *
     * Callers should not lock the collection before calling this method as this may lead to deadlocks.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param data        The content for the document
     * @param mimeType    The Internet Media Type of the document
     *
     * @return The stored Binary Document object
     *
     * @deprecated Use {@link #addBinaryResource(Txn, DBBroker, XmldbURI, InputStream, String, long)}
     */
    @Deprecated
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, XmldbURI name, byte[] data, String mimeType)
            throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException;

    /**
     * Store a binary document into the Collection
     *
     * Locks the collection while the resource is being saved. Triggers will be called after the collection
     * has been unlocked while keeping a lock on the resource to prevent modification.
     *
     * Callers should not lock the collection before calling this method as this may lead to deadlocks.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param data        The content for the document
     * @param mimeType    The Internet Media Type of the document
     * @param created     The created timestamp of the document
     * @param modified    The modified timestamp of the document
     *
     * @return The stored Binary Document object
     *
     * @deprecated Use {@link #addBinaryResource(Txn, DBBroker, BinaryDocument, InputStream, String, long, Date, Date)}
     */
    @Deprecated
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, XmldbURI name, byte[] data, String mimeType,
            Date created, Date modified) throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException;

    /**
     * Store a binary document into the Collection (streaming)
     *
     * Locks the collection while the resource is being saved. Triggers will be called after the collection
     * has been unlocked while keeping a lock on the resource to prevent modification.
     *
     * Callers should not lock the collection before calling this method as this may lead to deadlocks.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @param is          The content for the document
     * @param mimeType    The Internet Media Type of the document
     * @param size        The size in bytes of the document
     *
     * @return The stored Binary Document object
     */
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, XmldbURI name, InputStream is,
            String mimeType, long size) throws EXistException, PermissionDeniedException, LockException,
            TriggerException, IOException;

    /**
     * Store a binary document into the Collection (streaming)
     *
     * Locks the collection while the resource is being saved. Triggers will be called after the collection
     * has been unlocked while keeping a lock on the resource to prevent modification.
     *
     * Callers should not lock the collection before calling this method as this may lead to deadlocks.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param blob        the binary resource to store the data into
     * @param is          The content for the document
     * @param mimeType    The Internet Media Type of the document
     * @param size        The size in bytes of the document
     * @param created     The created timestamp of the document
     * @param modified    The modified timestamp of the document
     *
     * @return The stored Binary Document object
     */
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, BinaryDocument blob, InputStream is,
            String mimeType, long size, Date created, Date modified) throws EXistException, PermissionDeniedException,
            LockException, TriggerException, IOException;

    /**
     * Gets an Observable object for this Collection
     *
     * @return An observable of this Collection, or null if the Collection is not Observable
     */
    Observable getObservable();

    /**
     * Serializes the Collection to a variable byte representation
     *
     * @param outputStream The output stream to write the collection contents to
     */
    void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException;

    //TODO(AR) consider a better separation between Broker and Collection, possibly introduce a CollectionManager object
    interface InternalAccess {
        void addDocument(DocumentImpl doc) throws EXistException;
        int getId();
    }


    //TODO(AR) remove specific implementation details from below - i.e. the read functions etc;
    abstract class CollectionEntry {
        private final XmldbURI uri;
        private Permission permissions;
        private long created = -1;

        protected CollectionEntry(final XmldbURI uri, final Permission permissions) {
            this.uri = uri;
            this.permissions = permissions;
        }

        public abstract void readMetadata(DBBroker broker);

        public abstract void read(VariableByteInput is) throws IOException;

        public XmldbURI getUri() {
            return uri;
        }

        public long getCreated() {
            return created;
        }

        protected void setCreated(final long created) {
            this.created = created;
        }

        public Permission getPermissions() {
            return permissions;
        }

        protected void setPermissions(final Permission permissions) {
            this.permissions = permissions;
        }
    }

    class SubCollectionEntry extends CollectionEntry {
        public SubCollectionEntry(final SecurityManager sm, final XmldbURI uri) {
            super(uri, PermissionFactory.getDefaultCollectionPermission(sm));
        }

        @Override
        public void readMetadata(final DBBroker broker) {
            broker.readCollectionEntry(this);
        }

        @Override
        public void read(final VariableByteInput is) throws IOException {
            is.skip(1);
            final int collLen = is.readInt();
            for (int i = 0; i < collLen; i++) {
                is.readUTF();
            }
            getPermissions().read(is);
            setCreated(is.readLong());
        }

        public void read(final Collection collection) {
            setPermissions(collection.getPermissionsNoLock());
            setCreated(collection.getCreationTime());
        }
    }

    class DocumentEntry extends CollectionEntry {
        public DocumentEntry(final DocumentImpl document) {
            super(document.getURI(), document.getPermissions());
            setCreated(document.getMetadata().getCreated());
        }

        @Override
        public void readMetadata(final DBBroker broker) {
        }

        @Override
        public void read(final VariableByteInput is) throws IOException {
        }
    }
}
