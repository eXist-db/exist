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
import java.util.ArrayDeque;
import java.util.Deque;

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

    private static class SuspendedWaiter {
        final Thread thread;
        final LockMode lockMode;
        final int lockCount;

        public SuspendedWaiter(final Thread thread, final LockMode lockMode, final int lockCount) {
            this.thread = thread;
            this.lockMode = lockMode;
            this.lockCount = lockCount;
        }
    }

    private final static Logger LOG = LogManager.getLogger(ReentrantReadWriteLock.class);

    private final Object id_;
	private Thread owner_ = null;
    private final Deque<SuspendedWaiter> suspendedThreads = new ArrayDeque<>();

    private int holds_ = 0;
    private LockMode mode_ = LockMode.NO_LOCK;
    private final Deque<LockMode> modeStack = new ArrayDeque<>();
    private int writeLocks = 0;
    private LockListener listener = null;

    private final boolean DEBUG = false;
    private final Deque<StackTraceElement[]> seStack;

    public ReentrantReadWriteLock(final Object id) {
        this.id_ = id;
        if (DEBUG) {
            seStack = new ArrayDeque<>();
        } else {
            seStack = null;
        }
    }

    @Override
    public String getId() {
        return id_.toString();
    }

    @Override
    public boolean acquire() throws LockException {
        return acquire(LockMode.READ_LOCK);
    }

    @Override
    public boolean acquire(final LockMode mode) throws LockException {
        if (mode == LockMode.NO_LOCK) {
            LOG.warn("Acquired with LockMode.NO_LOCK!");
            return true;
        }

        if (Thread.interrupted()) {
            throw new LockException();
        }

        final Thread caller = Thread.currentThread();
        synchronized (this) {
            WaitingThread waitingOnResource;
            if (caller == owner_) {
                ++holds_;
                modeStack.push(mode);
                if (mode == LockMode.WRITE_LOCK) {
                    writeLocks++;
                }
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else if (owner_ == null) {
                owner_ = caller;
                holds_ = 1;
                modeStack.push(mode);
                if (mode== LockMode.WRITE_LOCK) {
                    writeLocks++;
                }
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
                modeStack.push(mode);
                if (mode== LockMode.WRITE_LOCK) {
                    writeLocks++;
                }
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
                            modeStack.push(mode);
                            if (mode== LockMode.WRITE_LOCK) {
                                writeLocks++;
                            }
                            mode_ = mode;
                            listener = waitingOnResource;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
                        } else if (caller == owner_) {
                            ++holds_;
                            modeStack.push(mode);
                            if (mode == LockMode.WRITE_LOCK) {
                                writeLocks++;
                            }
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
                            modeStack.push(mode);
                            if (mode == LockMode.WRITE_LOCK) {
                                writeLocks++;
                            }
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

    @Override
    public synchronized void wakeUp() {
        notifyAll();
    }

    @Override
    public boolean attempt(final LockMode mode) {
        if (mode == LockMode.NO_LOCK) {
            LOG.warn("Attempted acquire with LockMode.NO_LOCK!");
            return true;
        }

        final Thread caller = Thread.currentThread();
        synchronized (this) {
            if (caller == owner_) {
                ++holds_;
                modeStack.push(mode);
                if (mode == LockMode.WRITE_LOCK) {
                    writeLocks++;
                }
                if (DEBUG) {
                    final Throwable t = new Throwable();
                    seStack.push(t.getStackTrace());
                }
                mode_ = mode;
                return true;
            } else if (owner_ == null) {
                owner_ = caller;
                holds_ = 1;
                modeStack.push(mode);
                if (mode == LockMode.WRITE_LOCK) {
                    writeLocks++;
                }
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

    @Override
    public synchronized boolean isLockedForWrite() {
        return writeLocks > 0;
    }

    @Override
    public boolean isLockedForRead(final Thread owner) {
        // always returns false for this lock
        return false;
    }

    @Override
    public synchronized boolean hasLock() {
        return holds_ > 0;
    }

    @Override
    public boolean hasLock(final Thread owner) {
        return this.owner_ == owner;
    }

    public Thread getOwner() {
        return this.owner_;
    }

    @Override
    public synchronized void release(final LockMode mode) {
        if(mode == LockMode.NO_LOCK) {
            LOG.warn("Released with LockMode.NO_LOCK!");
            return;
        }

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
        LockMode top = modeStack.pop();
        mode_ = top;
        top = null; 	
        if (mode_ != mode) {
            LOG.warn("Released lock of different type. Expected " + mode_ +
                " got " + mode, new Throwable());
        }      	
        if (mode_ == LockMode.WRITE_LOCK) {
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
                mode_ = LockMode.NO_LOCK;
                notify();
            }
        }
        if (listener != null) {
            listener.lockReleased();
            listener = null;
        }
    }

    @Override
    public void release(final LockMode mode, final int count) {
        throw new UnsupportedOperationException(getClass().getName() +
                " does not support releasing multiple locks");
    }

    /**
     * Return the number of unreleased acquires performed
     * by the current thread.
     * Returns zero if current thread does not hold lock.
     **/
    public synchronized long holds() {
        if (Thread.currentThread() != owner_) {
            return 0;
        }
        return holds_;
    }

    @Override
    public synchronized LockInfo getLockInfo() {
        final String lockType = mode_ == LockMode.WRITE_LOCK ? LockInfo.WRITE_LOCK : LockInfo.READ_LOCK;
        return new LockInfo(LockInfo.COLLECTION_LOCK, lockType, getId(), 
            new String[] { (owner_==null)?"":owner_.getName() });
    }

    @Override
    public void debug(final PrintStream out) {
        getLockInfo().debug(out);
    }
}
