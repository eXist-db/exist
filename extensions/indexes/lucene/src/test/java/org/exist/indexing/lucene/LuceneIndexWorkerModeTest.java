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
package org.exist.indexing.lucene;

import org.exist.indexing.StreamListener.ReindexMode;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class LuceneIndexWorkerModeTest {

    @Test
    public void setModeStoreInitializesAndClearsBuffer() throws Exception {
        final LuceneIndexWorker worker = new LuceneIndexWorker(null, null);

        final List<Object> existing = new ArrayList<>();
        existing.add(new Object());
        setField(worker, "nodesToWrite", existing);
        setField(worker, "cachedNodesSize", 123);

        worker.setMode(ReindexMode.STORE);

        @SuppressWarnings("unchecked")
        final List<Object> nodesToWrite = (List<Object>) getField(worker, "nodesToWrite");
        final int cachedNodesSize = (Integer) getField(worker, "cachedNodesSize");
        assertSame("STORE should reuse and clear existing nodesToWrite list", existing, nodesToWrite);
        assertTrue("STORE should clear nodesToWrite list", nodesToWrite.isEmpty());
        assertEquals("STORE should reset cached nodes size", 0, cachedNodesSize);
    }

    @Test
    public void setModeRemoveSomeNodesInitializesRemovalSet() throws Exception {
        final LuceneIndexWorker worker = new LuceneIndexWorker(null, null);

        worker.setMode(ReindexMode.REMOVE_SOME_NODES);

        @SuppressWarnings("unchecked")
        final Set<Object> nodesToRemove = (Set<Object>) getField(worker, "nodesToRemove");
        assertNotNull("REMOVE_SOME_NODES should initialize removal set", nodesToRemove);
        assertTrue("newly initialized removal set should be empty", nodesToRemove.isEmpty());
    }

    @Test
    public void setModeNoopModesDoNotMutatePreparedBuffers() throws Exception {
        final LuceneIndexWorker worker = new LuceneIndexWorker(null, null);

        final List<Object> nodesToWrite = new ArrayList<>();
        final Set<Object> nodesToRemove = new java.util.TreeSet<>();
        setField(worker, "nodesToWrite", nodesToWrite);
        setField(worker, "nodesToRemove", nodesToRemove);
        setField(worker, "cachedNodesSize", 77);

        worker.setMode(ReindexMode.UNKNOWN);
        worker.setMode(ReindexMode.REPLACE_DOCUMENT);
        worker.setMode(ReindexMode.REMOVE_ALL_NODES);
        worker.setMode(ReindexMode.REMOVE_BINARY);

        assertSame(nodesToWrite, getField(worker, "nodesToWrite"));
        assertSame(nodesToRemove, getField(worker, "nodesToRemove"));
        assertEquals(77, getField(worker, "cachedNodesSize"));
    }

    @Test
    public void flushNoopModesDoNotThrowWithoutDocument() {
        final LuceneIndexWorker worker = new LuceneIndexWorker(null, null);
        worker.setMode(ReindexMode.UNKNOWN);
        worker.flush();
        worker.setMode(ReindexMode.REPLACE_DOCUMENT);
        worker.flush();
    }

    private static Object getField(final Object target, final String fieldName) throws Exception {
        final Field field = LuceneIndexWorker.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(final Object target, final String fieldName, final Object value) throws Exception {
        final Field field = LuceneIndexWorker.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
