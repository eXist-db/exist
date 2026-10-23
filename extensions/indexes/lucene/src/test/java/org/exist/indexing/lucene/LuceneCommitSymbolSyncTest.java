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

import org.apache.lucene.index.IndexWriter;
import org.exist.dom.persistent.SymbolTable;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * New symbols are on disk before a commit makes Lucene segments durable: an index's field names hold symbol ids
 * ({@link LuceneUtil#encodeQName}), and the Lucene index is neither journaled nor recovered (#2220).
 */
class LuceneCommitSymbolSyncTest {

    @RegisterExtension
    static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    void commitSyncsNewNames() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final SymbolTable symbols = pool.getSymbols();
        final LuceneIndex index = luceneIndex(pool);
        final IndexWriter writer = index.getWriter();
        try {
            symbols.getSymbol("a-name-of-the-lucene-commit-test");
            assertTrue(symbols.hasUnsyncedEntries(), "a new name is written, not yet forced to disk");
        } finally {
            index.releaseWriter(writer);
        }
        index.commit();
        assertFalse(symbols.hasUnsyncedEntries(), "the commit forced the new name to disk first");
    }

    /** The full-text index, by the id the test configuration gives it. */
    private static LuceneIndex luceneIndex(final BrokerPool pool) {
        return (LuceneIndex) pool.getIndexManager().getIndexByName("lucene-index");
    }
}
