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

import org.exist.collections.Collection;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Manages a set of documents.
 *
 * This class implements the NodeList interface for a collection of documents.
 * It also contains methods to retrieve the collections these documents
 * belong to.
 *
 * @author wolf
 */
public class DefaultDocumentSet extends Int2ObjectHashMap implements MutableDocumentSet {

    private BitSet docIds = new BitSet();
    private BitSet collectionIds = new BitSet();
    private Set<Collection> collections = new TreeSet<>();

    public DefaultDocumentSet() {
        super(29, 1.75);
    }

    public DefaultDocumentSet(final int initialSize) {
        super(initialSize, 1.75);
    }

    @Override
    public void clear() {
        super.clear();
        this.docIds = new BitSet();
        this.collectionIds = new BitSet();
        this.collections = new TreeSet<>();
    }

    @Override
    public void add(final DocumentImpl doc) {
        add(doc, true);
    }

    @Override
    public void add(final DocumentImpl doc, final boolean checkDuplicates) {
        final int docId = doc.getDocId();
        if(checkDuplicates && contains(docId)) {
            return;
        }

        docIds.set(docId);
        put(docId, doc);
        final Collection collection = doc.getCollection();
        if(collection != null && !collectionIds.get(collection.getId())) {
            collectionIds.set(collection.getId());
            collections.add(collection);
        }
    }

    public void add(final Node node) {
        if(!(node instanceof DocumentImpl)) {
            throw new RuntimeException("wrong implementation");
        }
        add((DocumentImpl) node);
    }

    @Override
    public void addAll(final DocumentSet other) {
        for(final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            add(i.next());
        }
    }

    @Override
    public void addCollection(final Collection collection) {
        if(!collectionIds.get(collection.getId())) {
            collectionIds.set(collection.getId());
            collections.add(collection);
        }
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return valueIterator();
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return collections.iterator();
    }

    @Override
    public int getDocumentCount() {
        return size();
    }

    public int getCollectionCount() {
        return collections.size();
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        return (DocumentImpl) get(docId);
    }

    @Override
    public XmldbURI[] getNames() {
        final XmldbURI result[] = new XmldbURI[size()];
        DocumentImpl d;
        int j = 0;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); j++) {
            d = i.next();
            result[j] = d.getFileURI();
        }
        Arrays.sort(result);
        return result;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        final DefaultDocumentSet r = new DefaultDocumentSet();
        DocumentImpl d;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            d = i.next();
            if(other.contains(d.getDocId())) {
                r.add(d);
            }
        }
        for(final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            d = i.next();
            if(contains(d.getDocId()) && (!r.contains(d.getDocId()))) {
                r.add(d);
            }
        }
        return r;
    }

    public DocumentSet union(final DocumentSet other) {
        final DefaultDocumentSet result = new DefaultDocumentSet();
        result.addAll(other);
        DocumentImpl d;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            d = i.next();
            if(!result.contains(d.getDocId())) {
                result.add(d);
            }
        }
        return result;
    }

    @Override
    public boolean contains(final DocumentSet other) {
        if(other.getDocumentCount() > size()) {
            return false;
        }
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED) {
                continue;
            }
            final DocumentImpl d = (DocumentImpl) values[idx];
            if(!contains(d.getDocId())) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean contains(final int id) {
        return docIds.get(id);
    }

    @Override
    public NodeSet docsToNodeSet() {
        final NodeSet result = new NewArrayNodeSet();
        DocumentImpl doc;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            doc = i.next();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
                result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    public int getMinDocId() {
        int min = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        DocumentImpl d;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            d = i.next();
            if(min == DocumentImpl.UNKNOWN_DOCUMENT_ID) {
                min = d.getDocId();
            } else if(d.getDocId() < min) {
                min = d.getDocId();
            }
        }
        return min;
    }

    public int getMaxDocId() {
        int max = DocumentImpl.UNKNOWN_DOCUMENT_ID;
        DocumentImpl d;
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            d = i.next();
            if(d.getDocId() > max) {
                max = d.getDocId();
            }
        }
        return max;
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        if(this == other)
        // we are comparing the same objects
        {
            return true;
        }
        if(size() != other.getDocumentCount()) {
            return false;
        }
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED) {
                continue;
            }
            if(!other.contains(keys[idx])) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void lock(final DBBroker broker, final boolean exclusive, final boolean checkExisting) throws LockException {
        DocumentImpl d;
        Lock dlock;
        //final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED) {
                continue;
            }
            d = (DocumentImpl) values[idx];
            dlock = d.getUpdateLock();
            //if (checkExisting && dlock.hasLock(thread))
            //continue;
            if(exclusive) {
                dlock.acquire(Lock.WRITE_LOCK);
            } else {
                dlock.acquire(Lock.READ_LOCK);
            }
        }
    }

    @Override
    public void unlock(final boolean exclusive) {
        DocumentImpl d;
        Lock dlock;
        final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED) {
                continue;
            }
            d = (DocumentImpl) values[idx];
            dlock = d.getUpdateLock();
            if(exclusive) {
                dlock.release(Lock.WRITE_LOCK);
            } else if(dlock.isLockedForRead(thread)) {
                dlock.release(Lock.READ_LOCK);
            }
        }
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        for(final Iterator<DocumentImpl> i = getDocumentIterator(); i.hasNext(); ) {
            result.append(i.next());
            result.append(", ");
        }
        return result.toString();
    }
}
