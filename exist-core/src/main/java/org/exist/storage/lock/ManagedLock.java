/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.lock;

import org.exist.util.LockException;

import java.util.concurrent.locks.ReentrantLock;

/**
 * Provides a simple wrapper around a Lock
 * so that it may be used in a try-with-resources
 * statement
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ManagedLock<T> implements AutoCloseable {
    protected final T lock;
    private final Runnable closer;
    protected volatile boolean closed = false;

    ManagedLock(final T lock, final Runnable closer) {
        this.lock = lock;
        this.closer = closer;
    }

    /**
     * Acquires and manages a lock with a specific mode
     *
     * @param lock The lock to call {@link Lock#acquire(Lock.LockMode)} on
     * @param mode the mode of the lock
     *
     * @return A managed lock which will be released with {@link #close()}
     *
     * @throws LockException if a lock error occurs
     */
    public static ManagedLock<Lock> acquire(final Lock lock, final Lock.LockMode mode) throws LockException {
        if(!lock.acquire(mode)) {
            throw new LockException("Unable to acquire lock");
        }
        return new ManagedLock<>(lock, () -> lock.release(mode));
    }

    /**
     * Acquires and manages a lock with a specific mode
     *
     * @param lock The lock to call {@link Lock#acquire(Lock.LockMode)} on
     * @param mode the mode of the lock
     * @param type the type of the lock
     *
     * @return A managed lock which will be released with {@link #close()}
     * @throws LockException if a lock error occurs
     */
    public static ManagedLock<Lock> acquire(final Lock lock, final Lock.LockMode mode, final Lock.LockType type) throws LockException {
        if(!lock.acquire(mode)) {
            throw new LockException("Unable to acquire lock: " + type);
        }
        return new ManagedLock<>(lock, () -> lock.release(mode));
    }

    /**
     * Attempts to acquire and manage a lock with a specific mode
     *
     * @param lock The lock to call {@link Lock#attempt(Lock.LockMode)} on
     * @param mode the mode of the lock
     *
     * @return A managed lock which will be released with {@link #close()}
     * @throws LockException if a lock error occurs
     */
    public static ManagedLock<Lock> attempt(final Lock lock, final Lock.LockMode mode) throws LockException {
        if(!lock.attempt(mode)) {
            throw new LockException("Unable to attempt to acquire lock");
        }
        return new ManagedLock<>(lock, () -> lock.release(mode));
    }

    /**
     * Acquires and manages a lock with a specific mode
     *
     * @param lock The lock to call {@link java.util.concurrent.locks.Lock#lock()} on
     * @param mode the mode of the lock
     *
     * @return A managed lock which will be released with {@link #close()}
     */
    public static ManagedLock<java.util.concurrent.locks.ReadWriteLock> acquire(final java.util.concurrent.locks.ReadWriteLock lock, final Lock.LockMode mode) {
        final java.util.concurrent.locks.Lock modeLock;
        switch(mode) {
            case READ_LOCK:
                modeLock = lock.readLock();
                break;

            case WRITE_LOCK:
                modeLock = lock.writeLock();
                break;

            default:
                throw new IllegalArgumentException();
        }

        modeLock.lock();
        return new ManagedLock<>(lock, modeLock::unlock);
    }

    /**
     * Attempts to acquire and manage a lock with a specific mode
     *
     * @param lock The lock to call {@link java.util.concurrent.locks.Lock#tryLock()} on
     * @param mode the mode of the lock
     *
     * @return A managed lock which will be released with {@link #close()}
     * @throws LockException if a lock error occurs
     */
    public static ManagedLock<java.util.concurrent.locks.ReadWriteLock> attempt(final java.util.concurrent.locks.ReadWriteLock lock, final Lock.LockMode mode) throws LockException {
        final java.util.concurrent.locks.Lock modeLock;
        switch(mode) {
            case READ_LOCK:
                modeLock = lock.readLock();
                break;

            case WRITE_LOCK:
                modeLock = lock.writeLock();
                break;

            default:
                throw new IllegalArgumentException();
        }

        if(!modeLock.tryLock()) {
            throw new LockException("Unable to attempt to acquire lock");
        }
        return new ManagedLock<>(lock, modeLock::unlock);
    }

    /**
     * Acquires and manages a lock
     *
     * @param lock The lock to call {@link java.util.concurrent.locks.Lock#lock()} on
     *
     * @return A managed lock which will be released with {@link #close()}
     */
    public static ManagedLock<ReentrantLock> acquire(final ReentrantLock lock) {
        lock.lock();
        return new ManagedLock<>(lock, lock::unlock);
    }

    /**
     * Attempts to acquire and manage a lock
     *
     * @param lock The lock to call {@link java.util.concurrent.locks.Lock#tryLock()} on
     *
     * @return A managed lock which will be released with {@link #close()}
     * @throws LockException if a lock error occurs
     */
    public static ManagedLock<ReentrantLock> attempt(final ReentrantLock lock) throws LockException {
        if(!lock.tryLock()) {
            throw new LockException("Unable to attempt to acquire lock");
        }
        return new ManagedLock<>(lock, lock::unlock);
    }

    /**
     * Determines if the lock has already been released
     *
     * @return true if the lock has already been released
     */
    boolean isReleased() {
        return closed;
    }

    /**
     * Releases the lock
     */
    @Override
    public void close() {
        if(!closed) {
            closer.run();
        }
        this.closed = true;
    }
}
