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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.swing.SwingWorker;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class ClientTask extends SwingWorker<Void, Void> {
    private static final Logger LOG = LogManager.getLogger(ClientTask.class);

    private final ClientTaskAction action;
    private final Runnable successAction;
    private final Consumer<Throwable> failureAction;

    private ClientTask(ClientTaskAction action, Runnable successAction, Consumer<Throwable> failureAction) {
        super();
        this.action = action;
        this.successAction = successAction;
        this.failureAction = failureAction;
    }

    public static ClientTask execute(ClientTaskAction action, Runnable successAction, Consumer<Throwable> failureAction) {
        final ClientTask clientTask = new ClientTask(action, successAction, failureAction);
        clientTask.execute();
        return clientTask;
    }

    public static ClientTask execute(ClientTaskAction action, Consumer<Throwable> failureAction) {
        return execute(action, ClientTask::successAction, failureAction);
    }

    public static ClientTask execute(ClientTaskAction action, Runnable successAction) {
        return execute(action, successAction, ClientTask::failureAction);
    }

    public static ClientTask execute(ClientTaskAction action) {
        return execute(action, ClientTask::successAction, ClientTask::failureAction);
    }

    private static void successAction() {
        LOG.debug("Task {} completed successfully", Thread.currentThread().getName());
    }

    private static void failureAction(Throwable e) {
        LOG.debug("Task {} failed", Thread.currentThread().getName(), e);
    }

    @Override
    protected Void doInBackground() throws Exception {
        action.execute();
        return null;
    }

    @Override
    protected void done() {
        try {
            get();
            successAction.run();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            failureAction.accept(e);
        } catch (final ExecutionException e) {
            failureAction.accept(e);
        }
    }

    @FunctionalInterface
    public interface ClientTaskAction {
        void execute() throws Exception;
    }
}
