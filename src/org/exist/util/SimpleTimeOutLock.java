package org.exist.util;

import org.apache.log4j.Category;

/**
 * Implements locking with timeout
 * 
 * Call acquire to acquire a lock followed by enter.
 * When you're done, release the lock with release.
 * By default, acquire times out after 3 minutes.
 * 
 * @author wolf
 *
 */
public class SimpleTimeOutLock implements Lock {
	
	private final static Category LOG =
		Category.getInstance(SimpleTimeOutLock.class);

	// the current key
	private Object currentKey = null;
	private int referenceCount = 0;
	private int mode;
	
	// wait for 3 minutes before timeout
	private long timeOut = 60000L;

	/**
	 * @see org.exist.util.Lock#hasKey(Object)
	 */
	public boolean hasKey(Object key) {
		return currentKey == key;
	}

	public void acquire(Object key) throws LockException {
		acquire(key, READ_LOCK);
	}
	
	/**
	 * Acquire a lock for the given key
	 * 
	 * To prevent deadlock situations, the method will throw 
	 * a LockException after 3 minutes of waiting.
	 * 
	 * @see org.exist.util.Lock#acquire(Object)
	 */
	public void acquire(Object key, int mode) throws LockException {
		synchronized (this) {
			if (currentKey == key) {
				++referenceCount;
				return;
			}
			final long start = System.currentTimeMillis();
			while (currentKey != null) {
				try {
					wait(1000);
				} catch (InterruptedException e) {
				}
				if (currentKey != null
					&& (System.currentTimeMillis() - start) > timeOut) {
					if(this.mode == READ_LOCK) {
						LOG.warn("blocking thread found: removing lock " + currentKey.hashCode());
						break;
					} else {
						LOG.warn(
							"timeout while waiting on lock "
								+ currentKey.hashCode());
					
						throw new LockException("cannot acquire lock");
					}
				}
			}
//			System.out.println(Thread.currentThread().getName() +
//				"acquired lock " + key.hashCode());
			currentKey = key;
			referenceCount = 1;
			this.mode = mode;
			//notifyAll();
		}
	}

	/**
	 * Enter a synchronized block of code with the given key.
	 * The key should have been acquired by calling acquire.
	 * 
	 * @see org.exist.util.Lock#enter(Object)
	 */
	public void enter(Object key) throws LockException {
		while (currentKey != key) {
			try {
				wait(timeOut);
			} catch (InterruptedException e) {
			}
			if (currentKey != key)
				throw new LockException();
		}
	}

	/**
	 * Release a key.
	 * 
	 * @see org.exist.util.Lock#release(Object)
	 */
	public void release(Object key) {
		synchronized (this) {
			if (currentKey == null) {
				LOG.warn("key : " + key.hashCode() + " already released.");
				return;
			}
			if (currentKey == key) {
//				System.out.println(Thread.currentThread().getName() + 
//					" released lock: " + currentKey.hashCode());
				if(--referenceCount == 0) {
					currentKey = null;
					notifyAll();
				}
			} else {
				// this should never happen
				LOG.warn(
					Thread.currentThread().getName()
						+ ": wrong key: "
						+ key.hashCode()
						+ "; current = "
						+ currentKey.hashCode());
			}
		}
	}

}
