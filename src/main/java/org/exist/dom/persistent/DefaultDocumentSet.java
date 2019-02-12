/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-2014 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.dom.persistent;

import net.jcip.annotations.NotThreadSafe;
import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;

import java.util.*;

/**
 * Manages a set of documents.
 *
 * This class implements the NodeList interface for a collection of documents.
 * It also contains methods to retrieve the collections these documents
 * belong to.
 *
 * @author wolf
 * @author aretter
 */
@NotThreadSafe
public class DefaultDocumentSet implements MutableDocumentSet {

    private static final int DEFAULT_SIZE = 29;
    private static final float DEFAULT_GROWTH = 1.75f;

    private final BitSet docIds = new BitSet();
    private final Map<Integer, DocumentImpl> docs;
    private final BitSet collectionIds = new BitSet();
    private final Set<Collection> collections = new LinkedHashSet<>();

    public DefaultDocumentSet() {
        this(DEFAULT_SIZE);
    }

    public DefaultDocumentSet(final int initialSize) {
        this.docs = new LinkedHashMap<>(initialSize, DEFAULT_GROWTH);
    }

    @Override
    public void clear() {
        this.docIds.clear();
        this.docs.clear();
        this.collectionIds.clear();
        this.collections.clear();
    }

    @Override
    public void add(final DocumentImpl doc) {
        add(doc, true);
    }

    @Override
    public void add(final DocumentImpl doc, final boolean checkDuplicates) {
        final int docId = doc.getDocId();
        if (checkDuplicates && contains(docId)) {
            return;
        }

        docIds.set(docId);
        docs.put(docId, doc);
        final Collection collection = doc.getCollection();
        if (collection != null && !collectionIds.get(collection.getId())) {
            collectionIds.set(collection.getId());
            collections.add(collection);
        }
    }

    public void add(final Node node) {
        if (!(node instanceof DocumentImpl)) {
            throw new IllegalArgumentException("wrong implementation");
        }
        add((DocumentImpl) node);
    }

    @Override
    public void addAll(final DocumentSet other) {
        for (final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            add(i.next());
        }
    }

    @Override
    public void addCollection(final Collection collection) {
        if (!collectionIds.get(collection.getId())) {
            collectionIds.set(collection.getId());
            collections.add(collection);
        }
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return docs.values().iterator();
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return collections.iterator();
    }

    @Override
    public int getDocumentCount() {
        return docs.size();
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        return docs.get(docId);
    }

    @Override
    public XmldbURI[] getNames() {
        final XmldbURI[] result = docs.values().stream()
                .map(DocumentImpl::getFileURI)
                .toArray(XmldbURI[]::new);
        Arrays.sort(result);
        return result;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        final DefaultDocumentSet result = new DefaultDocumentSet();

        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (other.contains(d.getDocId())) {
                result.add(d);
            }
        }
        for (final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (contains(d.getDocId()) && (!result.contains(d.getDocId()))) {
                result.add(d);
            }
        }
        return result;
    }

    public DocumentSet union(final DocumentSet other) {
        final DefaultDocumentSet result = new DefaultDocumentSet();
        result.addAll(other);
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (!result.contains(d.getDocId())) {
                result.add(d);
            }
        }
        return result;
    }

    @Override
    public boolean contains(final DocumentSet other) {
        if (other.getDocumentCount() > getDocumentCount()) {
            return false;
        }

        if(other instanceof DefaultDocumentSet) {
            // optimization for fast comparison when other is also a DefaultDocumentSet
            final DefaultDocumentSet otherDDS = (DefaultDocumentSet)other;
            final BitSet compare = new BitSet();
            compare.or(docIds);
            compare.and(otherDDS.docIds);
            return compare.equals(otherDDS.docIds);
        } else {
            // otherwise, fallback to general comparison
            final Iterator<DocumentImpl> otherDocumentIterator = other.getDocumentIterator();
            while (otherDocumentIterator.hasNext()) {
                final DocumentImpl otherDocument = otherDocumentIterator.next();
                if (!contains(otherDocument.getDocId())) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public boolean contains(final int id) {
        return docIds.get(id);
    }

    @Override
    public NodeSet docsToNodeSet() {
        final NodeSet result = new NewArrayNodeSet();
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            if (doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
                result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    public int getMinDocId() {
        return docIds.nextSetBit(0);
    }

    public int getMaxDocId() {
        int max = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (d.getDocId() > max) {
                max = d.getDocId();
            }
        }
        return max;
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        if (this == other) {
            // we are comparing the same objects
            return true;
        }

        if (getDocumentCount() != other.getDocumentCount()) {
            return false;
        }

        if(other instanceof DefaultDocumentSet) {
            // optimization for fast comparison when other is also a DefaultDocumentSet
            final DefaultDocumentSet otherDDS = (DefaultDocumentSet)other;
            return docIds.equals(otherDDS.docIds);
        } else {
            // otherwise, fallback to general comparison
            final Iterator<DocumentImpl> otherDocumentIterator = other.getDocumentIterator();
            while (otherDocumentIterator.hasNext()) {
                final DocumentImpl otherDocument = otherDocumentIterator.next();
                if (!contains(otherDocument.getDocId())) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public ManagedLocks<ManagedDocumentLock> lock(final DBBroker broker, final boolean exclusive) throws LockException {
        final LockManager lockManager = broker.getBrokerPool().getLockManager();
        final List<ManagedDocumentLock> managedDocumentLocks = new ArrayList<>();
        final Iterator<DocumentImpl> documentIterator = getDocumentIterator();
        try {
            while (documentIterator.hasNext()) {
                final DocumentImpl document = documentIterator.next();
                final ManagedDocumentLock managedDocumentLock;
                if (exclusive) {
                    managedDocumentLock = lockManager.acquireDocumentWriteLock(document.getURI());
                } else {
                    managedDocumentLock = lockManager.acquireDocumentReadLock(document.getURI());
                }
                managedDocumentLocks.add(managedDocumentLock);
            }
            return new ManagedLocks<>(managedDocumentLocks);
        } catch (final LockException e) {
            // unlock any previously locked documents
            if(!managedDocumentLocks.isEmpty()) {
                new ManagedLocks<>(managedDocumentLocks).close();
            }
            throw e;
        }
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            result.append(i.next());
            if(i.hasNext()) {
                result.append(", ");
            }
        }
        return result.toString();
    }
}
