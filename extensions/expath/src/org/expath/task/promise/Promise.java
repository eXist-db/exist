/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.expath.task.promise;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;


/**
 * This is just an abstraction over Java's Future class
 * to present an interface that is similar
 * to JavaScript's Promise/A+
 */
public class Promise<T, E extends Throwable> {

    private static final Logger LOG = LogManager.getLogger(Promise.class);

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();
    private final PromiseRunnable<T, E> runnable;
    private final Future<?> future;

    public Promise(final ExecutorFunction<T, E> executor) {
        this.runnable = new PromiseRunnable<>(executor);
        this.future = EXECUTOR_SERVICE.submit(this.runnable);
    }

    /**
     * Returns null if an error occurs with the executor framework
     */
    public @Nullable T await() throws E {
        try {
            // wait if computation is still processing
            future.get();

            if (runnable.rejectCapture.captured) {
                throw runnable.rejectCapture.value;
            }
            return runnable.resolveCapture.value;

        } catch (final InterruptedException | ExecutionException e) {
            LOG.error(e.getMessage(), e);
            return null;
        }
    }

    private static class PromiseRunnable<T, E extends Throwable> implements Runnable {
        private final ExecutorFunction<T, E> executor;
        final Capture<T> resolveCapture = new Capture<>();
        final Capture<E> rejectCapture = new Capture<>();

        public PromiseRunnable(final ExecutorFunction<T, E> executor) {
            this.executor = executor;
        }

        @Override
        public void run() {
            executor.apply(resolveCapture, rejectCapture);
        }
    }

    @FunctionalInterface
    public interface ExecutorFunction<T, E extends Throwable> {
        void apply(final Consumer<T> resolve, final Consumer<E> reject);
    }

    private static class Capture<T> implements Consumer<T> {
        @Nullable private T value = null;
        private boolean captured = false;

        @Override
        public void accept(@Nullable final T value) {
            if (!captured) {
                this.value = value;
            }
        }

        public boolean isCaptured() {
            return captured;
        }

        @Nullable
        public T getValue() {
            return value;
        }
    }
}
