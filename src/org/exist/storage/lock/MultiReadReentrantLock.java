/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
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

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.util.LockException;
import org.exist.dom.DocumentImpl;

/**
 * A reentrant read/write lock, which allows multiple readers to acquire a lock.
 * Waiting writers are preferred.
 * <p/>
 * This is an adapted and bug-fixed version of code taken from Apache's Turbine
 * JCS.
 */
public class MultiReadReentrantLock implements Lock {

    private final static Logger LOG = Logger.getLogger(MultiReadReentrantLock.class);

    /**
     * Number of threads waiting to read.
     */
    private int waitingForReadLock = 0;

    /**
     * Number of threads reading.
     */
    private List outstandingReadLocks = new ArrayList(4);

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
    private List waitingForWriteLock = null;

    /**
     * Default constructor.
     */
    public MultiReadReentrantLock() {
    }

    /* @deprecated Use other method
     * @see org.exist.storage.lock.Lock#acquire()
     */
    public boolean acquire() throws LockException {
        return acquire(Lock.READ_LOCK);
    }

    public boolean acquire(int mode) throws LockException {
        if (mode == Lock.NO_LOCK) {
            LOG.warn("acquired with no lock !");
            return true;
        }
        switch (mode) {
            case Lock.WRITE_LOCK:
                return writeLock();
            default:
                return readLock();
        }
    }

    /* (non-Javadoc)
	 * @see org.exist.util.Lock#attempt(int)
	 */
    public boolean attempt(int mode) {
        throw new RuntimeException("Not implemented");
    }

    /**
     * Issue a read lock if there is no outstanding write lock or threads
     * waiting to get a write lock. Caller of this method must be careful to
     * avoid synchronizing the calling code so as to avoid deadlock.
     */
    private synchronized boolean readLock() throws LockException {
        final Object owner = Thread.currentThread();
        if (writeLockedThread == owner) {
            // add acquired lock to the current list of read locks
            outstandingReadLocks.add(new LockOwner(owner));
            return true;
        }
        waitingForReadLock++;
        while (writeLockedThread != null) {
//            LOG.debug("readLock wait");
            try {
                wait(100);
            } catch (InterruptedException e) {
                throw new LockException("Interrupted while waiting for read lock");
            }
//            LOG.debug("wake up from readLock wait");
        }

//        LOG.debug("readLock acquired by thread: " + Thread.currentThread().getName());

        waitingForReadLock--;
        // add acquired lock to the current list of read locks
        outstandingReadLocks.add(new LockOwner(owner));
        return true;
    }

    /**
     * Issue a write lock if there are no outstanding read or write locks.
     * Caller of this method must be careful to avoid synchronizing the calling
     * code so as to avoid deadlock.
     */
    private boolean writeLock() throws LockException {
        Thread thisThread = Thread.currentThread();
        synchronized (this) {
            if (writeLockedThread == null && grantWriteLock()) {
                writeLockedThread = thisThread;
                outstandingWriteLocks++;

//                LOG.debug( "writeLock acquired without waiting by " + writeLockedThread.getName());

                return true;
            }
//            if (writeLockedThread == thisThread) {
//                LOG.debug("nested write lock: " + outstandingWriteLocks);
//            }
            if (waitingForWriteLock == null)
                waitingForWriteLock = new ArrayList(3);
            waitingForWriteLock.add(thisThread);
        }
        synchronized (thisThread) {
            while (thisThread != writeLockedThread) {
                if (LockOwner.DEBUG) {
                    LOG.debug( "writeLock wait on " + hashCode() + ". outstanding: " + outstandingWriteLocks);
                    StringBuffer buf = new StringBuffer("Waiting for write: ");
                    for (int i = 0; i < waitingForWriteLock.size(); i++) {
                        buf.append(' ');
                        buf.append(((Thread) waitingForWriteLock.get(i)).getName());
                    }
                    LOG.debug(buf.toString());
                    debugReadLocks("WAIT");
                }
                try {
                    // set this so if there is an error the app will not
                    // completely die!
                    thisThread.wait();
                } catch (InterruptedException e) {
                    throw new LockException("Interrupted");
                }
                //                log.debug( "wake up from writeLock wait" );
            }
            outstandingWriteLocks++; //testing
//            log.debug( "writeLock acquired " + writeLockedThread.getName());
        }
        synchronized (this) {
            int i = waitingForWriteLock.indexOf(thisThread);
            waitingForWriteLock.remove(i);
        }
        return true;
    }

    /* @deprecated : use other method
     * @see org.exist.storage.lock.Lock#release()
     */
    public void release() {
        release(Lock.READ_LOCK);
    }

    public void release(int mode) {
        switch (mode) {
            case Lock.WRITE_LOCK:
                releaseWrite();
                break;
            default:
                releaseRead(1);
                break;
        }
    }

    public void release(int mode, int count) {
        switch (mode) {
            case Lock.WRITE_LOCK:
                releaseWrite();
                break;
            default:
                releaseRead(count);
                break;
        }
    }

    private synchronized void releaseWrite() {
        if (Thread.currentThread() == writeLockedThread) {
            //log.info( "outstandingWriteLocks= " + outstandingWriteLocks );
            if (outstandingWriteLocks > 0)
                outstandingWriteLocks--;
//            else {
//                LOG.info("extra lock release, writelocks are " + outstandingWriteLocks + "and done was called");
//            }

            if (outstandingWriteLocks > 0) {
//                LOG.debug("writeLock released for a nested writeLock request: " + outstandingWriteLocks +
//                    "; thread: " + writeLockedThread.getName());
                return;
            }

            // if another thread is waiting for a write lock, we immediately pass control to it.
            // no further checks should be required here.
            if (grantWriteLockAfterRead()) {
                writeLockedThread = (Thread) waitingForWriteLock.get(0);
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("writeLock released and before notifying a write lock waiting thread " + writeLockedThread);
//                }
                synchronized (writeLockedThread) {
                    writeLockedThread.notify();
                }
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("writeLock released and after notifying a write lock waiting thread " + writeLockedThread);
//                }
            } else {
                writeLockedThread = null;
                if (waitingForReadLock > 0) {
//                    LOG.debug("writeLock released, notified waiting readers");
                    // wake up pending read locks
                    notifyAll();
                }
//                } else {
//                    LOG.debug("writeLock released, no readers waiting");
//                }
            }
        } else {
            LOG.warn("Possible lock problem: a thread released a write lock it didn't hold. Either the " +
                "thread was interrupted or it never acquired the lock.", new Throwable());
        }
//        LOG.debug("writeLock released: " + outstandingWriteLocks +
//            "; thread: " + Thread.currentThread().getName());
    }

    /**
     * Threads call this method to relinquish a lock that they previously got
     * from this object.
     *
     * @throws IllegalStateException if called when there are no outstanding locks or there is a
     *                               write lock issued to a different thread.
     */
    private synchronized void releaseRead(int count) {
        if (!outstandingReadLocks.isEmpty() && writeLockedThread == null) {
            removeReadLock(count);
//            if (LOG.isDebugEnabled()) {
//                LOG.debug("readLock on " + hashCode() + " released " + Thread.currentThread().getName());
//                LOG.debug("remaining read locks: " + listReadLocks());
//            }
            if (writeLockedThread == null && grantWriteLockAfterRead()) {
                writeLockedThread = (Thread) waitingForWriteLock.get(0);
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("readLock released and before notifying a write lock waiting thread " + writeLockedThread);
//                    LOG.debug("remaining read locks: " + outstandingReadLocks.size());
//                }
                synchronized (writeLockedThread) {
                    writeLockedThread.notifyAll();
                }
//                if (LOG.isDebugEnabled()) {
//                    LOG.debug("readLock released and after notifying a write lock waiting thread " + writeLockedThread);
//                }
            }
            return;
        } else {
            LOG.warn("Possible lock problem: thread " + Thread.currentThread().getName() +
                    " released a read lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock. " +
                    "Write lock: " + (writeLockedThread != null ? writeLockedThread.getName() : "null"),
                    new Throwable());
            if (LockOwner.DEBUG)
                debugReadLocks("ILLEGAL RELEASE");
        }
    }

    public synchronized boolean isLockedForWrite() {
        return writeLockedThread != null || (waitingForWriteLock != null && waitingForWriteLock.size() > 0);
    }

    public synchronized boolean hasLock() {
        return !outstandingReadLocks.isEmpty() || isLockedForWrite();
    }

    public synchronized boolean isLockedForRead(Thread owner) {
        for (int i = outstandingReadLocks.size() - 1; i > -1; i--) {
            if (((LockOwner) outstandingReadLocks.get(i)).getOwner() == owner)
                return true;
        }
        return false;
    }

    private void removeReadLock(int count) {
        Object owner = Thread.currentThread();
        for (int i = outstandingReadLocks.size() - 1; i > -1 && count > 0; i--) {
            LockOwner current = (LockOwner) outstandingReadLocks.get(i);
            if (current.getOwner() == owner) {
                outstandingReadLocks.remove(i);
                --count;
            }
        }
    }

    /**
     * Check if a write lock can be granted, either because there are no
     * read locks or the read lock belongs to the current thread and can be
     * upgraded.
     *
     * @return true if the write lock can be granted
     */
    private boolean grantWriteLock() {
        final int size = outstandingReadLocks.size();
        if (size == 0)
            return true;
        Thread waiter = Thread.currentThread();
        LockOwner next;
        for (int i = 0; i < size; i++) {
            next = (LockOwner) outstandingReadLocks.get(i);
            if (next.getOwner() != waiter)
                return false;
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
        // waiting write locks?
        if (waitingForWriteLock != null && waitingForWriteLock.size() > 0) {
            // yes, check read locks
            final int size = outstandingReadLocks.size();
            if (size > 0) {
                // grant lock if all read locks are held by the write thread
                final Thread waitingWrite = (Thread) waitingForWriteLock.get(0);
                return isCompatible(waitingWrite);
            } else
                return true;
        }
        return false;
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
    private boolean isCompatible(Thread waiting) {
        LockOwner next;
        for (int i = 0; i < outstandingReadLocks.size(); i++) {
            next = (LockOwner) outstandingReadLocks.get(i);
            if (next.getOwner() != waiting && (waitingForWriteLock == null
                    || !waitingForWriteLock.contains(next.getOwner())))
                return false;
        }
        return true;
    }

    private void debugReadLocks(String msg) {
        for (int i = 0; i < outstandingReadLocks.size(); i++) {
            LockOwner owner = (LockOwner) outstandingReadLocks.get(i);
            LOG.debug(msg + ": " + owner.getOwner(), owner.getStack());
        }
    }

    private String listReadLocks() {
        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < outstandingReadLocks.size(); i++) {
            LockOwner owner = (LockOwner) outstandingReadLocks.get(i);
            buf.append(' ');
            buf.append(((Thread) owner.getOwner()).getName());
        }
        return buf.toString();
    }
}