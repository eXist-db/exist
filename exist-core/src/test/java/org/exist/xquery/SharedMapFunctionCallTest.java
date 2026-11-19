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
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.source.StringSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A map or an array created by one query, and called as a function by several other queries at the same time, as
 * happens when it is shared through the cache module.
 */
public class SharedMapFunctionCallTest {

    private static final int THREADS = 8;
    private static final int ITERATIONS = 50;

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    void mapCalledAsAFunctionConcurrently() throws Exception {
        final Sequence map = execute("map:merge(for $i in 1 to 200 return map { $i: $i })", null);
        callConcurrently(map, "every $k in map:keys($shared) satisfies $shared($k) eq $k");
    }

    @Test
    void arrayCalledAsAFunctionConcurrently() throws Exception {
        final Sequence array = execute("array { 1 to 200 }", null);
        callConcurrently(array, "every $i in 1 to array:size($shared) satisfies $shared($i) eq $i");
    }

    private static void callConcurrently(final Sequence shared, final String query) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Void>> threads = new ArrayList<>();
            for (int i = 0; i < THREADS; i++) {
                threads.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < ITERATIONS; j++) {
                        assertTrue(execute("declare variable $shared external; " + query, shared).effectiveBooleanValue());
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

    private static Sequence execute(final String query, final Sequence shared) throws EXistException, PermissionDeniedException, XPathException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQueryContext context = new XQueryContext(pool);
            if (shared != null) {
                context.declareVariable("shared", shared);
            }
            final CompiledXQuery compiled = pool.getXQueryService().compile(context, new StringSource(query));
            return pool.getXQueryService().execute(broker, compiled, null);
        }
    }
}
