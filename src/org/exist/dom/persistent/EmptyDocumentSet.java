/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

import java.util.Collections;
import java.util.Iterator;

/**
 * An Empty DocumentSet
 *
 * @author aretter
 */
public class EmptyDocumentSet implements DocumentSet {

    /**
     * Use {@link DocumentSet#EMPTY_DOCUMENT_SET}
     */
    EmptyDocumentSet() {
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return Collections.emptyIterator();
    }

    @Override
    public int getDocumentCount() {
        return 0;
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        return null;
    }

    private final XmldbURI[] NO_NAMES = new XmldbURI[0];

    @Override
    public XmldbURI[] getNames() {
        return NO_NAMES;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    @Override
    public boolean contains(final DocumentSet other) {
        return false;
    }

    @Override
    public boolean contains(final int id) {
        return false;
    }

    @Override
    public NodeSet docsToNodeSet() {
        return NodeSet.EMPTY_SET;
    }

    @Override
    public ManagedLocks<ManagedDocumentLock> lock(final DBBroker broker, final boolean exclusive) throws LockException {
        return new ManagedLocks<>(Collections.emptyList());
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        return false;
    }
}
