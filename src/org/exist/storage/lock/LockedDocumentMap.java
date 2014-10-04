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

import org.exist.collections.Collection;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.util.hashtable.Int2ObjectHashMap;

/**
 * This map is used by the XQuery engine to track how many read locks were
 * acquired for a document during query execution.
 */
public class LockedDocumentMap extends Int2ObjectHashMap<Object> {

    public LockedDocumentMap() {
        super(29, 1.75);
    }

    public void add(DocumentImpl document) {
        LockedDocument entry = (LockedDocument) get(document.getDocId());
        if (entry == null) {
            entry = new LockedDocument(document);
            put(document.getDocId(), entry);
        }
        entry.locksAcquired++;
    }

    public MutableDocumentSet toDocumentSet() {
        final MutableDocumentSet docs = new DefaultDocumentSet(size());
        LockedDocument lockedDocument;
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                {continue;}
            lockedDocument = (LockedDocument) values[idx];
            docs.add(lockedDocument.document);
        }
        return docs;
    }

    public DocumentSet getDocsByCollection(Collection collection,
            boolean includeSubColls, MutableDocumentSet targetSet) {
        if (targetSet == null)
            {targetSet = new DefaultDocumentSet(size());}
        LockedDocument lockedDocument;
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                {continue;}
            lockedDocument = (LockedDocument) values[idx];
            if (lockedDocument.document.getCollection().getURI().startsWith(collection.getURI()))
                {targetSet.add(lockedDocument.document);}
        }
        return targetSet;
    }

    public void unlock() {
        LockedDocument lockedDocument;
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                {continue;}
            lockedDocument = (LockedDocument) values[idx];
            unlockDocument(lockedDocument); 
        }
    }

    public LockedDocumentMap unlockSome(DocumentSet keep) {
        LockedDocument lockedDocument;
        for(int idx = 0; idx < tabSize; idx++) {
            if(values[idx] == null || values[idx] == REMOVED)
                {continue;}
            lockedDocument = (LockedDocument) values[idx];
            if (!keep.contains(lockedDocument.document.getDocId())) {
                values[idx] = REMOVED;
                unlockDocument(lockedDocument);
            }
        }
        return this;
    }

    private void unlockDocument(LockedDocument lockedDocument) {
        final Lock documentLock = lockedDocument.document.getUpdateLock();
        documentLock.release(Lock.WRITE_LOCK, lockedDocument.locksAcquired);
    }

    private static class LockedDocument {
        private DocumentImpl document;
        private int locksAcquired = 0;
        public LockedDocument(DocumentImpl document) {
            this.document = document;
        }
    }
}
