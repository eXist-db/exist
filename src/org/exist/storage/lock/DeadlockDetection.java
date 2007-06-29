package org.exist.storage.lock;

import java.util.Map;
import java.util.Collections;
import java.util.HashMap;

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

    private final static Map waitForResource = Collections.synchronizedMap(new HashMap());
    private final static Map waitForCollection = Collections.synchronizedMap(new HashMap());

    /**
     * Register a thread as waiting for a resource lock.
     *
     * @param thread the thread
     * @param waiter the WaitingThread object which wraps around the thread
     */
    public static void addResourceWaiter(Thread thread, WaitingThread waiter) {
        waitForResource.put(thread, waiter);
    }

    /**
     * Deregister a waiting thread.
     *  
     * @param thread
     * @return
     */
    public static Lock clearResourceWaiter(Thread thread) {
        WaitingThread waiter = (WaitingThread) waitForResource.remove(thread);
        if (waiter != null)
            return waiter.getLock();
        return null;
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
     * @return
     */
    public static WaitingThread deadlockCheckResource(Thread threadA, Thread threadB) {
        // check if threadB is waiting for a resource lock
        WaitingThread waitingThread = (WaitingThread) waitForResource.get(threadB);
        // if lock != null, check if thread B waits for a resource lock currently held by thread A
        if (waitingThread != null) {
            return waitingThread.getLock().hasLock(threadA) ? waitingThread : null;
        }
        return null;
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
        // check if threadB is waiting for a resource lock
        WaitingThread waitingThread = (WaitingThread) waitForResource.get(threadB);
        // if lock != null, check if thread B waits for a resource lock currently held by thread A
        if (waitingThread != null) {
            return waitingThread.getLock().hasLock(threadA);
        }
        return false;
    }

    /**
     * Register a thread as waiting for a resource lock.
     *
     * @param waiter the thread
     * @param lock the lock object
     */
    public static void addCollectionWaiter(Thread waiter, Lock lock) {
        waitForCollection.put(waiter, lock);
    }

    public static Lock clearCollectionWaiter(Thread waiter) {
        return (Lock) waitForCollection.remove(waiter);
    }

    public static Lock isWaitingFor(Thread waiter) {
        return (Lock) waitForCollection.get(waiter);
    }
}
