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

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.sync.Sync;

import java.util.Stack;

public class SystemTaskManager {

    //private final static Logger LOG = LogManager.getLogger(SystemTaskManager.class);

    /**
	 * The pending system maintenance tasks of the database instance.
	 */
	private final Stack<SystemTask> waitingSystemTasks = new Stack<SystemTask>();

    private BrokerPool pool;
    
    public SystemTaskManager(BrokerPool pool) {
        this.pool = pool;
    }

    public void triggerSystemTask(SystemTask task) {
        synchronized (waitingSystemTasks) {
            waitingSystemTasks.push(task);
            pool.getTransactionManager().processSystemTasks();
        }
    }

    public void processTasks() {
        //dont run the task if we are shutting down
        if (pool.isShuttingDown()) {
            return;
        }

        synchronized (waitingSystemTasks) {
            DBBroker broker = null;
            Subject oldUser = null;
            try {
                broker = pool.get(null);
                oldUser = broker.getSubject();
                broker.setSubject(pool.getSecurityManager().getSystemSubject());
                while (!waitingSystemTasks.isEmpty()) {
                	final SystemTask task = waitingSystemTasks.pop();
                	if (task.afterCheckpoint())
                		{pool.sync(broker, Sync.MAJOR_SYNC);}
                    runSystemTask(task, broker);
                }

            } catch (final Exception e) {
                SystemTask.LOG.warn("System maintenance task reported error: " + e.getMessage(), e);
                
            } finally {
                if (oldUser != null) {
                    broker.setSubject(oldUser);
                }
                pool.release(broker);
            }
        }
    }

    private void runSystemTask(SystemTask task, DBBroker broker) throws EXistException {
        if (SystemTask.LOG.isDebugEnabled())
            {SystemTask.LOG.debug("Running system maintenance task: " + task.getClass().getName());}
        task.execute(broker);
        if (SystemTask.LOG.isDebugEnabled())
            {SystemTask.LOG.debug("System task completed.");}
    }

    public void initialize() {
        waitingSystemTasks.clear();
    }

    public void shutdown() {
        synchronized (waitingSystemTasks) {
            waitingSystemTasks.clear();
        }
    }
}