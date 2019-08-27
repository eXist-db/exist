/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.scheduler.impl;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.NamedThreadFactory;

import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

/**
 * Schedulable Task for shutting down the database
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ShutdownTask implements SystemTask {

    @Override
    public String getName() {
        return "Database Shutdown";
    }

    @Override
    public void configure(final Configuration config, final Properties properties) throws EXistException {
    }

    @Override
    public void execute(final DBBroker broker, final Txn transaction) throws EXistException {

        //NOTE - shutdown must be executed asynchronously from the scheduler, to avoid a deadlock with shutting down the scheduler
        final Callable shutdownCallable = new AsyncShutdown(broker.getBrokerPool());
        Executors.newSingleThreadExecutor(new NamedThreadFactory(broker.getBrokerPool(), "shutdown-task-async-shutdown")).submit(shutdownCallable);
    }

    @Override
    public boolean afterCheckpoint() {
        return true;
    }

    private static class AsyncShutdown implements Callable<Void> {
        private final BrokerPool brokerPool;
        public AsyncShutdown(final BrokerPool brokerPool) {
            this.brokerPool = brokerPool;
        }

        @Override
        public Void call() {
            brokerPool.shutdown();
            return null;
        }
    }
}
