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

import org.exist.storage.BrokerPool;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * New symbols are on disk before a page that may refer to them is written, also to a file that isn't journaled, such
 * as the structural index or the value index (#2220): the page is written first and the journal flushed after it, if
 * at all, so the journal's sync alone would not order them.
 */
class SymbolTablePageSyncTest {

    @RegisterExtension
    static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    void writingAPageOfAFileWithoutJournalSyncsNewNames(@TempDir final Path dir) throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final SymbolTable symbols = pool.getSymbols();
        final BFile file = new BFile(pool, (byte) 100, (short) 1, false, dir.resolve("page-sync-test.dbx"),
                pool.getCacheManager(), 1.25, 0.04);
        try {
            file.create();
            symbols.getSymbol("a-name-of-the-page-sync-test");
            assertTrue(symbols.hasUnsyncedEntries(), "a new name is written, not yet forced to disk");
            file.put(new Value("key".getBytes(StandardCharsets.UTF_8)), "value".getBytes(StandardCharsets.UTF_8), true);
            file.flush();
            assertFalse(symbols.hasUnsyncedEntries(), "writing the file's pages forced the new name to disk first");
        } finally {
            file.close();
        }
    }
}
