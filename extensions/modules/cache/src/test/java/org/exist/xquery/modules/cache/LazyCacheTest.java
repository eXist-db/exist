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
package org.exist.xquery.modules.cache;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.*;

public class LazyCacheTest {

    private static Path getLazyConfig() {
        try {
            return Paths.get(LazyCacheTest.class.getResource("/lazy-cache-conf.xml").toURI());
        } catch (final URISyntaxException e) {
            throw new IllegalStateException("Unable to find: lazy-cache-conf.xml");
        }
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(null, getLazyConfig(), null, true, true);

    @Test
    public void putOnLazilyCreatedCache() throws XPathException, PermissionDeniedException, EXistException {
        Sequence result = executeQuery("cache:put('foo', 'bar', 'baz1')");
        assertNotNull(result);
        assertTrue(result.isEmpty());

        result = executeQuery("cache:put('foo', 'bar', 'baz2')");
        assertNotNull(result);
        assertEquals(1, result.getItemCount());
        assertEquals("baz1", result.itemAt(0).getStringValue());
    }

    private static Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool brokerPool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = brokerPool.getBroker();
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {

            final Sequence result = brokerPool.getXQueryService().execute(broker, query, null);

            transaction.commit();

            return result;
        }
    }
}
