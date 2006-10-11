/*
  File: ReentrantLock.java

  Originally written by Doug Lea and released into the public domain.
  This may be used for any purposes whatsoever without acknowledgment.
  Thanks for the assistance and support of Sun Microsystems Labs,
  and everyone contributing, testing, and using this code.

*/

package org.exist.storage.lock;

import org.exist.util.LockException;
import org.apache.log4j.Logger;

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

    private final static Logger log = Logger.getLogger(ReentrantReadWriteLock.class);

    protected String id_ = null;
	protected Thread owner_ = null;
	protected long holds_ = 0;
	protected int mode_ = Lock.READ_LOCK;
	private long timeOut_ = 240000L;

	public ReentrantReadWriteLock(String id) {
		id_ = id;
	}
	
	public boolean acquire() throws LockException {
		return acquire(Lock.READ_LOCK);
	}

	public boolean acquire(int mode) throws LockException {
		if (Thread.interrupted())
			throw new LockException();
		Thread caller = Thread.currentThread();
		synchronized (this) {
			if (caller == owner_) {
				++holds_;
				mode_ = mode;
//				System.out.println("thread " + caller.getName() + " acquired lock on " + id_ +
//					"; locks held = " + holds_);
				return true;
			} else if (owner_ == null) {
				owner_ = caller;
				holds_ = 1;
				mode_ = mode;
//				System.out.println("thread " + caller.getName() + " acquired lock on " + id_);
				return true;
			} else {
				long waitTime = timeOut_;
				long start = System.currentTimeMillis();
				try {
					for (;;) {
						wait(waitTime);
						if (caller == owner_) {
							++holds_;
							mode_ = mode;
//							System.out.println("thread " + caller.getName() + " acquired lock on " + id_ +
//								"; locks held = " + holds_);
							return true;
						} else if (owner_ == null) {
							owner_ = caller;
							holds_ = 1;
							mode_ = mode;
//							System.out.println("thread " + caller.getName() + " acquired lock on " + id_ +
//								"; locks held = " + holds_);
							return true;
						} else {
							waitTime = timeOut_ - (System.currentTimeMillis() - start);
							if (waitTime <= 0) {
								// blocking thread found: if the lock is read only, remove it
								if (mode_ == Lock.READ_LOCK) {
									System.out.println("releasing blocking thread " + owner_.getName());
									owner_ = caller;
									holds_ = 1;
									mode_ = mode;
//									System.out.println("thread " + caller.getName() + " acquired lock on " + id_ +
//										"; locks held = " + holds_);
									return true;
								} else
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
				mode_ = mode;
//				System.out.println("thread " + caller.getName() + " acquired lock on " + id_ +
//					"; locks held = " + holds_);
				return true;
			} else if (owner_ == null) {
				owner_ = caller;
				holds_ = 1;
				mode_ = mode;
//				System.out.println("thread " + caller.getName() + " acquired lock on " + id_);
				return true;
			} else {
				return false;
			}
		}
	}
	
    /* (non-Javadoc)
     * @see org.exist.util.Lock#isLockedForWrite()
     */
    public boolean isLockedForWrite() {
        return holds_ > 0 && mode_ == Lock.WRITE_LOCK;
    }
    
    /* (non-Javadoc)
     * @see org.exist.util.Lock#release(int)
     */
    public void release(int mode) {
        release();
    }
    
	/**
	 * Release the lock.
	 * @exception Error thrown if not current owner of lock
	 **/
	public synchronized void release() {
        if (Thread.currentThread() != owner_) {
            log.warn("Possible lock problem: thread " + Thread.currentThread() +
                    " released a lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock. The lock was owned by: "
                    + owner_);
            return;
        }
		if (--holds_ == 0) {
//			System.out.println("thread " + owner_.getName() + " released lock on " + id_ +
//				"; locks held = " + holds_);
			owner_ = null;
			mode_ = Lock.READ_LOCK;
			notify();
		}
	}

	/** 
	 * Release the lock N times. <code>release(n)</code> is
	 * equivalent in effect to:
	 * <pre>
	 *   for (int i = 0; i < n; ++i) release();
	 * </pre>
	 * <p>
	 * @exception Error thrown if not current owner of lock
	 * or has fewer than N holds on the lock
	 **/
	public synchronized void release(long n) {
		if (Thread.currentThread() != owner_ || n > holds_)
            log.warn("Possible lock problem: thread " + Thread.currentThread() +
                    " released a lock it didn't hold. Either the " +
                    "thread was interrupted or it never acquired the lock. The lock was owned by: "
                    + owner_);

		holds_ -= n;
		if (holds_ == 0) {
			owner_ = null;
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
