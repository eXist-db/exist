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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
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

    private final static Object latch = new Object();

    private final static Map waitForResource = new HashMap();
    private final static Map waitForCollection = new HashMap();

    /**
     * Register a thread as waiting for a resource lock.
     *
     * @param thread the thread
     * @param waiter the WaitingThread object which wraps around the thread
     */
    public static void addResourceWaiter(Thread thread, WaitingThread waiter) {
        synchronized (latch) {
            waitForResource.put(thread, waiter);
        }
    }

    /**
     * Deregister a waiting thread.
     *  
     * @param thread
     * @return lock
     */
    public static Lock clearResourceWaiter(Thread thread) {
        synchronized (latch) {
            WaitingThread waiter = (WaitingThread) waitForResource.remove(thread);
            if (waiter != null)
                return waiter.getLock();
            return null;
        }
    }

    public static WaitingThread getResourceWaiter(Thread thread) {
        synchronized (latch) {
            return (WaitingThread) waitForResource.get(thread);
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
    public static WaitingThread deadlockCheckResource(Thread threadA, Thread threadB) {
        synchronized (latch) {
            // check if threadB is waiting for a resource lock
            WaitingThread waitingThread = (WaitingThread) waitForResource.get(threadB);
            // if lock != null, check if thread B waits for a resource lock currently held by thread A
            if (waitingThread != null) {
//            	LOG.debug("deadlockCheck: " + threadB.getName() + " -> " + waitingThread.getLock().hasLock(threadA));
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
    public static boolean isBlockedBy(Thread threadA, Thread threadB) {
        synchronized (latch) {
            // check if threadB is waiting for a resource lock
            WaitingThread waitingThread = (WaitingThread) waitForResource.get(threadB);
            // if lock != null, check if thread B waits for a resource lock currently held by thread A
            if (waitingThread != null) {
                return waitingThread.getLock().hasLock(threadA);
            }
            return false;
        }
    }

    public static boolean wouldDeadlock(Thread waiter, Thread owner, List waiters) {
        synchronized (latch) {
            WaitingThread wt = (WaitingThread) waitForResource.get(owner);
            if (wt != null) {
                if (waiters.contains(wt)) {
                    // probably a deadlock, but not directly connected to the current thread
                    // return to avoid endless loop
                    return false;
                }
                waiters.add(wt);
                Lock l = wt.getLock();
                Thread t = ((MultiReadReentrantLock) l).getWriteLockedThread();
                if (t == owner) {
    //                System.out.println("Waiter: " + waiter.getName() + " Thread: " + t.getName() + " == " + owner.getName() +
    //                    " type: " + wt.getLockType());
    //                debug(t.getName(), l.getLockInfo());
                    // the thread acquired the lock in the meantime
                    return false;
                }
                if (t != null) {
                    if (t == waiter)
                        return true;
                    return wouldDeadlock(waiter, t, waiters);
                } else
                    return false;
            } else {
                Lock l = (Lock) waitForCollection.get(owner);
                if (l != null) {
                    Thread t = ((ReentrantReadWriteLock) l).getOwner();
                    if (t == owner) {
    //                    System.out.println("Thread " + t.getName() + " == " + owner.getName());
    //                    debug(t.getName(), l.getLockInfo());
                        // the thread acquired the lock in the meantime
                        return false;
                    }
                    if (t != null) {
                        if (t == waiter)
                            return true;
                        return wouldDeadlock(waiter, t, waiters);
                    }
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
    public static void addCollectionWaiter(Thread waiter, Lock lock) {
        synchronized (latch) {
            waitForCollection.put(waiter, lock);
        }
    }

    public static Lock clearCollectionWaiter(Thread waiter) {
        synchronized (latch) {
            return (Lock) waitForCollection.remove(waiter);
        }
    }

    public static Lock isWaitingFor(Thread waiter) {
        synchronized (latch) {
            return (Lock) waitForCollection.get(waiter);
        }
    }

    public static Map getWaitingThreads() {
        Map table = new HashMap();
        for (Iterator i = waitForResource.values().iterator(); i.hasNext(); ) {
            WaitingThread waitingThread = (WaitingThread) i.next();
            table.put(waitingThread.getThread().getName(), waitingThread.getLock().getLockInfo());
        }
        for (Iterator i = waitForCollection.entrySet().iterator(); i.hasNext(); ) {
            Map.Entry entry = (Map.Entry) i.next();
            Thread thread = (Thread) entry.getKey();
            table.put(thread.getName(), ((Lock)entry.getValue()).getLockInfo());
        }
        return table;
    }

    public static void debug(String name, LockInfo info) {
        StringWriter sout = new StringWriter();
        PrintWriter writer = new PrintWriter(sout);
        debug(writer, name, info);
        writer.flush();
        writer.close();
        System.out.println(sout.toString());
    }

    public static void debug(PrintWriter writer, String name, LockInfo info) {
        writer.println("THREAD: " + name);
        if (info != null) {
            writer.println("Lock type: " + info.getLockType());
            writer.println("Lock mode: " + info.getLockMode());
            writer.println("Lock id: " + info.getId());
            writer.println("Held by: " + arrayToString(info.getOwners()));
            writer.println("Read locks: " + arrayToString(info.getReadLocks()));
            writer.println("Wait for read: " + arrayToString(info.getWaitingForRead()));
            writer.println("Wait for write: " + arrayToString(info.getWaitingForWrite()));
        }
    }

    public static void debug() {
        StringWriter sout = new StringWriter();
        PrintWriter writer = new PrintWriter(sout);
        Map threads = getWaitingThreads();
        for (Iterator i = threads.entrySet().iterator(); i.hasNext();) {
            Map.Entry entry = (Map.Entry) i.next();
            debug(writer, entry.getKey().toString(), (LockInfo) entry.getValue());
        }
        writer.close();
        System.out.println(sout.toString());
    }

    private static String arrayToString(Object[] a) {
        if (a == null)
            return "null";
        if (a.length == 0)
            return "[]";

        StringBuffer buf = new StringBuffer();

        for (int i = 0; i < a.length; i++) {
            if (i == 0)
                buf.append('[');
            else
                buf.append(", ");

            buf.append(a[i] == null ? "null" : a[i].toString());
        }

        buf.append("]");
        return buf.toString();
    }
}