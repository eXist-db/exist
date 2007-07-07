/*
  File: ReentrantLock.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

*/

package org.exist.storage.lock;

import org.apache.log4j.Logger;
import org.exist.util.LockException;

import java.util.Stack;

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

    private class SuspendedWaiter {
        Thread thread;
        int lockMode;

        public SuspendedWaiter(Thread thread, int lockMode) {
            this.thread = thread;
            this.lockMode = lockMode;
        }
    }
    
    private final static Logger LOG = Logger.getLogger(ReentrantReadWriteLock.class);

    protected String id_ = null;
	protected Thread owner_ = null;
    protected Stack suspendedThreads = new Stack();
    
    protected long holds_ = 0;
	public int mode_ = Lock.NO_LOCK;
	private long timeOut_ = 240000L;
	private Stack modeStack = new Stack();
	private int writeLocks = 0;
	private boolean DEBUG = false;
	private Stack seStack;
    private LockListener listener = null;

    public ReentrantReadWriteLock(String id) {
        id_ = id;
        if (DEBUG)
            seStack = new Stack();
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
        synchronized (this) {
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
//                LOG.warn("DEADLOCK detected: " + owner_.getName() + " -> " + caller.getName());
                waitingOnResource.suspendWaiting();
                SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_);
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
				long waitTime = timeOut_;
				long start = System.currentTimeMillis();
                DeadlockDetection.addCollectionWaiter(caller, this);
//                LOG.warn(caller.getName() + " waiting on WRITE lock held by " + owner_.getName());
                try {
					for (;;) {
						wait(waitTime);
                        if ((waitingOnResource = DeadlockDetection.deadlockCheckResource(caller, owner_)) != null) {
//                            LOG.warn("DEADLOCK detected after wakeUp: " + owner_.getName() + " -> " + caller.getName());
                            waitingOnResource.suspendWaiting();
                            SuspendedWaiter suspended = new SuspendedWaiter(owner_, mode_);
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
						} else {
							waitTime = timeOut_ - (System.currentTimeMillis() - start);
							if (waitTime <= 0) {
								// blocking thread found: if the lock is read only, remove it
								if (writeLocks == 0) {
									System.out.println("releasing blocking thread " + owner_.getName() + " on " + id_ + " (" + modeStack.size() + " acquisitions)");
									if (DEBUG) {
										LOG.debug("Lock was acquired by :");
										while (!seStack.isEmpty()) {
											StackTraceElement[] se = (StackTraceElement[])seStack.pop();
											LOG.debug(se);
									    	se = null;
										}
									}
									owner_ = caller;
									while (!modeStack.isEmpty()) {
								    	Integer top = (Integer)modeStack.pop();
								    	top = null;
									}									
									holds_ = 1;
									modeStack.push(new Integer(mode));
									if (DEBUG) {
										Throwable t = new Throwable();
										seStack.push(t.getStackTrace());
									}
									mode_ = mode;
                                    DeadlockDetection.clearCollectionWaiter(owner_);
                                    return true;
								} else
									LOG.warn("Write lock timed out");
									if (DEBUG) {
										LOG.debug("Lock was acquired by :");
										while (!seStack.isEmpty()) {
											StackTraceElement[] se = (StackTraceElement[])seStack.pop();
											LOG.debug(se);
									    	se = null;
										}
									}
                                DeadlockDetection.clearCollectionWaiter(owner_);
                                throw new LockException("time out while acquiring a lock");
							}
						}
					}
				} catch (InterruptedException ex) {
					notify();
					throw new LockException("interrupted while waiting for lock");
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

    /* (non-Javadoc)
     * @see org.exist.util.Lock#release(int)
     */
    public synchronized void release(int mode) {
        if (Thread.currentThread() != owner_) {
            LOG.warn("Possible lock problem: thread " + Thread.currentThread() +
                    " released a lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock. The lock was owned by: "
                    + owner_);
			if (DEBUG) {
				LOG.debug("Lock was acquired by :");
				while (!seStack.isEmpty()) {
					StackTraceElement[] se = (StackTraceElement[])seStack.pop();
					LOG.debug(se);
			    	se = null;
				}
			}            
            return;
        }
        Integer top = (Integer)modeStack.pop();
    	mode_ = top.intValue();
    	top = null; 	
    	if (mode_ != mode) {
    		LOG.warn("Released lock of different type. Expected " + mode_ + " got " + mode, new Throwable());    		
    	}      	
		if (mode_ == Lock.WRITE_LOCK) {
//            if (isCollectionLock)
//                LOG.warn(owner_.getName() + " RELEASED WRITE lock", new Throwable());
            writeLocks--;
        }
        if (DEBUG) {
    		StackTraceElement[] se = (StackTraceElement[])seStack.pop();
    		se = null;
    	}
        if (!suspendedThreads.isEmpty()) {
            SuspendedWaiter suspended = (SuspendedWaiter) suspendedThreads.pop();
            owner_ = suspended.thread;
            mode_ = suspended.lockMode;
        } else if (--holds_ == 0) {
			owner_ = null;
			mode_ = Lock.NO_LOCK;
			notify();
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
			return 0;
		return holds_;
	}

    public synchronized LockInfo getLockInfo() {
        String lockType = mode_ == Lock.WRITE_LOCK ? LockInfo.WRITE_LOCK : LockInfo.READ_LOCK;
        return new LockInfo(LockInfo.COLLECTION_LOCK, lockType, new String[] { owner_.getName() });
    }
}
