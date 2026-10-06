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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.test.ExistEmbeddedServer;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the service-mode state of BrokerPool.
 *
 * Specifically exercises the cross-thread visibility contract of
 * {@link BrokerPool#isInServiceMode()}: a thread that did not call
 * {@code enterServiceMode} or {@code exitServiceMode} must observe the
 * correct boolean value.  Without {@code volatile} on the backing field
 * the Java Memory Model gives no such guarantee.
 */
public class BrokerPoolServiceModeTest {

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    /**
     * Single-thread baseline: the flag transitions correctly within one thread.
     */
    @Test
    void isInServiceModeTransitionsSingleThread()
            throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().getSystemSubject();

        assertFalse(pool.isInServiceMode(), "pool must not be in service mode initially");

        // enterServiceMode returns the internal service broker; callers must not close it —
        // exitServiceMode handles pool cleanup. Follow the same pattern as GetReleaseBrokerDeadlocksTest.
        pool.enterServiceMode(admin);
        try {
            assertTrue(pool.isInServiceMode(), "pool must report in-service after enterServiceMode");
        } finally {
            pool.exitServiceMode(admin);
        }

        assertFalse(pool.isInServiceMode(), "pool must not be in service mode after exitServiceMode");
    }

    /**
     * Cross-thread visibility: an observer thread that shares no other
     * synchronization with the entering/exiting thread must still observe the
     * correct value of {@code isInServiceMode()}.
     *
     * The only happens-before edge the observer relies on is the one implied
     * by {@code volatile}: the write in enterServiceMode / exitServiceMode
     * must be visible to any subsequent read on any thread.  Without volatile
     * this test is correct by luck on x86 (TSO) but violates the JMM on
     * weakly-ordered architectures (ARM, POWER).
     */
    @Test
    void isInServiceModeVisibleAcrossThreads()
            throws EXistException, PermissionDeniedException, InterruptedException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final Subject admin = pool.getSecurityManager().getSystemSubject();

        // Latches sequence the observer so it reads at known points:
        //   serviceModeEnteredLatch — main enters service mode → observer reads
        //   observedEnteredLatch    — observer done reading → main may exit
        //   serviceModeExitedLatch  — main exits service mode → observer reads again
        final CountDownLatch serviceModeEnteredLatch = new CountDownLatch(1);
        final CountDownLatch observedEnteredLatch    = new CountDownLatch(1);
        final CountDownLatch serviceModeExitedLatch  = new CountDownLatch(1);

        final AtomicBoolean seenInServiceMode  = new AtomicBoolean(false);
        final AtomicBoolean seenExitedMode     = new AtomicBoolean(false);
        final AtomicReference<Throwable> observerError = new AtomicReference<>();

        final Thread observer = new Thread(() -> {
            try {
                // Wait for main to enter service mode, then sample.
                // Use conditional instead of assertTrue: assertions thrown inside a thread
                // body are not reported to JUnit even if uncaught (IJU_ASSERT_METHOD_INVOKED_FROM_RUN_OR_WAIT).
                if (!serviceModeEnteredLatch.await(10, TimeUnit.SECONDS)) {
                    observerError.set(new AssertionError("service mode was not entered within 10s"));
                    return;
                }
                seenInServiceMode.set(pool.isInServiceMode());
                observedEnteredLatch.countDown();

                // Wait for main to exit service mode, then sample again.
                if (!serviceModeExitedLatch.await(10, TimeUnit.SECONDS)) {
                    observerError.set(new AssertionError("service mode was not exited within 10s"));
                    return;
                }
                seenExitedMode.set(!pool.isInServiceMode());
            } catch (final Throwable t) {
                observerError.set(t);
            }
        }, "service-mode-observer");

        observer.start();
        try {
            pool.enterServiceMode(admin);
            serviceModeEnteredLatch.countDown();       // tell observer to read

            assertTrue(observedEnteredLatch.await(10, TimeUnit.SECONDS),
                    "observer must signal within 10s");

            pool.exitServiceMode(admin);
            serviceModeExitedLatch.countDown();        // tell observer to read again
        } finally {
            observer.join(10_000);
        }

        assertNull(observerError.get(), "observer thread must not throw");
        assertTrue(seenInServiceMode.get(),
                "observer must see isInServiceMode() == true while in service mode");
        assertTrue(seenExitedMode.get(),
                "observer must see isInServiceMode() == false after exit");
    }
}
