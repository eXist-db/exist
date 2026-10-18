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
package org.exist.xquery.update;

import org.exist.EXistException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.exist.util.PropertiesBuilder.propertiesBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Two threads modify nodes of the same document with update expressions while every update defragments it,
 * which changes the nodes' positions. Each update expression must modify the nodes that it selected in the
 * document as it is when it modifies them, so no update fails, and the document stays consistent.
 */
public class ConcurrentUpdateStressTest {

    private static final String DOCUMENT = "/db/concurrent-update-stress/list.xml";
    private static final int ITERATIONS = 100;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(propertiesBuilder().put(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR, -1).build(), true, true);

    @BeforeEach
    public void setUp() throws EXistException, PermissionDeniedException, XPathException {
        execute("""
                xmldb:store(xmldb:create-collection("/db", "concurrent-update-stress"), "list.xml",
                    <list>{for $i in 1 to 20 return <item n="{$i}">{$i}</item>}</list>)
                """);
    }

    @Test
    void replaceInsertAndDeleteConcurrently() throws Exception {
        final String list = "doc('" + DOCUMENT + "')/list";
        run(
                List.of(
                        "update replace " + list + "/item[1] with <item n='a'>a</item>",
                        "update insert <item n='x'>x</item> into " + list),
                List.of(
                        "update replace " + list + "/item[last()] with <item n='b'>b</item>",
                        "update delete " + list + "/item[@n = 'x'][1]",
                        "update value " + list + "/item[2] with 'c'"));

        assertEquals("a", execute("string(" + list + "/item[1]/@n)").getStringValue());
        assertEquals(0, execute("count(" + list + "/item[not(@n)])").itemAt(0).toJavaObject(Integer.class));

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDocument = broker.getXMLResource(XmldbURI.create(DOCUMENT), LockMode.READ_LOCK)) {
            assertNotNull(lockedDocument);
            broker.checkXMLResourceConsistency(lockedDocument.getDocument());
        }
    }

    /**
     * Runs each list of queries in its own thread, {@link #ITERATIONS} times, at the same time.
     */
    @SafeVarargs
    private static void run(final List<String>... queries) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(queries.length);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final Future<?>[] threads = new Future<?>[queries.length];
            for (int i = 0; i < queries.length; i++) {
                final List<String> threadQueries = queries[i];
                threads[i] = executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < ITERATIONS; j++) {
                        for (final String query : threadQueries) {
                            execute(query);
                        }
                    }
                    return null;
                });
            }
            start.countDown();
            for (final Future<?> thread : threads) {
                thread.get(5, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static Sequence execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
