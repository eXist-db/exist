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
import org.exist.storage.lock.LockOrderRecorder;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks how an update expression is validated against the WRITE_LOCKs that other threads acquire on the document
 * it modifies while it is evaluated: it is evaluated again after one, it is serialized while they keep being
 * acquired, it isn't affected by those on other documents, and it never requests a collection lock while it holds a
 * document lock.
 */
public class UpdateValidationTest {

    private static final String COLLECTION = "/db/update-validation";
    private static final String DOCUMENT = COLLECTION + "/counter.xml";
    private static final XmldbURI DOCUMENT_URI = XmldbURI.create(DOCUMENT);
    private static final XmldbURI OTHER_DOCUMENT = XmldbURI.create("/db/update-validation-other/other.xml");
    private static final String INCREMENT = "update value doc('" + DOCUMENT + "')/root/@n with xs:integer(doc('" + DOCUMENT + "')/root/@n) + 1";

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeEach
    public void setUp() throws EXistException, PermissionDeniedException, XPathException {
        execute("""
                xmldb:store(xmldb:create-collection("/db", "update-validation"), "counter.xml", <root n="0"/>)
                """);
    }

    /**
     * A WRITE_LOCK on the document taken by another thread while the value is evaluated invalidates the
     * evaluation, so it is evaluated again, which takes twice as long as the wait in it.
     */
    @Test
    void evaluatedAgainAfterAWriteLockDuringTheEvaluation() throws Exception {
        final long wait = 500;
        final CountDownLatch evaluating = new CountDownLatch(1);
        final CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            try {
                evaluating.await();
                Thread.sleep(wait / 5);
                existEmbeddedServer.getBrokerPool().getLockManager().acquireDocumentWriteLock(DOCUMENT_URI).close();
            } catch (final InterruptedException | LockException e) {
                throw new IllegalStateException(e);
            }
        });

        final long start = System.nanoTime();
        evaluating.countDown();
        execute("update value doc('" + DOCUMENT + "')/root/@n with (util:wait(" + wait + "), xs:integer(doc('" + DOCUMENT + "')/root/@n) + 1)");
        final long elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        writer.get(10, TimeUnit.SECONDS);

        assertEquals(1, counter());
        assertTrue(elapsed >= 2 * wait, "evaluated once only, in " + elapsed + " ms");
    }

    /**
     * While another thread keeps taking WRITE_LOCKs on the document, the update expressions are invalidated again
     * and again, and so serialized, and none of their increments is lost.
     */
    @Test
    void serializedWhileTheDocumentKeepsBeingWritten() throws Exception {
        whileWriteLockingRepeatedly(DOCUMENT_URI, () -> {
            final int threads = 4;
            final int iterations = 25;
            incrementConcurrently(threads, iterations);
            assertEquals(threads * iterations, counter());
        });
    }

    /**
     * A WRITE_LOCK held on another document throughout doesn't invalidate the update expressions.
     */
    @Test
    void unaffectedByAWriteLockOnAnotherDocument() throws Exception {
        holdingWriteLockElsewhere(() -> {
            final int threads = 4;
            final int iterations = 25;
            incrementConcurrently(threads, iterations);
            assertEquals(threads * iterations, counter());
        });
    }

    @Test
    void noCollectionLockAfterADocumentLock() throws Exception {
        assertEquals(List.of(), LockOrderRecorder.violations(existEmbeddedServer.getBrokerPool(), COLLECTION, () -> execute(INCREMENT)));
    }

    @Test
    void noCollectionLockAfterADocumentLockWhenSerialized() throws Exception {
        whileWriteLockingRepeatedly(DOCUMENT_URI, () ->
                assertEquals(List.of(), LockOrderRecorder.violations(existEmbeddedServer.getBrokerPool(), COLLECTION, () -> execute(INCREMENT))));
    }

    /**
     * Runs the operation while another thread keeps acquiring and releasing a WRITE_LOCK on the document.
     */
    private static void whileWriteLockingRepeatedly(final XmldbURI document, final LockOrderRecorder.Operation operation) throws Exception {
        final AtomicBoolean running = new AtomicBoolean(true);
        final CompletableFuture<Void> writer = CompletableFuture.runAsync(() -> {
            try {
                while (running.get()) {
                    existEmbeddedServer.getBrokerPool().getLockManager().acquireDocumentWriteLock(document).close();
                    Thread.sleep(1);
                }
            } catch (final InterruptedException | LockException e) {
                throw new IllegalStateException(e);
            }
        });
        try {
            operation.run();
        } finally {
            running.set(false);
            writer.get(10, TimeUnit.SECONDS);
        }
    }

    /**
     * Runs the operation while another thread holds a WRITE_LOCK on a document in another collection.
     */
    private static void holdingWriteLockElsewhere(final LockOrderRecorder.Operation operation) throws Exception {
        final CountDownLatch locked = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> {
            try (final ManagedDocumentLock ignored = existEmbeddedServer.getBrokerPool().getLockManager().acquireDocumentWriteLock(OTHER_DOCUMENT)) {
                locked.countDown();
                release.await();
            } catch (final InterruptedException | LockException e) {
                throw new IllegalStateException(e);
            }
        });
        locked.await();
        try {
            operation.run();
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
        }
    }

    private static void incrementConcurrently(final int threads, final int iterations) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            final CountDownLatch start = new CountDownLatch(1);
            final List<Future<Void>> running = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                running.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < iterations; j++) {
                        execute(INCREMENT);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (final Future<Void> thread : running) {
                thread.get(5, TimeUnit.MINUTES);
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int counter() throws EXistException, PermissionDeniedException, XPathException {
        return execute("xs:integer(doc('" + DOCUMENT + "')/root/@n)").itemAt(0).toJavaObject(Integer.class);
    }

    private static Sequence execute(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            return pool.getXQueryService().execute(broker, query, null);
        }
    }
}
