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
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Concurrent read-modify-write with {@code update value}: each thread increments a counter that it reads in the
 * same update expression. An update expression evaluates its selection and its new value before it locks the
 * documents, so without validation two threads read the same value, both write value + 1, and an increment is lost.
 */
public class ConcurrentUpdateValueTest {

    private static final String DOCUMENT = "/db/concurrent-update/counter.xml";
    private static final int THREADS = 8;
    private static final int ITERATIONS = 50;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    public void setUp() throws EXistException, PermissionDeniedException, XPathException {
        execute("""
                xmldb:store(xmldb:create-collection("/db", "concurrent-update"), "counter.xml",
                    <root n="0"><count>0</count></root>)
                """);
    }

    @Test
    void noLostUpdatesToAnAttribute() throws Exception {
        incrementConcurrently("update value doc('" + DOCUMENT + "')/root/@n with xs:integer(doc('" + DOCUMENT + "')/root/@n) + 1");
        assertEquals(THREADS * ITERATIONS, counter("xs:integer(doc('" + DOCUMENT + "')/root/@n)"));
    }

    @Test
    void noLostUpdatesToATextNode() throws Exception {
        incrementConcurrently("update value doc('" + DOCUMENT + "')/root/count/text() with xs:integer(doc('" + DOCUMENT + "')/root/count) + 1");
        assertEquals(THREADS * ITERATIONS, counter("xs:integer(doc('" + DOCUMENT + "')/root/count)"));
    }

    private static void incrementConcurrently(final String query) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Void>> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                threads.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < ITERATIONS; j++) {
                        execute(query);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<Void> thread : threads) {
                thread.get(5, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int counter(final String query) throws EXistException, PermissionDeniedException, XPathException {
        return execute(query).itemAt(0).toJavaObject(Integer.class);
    }

    private static Sequence execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            return xquery.execute(broker, query, null);
        }
    }
}
