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

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;

/**
 * Shared {@link SwingWorker} pattern for XML:DB work off the EDT with EDT completion (#4355).
 *
 * @param <T> result type produced in {@link #loadInBackground()}
 */
public abstract class ClientSwingXmlWorker<T> extends SwingWorker<T, Void> {

    /**
     * Runs off the EDT; perform XML:DB / blocking I/O here.
     */
    protected abstract T loadInBackground() throws Exception;

    @Override
    protected final T doInBackground() throws Exception {
        return loadInBackground();
    }

    /**
     * Runs on the EDT after {@link #loadInBackground()} completed successfully.
     */
    protected void onSuccess(final T result) {
    }

    /**
     * Runs on the EDT when {@link #loadInBackground()} failed.
     */
    protected void onFailure(final Throwable t) {
        if (t instanceof Exception ex) {
            ClientFrame.showErrorMessage(ex.getMessage(), ex);
        } else {
            ClientFrame.showErrorMessage(t.toString(), new Exception(t));
        }
    }

    @Override
    protected final void done() {
        try {
            onSuccess(get());
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            final Throwable cause = e.getCause() != null ? e.getCause() : e;
            onFailure(cause);
        }
    }
}
