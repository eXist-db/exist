/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.storage;

import net.jcip.annotations.GuardedBy;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.Txn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

public class SystemTaskManager implements BrokerPoolService {

    private final static Logger LOG = LogManager.getLogger(SystemTaskManager.class);

    /**
	 * The pending system maintenance tasks of the database instance.
	 */
    @GuardedBy("itself")
	private final Deque<SystemTask> waitingSystemTasks = new ArrayDeque<>();

    private final BrokerPool pool;
    
    public SystemTaskManager(final BrokerPool pool) {
        this.pool = pool;
    }

    public void addSystemTask(final SystemTask task) {
        synchronized (waitingSystemTasks) {
            waitingSystemTasks.push(task);
        }
    }

    /**
     * Process system tasks.
     *
     * @param systemBroker a broker running as the SYSTEM subject.
     */
    public void processTasks(final DBBroker systemBroker, final Txn transaction) {
        //dont run the task if we are shutting down
        if (pool.isShuttingDown() || pool.isShutDown()) {
            return;
        }

        // broker must be running as the SYSTEM subject
        if (!systemBroker.getCurrentSubject().equals(pool.getSecurityManager().getSystemSubject())) {
            throw new IllegalArgumentException("Process tasks requires a system broker");
        }

        synchronized (waitingSystemTasks) {
            if(!waitingSystemTasks.isEmpty()) {
                try {
                    while (!waitingSystemTasks.isEmpty()) {
                        final SystemTask task = waitingSystemTasks.pop();

                        if (pool.isShuttingDown()) {
                            LOG.info("Skipping SystemTask: '" + task.getName() + "' as database is shutting down...");
                        } else if (pool.isShutDown()) {
                            LOG.warn("Unable to execute SystemTask: '" + task.getName() + "' as database is shut down!");
                        } else {
                            if (task.afterCheckpoint()) {
                                pool.sync(systemBroker, Sync.MAJOR);
                            }
                            runSystemTask(task, systemBroker, transaction);
                        }
                    }
                } catch (final Exception e) {
                    LOG.error("System maintenance task reported error: " + e.getMessage(), e);
                }
            }
        }
    }

    private void runSystemTask(final SystemTask task, final DBBroker broker, final Txn transaction) throws EXistException {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Running system maintenance task: " + task.getClass().getName());
        }

        task.execute(broker, transaction);

        if (LOG.isDebugEnabled()) {
            LOG.debug("System task completed.");
        }
    }
}