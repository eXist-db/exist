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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.jcip.annotations.NotThreadSafe;
import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.Lock.LockMode;
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

    private final static int DEFAULT_SIZE = 29;

    private final BitSet docIds = new BitSet();
    private final BitSet collectionIds = new BitSet();
    private final Set<Collection> collections = new TreeSet<>();

    private final Deque<Runnable> lockReleasers = new ArrayDeque<>();

    private final Int2ObjectMap<DocumentImpl> map;

    public DefaultDocumentSet() {
        this.map = new Int2ObjectOpenHashMap<>(DEFAULT_SIZE);
    }

    public DefaultDocumentSet(final int initialSize) {
        this.map =  new Int2ObjectOpenHashMap<>(initialSize);
    }

    @Override
    public void clear() {
        map.clear();
        this.docIds.clear();
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
        map.put(docId, doc);
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

    @SuppressWarnings("unchecked")
    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return map.values().iterator();
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return collections.iterator();
    }

    @Override
    public int getDocumentCount() {
        return map.size();
    }

    public int getCollectionCount() {
        return collections.size();
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        return map.get(docId);
    }

    @Override
    public XmldbURI[] getNames() {
        final XmldbURI result[] = new XmldbURI[map.size()];
        int j = 0;
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); j++) {
            final DocumentImpl d = i.next();
            result[j] = d.getFileURI();
        }
        Arrays.sort(result);
        return result;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        final DefaultDocumentSet r = new DefaultDocumentSet();
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (other.contains(d.getDocId())) {
                r.add(d);
            }
        }
        for (final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (contains(d.getDocId()) && (!r.contains(d.getDocId()))) {
                r.add(d);
            }
        }
        return r;
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
        if (other.getDocumentCount() > map.size()) {
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
        int min = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        for (final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl d = i.next();
            if (min == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
                min = d.getDocId();
            } else if (d.getDocId() < min) {
                min = d.getDocId();
            }
        }
        return min;
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
    public void lock(final DBBroker broker, final boolean exclusive) throws LockException {
        final ObjectIterator<DocumentImpl> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            final DocumentImpl d = iterator.next();
            final Lock dlock = d.getUpdateLock();
            dlock.acquire(exclusive ? LockMode.WRITE_LOCK : LockMode.READ_LOCK);
            lockReleasers.push(() -> dlock.release(exclusive ? LockMode.WRITE_LOCK : LockMode.READ_LOCK));
        }
    }

    @Override
    public void unlock() {
        // NOTE: locks are released in the reverse order that they were acquired
        while(!lockReleasers.isEmpty()) {
            lockReleasers.pop().run();
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
