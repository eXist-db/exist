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
package org.exist.storage;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Remembers when the nodes of a document were given new ids, so that an operation that selected nodes of the document
 * before it locked it can tell that the ids it holds are stale.
 *
 * <p>Defragmenting a document rewrites it with dense ids, which gives most nodes of a document that has had nodes
 * inserted other ids. An operation that selects nodes, locks their documents and then changes the nodes, such as an
 * XQuery Update expression, does not hold the lock while it selects. A defragmentation in between leaves it with ids that
 * name other nodes, or none. The operation reads {@link #current()} before it selects, locks the documents, and asks
 * {@link #changedSince(int, long)} for each of them: while it holds the lock, the document cannot be defragmented any
 * more, so an answer of false is final.</p>
 *
 * <p>The state is not persisted: it only matters to operations that are running now. An entry stays until the document is
 * removed and its id is given back, so there is one per document that was defragmented and still exists.</p>
 */
public final class DocumentRestructures {

    private final AtomicLong counter = new AtomicLong();
    private final ConcurrentHashMap<Integer, Long> lastRestructured = new ConcurrentHashMap<>();

    /**
     * Returns the value to remember before selecting nodes.
     *
     * @return the number of restructurings so far
     */
    public long current() {
        return counter.get();
    }

    /**
     * Records that the nodes of a document were given new ids. Called while the caller still holds the write lock of the
     * document, so that an operation that waits for the lock sees it.
     *
     * @param docId the id of the document
     */
    public void restructured(final int docId) {
        lastRestructured.put(docId, counter.incrementAndGet());
    }

    /**
     * Forgets a document, when its id is given back to be used by another document.
     *
     * @param docId the id of the document
     */
    public void forget(final int docId) {
        lastRestructured.remove(docId);
    }

    /**
     * Tells whether the nodes of a document were given new ids after a value of {@link #current()} was read.
     *
     * @param docId the id of the document
     * @param stamp the value of {@link #current()} read before the nodes were selected
     * @return true if the ids of the nodes may have changed since
     */
    public boolean changedSince(final int docId, final long stamp) {
        final Long at = lastRestructured.get(docId);
        return at != null && at > stamp;
    }
}
