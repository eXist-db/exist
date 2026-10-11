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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.UpdateListener;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A {@code LocationStep} caches the element set it looked up in the structural index, and an update
 * listener clears that cache when a document is added, removed or updated. The listener runs on the thread
 * that made the update, so it can clear the cache between the evaluating thread filling it and using it.
 * The step then dereferenced {@code null} ("Cannot invoke NodeSet.selectPrecedingSiblings ... because
 * this.currentSet is null"), seen when XQSuite files ran in parallel and one stored or removed a document
 * while another evaluated a sibling axis.
 *
 * <p>The window is a few instructions wide, so the test keeps one thread adding and removing documents and
 * another sending update notifications back to back while several threads evaluate sibling and ancestor
 * axes, for a bounded time.</p>
 */
public class LocationStepUpdateListenerTest {

    @RegisterExtension
    public static final ExistXmldbEmbeddedServer existEmbeddedServer =
            new ExistXmldbEmbeddedServer(false, true, true);

    private static final int READERS = 4;
    private static final long RUN_MILLIS = 5_000;

    private static final String[] QUERIES = {
            "count(/data/a/following-sibling::b)",
            "count(/data/b/preceding-sibling::a)",
            "count(/data/b/ancestor::data)",
            "count(/data/a/following::b)",
            "count(/data//b/parent::data)"
    };

    @BeforeAll
    public static void storeFixture() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("xmldb:store('/db', 'steps.xml', <data><a/><b/><a/><b/></data>)");
    }

    @AfterAll
    public static void removeFixture() throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        xqs.query("xmldb:remove('/db', 'steps.xml')");
    }

    @Test
    public void cachedElementSetSurvivesConcurrentDocumentUpdates() throws Exception {
        final AtomicBoolean stop = new AtomicBoolean(false);
        final AtomicReference<Throwable> failure = new AtomicReference<>();

        final List<Thread> threads = new ArrayList<>();
        threads.add(thread("writer", stop, failure, () -> storeAndRemoveDocuments(stop)));
        threads.add(thread("notifier", stop, failure, () -> sendUpdateNotifications(stop)));
        for (int r = 0; r < READERS; r++) {
            final int seed = r;
            threads.add(thread("reader-" + r, stop, failure, () -> evaluateAxes(stop, seed)));
        }

        threads.forEach(Thread::start);
        final long end = System.currentTimeMillis() + RUN_MILLIS;
        while (!stop.get() && System.currentTimeMillis() < end) {
            Thread.sleep(100);
        }
        stop.set(true);
        for (final Thread t : threads) {
            t.join();
        }

        assertNull(failure.get(), () -> "a concurrent update broke a running query: " + failure.get());
    }

    /**
     * A thread that runs the action and, if it throws, records the first failure and stops the others.
     */
    private static Thread thread(final String name, final AtomicBoolean stop, final AtomicReference<Throwable> failure,
            final ThrowingRunnable action) {
        return new Thread(() -> {
            try {
                action.run();
            } catch (final Throwable t) {
                failure.compareAndSet(null, t);
                stop.set(true);
            }
        }, name);
    }

    /** Adds and removes documents: every removal notifies all registered update listeners. */
    private static void storeAndRemoveDocuments(final AtomicBoolean stop) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        int i = 0;
        while (!stop.get()) {
            final String name = "tmp-" + (i++ % 8) + ".xml";
            xqs.query("xmldb:store('/db', '" + name + "', <other><a/><b/></other>)");
            xqs.query("xmldb:remove('/db', '" + name + "')");
        }
    }

    /** What a store, a remove or an update of any document sends to every listener, back to back. */
    private static void sendUpdateNotifications(final AtomicBoolean stop) throws EXistException {
        final BrokerPool pool = BrokerPool.getInstance();
        while (!stop.get()) {
            pool.getNotificationService().notifyUpdate(null, UpdateListener.ADD);
        }
    }

    private static void evaluateAxes(final AtomicBoolean stop, final int seed) throws XMLDBException {
        final XQueryService xqs = existEmbeddedServer.getRoot().getService(XQueryService.class);
        int i = seed;
        while (!stop.get()) {
            final ResourceSet rs = xqs.query(QUERIES[i++ % QUERIES.length]);
            assertEquals(1, rs.getSize());
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
