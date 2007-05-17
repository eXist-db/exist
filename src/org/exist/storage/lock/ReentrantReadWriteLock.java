/*
  File: ReentrantLock.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

*/

package org.exist.storage.lock;

import java.util.Stack;

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

    private final static Logger LOG = Logger.getLogger(ReentrantReadWriteLock.class);

    protected String id_ = null;
	protected Thread owner_ = null;
	protected long holds_ = 0;
	public int mode_ = Lock.NO_LOCK;
	private long timeOut_ = 240000L;
	private Stack modeStack = new Stack();
	private int writeLocks = 0;
	private boolean DEBUG = false;
	private Stack seStack;

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
			} else {
				long waitTime = timeOut_;
				long start = System.currentTimeMillis();
				try {
					for (;;) {
						wait(waitTime);
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

    public boolean isLockedForRead(Object owner) {
        // always returns false for this lock
        return false;
    }

    public synchronized boolean hasLock() {
    	return holds_ > 0;
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
    		LOG.warn("Released lock of different type. Expected " + mode_ + " got " + mode);
    		Thread.dumpStack();    		
    	}      	
		if (mode_ == Lock.WRITE_LOCK)
			writeLocks--;   	
    	if (DEBUG) {
    		StackTraceElement[] se = (StackTraceElement[])seStack.pop();
    		se = null;
    	} 
    	if (--holds_ == 0) {
			owner_ = null;
			mode_ = Lock.NO_LOCK;
			notify();
		}
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

}
