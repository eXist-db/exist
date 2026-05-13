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

import javax.swing.SwingUtilities;
import java.lang.reflect.InvocationTargetException;

/**
 * Helpers for running work on the Swing event dispatch thread (EDT).
 * Used by the Java Admin Client to satisfy Swing single-thread rules
 * (<a href="https://github.com/eXist-db/exist/issues/4355">#4355</a>).
 */
final class ClientSwingEdt {

    private ClientSwingEdt() {
    }

    /**
     * Runs {@code task} on the EDT, blocking the caller until it completes.
     * If the caller is already on the EDT, {@code task} runs immediately.
     */
    static void invokeAndWaitIfNeeded(final Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            try {
                SwingUtilities.invokeAndWait(task);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (final InvocationTargetException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new RuntimeException(cause);
            }
        }
    }

    /**
     * Runs {@code task} asynchronously on the EDT when the caller is not on the EDT.
     * If the caller is already on the EDT, {@code task} runs immediately.
     */
    static void invokeLaterIfNeeded(final Runnable task) {
        if (SwingUtilities.isEventDispatchThread()) {
            task.run();
        } else {
            SwingUtilities.invokeLater(task);
        }
    }
}
