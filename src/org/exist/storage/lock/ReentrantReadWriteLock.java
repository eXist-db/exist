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

import java.util.Stack;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;
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
    
    private final static Logger LOG = Logger.getLogger(ReentrantReadWriteLock.class);

    protected Object id_ = null;
	protected Thread owner_ = null;
    protected Stack suspendedThreads = new Stack();
    
    protected int holds_ = 0;
	public int mode_ = Lock.NO_LOCK;
//	private long timeOut_ = 240000L;
	private Stack modeStack = new Stack();
	private int writeLocks = 0;
	private boolean DEBUG = false;
	private Stack seStack;
    private LockListener listener = null;

    private ReentrantLock lock = DeadlockDetection.getLock();
    private Condition monitor = lock.newCondition();
    
    public ReentrantReadWriteLock(Object id) {
        id_ = id;
        if (DEBUG)
            seStack = new Stack();
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
			throw new LockException();
		Thread caller = Thread.currentThread();
		lock.lock();
        try {
            WaitingThread waitingOnResource;
            if (caller == owner_) {
				++holds_;
				modeStack.push(new Integer(mode));
				if (mode == Lock.WRITE_LOCK)
					writeLocks++;
				if (DEBUG) {
					Throwable t = new Throwable();
					seStack.push(t.getStackTrace());
				}
				mode_ = mode;
				return true;
			} else if (owner_ == null) {
				owner_ = caller;
				holds_ = 1;
				modeStack.push(new Integer(mode));
				if (mode== Lock.WRITE_LOCK)
					writeLocks++;
				if (DEBUG) {
					Throwable t = new Throwable();
					seStack.push(t.getStackTrace());
				}				
				mode_ = mode;
				return true;
            } else if ((waitingOnResource = DeadlockDetection.deadlockCheckResource(caller, owner_)) != null) {
//                LOG.warn("DEADLOCK detected on " + getId() + ": " + owner_.getName() + " -> " + caller.getName());
                waitingOnResource.suspendWaiting();
                SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_, holds_);
                suspendedThreads.push(suspended);
                owner_ = caller;
                holds_ = 1;
                modeStack.push(new Integer(mode));
				if (mode== Lock.WRITE_LOCK)
					writeLocks++;
                mode_ = mode;
                listener = waitingOnResource;
                return true;
            } else {
				long start = System.currentTimeMillis();
                DeadlockDetection.addCollectionWaiter(caller, this);
//                LOG.warn(caller.getName() + " waiting on lock held by " + owner_.getName());
                try {
					for (;;) {
						monitor.await(WAIT_CHECK_PERIOD, TimeUnit.MILLISECONDS);
                        if ((waitingOnResource = DeadlockDetection.deadlockCheckResource(caller, owner_)) != null) {
//                            LOG.warn("DEADLOCK detected after wakeUp: " + owner_.getName() + " -> " + caller.getName());
                            waitingOnResource.suspendWaiting();
                            SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_, holds_);
                            suspendedThreads.push(suspended);
                            owner_ = caller;
                            holds_ = 1;
                            modeStack.push(new Integer(mode));
                            if (mode== Lock.WRITE_LOCK)
                                writeLocks++;
                            mode_ = mode;
                            listener = waitingOnResource;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
                        } else if (caller == owner_) {
							++holds_;
							modeStack.push(new Integer(mode));
							if (mode == Lock.WRITE_LOCK)
								writeLocks++;
							if (DEBUG) {
								Throwable t = new Throwable();
								seStack.push(t.getStackTrace());
							}
							mode_ = mode;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
						} else if (owner_ == null) {
							owner_ = caller;
							holds_ = 1;
							modeStack.push(new Integer(mode));
							if (mode == Lock.WRITE_LOCK)
								writeLocks++;
							if (DEBUG) {
								Throwable t = new Throwable();
								seStack.push(t.getStackTrace());
							}
							mode_ = mode;
                            DeadlockDetection.clearCollectionWaiter(owner_);
                            return true;
						} // else {
//							long waitTime = timeOut_ - (System.currentTimeMillis() - start);
//							if (waitTime <= 0) {
//								// blocking thread found: if the lock is read only, remove it
//								if (writeLocks == 0) {
//						our			System.out.println("releasing blocking thread " + owner_.getName() + " on " + id_ + " (" + modeStack.size() + " acquisitions)");
//									if (DEBUG) {
//										LOG.debug("Lock was acquired by :");
//										while (!seStack.isEmpty()) {
//											StackTraceElement[] se = (StackTraceElement[])seStack.pop();
//											LOG.debug(se);
//									    	se = null;
//										}
//									}
//									owner_ = caller;
//									while (!modeStack.isEmpty()) {
//								    	Integer top = (Integer)modeStack.pop();
//								    	top = null;
//									}
//									holds_ = 1;
//									modeStack.push(new Integer(mode));
//									if (DEBUG) {
//										Throwable t = new Throwable();
//										seStack.push(t.getStackTrace());
//									}
//									mode_ = mode;
//                                    DeadlockDetection.clearCollectionWaiter(owner_);
//                                    return true;
//								} else
//									LOG.warn("Write lock timed out");
//									if (DEBUG) {
//										LOG.debug("Lock was acquired by :");
//										while (!seStack.isEmpty()) {
//											StackTraceElement[] se = (StackTraceElement[])seStack.pop();
//											LOG.debug(se);
//									    	se = null;
//										}
//									}
//                                DeadlockDetection.clearCollectionWaiter(owner_);
//                                throw new LockException("time out while acquiring a lock");
//							}
//						}
					}
				} catch (InterruptedException ex) {
					monitor.signal();
					throw new LockException("interrupted while waiting for lock");
				}
			}
		} finally {
			lock.unlock();
		}
	}

    public void wakeUp() {
    	lock.lock();
        try {
			monitor.signal();
		} finally {
			lock.unlock();
		}
    }
    
    public boolean attempt(int mode) {
		Thread caller = Thread.currentThread();
		lock.lock();
		try {
			if (caller == owner_) {
				++holds_;
				modeStack.push(new Integer(mode));
				if (mode == Lock.WRITE_LOCK)
					writeLocks++;
				if (DEBUG) {
					Throwable t = new Throwable();
					seStack.push(t.getStackTrace());
				}				
				mode_ = mode;
				return true;
			} else if (owner_ == null) {
				owner_ = caller;
				holds_ = 1;
				modeStack.push(new Integer(mode));
				if (mode == Lock.WRITE_LOCK)
					writeLocks++;
				if (DEBUG) {
					Throwable t = new Throwable();
					seStack.push(t.getStackTrace());
				}				
				mode_ = mode;
				return true;
			} else {
				return false;
			}
		} finally {
			lock.unlock();
		}
	}

    /* (non-Javadoc)
     * @see org.exist.util.Lock#isLockedForWrite()
     */
    public boolean isLockedForWrite() {
    	lock.lock();
    	try {
			return writeLocks > 0;
		} finally {
			lock.unlock();
		}
    }

    public boolean isLockedForRead(Thread owner) {
        // always returns false for this lock
        return false;
    }

    public boolean hasLock() {
    	lock.lock();
    	try {
			return holds_ > 0;
		} finally {
			lock.unlock();
		}
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
    public void release(int mode) {
    	lock.lock();
        try {
			if (Thread.currentThread() != owner_) {
				LOG.warn("Possible lock problem: thread "
						+ Thread.currentThread()
						+ " released a lock on "
						+ getId()
						+ " it didn't hold. Either the "
						+ "thread was interrupted or it never acquired the lock. The lock was owned by: "
						+ owner_);
				if (DEBUG) {
					LOG.debug("Lock was acquired by :");
					while (!seStack.isEmpty()) {
						StackTraceElement[] se = (StackTraceElement[]) seStack
								.pop();
						LOG.debug(se);
						se = null;
					}
				}
				return;
			}
			Integer top = (Integer) modeStack.pop();
			mode_ = top.intValue();
			top = null;
			if (mode_ != mode) {
				LOG.warn("Released lock of different type. Expected " + mode_
						+ " got " + mode, new Throwable());
			}
			if (mode_ == Lock.WRITE_LOCK) {
				//            if (isCollectionLock)
				//                LOG.warn(owner_.getName() + " RELEASED WRITE lock", new Throwable());
				writeLocks--;
			}
			if (DEBUG) {
				StackTraceElement[] se = (StackTraceElement[]) seStack.pop();
				se = null;
			}
			//        LOG.debug("Lock " + getId() + " released by " + owner_.getName());
			if (--holds_ == 0) {
				if (!suspendedThreads.isEmpty()) {
					SuspendedWaiter suspended = (SuspendedWaiter) suspendedThreads
							.pop();
					owner_ = suspended.thread;
					mode_ = suspended.lockMode;
					holds_ = suspended.lockCount;
				} else {
					owner_ = null;
					mode_ = Lock.NO_LOCK;
					monitor.signal();
				}
			}
			if (listener != null) {
				listener.lockReleased();
				listener = null;
			}
		} finally {
			lock.unlock();
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
	public long holds() {
		lock.lock();
		try {
			if (Thread.currentThread() != owner_)
				return 0;
			return holds_;
		} finally {
			lock.unlock();
		}
	}

    public LockInfo getLockInfo() {
    	lock.lock();
        try {
			String lockType = mode_ == Lock.WRITE_LOCK ? LockInfo.WRITE_LOCK
					: LockInfo.READ_LOCK;
			return new LockInfo(LockInfo.COLLECTION_LOCK, lockType, getId(),
					new String[] { owner_.getName() });
		} finally {
			lock.unlock();
		}
    }
}
