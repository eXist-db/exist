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
package org.exist.client;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClientSwingEdt}.
 */
class ClientSwingEdtTest {

    @Test
    void invokeLaterIfNeededRunsOnEdtWhenCalledFromBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicBoolean ranOnEdt = new AtomicBoolean();
        final CountDownLatch latch = new CountDownLatch(1);

        final Thread background = new Thread(() -> {
            ClientSwingEdt.invokeLaterIfNeeded(() -> {
                ranOnEdt.set(SwingUtilities.isEventDispatchThread());
                latch.countDown();
            });
        });
        background.start();
        background.join();

        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
        SwingUtilities.invokeAndWait(() -> {
        });
        assertThat(ranOnEdt.get()).isTrue();
    }

    @Test
    void invokeLaterIfNeededRunsInlineWhenAlreadyOnEdt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicReference<Thread> edtThread = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> edtThread.set(Thread.currentThread()));
        final AtomicReference<Thread> runThread = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ClientSwingEdt.invokeLaterIfNeeded(() -> runThread.set(Thread.currentThread())));

        assertThat(runThread.get()).isSameAs(edtThread.get());
    }

    @Test
    void invokeAndWaitIfNeededRunsOnEdtFromBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicBoolean ranOnEdt = new AtomicBoolean();
        final Thread background = new Thread(() -> ClientSwingEdt.invokeAndWaitIfNeeded(() -> ranOnEdt.set(SwingUtilities.isEventDispatchThread())));
        background.start();
        background.join();

        assertThat(ranOnEdt.get()).isTrue();
    }

    @Test
    void invokeAndWaitIfNeededRunsInlineWhenAlreadyOnEdt() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicReference<Thread> edtThread = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> edtThread.set(Thread.currentThread()));
        final AtomicReference<Thread> runThread = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> ClientSwingEdt.invokeAndWaitIfNeeded(() -> runThread.set(Thread.currentThread())));

        assertThat(runThread.get()).isSameAs(edtThread.get());
    }

    @Test
    void invokeAndWaitIfNeededPropagatesRuntimeExceptionFromBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicReference<Throwable> uncaught = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        final Thread background = new Thread(() -> {
            try {
                ClientSwingEdt.invokeAndWaitIfNeeded(() -> {
                    throw new IllegalStateException("boom");
                });
            } finally {
                finished.countDown();
            }
        });
        background.setUncaughtExceptionHandler((t, ex) -> uncaught.set(ex));
        background.start();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        background.join();
        assertThat(uncaught.get()).isInstanceOf(IllegalStateException.class).hasMessage("boom");
    }

    @Test
    void invokeAndWaitIfNeededPropagatesErrorFromBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final AtomicReference<Throwable> uncaught = new AtomicReference<>();
        final CountDownLatch finished = new CountDownLatch(1);
        final Thread background = new Thread(() -> {
            try {
                ClientSwingEdt.invokeAndWaitIfNeeded(() -> {
                    throw new AssertionError("fatal");
                });
            } finally {
                finished.countDown();
            }
        });
        background.setUncaughtExceptionHandler((t, ex) -> uncaught.set(ex));
        background.start();
        assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
        background.join();
        assertThat(uncaught.get()).isInstanceOf(AssertionError.class).hasMessage("fatal");
    }

    @Test
    void invokeLaterIfNeededPreservesOrderFromBackground() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());

        final List<Integer> order = Collections.synchronizedList(new ArrayList<>());
        final Thread background = new Thread(() -> {
            for (int i = 1; i <= 3; i++) {
                final int n = i;
                ClientSwingEdt.invokeLaterIfNeeded(() -> order.add(n));
            }
        });
        background.start();
        background.join();
        SwingUtilities.invokeAndWait(() -> {
        });
        assertThat(order).containsExactly(1, 2, 3);
    }
}
