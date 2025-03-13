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
package org.exist.storage.lock;

import net.jcip.annotations.NotThreadSafe;
import org.exist.collections.Collection;
import org.exist.dom.persistent.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This map is used by the XQuery engine to track how many read locks were
 * acquired for a document during query execution.
 */
@NotThreadSafe
public class LockedDocumentMap {

    private final static int DEFAULT_SIZE = 29;
    private final static float DEFAULT_GROWTH = 1.75f;

    private final Map<Integer, List<LockedDocument>> map = new LinkedHashMap<>(DEFAULT_SIZE, DEFAULT_GROWTH);

    public void add(final LockedDocument lockedDocument) {
        map.compute(lockedDocument.getDocument().getDocId(), (k, v) -> {
            if(v == null) {
                v = new ArrayList<>();
            }

            v.add(lockedDocument);

            return v;
        });
    }

    public MutableDocumentSet toDocumentSet() {
        final MutableDocumentSet docs = new DefaultDocumentSet(map.size());
        for(final List<LockedDocument> documentLocks : map.values()) {
            docs.add(documentLocks.getFirst().getDocument());
        }
        return docs;
    }

    public DocumentSet getDocsByCollection(final Collection collection, MutableDocumentSet targetSet) {
        if (targetSet == null) {
            targetSet = new DefaultDocumentSet(map.size());
        }

        for(final List<LockedDocument> documentLocks : map.values()) {
            final DocumentImpl doc = documentLocks.getFirst().getDocument();
            if(doc.getCollection().getURI().startsWith(collection.getURI())) {
                targetSet.add(doc);
            }
        }
        return targetSet;
    }

    public void unlock() {
        // NOTE: locks should be released in the reverse order that they were acquired
        final List<List<LockedDocument>> documentsLockedDocuments = new ArrayList<>(map.values());
        for(int i = documentsLockedDocuments.size() - 1; i >= 0; i--) {
            final List<LockedDocument> documentLocks = documentsLockedDocuments.get(i);

            for(int j = documentLocks.size() - 1; j >= 0; j--) {
                final LockedDocument documentLock = documentLocks.get(j);
                documentLock.close();
            }
        }
    }

    public LockedDocumentMap unlockSome(final DocumentSet keep) {
        final int[] docIdsToRemove = new int[map.size() - keep.getDocumentCount()];

        // NOTE: locks should be released in the reverse order that they were acquired
        final List<List<LockedDocument>> documentsLockedDocuments = new ArrayList<>(map.values());
        final int len = documentsLockedDocuments.size();
        for(int i = len - 1; i >= 0; i--) {
            final List<LockedDocument> documentLocks = documentsLockedDocuments.get(i);

            final int docId = documentLocks.getFirst().getDocument().getDocId();
            if(!keep.contains(docId)) {
                for (int j = documentLocks.size() - 1; j >= 0; j--) {
                    final LockedDocument documentLock = documentLocks.get(j);
                    documentLock.close();
                }

                docIdsToRemove[len - 1 - i] = docId;
            }
        }

        // cleanup
        for(final int docIdToRemove : docIdsToRemove) {
            map.remove(docIdToRemove);
        }

        return this;
    }

    public boolean containsKey(final int docId) {
        return map.containsKey(docId);
    }

    public int size() {
        return map.size();
    }
}
