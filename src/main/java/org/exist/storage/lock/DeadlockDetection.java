/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Deadlock detection for resource and collection locks. The static methods in this class
 * keep track of all waiting threads, which are currently waiting on a resource or collection
 * lock. In some scenarios (e.g. a complex XQuery which modifies resources), a single thread
 * may acquire different read/write locks on resources in a collection. The locks can be arbitrarily
 * nested. For example, a thread may first acquire a read lock on a collection, then a read lock on
 * a resource and later acquires a write lock on the collection to remove the resource.
 *
 * Since we have locks on both, collections and resources, deadlock situations are sometimes
 * unavoidable. For example, imagine the following scenario:
 *
 * <ul>
 *  <li>T1 owns write lock on resource</li>
 *  <li>T2 owns write lock on collection</li>
 *  <li>T2 wants to acquire write lock on resource locked by T1</li>
 *  <li>T1 tries to acquire write lock on collection currently locked by T2</li>
 *  <li>DEADLOCK</li>
 * </ul>
 *
 * The code should probably be redesigned to avoid this kind of crossed collection-resource
 * locking, which easily leads to circular wait conditions. However, this needs to be done with care. In
 * the meantime, DeadlockDetection is used to detect deadlock situations as the one described
 * above. The lock classes can
 * then try to resolve the deadlock by suspending one thread.
 */
public class DeadlockDetection {

    private final static Logger LOG = LogManager.getLogger(DeadlockDetection.class);

    private final static Map<Thread, WaitingThread> waitForResource = new HashMap<>();
    private final static Map<Thread, Lock> waitForCollection = new HashMap<>();

    /**
     * Register a thread as waiting for a resource lock.
     *
     * @param thread the thread
     * @param waiter the WaitingThread object which wraps around the thread
     */
    public static void addResourceWaiter(final Thread thread, final WaitingThread waiter) {
        synchronized (DeadlockDetection.class) {
            waitForResource.put(thread, waiter);
        }
    }

    /**
     * Deregister a waiting thread.
     *  
     * @param thread
     * @return lock
     */
    public static Lock clearResourceWaiter(final Thread thread) {
        synchronized (DeadlockDetection.class) {
            final WaitingThread waiter = waitForResource.remove(thread);
            if (waiter != null)
                {return waiter.getLock();}
            return null;
        }
    }

    public static WaitingThread getResourceWaiter(final Thread thread) {
        synchronized (DeadlockDetection.class) {
            return waitForResource.get(thread);
        }
    }

    /**
     * Check if there's a risk for a circular wait between threadA and threadB. The method tests if
     * threadB is currently waiting for a resource lock (read or write). It then checks
     * if threadA holds a lock on this resource. If yes, the {@link org.exist.storage.lock.WaitingThread}
     * object for threadB is returned. This object can be used to suspend the waiting thread
     * in order to temporarily yield the lock to threadA.
     *
     * @param threadA
     * @param threadB
     * @return waiting thread
     */
    public static WaitingThread deadlockCheckResource(final Thread threadA, final Thread threadB) {
        synchronized (DeadlockDetection.class) {
            //Check if threadB is waiting for a resource lock
            final WaitingThread waitingThread = waitForResource.get(threadB);
            //If lock != null, check if thread B waits for a resource lock currently held by thread A
            if (waitingThread != null) {
                return waitingThread.getLock().hasLock(threadA) ? waitingThread : null;
            }
            return null;
        }
    }

    /**
     * Check if the second thread is currently waiting for a resource lock and
     * is blocked by the first thread.
     *
     * @param threadA the thread whose lock might be blocking threadB
     * @param threadB the thread to check
     * @return true if threadB is currently blocked by a lock held by threadA
     */
    public static boolean isBlockedBy(final Thread threadA, final Thread threadB) {
        synchronized (DeadlockDetection.class) {
            //Check if threadB is waiting for a resource lock
            final WaitingThread waitingThread = waitForResource.get(threadB);
            //If lock != null, check if thread B waits for a resource lock currently held by thread A
            if (waitingThread != null) {
                return waitingThread.getLock().hasLock(threadA);
            }
            return false;
        }
    }

    public static boolean wouldDeadlock(final Thread waiter, final Thread owner, final List<WaitingThread> waiters) {
        synchronized (DeadlockDetection.class) {
            final WaitingThread wt = waitForResource.get(owner);
            if (wt != null) {
                if (waiters.contains(wt)) {
                    // probably a deadlock, but not directly connected to the current thread
                    // return to avoid endless loop
                    return false;
                }
                waiters.add(wt);
                final Lock l = wt.getLock();
                final Thread t = ((MultiReadReentrantLock) l).getWriteLockedThread();
                if (t == owner) {
                    return false;
                }
                if (t != null) {
                    if (t == waiter)
                        {return true;}
                    return wouldDeadlock(waiter, t, waiters);
                }
                return false;
            }
            final Lock l = waitForCollection.get(owner);
            if (l != null) {
                final Thread t = ((ReentrantReadWriteLock) l).getOwner();
                if (t == owner) {
                    return false;
                }
                if (t != null) {
                    if (t == waiter)
                        {return true;}
                    return wouldDeadlock(waiter, t, waiters);
                }
            }
            return false;
        }
    }

    /**
     * Register a thread as waiting for a resource lock.
     *
     * @param waiter the thread
     * @param lock the lock object
     */
    public static void addCollectionWaiter(final Thread waiter, final Lock lock) {
        synchronized (DeadlockDetection.class) {
            waitForCollection.put(waiter, lock);
        }
    }

    public static Lock clearCollectionWaiter(final Thread waiter) {
        synchronized (DeadlockDetection.class) {
            return waitForCollection.remove(waiter);
        }
    }

    public static Lock isWaitingFor(final Thread waiter) {
        synchronized (DeadlockDetection.class) {
            return waitForCollection.get(waiter);
        }
    }

    public static Map<String, LockInfo> getWaitingThreads() {
        final Map<String, LockInfo> table = new HashMap<>();
        for (final WaitingThread waitingThread : waitForResource.values()) {
            table.put(waitingThread.getThread().getName(), waitingThread.getLock().getLockInfo());
        }
        for (final Map.Entry<Thread, Lock> entry : waitForCollection.entrySet()) {
            table.put(entry.getKey().getName(), entry.getValue().getLockInfo());
        }
        return table;
    }

    public static void debug(final String name, final LockInfo info) {
        try(final StringWriter sout = new StringWriter();
                final PrintWriter writer = new PrintWriter(sout)) {
            debug(writer, name, info);
            System.out.println(sout.toString());
        } catch(final IOException e) {
            LOG.error(e.getMessage(), e);
        }
    }

    public static void debug(final PrintWriter writer, final String name, final LockInfo info) {
        writer.println("Thread: " + name);
        if (info != null) {
            writer.format("%20s: %s\n", "Lock type", info.getLockType());
            writer.format("%20s: %s\n", "Lock mode", info.getLockMode());
            writer.format("%20s: %s\n", "Lock id", info.getId());
            writer.format("%20s: %s\n", "Held by", Arrays.toString(info.getOwners()));
            writer.format("%20s: %s\n", "Held by", Arrays.toString(info.getOwners()));
            writer.format("%20s: %s\n", "Held by", Arrays.toString(info.getOwners()));
            writer.format("%20s: %s\n", "Waiting for read", Arrays.toString(info.getWaitingForRead()));
            writer.format("%20s: %s\n\n", "Waiting for write", Arrays.toString(info.getWaitingForWrite()));
        }
    }

    public static void debug(final PrintWriter writer) {
        writer.println("Threads currently waiting for a lock:");
        writer.println("=====================================");
        
        final Map<String, LockInfo> threads = getWaitingThreads();
        for (final Map.Entry<String, LockInfo> entry : threads.entrySet()) {
            debug(writer, entry.getKey(), entry.getValue());
        }
    }
}
