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
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * New symbols are on disk once the journal has written the records that may refer to them (#2220).
 */
class SymbolTableJournalSyncTest {

    @RegisterExtension
    static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    @Test
    void storingADocumentSyncsItsNewNames() throws Exception {
        final BrokerPool pool = server.getBrokerPool();
        final SymbolTable symbols = pool.getSymbols();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final short id = symbols.getSymbol("a-name-of-this-test");
            assertTrue(symbols.hasUnsyncedEntries(), "a new name is written, not yet forced to disk");
            pool.getXQueryService().execute(broker,
                    "xmldb:store('/db', 'symbol-table-journal-sync.xml', <a-name-of-this-test/>)", null);
            assertFalse(symbols.hasUnsyncedEntries(), "the commit's journal write forced the new name to disk");
            assertTrue(id > 0);
        }
    }
}
