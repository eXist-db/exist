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
 *
 * File: ReentrantLock.java
 *
 * Originally written by Doug Lea and released into the public domain.
 * This may be used for any purposes whatsoever without acknowledgment.
 * Thanks for the assistance and support of Sun Microsystems Labs,
 * and everyone contributing, testing, and using this code.
 *
 * $Id$
 *
*/
package org.exist.storage.lock;

import java.io.PrintStream;
import java.util.Stack;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.LockException;

/**
 * A lock with the same semantics as builtin
 * Java synchronized locks: Once a thread has a lock, it
 * can re-obtain it any number of times without blocking.
 * The lock is made available to other threads when
 * as many releases as acquires have occurred.
 * 
 * The lock has a timeout: a read lock will be released if the
 * timeout is reached.
*/

public class ReentrantReadWriteLock implements Lock {

    private static final int WAIT_CHECK_PERIOD = 200;

    private class SuspendedWaiter {
        Thread thread;
        int lockMode;
        int lockCount;

        public SuspendedWaiter(Thread thread, int lockMode, int lockCount) {
            this.thread = thread;
            this.lockMode = lockMode;
            this.lockCount = lockCount;
        }
    }

    private final static Logger LOG = LogManager.getLogger(ReentrantReadWriteLock.class);

    protected Object id_ = null;
	protected Thread owner_ = null;
    protected Stack<SuspendedWaiter> suspendedThreads = new Stack<SuspendedWaiter>();

    protected int holds_ = 0;
    public int mode_ = Lock.NO_LOCK;
    private Stack<Integer> modeStack = new Stack<Integer>();
    private int writeLocks = 0;
    private boolean DEBUG = false;
    private Stack<StackTraceElement[]> seStack;
    private LockListener listener = null;

    public ReentrantReadWriteLock(Object id) {
        id_ = id;
        if (DEBUG)
            {seStack = new Stack<StackTraceElement[]>();}
    }

    public String getId() {
        return id_.toString();
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
        if (Thread.interrupted())
            {throw new LockException();}
        Thread caller = Thread.currentThread();
        synchronized (this) {
            WaitingThread waitingOnResource;
            if (caller == owner_) {
                ++holds_;
                modeStack.push(Integer.valueOf(mode));
                if (mode == Lock.WRITE_LOCK)
                    {writeLocks++;}
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else if (owner_ == null) {
                owner_ = caller;
                holds_ = 1;
                modeStack.push(Integer.valueOf(mode));
                if (mode== Lock.WRITE_LOCK)
                    {writeLocks++;}
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else if ((waitingOnResource = 
                    DeadlockDetection.deadlockCheckResource(caller, owner_)) != null) {
                waitingOnResource.suspendWaiting();
                final SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_, holds_);
                suspendedThreads.push(suspended);
                owner_ = caller;
                holds_ = 1;
                modeStack.push(Integer.valueOf(mode));
                if (mode== Lock.WRITE_LOCK)
                    {writeLocks++;}
                mode_ = mode;
                listener = waitingOnResource;
                return true;
            } else {
                DeadlockDetection.addCollectionWaiter(caller, this);
                try {
                    for (;;) {
                        wait(WAIT_CHECK_PERIOD);
                        if ((waitingOnResource = DeadlockDetection.deadlockCheckResource(caller, owner_)) != null) {
                            waitingOnResource.suspendWaiting();
                            final SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_, holds_);
                            suspendedThreads.push(suspended);
                            owner_ = caller;
                            holds_ = 1;
                            modeStack.push(Integer.valueOf(mode));
                            if (mode== Lock.WRITE_LOCK)
                                {writeLocks++;}
                            mode_ = mode;
                            listener = waitingOnResource;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
                        } else if (caller == owner_) {
                            ++holds_;
                            modeStack.push(Integer.valueOf(mode));
                            if (mode == Lock.WRITE_LOCK)
                                {writeLocks++;}
                            if (DEBUG) {
                                final Throwable t = new Throwable();
                                seStack.push(t.getStackTrace());
                            }
                            mode_ = mode;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
                        } else if (owner_ == null) {
                            owner_ = caller;
                            holds_ = 1;
                            modeStack.push(Integer.valueOf(mode));
                            if (mode == Lock.WRITE_LOCK)
                                {writeLocks++;}
                            if (DEBUG) {
                                final Throwable t = new Throwable();
                                seStack.push(t.getStackTrace());
                            }
                            mode_ = mode;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
                        }
                    }
                } catch (final InterruptedException ex) {
                    notify();
                    throw new LockException("Interrupted while waiting for lock");
                }
            }
        }
    }

    public synchronized void wakeUp() {
        notify();
    }

    public boolean attempt(int mode) {
        Thread caller = Thread.currentThread();
        synchronized (this) {
            if (caller == owner_) {
                ++holds_;
                modeStack.push(Integer.valueOf(mode));
                if (mode == Lock.WRITE_LOCK)
                    {writeLocks++;}
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else if (owner_ == null) {
                owner_ = caller;
                holds_ = 1;
                modeStack.push(Integer.valueOf(mode));
                if (mode == Lock.WRITE_LOCK)
                    {writeLocks++;}
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else {
                return false;
            }
        }
    }

    /* (non-Javadoc)
     * @see org.exist.util.Lock#isLockedForWrite()
     */
    public synchronized boolean isLockedForWrite() {
        return writeLocks > 0;
    }

    public boolean isLockedForRead(Thread owner) {
        // always returns false for this lock
        return false;
    }

    public synchronized boolean hasLock() {
        return holds_ > 0;
    }

    public boolean hasLock(Thread owner) {
        return this.owner_ == owner;
    }

    public Thread getOwner() {
        return this.owner_;
    }

    /* (non-Javadoc)
     * @see org.exist.util.Lock#release(int)
     */
    public synchronized void release(int mode) {
        if (Thread.currentThread() != owner_) {
            
            if(LOG.isDebugEnabled()){
                LOG.warn("Possible lock problem: thread " + Thread.currentThread() +
                    " Released a lock on " + getId() + " it didn't hold." +
                    " Either the thread was interrupted or it never acquired the lock." +
                    " The lock was owned by: " + owner_);
            }
                   
            if (DEBUG) {
                LOG.debug("Lock was acquired by :");
                while (!seStack.isEmpty()) {
                    StackTraceElement[] se = seStack.pop();
                    LOG.debug(se);
                    se = null;
                }
            }
            return;
        }
        Integer top = modeStack.pop();
        mode_ = top.intValue();
        top = null; 	
        if (mode_ != mode) {
            LOG.warn("Released lock of different type. Expected " + mode_ +
                " got " + mode, new Throwable());
        }      	
        if (mode_ == Lock.WRITE_LOCK) {
            writeLocks--;
        }
        if (DEBUG) {
            seStack.pop();
        }
        if (--holds_ == 0) {
            if (!suspendedThreads.isEmpty()) {
                final SuspendedWaiter suspended = suspendedThreads.pop();
                owner_ = suspended.thread;
                mode_ = suspended.lockMode;
                holds_ = suspended.lockCount;
            } else {
                owner_ = null;
                mode_ = Lock.NO_LOCK;
                notify();
            }
        }
        if (listener != null) {
            listener.lockReleased();
            listener = null;
        }
    }

    public void release(int mode, int count) {
        throw new UnsupportedOperationException(getClass().getName() +
                " does not support releasing multiple locks");
    }

    /**
     * Return the number of unreleased acquires performed
     * by the current thread.
     * Returns zero if current thread does not hold lock.
     **/
    public synchronized long holds() {
        if (Thread.currentThread() != owner_)
            {return 0;}
        return holds_;
    }

    public synchronized LockInfo getLockInfo() {
        final String lockType = mode_ == Lock.WRITE_LOCK ? LockInfo.WRITE_LOCK : LockInfo.READ_LOCK;
        return new LockInfo(LockInfo.COLLECTION_LOCK, lockType, getId(), 
            new String[] { (owner_==null)?"":owner_.getName() });
    }

    @Override
    public void debug(PrintStream out) {
        getLockInfo().debug(out);
    }
}
