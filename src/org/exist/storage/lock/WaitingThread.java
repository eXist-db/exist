package org.exist.storage.lock;

import org.exist.util.LockException;
import org.apache.log4j.Logger;

/**
 * Wraps around a thread in order to be able to suspend it completely while it is waiting
 * for a lock.
 */
public class WaitingThread implements LockListener {

    private final static Logger LOG = Logger.getLogger(WaitingThread.class);
    
    private Object monitor;
    private MultiReadReentrantLock lock;

    private Thread thread;

    private boolean suspended = false;

    public WaitingThread(Thread thread, Object monitor, MultiReadReentrantLock lock) {
        this.monitor = monitor;
        this.lock = lock;
        this.thread = thread;
    }

    /**
     * Start waiting on the monitor object. Continue waiting if the thread wakes up
     * and suspended is set to true. Only stop waiting if suspended is false.
     *
     * @throws LockException
     */
    public void doWait() throws LockException {
        do {
            synchronized (monitor) {
                try {
                    monitor.wait();
                } catch (InterruptedException e) {
                    throw new LockException("Interrupted while waiting for read lock");
                }
            }
        } while (suspended);
    }

    /**
     * Put the thread into suspended mode, i.e. keep it asleep even if
     * a notify causes it to wake up temporarily.
     */
    public void suspendWaiting() {
        suspended = true;
    }

    /**
     * Wake the thread from suspended mode.
     */
    public void lockReleased() {
//        LOG.debug("Reactivate suspended lock: " + thread.getName());
        suspended = false;
        synchronized (monitor) {
            monitor.notify();
        }
    }

    public boolean isSuspended() {
        return suspended;
    }

    public Thread getThread() {
        return thread;
    }

    public Lock getLock() {
        return lock;
    }
}
