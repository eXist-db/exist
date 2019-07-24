package org.exist.collections;

import org.exist.EXistException;
import org.exist.Resource;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.*;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
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

import static org.exist.storage.lock.Lock.LockMode.READ_LOCK;
import static org.exist.storage.lock.Lock.LockMode.WRITE_LOCK;

/**
 * Represents a Collection in the database. A collection maintains a list of
 * child Collections and documents, and provides the methods to store/remove resources.
 *
 * Collections are shared between {@link org.exist.storage.DBBroker} instances. The caller
 * is responsible to lock/unlock the collection. Call {@link org.exist.storage.DBBroker#openCollection(org.exist.xmldb.XmldbURI, org.exist.storage.lock.Lock.LockMode)}
 * to get a collection with a read or write lock and {@link #close()} to release the lock.
 */
public interface Collection extends Resource, Comparable<Collection>, AutoCloseable {

    /**
     * The length in bytes of the Collection ID
     */
    int LENGTH_COLLECTION_ID = 4; //sizeof int

    /**
     * The ID of an unknown Collection
     */
    int UNKNOWN_COLLECTION_ID = -1;

    /**
     * Get the internal id.
     *
     * @return The id of the Collection
     */
    int getId();

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
     * Set the mode of the Collection.
     *
     * @param broker The database broker.
     * @param mode The unix like mode of the Collection permissions
     * @throws LockException if dbbroker is locked
     * @throws PermissionDeniedException if use does not have required permissions
     */
    void setPermissions(DBBroker broker, int mode) throws LockException, PermissionDeniedException;

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
    @EnsureContainerLocked(mode=WRITE_LOCK) void setCreationTime(long timestamp);

    /**
     * Get the Collection Configuration of this Collection
     *
     * @param broker The database broker
     * @return CollectionConfiguration of this Collection
     */
    @Nullable CollectionConfiguration getConfiguration(DBBroker broker);

    /**
     * Get the index configuration for this collection
     *
     * @param broker The database broker
     * @return IndexSpec aka configuration for this collection
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
     * Returns the estimated amount of memory used by this collection
     * and its documents. This information is required by the
     * {@link org.exist.collections.CollectionCache} to be able
     * to resize the caches.
     *
     * @return estimated amount of memory in bytes
     */
    int getMemorySize();

    /**
     * Returns the estimated amount of memory used by this collection
     * and its documents. This information is required by the
     * {@link org.exist.collections.CollectionCache} to be able
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
     * Determines if this Collection has any documents, or child Collections
     *
     * @param broker The database broker
     * @return true if the collection is empty, false otherwise
     * @throws PermissionDeniedException if user has not sufficient rights
     */
    boolean isEmpty(DBBroker broker) throws PermissionDeniedException;

    /**
     * Returns the number of documents in this Collection
     *
     * @param broker The database broker
     * @return The number of documents in the Collection, or -1 if the collection could not be locked
     * @throws PermissionDeniedException if user has not sufficient rights
     */
    int getDocumentCount(DBBroker broker) throws PermissionDeniedException;

    /**
     * Returns the number of documents in this Collection
     *
     * @param broker The database broker
     * @return The number of documents in the Collection
     * @throws PermissionDeniedException if user has not sufficient rights
     * @deprecated Use {@link #getDocumentCount(DBBroker)}
     */
    @Deprecated
    int getDocumentCountNoLock(DBBroker broker) throws PermissionDeniedException;

    /**
     * Return the number of child Collections within this Collection.
     *
     * @param broker The database broker
     * @throws PermissionDeniedException if user has not sufficient rights
     * @return The childCollectionCount value
     */
    int getChildCollectionCount(DBBroker broker) throws PermissionDeniedException;

    /**
     * Check if the Collection has a child document
     *
     * @param broker The database broker
     * @param name   the name (without path) of the document
     * @throws PermissionDeniedException if user has not sufficient rights
     * @return true when the collection has the document, false otherwise
     */
    boolean hasDocument(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Check if the collection has a child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     * @return true if the child Collection exists, false otherwise
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    boolean hasChildCollection(DBBroker broker, XmldbURI name) throws PermissionDeniedException, LockException;

    /**
     * Check if the collection has a child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     * @return true if the child Collection exists, false otherwise
     * @throws PermissionDeniedException if user has not sufficient rights
     * @deprecated Use {@link #hasChildCollection(DBBroker, XmldbURI)} instead
     */
    @Deprecated
    boolean hasChildCollectionNoLock(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Add a new child Collection to this Collection
     *
     * @param broker The database broker
     * @param child  The child Collection to add to this Collection
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    void addCollection(DBBroker broker, @EnsureLocked(mode=WRITE_LOCK) Collection child)
            throws PermissionDeniedException, LockException;

    /**
     * Get the Document and child Collection
     * entries of this Collection
     *
     * @param broker The database broker
     * @return A list of entries in this Collection
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     */
    List<CollectionEntry> getEntries(DBBroker broker)
            throws PermissionDeniedException, LockException, IOException;

    /**
     * Get the entry for a child Collection
     *
     * @param broker The database broker
     * @param name   The name of the child Collection
     * @return The child Collection entry
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     */
    CollectionEntry getChildCollectionEntry(DBBroker broker, String name)
            throws PermissionDeniedException, LockException, IOException;

    /**
     * Get the entry for a resource
     *
     * @param broker The database broker
     * @param name   The name of the resource
     * @return The resource entry
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     */
    CollectionEntry getResourceEntry(DBBroker broker, String name)
            throws PermissionDeniedException, LockException, IOException;

    /**
     * Update the specified child Collection
     *
     * @param broker The database broker
     * @param child  The child Collection to update
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    void update(DBBroker broker, @EnsureLocked(mode=WRITE_LOCK) Collection child) throws PermissionDeniedException, LockException;

    /**
     * Add a document to the collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         The document to add to the Collection
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    void addDocument(Txn transaction, DBBroker broker, DocumentImpl doc)
            throws PermissionDeniedException, LockException;

    /**
     * Removes the document from the internal list of resources, but
     * doesn't delete the document object itself.
     *
     * @param broker The database broker
     * @param doc    The document to unlink from the Collection
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    void unlinkDocument(DBBroker broker, @EnsureLocked(mode=WRITE_LOCK) DocumentImpl doc) throws PermissionDeniedException, LockException;

    /**
     * Return an iterator over all child Collections
     *
     * The list of child Collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @param broker The database broker
     * @return An iterator over the child Collections
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    Iterator<XmldbURI> collectionIterator(DBBroker broker) throws PermissionDeniedException, LockException;

    /**
     * Return an iterator over all child Collections.
     *
     * The list of child Collections is copied first, so modifications
     * via the iterator have no effect.
     *
     * @param broker The database broker
     * @return An iterator over the child Collections
     * @throws PermissionDeniedException if user has not sufficient rights
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    Iterator<DocumentImpl> iterator(DBBroker broker) throws PermissionDeniedException, LockException;

    /**
     * Returns an iterator on the documents in this Collection
     *
     * @param broker The database broker
     * @return A iterator of all the documents in the Collection.
     * @throws PermissionDeniedException if user has not sufficient rights
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
     * @throws PermissionDeniedException if user has not sufficient rights
     */
    List<Collection> getDescendants(DBBroker broker, Subject user) throws PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @return The mutable document set provided in {@code docs}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive)
            throws PermissionDeniedException, LockException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @param lockMap   A map that receives the locks we have taken on documents
     * @return The mutable document set provided in {@code docs}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    MutableDocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive,
                               LockedDocumentMap lockMap) throws PermissionDeniedException, LockException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker    The database broker
     * @param docs      A mutable document set which receives the documents
     * @param recursive true if we should get all descendants, false just retrieves the children
     * @param lockMap   A map that receives the locks we have taken on documents
     * @param lockType  The type of lock to acquire on the documents
     * @return The mutable document set provided in {@code docs}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    DocumentSet allDocs(DBBroker broker, MutableDocumentSet docs, boolean recursive, LockedDocumentMap lockMap,
                        LockMode lockType) throws LockException, PermissionDeniedException;

    /**
     * Gets all of the documents from the Collection
     *
     * @param broker The database broker
     * @param docs   A mutable document set which receives the documents
     * @return The mutable document set provided in {@code docs}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    DocumentSet getDocuments(DBBroker broker, MutableDocumentSet docs) throws PermissionDeniedException, LockException;

    /**
     * Gets all of the documents from the Collection (without locking)
     *
     * @param broker The database broker
     * @param docs   A mutable document set which receives the documents
     * @return The mutable document set provided in {@code docs}
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
     * @return The mutable document set provided in {@code docs}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked*
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
     * @throws PermissionDeniedException if user has not sufficient rights
     */
    @Nullable @EnsureUnlocked DocumentImpl getDocument(DBBroker broker, XmldbURI name) throws PermissionDeniedException;

    /**
     * Retrieve a child resource after putting a read lock on it.
     * With this method, access to the received document object is safe.
     *
     * @param broker The database broker
     * @param name   The name of the document (without collection path)
     * @return The locked document or null if it doesn't exist
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     *
     * @deprecated Use {@link #getDocumentWithLock(org.exist.storage.DBBroker, org.exist.xmldb.XmldbURI, org.exist.storage.lock.Lock.LockMode)}
     */
    @Deprecated
    @Nullable LockedDocument getDocumentWithLock(DBBroker broker, XmldbURI name)
            throws LockException, PermissionDeniedException;

    /**
     * Retrieve a child resource after putting a lock on it.
     * With this method, access to the received document object is safe.
     *
     * @param broker   The database broker
     * @param name     The name of the document (without collection path)
     * @param lockMode The mode of the lock to acquire
     * @return The locked document or null if it doesn't exist
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked*
     */
    @Nullable LockedDocument getDocumentWithLock(DBBroker broker, XmldbURI name, LockMode lockMode)
            throws LockException, PermissionDeniedException;

    /**
     * Get a child resource as identified by path. This method doesn't put
     * a lock on the document nor does it recognize locks held by other threads.
     * There's no guarantee that the document still exists when accessing it.
     *
     * @param broker  The database broker
     * @param rawPath The path of the document
     * @return the document or null if it doesn't exist
     * @throws PermissionDeniedException if user has not sufficient rights
     * @deprecated Use {@link #getDocument(DBBroker, XmldbURI)} instead
     */
    @Deprecated
    @Nullable DocumentImpl getDocumentNoLock(DBBroker broker, String rawPath) throws PermissionDeniedException;

    /**
     * Remove the specified child Collection
     *
     * @param broker The database broker
     * @param name   the name of the child Collection (without path)
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     */
    void removeCollection(DBBroker broker, XmldbURI name) throws LockException, PermissionDeniedException;

    /**
     * Removes a document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         The document to remove
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     */
    void removeResource(Txn transaction, DBBroker broker, DocumentImpl doc)
            throws PermissionDeniedException, LockException, IOException, TriggerException;

    /**
     * Remove an XML document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     */
    void removeXMLResource(Txn transaction, DBBroker broker, XmldbURI name)
            throws PermissionDeniedException, TriggerException, LockException, IOException;

    /**
     * Remove a Binary document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws TriggerException in case of eXist-db trigger error
     */
    void removeBinaryResource(Txn transaction, DBBroker broker, XmldbURI name)
            throws PermissionDeniedException, LockException, TriggerException;

    /**
     * Remove a Binary document from this Collection
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param doc         the document to remove
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws TriggerException in case of eXist-db trigger error
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     *
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
     * @param source      The source of the document to store
     * @param reader      The XML reader to use for reading the {@code source}
     * @return An {@link IndexInfo} with a write lock on the document
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI name, InputSource source, XMLReader reader)
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    IndexInfo validateXMLResource(Txn transaction, DBBroker broker, XmldbURI name, Node node)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException;

    /**
     * Stores an XML document into the Collection
     *
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, InputSource)} should have been called previously in order
     * to acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param source      The source of the document to store
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, InputSource source)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Stores an XML document into the Collection
     *
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, InputSource, XMLReader)} should have been called previously
     * in order to acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param source      The source of the document to store
     * @param reader      The XML reader to use for reading the {@code source}
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked*
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source, final XMLReader reader)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Stores an XML document into the Collection
     *
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, String)} should have been called previously in order to
     * acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param data        The data of the document to store
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, String data)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Stores an XML document into the Collection
     *
     * {@link #validateXMLResource(Txn, DBBroker, XmldbURI, Node)} should have been called previously in order to
     * acquire a write lock for the document. Launches the finish trigger.
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param info        Tracks information between validate and store phases
     * @param node        The document node of the document to store
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
     * @throws SAXException internal SAXException
     */
    void store(Txn transaction, DBBroker broker, IndexInfo info, Node node)
            throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException;

    /**
     * Creates a Binary Document object
     *
     * @param transaction The database transaction
     * @param broker      The database broker
     * @param name        the name (without path) of the document
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception*
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception
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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception

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
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception

     *
     * @return The stored Binary Document object
     */
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, BinaryDocument blob, InputStream is,
            String mimeType, long size, Date created, Date modified) throws EXistException, PermissionDeniedException,
            LockException, TriggerException, IOException;

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
     * @param preserve    In the case of a copy, cause the copy process to preserve the following attributes of each
     *                    source in the copy: modification time, file mode, user ID, and group ID, as allowed by
     *                    permissions. Access Control Lists (ACLs) will also be preserved.
     * @throws PermissionDeniedException if user has not sufficient rights
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors
     * @throws TriggerException in case of eXist-db trigger error
     * @throws EXistException general eXist-db exception

     *
     * @return The stored Binary Document object
     */
    BinaryDocument addBinaryResource(Txn transaction, DBBroker broker, BinaryDocument blob, InputStream is,
            String mimeType, long size, Date created, Date modified, DBBroker.PreserveType preserve)
            throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException;

    /**
     * Serializes the Collection to a variable byte representation
     *
     * @param outputStream The output stream to write the collection contents to
     * @throws LockException if broker is locked
     * @throws IOException in case of I/O errors

     */
    @EnsureContainerLocked(mode=READ_LOCK) void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException;

    @Override void close();

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

        public abstract void readMetadata(DBBroker broker) throws IOException, LockException;

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
        public void readMetadata(final DBBroker broker) throws IOException, LockException {
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
        public void read(final VariableByteInput is) {
        }
    }
}
