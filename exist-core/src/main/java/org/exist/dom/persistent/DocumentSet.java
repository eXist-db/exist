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
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.util.Iterator;

public interface DocumentSet {

    DocumentSet EMPTY_DOCUMENT_SET = new EmptyDocumentSet();

    Iterator<DocumentImpl> getDocumentIterator();

    Iterator<Collection> getCollectionIterator();

    int getDocumentCount();

    DocumentImpl getDoc(int docId);

    XmldbURI[] getNames();

    DocumentSet intersection(DocumentSet other);

    boolean contains(DocumentSet other);

    boolean contains(int id);

    NodeSet docsToNodeSet();

    /**
     * Locks all of the documents currently in the document set.
     *
     * @param broker the eXist-db DBBroker
     * @param exclusive true if a WRITE_LOCK is required, false if a READ_LOCK is required
     * @return The locks
     * @throws LockException if locking any document fails, when thrown no locks will be held on any documents in the set
     */
    ManagedLocks<ManagedDocumentLock> lock(DBBroker broker, boolean exclusive) throws LockException;

    boolean equalDocs(DocumentSet other);
}
