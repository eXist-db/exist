/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.storage.lock;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.jcip.annotations.NotThreadSafe;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.storage.lock.Lock.LockMode;

import javax.annotation.Nullable;

/**
 * This map is used by the XQuery engine to track how many read locks were
 * acquired for a document during query execution.
 */
@NotThreadSafe
public class LockedDocumentMap {

    private final static int DEFAULT_SIZE = 29;

    private final Int2ObjectMap<LockedDocument> map = new Int2ObjectOpenHashMap<>(DEFAULT_SIZE);

    public void add(final DocumentImpl document) {
        LockedDocument entry = map.get(document.getDocId());
        if (entry == null) {
            entry = new LockedDocument(document);
            map.put(document.getDocId(), entry);
        }
        entry.locksAcquired++;
    }

    public MutableDocumentSet toDocumentSet() {
        final MutableDocumentSet docs = new DefaultDocumentSet(map.size());
        final ObjectIterator<LockedDocument> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            final LockedDocument lockedDocument = iterator.next();
            docs.add(lockedDocument.document);
        }
        return docs;
    }

    public DocumentSet getDocsByCollection(final Collection collection, @Nullable MutableDocumentSet targetSet) {
        if (targetSet == null) {
            targetSet = new DefaultDocumentSet(map.size());
        }
        final ObjectIterator<LockedDocument> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            final LockedDocument lockedDocument = iterator.next();
            if (lockedDocument.document.getCollection().getURI().startsWith(collection.getURI())) {
                targetSet.add(lockedDocument.document);
            }
        }
        return targetSet;
    }

    public void unlock() {
        final ObjectIterator<LockedDocument> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            final LockedDocument lockedDocument = iterator.next();
            unlockDocument(lockedDocument);
        }
    }

    public LockedDocumentMap unlockSome(final DocumentSet keep) {
        final IntList remove = new IntArrayList();
        final ObjectIterator<Int2ObjectMap.Entry<LockedDocument>> iterator = map.int2ObjectEntrySet().iterator();
        while (iterator.hasNext()) {
            final Int2ObjectMap.Entry<LockedDocument> entry = iterator.next();
            final LockedDocument lockedDocument = entry.getValue();
            if (!keep.contains(lockedDocument.document.getDocId())) {
                remove.add(entry.getIntKey());
                unlockDocument(lockedDocument);
            }
        }

        for (int key : remove) {
            map.remove(key);
        }

        return this;
    }

    private void unlockDocument(final LockedDocument lockedDocument) {
        final Lock documentLock = lockedDocument.document.getUpdateLock();
        documentLock.release(LockMode.WRITE_LOCK, lockedDocument.locksAcquired);
    }

    public int size() {
        return map.size();
    }

    public boolean containsKey(final int docId) {
        return map.containsKey(docId);
    }

    private static class LockedDocument {
        private final DocumentImpl document;
        private int locksAcquired = 0;

        LockedDocument(final DocumentImpl document) {
            this.document = document;
        }
    }
}
