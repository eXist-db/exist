/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.storage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.LockException;
import org.exist.util.DeadlockException;

/**
 * Wraps around a thread in order to be able to suspend it completely while it is waiting
 * for a lock.
 */
public class WaitingThread implements LockListener {
    private static final Logger LOG = LogManager.getLogger(WaitingThread.class);

    private final Thread thread;
    private final Object monitor;
    private final MultiReadReentrantLock lock;
    private final LockMode lockMode;

    private boolean suspended = false;
    private boolean deadlocked = false;

    public WaitingThread(final Thread thread, final Object monitor, final MultiReadReentrantLock lock, final LockMode lockMode) {
        this.monitor = monitor;
        this.lock = lock;
        this.thread = thread;
        this.lockMode = lockMode;
    }

    /**
     * Start waiting on the monitor object. Continue waiting if the thread wakes up
     * and suspended is set to true. Only stop waiting if suspended is false.
     *
     * @throws LockException
     */
    public void doWait() throws LockException {
        do {
            synchronized (monitor) {
                try {
                    monitor.wait(500);
                } catch (final InterruptedException e) {
                    throw new LockException("Interrupted while waiting for read lock");
                }
            }
            if (deadlocked) {
                LOG.warn("Deadlock detected: cancelling wait...");
                throw new DeadlockException();
            }
        } while (suspended);
    }

    public void signalDeadlock() {
        deadlocked = true;
        synchronized (monitor) {
            monitor.notify();
        }
    }

    /**
     * Put the thread into suspended mode, i.e. keep it asleep even if
     * a notify causes it to wake up temporarily.
     */
    public void suspendWaiting() {
        suspended = true;
    }

    /**
     * Wake the thread from suspended mode.
     */
    @Override
    public void lockReleased() {
//        LOG.debug("Reactivate suspended lock: " + thread.getName());
        suspended = false;
        synchronized (monitor) {
            monitor.notify();
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    public Thread getThread() {
        return thread;
    }

    public Lock getLock() {
        return lock;
    }

    public LockMode getLockMode() {
        return lockMode;
    }

    @Override
    public boolean equals(final Object obj) {
        if(obj == null || !(obj instanceof WaitingThread)) {
         return false;
        }

        return thread == ((WaitingThread)obj).getThread();
    }
}
