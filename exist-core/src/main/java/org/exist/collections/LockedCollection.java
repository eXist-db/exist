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
package org.exist.collections;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.QName;
import org.exist.dom.persistent.*;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.*;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockedDocumentMap;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.DocumentType;
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

/**
 * Just a Delegate to a {@link Collection} which allows us to also hold a lock
 * lease which is released when {@link Collection#close()} is called. This
 * allows us to use ARM (Automatic Resource Management) e.g. try-with-resources
 * with eXist Collection objects
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class LockedCollection implements Collection {
    private final ManagedCollectionLock managedCollectionLock;
    private final Collection collection;

    public LockedCollection(final ManagedCollectionLock managedCollectionLock, final Collection collection) {
        this.managedCollectionLock = managedCollectionLock;
        this.collection = collection;
    }

    //TODO(AR) if we decide that LockedCollection shouldn't implement Collection (but instead become a Tuple2<ManagedCollectionLock, Collection>) then drop this method
    /**
     * Given a Collection implementation returns the non-lock wrapped
     * implementation.
     *
     * @param collection a Collection object.
     * @return the actual non-lock wrapped Collection object.
     */
    public static Collection unwrapLocked(final Collection collection) {
        //TODO(AR) do we want to stay with LockedCollection implements Collection design {@link LockedCollection#getCollection()}
        if(collection instanceof LockedCollection) {
            // unwrap the locked collection
            return ((LockedCollection)collection).collection;
        } else {
            return collection;
        }
    }

    /**
     * Unlocks and Closes the Collection
     */
    @Override
    public void close() {
        collection.close();
        managedCollectionLock.close();
    }

    @Override
    public int getId() {
        return collection.getId();
    }

    @Override
    public XmldbURI getURI() {
        return collection.getURI();
    }

    @Override
    public void setPath(final XmldbURI path) {
        collection.setPath(path);
    }

    @Override
    public void setPath(final XmldbURI path, final boolean updateChildren) {
        collection.setPath(path, updateChildren);
    }

    @Deprecated
    @Override
    public CollectionMetadata getMetadata() {
        return collection.getMetadata();
    }

    @Override
    public Permission getPermissions() {
        return collection.getPermissions();
    }

    @Override
    public Permission getPermissionsNoLock() {
        return collection.getPermissionsNoLock();
    }

    @Override
    public void setPermissions(final DBBroker broker, final int mode) throws LockException, PermissionDeniedException {
        collection.setPermissions(broker, mode);
    }

    @Override
    public long getCreated() {
        return collection.getCreated();
    }

    @Override
    public void setCreated(final long timestamp) {
        collection.setCreated(timestamp);
    }

    @Override
    public CollectionConfiguration getConfiguration(final DBBroker broker) {
        return collection.getConfiguration(broker);
    }

    @Override
    public IndexSpec getIndexConfiguration(final DBBroker broker) {
        return collection.getIndexConfiguration(broker);
    }

    @Override
    public GeneralRangeIndexSpec getIndexByPathConfiguration(final DBBroker broker, final NodePath nodePath) {
        return collection.getIndexByPathConfiguration(broker, nodePath);
    }

    @Override
    public QNameRangeIndexSpec getIndexByQNameConfiguration(final DBBroker broker, final QName nodeName) {
        return collection.getIndexByQNameConfiguration(broker, nodeName);
    }

    @Override
    public boolean isTempCollection() {
        return collection.isTempCollection();
    }

    @Override
    public int getMemorySize() {
        return collection.getMemorySize();
    }

    @Override
    public int getMemorySizeNoLock() {
        return collection.getMemorySizeNoLock();
    }

    @Override
    public XmldbURI getParentURI() {
        return collection.getParentURI();
    }

    @Override
    public boolean isEmpty(final DBBroker broker) throws PermissionDeniedException {
        return collection.isEmpty(broker);
    }

    @Override
    public int getDocumentCount(final DBBroker broker) throws PermissionDeniedException {
        return collection.getDocumentCount(broker);
    }

    @Override
    @Deprecated
    public int getDocumentCountNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.getDocumentCountNoLock(broker);
    }

    @Override
    public int getChildCollectionCount(final DBBroker broker) throws PermissionDeniedException {
        return collection.getChildCollectionCount(broker);
    }

    @Override
    public boolean hasDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.hasDocument(broker, name);
    }

    @Override
    public boolean hasChildCollection(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException {
        return collection.hasChildCollection(broker, name);
    }

    @Override
    @Deprecated
    public boolean hasChildCollectionNoLock(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.hasChildCollectionNoLock(broker, name);
    }

    @Override
    public void addCollection(final DBBroker broker, final Collection child) throws PermissionDeniedException, LockException {
        collection.addCollection(broker, child);
    }

    @Override
    public List<CollectionEntry> getEntries(final DBBroker broker) throws PermissionDeniedException, LockException, IOException {
        return collection.getEntries(broker);
    }

    @Override
    public CollectionEntry getChildCollectionEntry(final DBBroker broker, final String name) throws PermissionDeniedException, LockException, IOException {
        return collection.getChildCollectionEntry(broker, name);
    }

    @Override
    public CollectionEntry getResourceEntry(final DBBroker broker, final String name) throws PermissionDeniedException, LockException, IOException {
        return collection.getResourceEntry(broker, name);
    }

    @Override
    public void addDocument(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException {
        collection.addDocument(transaction, broker, doc);
    }

    @Override
    public void unlinkDocument(final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException {
        collection.unlinkDocument(broker, doc);
    }

    @Override
    public Iterator<XmldbURI> collectionIterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        return collection.collectionIterator(broker);
    }

    @Override
    @Deprecated
    public Iterator<XmldbURI> collectionIteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.collectionIteratorNoLock(broker);
    }

    @Override
    public Iterator<DocumentImpl> iterator(final DBBroker broker) throws PermissionDeniedException, LockException {
        return collection.iterator(broker);
    }

    @Override
    @Deprecated
    public Iterator<DocumentImpl> iteratorNoLock(final DBBroker broker) throws PermissionDeniedException {
        return collection.iteratorNoLock(broker);
    }

    @Override
    public List<Collection> getDescendants(final DBBroker broker, final Subject user) throws PermissionDeniedException {
        return collection.getDescendants(broker, user);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive) throws PermissionDeniedException, LockException {
        return collection.allDocs(broker, docs, recursive);
    }

    @Override
    public MutableDocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap lockMap) throws PermissionDeniedException, LockException {
        return collection.allDocs(broker, docs, recursive, lockMap);
    }

    @Override
    public DocumentSet allDocs(final DBBroker broker, final MutableDocumentSet docs, final boolean recursive, final LockedDocumentMap lockMap, final Lock.LockMode lockType) throws LockException, PermissionDeniedException {
        return collection.allDocs(broker, docs, recursive, lockMap, lockType);
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs) throws PermissionDeniedException, LockException {
        return collection.getDocuments(broker, docs);
    }

    @Override
    @Deprecated
    public DocumentSet getDocumentsNoLock(final DBBroker broker, final MutableDocumentSet docs) {
        return collection.getDocumentsNoLock(broker, docs);
    }

    @Override
    public DocumentSet getDocuments(final DBBroker broker, final MutableDocumentSet docs, final LockedDocumentMap lockMap, final Lock.LockMode lockType) throws LockException, PermissionDeniedException {
        return collection.getDocuments(broker, docs, lockMap, lockType);
    }

    @Override
    public DocumentImpl getDocument(final DBBroker broker, final XmldbURI name) throws PermissionDeniedException {
        return collection.getDocument(broker, name);
    }

    @Override
    @Deprecated
    public LockedDocument getDocumentWithLock(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
        return collection.getDocumentWithLock(broker, name);
    }

    @Override
    public LockedDocument getDocumentWithLock(final DBBroker broker, final XmldbURI name, final Lock.LockMode lockMode) throws LockException, PermissionDeniedException {
        return collection.getDocumentWithLock(broker, name, lockMode);
    }

    @Override
    @Deprecated
    public DocumentImpl getDocumentNoLock(final DBBroker broker, final String rawPath) throws PermissionDeniedException {
        return collection.getDocumentNoLock(broker, rawPath);
    }

    @Override
    public void removeCollection(final DBBroker broker, final XmldbURI name) throws LockException, PermissionDeniedException {
        collection.removeCollection(broker, name);
    }

    @Override
    public void removeResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException, IOException, TriggerException {
        collection.removeResource(transaction, broker, doc);
    }

    @Override
    public void removeXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, TriggerException, LockException, IOException {
        collection.removeXMLResource(transaction, broker, name);
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, name);
    }

    @Override
    public void removeBinaryResource(final Txn transaction, final DBBroker broker, final DocumentImpl doc) throws PermissionDeniedException, LockException, TriggerException {
        collection.removeBinaryResource(transaction, broker, doc);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, @Nullable final MimeType mimeType) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        broker.storeDocument(transaction, name, source, mimeType, collection);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, @Nullable final MimeType mimeType, @Nullable final Date createdDate, @Nullable final Date lastModifiedDate, @Nullable final Permission permission, @Nullable final DocumentType documentType, @Nullable final XMLReader xmlReader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, broker, name, source, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node, @Nullable final MimeType mimeType) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        broker.storeDocument(transaction, name, node, mimeType, collection);
    }

    @Override
    public void storeDocument(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node, @Nullable final MimeType mimeType, @Nullable final Date createdDate, @Nullable final Date lastModifiedDate, @Nullable final Permission permission, @Nullable final DocumentType documentType, @Nullable final XMLReader xmlReader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        collection.storeDocument(transaction, broker, name, node, mimeType, createdDate, lastModifiedDate, permission, documentType, xmlReader);
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, source);
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputSource source, final XMLReader reader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, source, reader);
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, data);
    }

    @Deprecated
    @Override
    public IndexInfo validateXMLResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException, IOException {
        return collection.validateXMLResource(transaction, broker, name, node);
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, source);
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final InputSource source, final XMLReader reader) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, source, reader);
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final String data) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, data);
    }

    @Deprecated
    @Override
    public void store(final Txn transaction, final DBBroker broker, final IndexInfo info, final Node node) throws EXistException, PermissionDeniedException, TriggerException, SAXException, LockException {
        collection.store(transaction, broker, info, node);
    }

    @Deprecated
    @Override
    public BinaryDocument validateBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name) throws PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.validateBinaryResource(transaction, broker, name);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, is, mimeType, size, created, modified);
    }

    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size, final Date created, final Date modified, @Nullable final Permission permission) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, is, mimeType, size, created, modified, permission);
    }

    @Override
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, data, mimeType);
    }

    @Override
    @Deprecated
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final byte[] data, final String mimeType, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, data, mimeType, created, modified);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final XmldbURI name, final InputStream is, final String mimeType, final long size) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, name, is, mimeType, size);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, blob, is, mimeType, size, created, modified);
    }

    @Deprecated
    @Override
    public BinaryDocument addBinaryResource(final Txn transaction, final DBBroker broker, final BinaryDocument blob, final InputStream is, final String mimeType, final long size, final Date created, final Date modified, final DBBroker.PreserveType preserve) throws EXistException, PermissionDeniedException, LockException, TriggerException, IOException {
        return collection.addBinaryResource(transaction, broker, blob, is, mimeType, size, created, modified, preserve);
    }

    @Override
    public void serialize(final VariableByteOutputStream outputStream) throws IOException, LockException {
        collection.serialize(outputStream);
    }

    @Override
    public int compareTo(final Collection o) {
        return collection.compareTo(o);
    }
}
