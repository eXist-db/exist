/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2007 The eXist Project
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
 * Original code is
 * 
 * Copyright 2001-2004 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *  
 * $Id$
 */
package org.exist.storage.lock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.DeadlockException;
import org.exist.util.LockException;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

/**
 * A reentrant read/write lock, which allows multiple readers to acquire a lock.
 * Waiting writers are preferred.
 * <p/>
 * This is an adapted and bug-fixed version of code taken from Apache's Turbine
 * JCS.
 */
public class MultiReadReentrantLock implements Lock {

    private final static Logger LOG = LogManager.getLogger(MultiReadReentrantLock.class);

    private final Object id;

    /**
     * Number of threads waiting to read.
     */
    private int waitingForReadLock = 0;

    /**
     * Number of threads reading.
     */
    private final List<LockOwner> outstandingReadLocks = new ArrayList<>(4);

    /**
     * The thread that has the write lock or null.
     */
    private Thread writeLockedThread;

    /**
     * The number of (nested) write locks that have been requested from
     * writeLockedThread.
     */
    private int outstandingWriteLocks = 0;

    /**
     * Threads waiting to get a write lock are tracked in this ArrayList to
     * ensure that write locks are issued in the same order they are requested.
     */
    private final List<WaitingThread> waitingForWriteLock = new ArrayList<>(3);

    /**
     * Default constructor.
     */
    public MultiReadReentrantLock(final Object id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id.toString();
    }

    /**
     * @deprecated Use {@link #acquire(LockMode)}
     */
    @Override
    public boolean acquire() throws LockException {
        return acquire(LockMode.READ_LOCK);
    }

    @Override
    public boolean acquire(final LockMode mode) throws LockException {
        switch (mode) {
            case NO_LOCK:
                LOG.warn("Acquired with LockMode.NO_LOCK!");
                return true;

            case READ_LOCK:
                return readLock(true);

            case WRITE_LOCK:
                return writeLock(true);

            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public boolean attempt(final LockMode mode) {
        try {
            switch (mode) {
                case NO_LOCK:
                    LOG.warn("Attempted acquire with LockMode.NO_LOCK!");
                    return true;

                case READ_LOCK:
                    return readLock(false);

                case WRITE_LOCK:
                    return writeLock(false);

                default:
                    throw new IllegalStateException();
            }
        } catch (final LockException e) {
            return false;
        }
    }

    /**
     * Issue a read lock if there is no outstanding write lock or threads
     * waiting to get a write lock. Caller of this method must be careful to
     * avoid synchronizing the calling code so as to avoid deadlock.
    * @param waitIfNecessary whether to wait if the lock is not available right away
     */
    private synchronized boolean readLock(boolean waitIfNecessary) throws LockException {
        final Thread thisThread = Thread.currentThread();
        if (writeLockedThread == thisThread) {
            // add acquired lock to the current list of read locks
            outstandingReadLocks.add(new LockOwner(thisThread));
            //LOG.debug("Thread already holds a write lock");
            return true;
        }
        deadlockCheck();
        waitingForReadLock++;
        if (writeLockedThread != null) {
           if (!waitIfNecessary) {return false;}
            final WaitingThread waiter = new WaitingThread(thisThread, this, this, LockMode.READ_LOCK);
            DeadlockDetection.addResourceWaiter(thisThread, waiter);
            while (writeLockedThread != null) {
                //LOG.debug("readLock wait by " + thisThread.getName() + " for " + getId());
                waiter.doWait();
                //LOG.debug("wake up from readLock wait");
            }
            DeadlockDetection.clearResourceWaiter(thisThread);
        }        waitingForReadLock--;
        //Add acquired lock to the current list of read locks
        outstandingReadLocks.add(new LockOwner(thisThread));
        return true;
    }

    /**
     * Issue a write lock if there are no outstanding read or write locks.
     * Caller of this method must be careful to avoid synchronizing the calling
     * code so as to avoid deadlock.
    * @param waitIfNecessary whether to wait if the lock is not available right away
     */
    private boolean writeLock(boolean waitIfNecessary) throws LockException {
        Thread thisThread = Thread.currentThread();
        WaitingThread waiter;
        synchronized (this) {
            if (writeLockedThread == thisThread) {
                outstandingWriteLocks++;
                return true;
            }
            if (writeLockedThread == null && grantWriteLock()) {
                writeLockedThread = thisThread;
                outstandingWriteLocks++;
                return true;
            }
            if (!waitIfNecessary) {
                return false;
            }
            deadlockCheck();
            waiter = new WaitingThread(thisThread, thisThread, this, LockMode.WRITE_LOCK);
            addWaitingWrite(waiter);
            DeadlockDetection.addResourceWaiter(thisThread, waiter);
        }
        List<WaitingThread> deadlockedThreads = null;
        LockException exceptionCaught = null;
        synchronized (thisThread) {
            if (thisThread != writeLockedThread) {
                while (thisThread != writeLockedThread && deadlockedThreads == null) {
                    if (LockOwner.DEBUG) {
                        final StringBuilder buf = new StringBuilder("Waiting for write: ");
                        for (int i = 0; i < waitingForWriteLock.size(); i++) {
                            buf.append(' ');
                            buf.append((waitingForWriteLock.get(i)).getThread().getName());
                        }
                        LOG.debug(buf.toString());
                        debugReadLocks("WAIT");
                    }
                    deadlockedThreads = checkForDeadlock(thisThread);
                    if (deadlockedThreads == null) {
                        try {
                            waiter.doWait();
                        } catch (LockException e) {
                            //Don't throw the exception now, leave the synchronized block and clean up first
                            exceptionCaught = e;
                            break;
                        }
                    }
                }
            }
            if (deadlockedThreads == null && exceptionCaught == null) {
                outstandingWriteLocks++;
            }
        }
        synchronized (this) {
            DeadlockDetection.clearResourceWaiter(thisThread);
            removeWaitingWrite(waiter);
        }
        if (exceptionCaught != null)
            {throw exceptionCaught;}
        if (deadlockedThreads != null) {
            for (final WaitingThread wt : deadlockedThreads) {
                wt.signalDeadlock();
            }
            throw new DeadlockException();
        }
        return true;
    }

    private void addWaitingWrite(WaitingThread waiter) {
        waitingForWriteLock.add(waiter);
    }

    private void removeWaitingWrite(WaitingThread waiter) {
        for (int i = 0; i < waitingForWriteLock.size(); i++) {
            final WaitingThread next = waitingForWriteLock.get(i);
            if (next.getThread() == waiter.getThread()) {
                waitingForWriteLock.remove(i);
                break;
            }
        }
    }

    /**
     * @deprecated Use {@link #release(LockMode)}
     */
    public void release() {
        release(LockMode.READ_LOCK);
    }

    @Override
    public void release(final LockMode mode) {
        switch (mode) {
            case NO_LOCK:
                LOG.warn("Released with LockMode.NO_LOCK!");
                break;

            case READ_LOCK:
                releaseRead(1);
                break;

            case WRITE_LOCK:
                releaseWrite(1);
                break;

            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void release(final LockMode mode, final int count) {
        switch (mode) {
            case NO_LOCK:
                LOG.warn("Released with LockMode.NO_LOCK and count=" + count + "!");
                break;

            case READ_LOCK:
                releaseRead(count);
                break;

            case WRITE_LOCK:
                releaseWrite(count);
                break;

            default:
                throw new IllegalStateException();
        }
    }

    private synchronized void releaseWrite(int count) {
        if (Thread.currentThread() == writeLockedThread) {
            if (outstandingWriteLocks > 0)
                {outstandingWriteLocks -= count;}
            if (outstandingWriteLocks > 0) {
                return;
            }
            //If another thread is waiting for a write lock, we immediately 
            //pass control to it. No further checks should be required here.
            if (grantWriteLockAfterRead()) {
                final WaitingThread waiter = waitingForWriteLock.get(0);
                removeWaitingWrite(waiter);
                DeadlockDetection.clearResourceWaiter(waiter.getThread());
                writeLockedThread = waiter.getThread();
                synchronized (writeLockedThread) {
                    writeLockedThread.notifyAll();
                }
            } else {
                writeLockedThread = null;
                if (waitingForReadLock > 0) {
                    //Wake up pending read locks
                    notifyAll();
                }
            }
        } else {
            LOG.warn("Possible lock problem: a thread released a write lock it didn't hold. Either the " +
                "thread was interrupted or it never acquired the lock.", new Throwable());
            //TODO : throw exception ? -pb
        }
    }

    /**
     * Threads call this method to relinquish a lock that they previously got
     * from this object.
     *
     * @throws IllegalStateException if called when there are no outstanding locks or there is a
     * write lock issued to a different thread.
     */
    private synchronized void releaseRead(int count) {
        if (!outstandingReadLocks.isEmpty()) {
            removeReadLock(count);
            if (writeLockedThread == null && grantWriteLockAfterRead()) {
                final WaitingThread waiter = waitingForWriteLock.get(0);
                removeWaitingWrite(waiter);
                DeadlockDetection.clearResourceWaiter(waiter.getThread());
                writeLockedThread = waiter.getThread();
                synchronized (writeLockedThread) {
                    writeLockedThread.notifyAll();
                }
            }
            return;
        } else {
            LOG.warn("Possible lock problem: thread " + Thread.currentThread().getName() +
                    " released a read lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock. " +
                    "Write lock: " + (writeLockedThread != null ? writeLockedThread.getName() : "null"),
                    new Throwable());
            if (LockOwner.DEBUG) {
                debugReadLocks("ILLEGAL RELEASE");
            }
            //TODO : throw exception ? -pb
        }
    }

    @Override
    public synchronized boolean isLockedForWrite() {
        return writeLockedThread != null || (waitingForWriteLock != null && waitingForWriteLock.size() > 0);
    }

    @Override
    public synchronized boolean hasLock() {
        return !outstandingReadLocks.isEmpty() || isLockedForWrite();
    }

    @Override
    public synchronized boolean isLockedForRead(Thread owner) {
        for (int i = outstandingReadLocks.size() - 1; i > -1; i--) {
            if ((outstandingReadLocks.get(i)).getOwner() == owner) {
                return true;
            }
        }
        return false;
    }

    private void removeReadLock(int count) {
        final Object owner = Thread.currentThread();
        for (int i = outstandingReadLocks.size() - 1; i > -1 && count > 0; i--) {
            final LockOwner current = outstandingReadLocks.get(i);
            if (current.getOwner() == owner) {
                outstandingReadLocks.remove(i);
                --count;
            }
        }
    }

    private void deadlockCheck() throws DeadlockException {
        for (final LockOwner next : outstandingReadLocks) {
            final Lock lock = DeadlockDetection.isWaitingFor(next.getOwner());
            if (lock != null) {
                lock.wakeUp();
            }
        }
    }

    /**
     * Detect circular wait on different resources: thread A has a write lock on
     * resource R1; thread B has a write lock on resource R2; thread A tries to
     * acquire lock on R2; thread B now tries to acquire lock on R1. Solution:
     * suspend existing write lock of thread A and grant it to B.
     *
     * @return true if the write lock should be granted to the current thread
     */
    private List<WaitingThread> checkForDeadlock(Thread waiter) {
        final ArrayList<WaitingThread> waiters = new ArrayList<WaitingThread>(10);
        if (DeadlockDetection.wouldDeadlock(waiter, writeLockedThread, waiters)) {
            LOG.warn("Potential deadlock detected on lock " + getId() + "; killing threads: " + waiters.size());
            return waiters.size() > 0 ? waiters : null;
        }
        return null;
    }

    /**
     * Check if a write lock can be granted, either because there are no
     * read locks, the read lock belongs to the current thread and can be
     * upgraded or the thread which holds the lock is blocked by another
     * lock held by the current thread.
     *
     * @return true if the write lock can be granted
     */
    private boolean grantWriteLock() {
        if (outstandingReadLocks.isEmpty()) {
            return true;
        }
        final Thread waiter = Thread.currentThread();
        //Walk through outstanding read locks
        for (final LockOwner next : outstandingReadLocks) {
            //If the read lock is owned by the current thread, all is OK and we continue
            if (next.getOwner() != waiter) {
                //Otherwise, check if the lock belongs to a thread which is currently blocked
                //by a lock owned by the current thread. if yes, it will be safe to grant the
                //write lock: the other thread will be blocked anyway.
                if (!DeadlockDetection.isBlockedBy(waiter, next.getOwner())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Check if a write lock can be granted, either because there are no
     * read locks or the read lock belongs to the current thread and can be
     * upgraded. This method is called whenever a lock is released.
     *
     * @return true if the write lock can be granted
     */
    private boolean grantWriteLockAfterRead() {
        //Waiting write locks?
        if (waitingForWriteLock != null && waitingForWriteLock.size() > 0) {
            //Yes, check read locks
            final int size = outstandingReadLocks.size();
            if (size > 0) {
                //Grant lock if all read locks are held by the write thread
                final WaitingThread waiter = waitingForWriteLock.get(0);
                return isCompatible(waiter.getThread());
            }
            return true;
        }
        return false;
    }

    /**
     * Check if the specified thread has a read lock on the resource.
     *
     * @param owner the thread
     * @return true if owner has a read lock
     */
    private boolean hasReadLock(final Thread owner) {
        for (final LockOwner next : outstandingReadLocks) {
            if (next.getOwner() == owner) {
                return true;
            }
        }
        return false;
    }

    public Thread getWriteLockedThread() {
        return writeLockedThread;
    }
    
    /**
     * Check if the specified thread holds either a write or a read lock
     * on the resource.
     *
     * @param owner the thread
     * @return true if owner has a lock
     */
    @Override
    public boolean hasLock(final Thread owner) {
        if (writeLockedThread == owner) {
            return true;
        }
        return hasReadLock(owner);
    }

    @Override
    public void wakeUp() {
        //Nothing to do
    }

    /**
     * Check if the pending request for a write lock is compatible
     * with existing read locks and other write requests. A lock request is
     * compatible with another lock request if: (a) it belongs to the same thread,
     * (b) it belongs to a different thread, but this thread is also waiting for a write lock.
     *
     * @param waiting
     * @return true if the lock request is compatible with all other requests and the
     * lock can be granted.
     */
    private boolean isCompatible(final Thread waiting) {
        for (final LockOwner next : outstandingReadLocks) {
            //If the read lock is owned by the current thread, all is OK and we continue
            if (next.getOwner() != waiting) {
                //Otherwise, check if the lock belongs to a thread which is currently blocked
                //by a lock owned by the current thread. if yes, it will be safe to grant the
                //write lock: the other thread will be blocked anyway.
                if (!DeadlockDetection.isBlockedBy(waiting, next.getOwner())) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public synchronized LockInfo getLockInfo() {
        LockInfo info;
        String[] readers = new String[0];
        if (outstandingReadLocks != null) {
            readers = new String[outstandingReadLocks.size()];
            for (int i = 0; i < outstandingReadLocks.size(); i++) {
                final LockOwner owner = outstandingReadLocks.get(i);
                readers[i] = owner.getOwner().getName();
            }
        }
        if (writeLockedThread != null) {
            info = new LockInfo(LockInfo.RESOURCE_LOCK, LockInfo.WRITE_LOCK, getId(), 
                    new String[] {writeLockedThread.getName()});
            info.setReadLocks(readers);
        } else {
            info = new LockInfo(LockInfo.RESOURCE_LOCK, LockInfo.READ_LOCK, getId(), readers);
        }
        if (waitingForWriteLock != null) {
            final String waitingForWrite[] = new String[waitingForWriteLock.size()];
            for (int i = 0; i < waitingForWriteLock.size(); i++) {
                waitingForWrite[i] = waitingForWriteLock.get(i).getThread().getName();
            }
            info.setWaitingForWrite(waitingForWrite);
        }
        return info;
    }

    private void debugReadLocks(String msg) {
        for (final LockOwner owner : outstandingReadLocks) {
            LOG.debug(msg + ": " + owner.getOwner(), owner.getStack());
        }
    }

    @Override
    public void debug(final PrintStream out) {
        getLockInfo().debug(out);
    }
}