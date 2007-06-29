package org.exist.storage.lock;

/**
 * Notify a listener that a lock has been released.
 */
public interface LockListener {

    void lockReleased();
}