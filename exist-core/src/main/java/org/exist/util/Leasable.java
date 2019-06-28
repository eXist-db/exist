/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util;

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;

import javax.annotation.Nullable;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;

/**
 * A container for an Object upon which
 * leases may be taken and returned.
 *
 * The container keeps a reference count of
 * the number of active leases.
 *
 * If a `closer` is provided then when the
 * number of active leases returns to zero,
 * the closer is invoked. Once the closer is
 * invoked, all subsequent leases of the object
 * are invalid.
 *
 * This is useful for when you have an object which
 * makes use of resources that have to be freed when
 * you are finished with the object, but you want to be
 * able to freely share the object around.
 *
 * @param <T> The type of the leasable object.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class Leasable<T> {
    private final T object;
    @Nullable private final Consumer<T> closer;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    @GuardedBy("lock") private int leases;
    @GuardedBy("lock") private boolean closed;

    public Leasable(final T object) {
        this(object, null);
    }

    public Leasable(final T object, @Nullable final Consumer<T> closer) {
        this.object = object;
        this.closer = closer;
    }

    /**
     * Creates a {@code Leasable<U>} from an {@code U extends AutoCloseable}.
     *
     * The {@link AutoCloseable#close()} method will be involed as the `closer`
     * and exception thrown by {@link AutoCloseable#close()} will be promoted
     * to an {@link IllegalLeasableState} exception.
     *
     *
     * @param <U> the type of the auto-closeable object.
     *
     * @param object The object to setup for leasing.
     *
     * @return The leasable object.
     */
    public static <U extends AutoCloseable> Leasable<U> fromCloseable(final U object) {
        return new Leasable<>(object, _object -> {
            try {
                _object.close();
            } catch (final Exception e) {
                throw new IllegalLeasableState(e);
            }
        });
    }

    /**
     * Take a lease on the object.
     *
     * This will increment the reference
     * count of the object by 1.
     *
     * Note, callers are expected to call
     * {@link Lease#close()} when they are
     * finished with the lease.
     *
     * @return a lease of the object.
     */
    public Lease lease() {
        lock.writeLock().lock();
        try {
            if (closed) {
                throw new IllegalLeasableState("Object is closed");
            }

            final Lease lease = new Lease();
            leases++;
            return lease;
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Determines if the object is leased.
     *
     * @return true if the object is leased.
     */
    public boolean isLeased() {
        lock.readLock().lock();
        try {
            if (closed) {
                return false;
            }

            return leases > 0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Represents a lease of the leasable object.
     */
    public class Lease implements AutoCloseable {

        /**
         * Access to the leased object.
         *
         * @return the leased object.
         */
        public T get() {
            lock.readLock().lock();
            try {
                if (closed) {
                    throw new IllegalLeasableState("Object is closed");
                }

                if(leases == 0) {
                    throw new IllegalLeasableState("Lease was returned");
                }

                return object;
            } finally {
                lock.readLock().unlock();
            }
        }

        /**
         * Releases the lease on the leasable object.
         */
        @Override
        public void close() {
            lock.writeLock().lock();
            try {
                if (closed) {
                    throw new IllegalLeasableState("Object is closed");
                }

                if(leases == 0) {
                    throw new IllegalLeasableState("Lease was already returned");
                }

                leases--;

                if (leases == 0 && closer != null) {
                    closer.accept(object);
                    closed = true;
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }

    /**
     * Represents an illegal state of the leasable/leased object.
     */
    public static class IllegalLeasableState extends IllegalStateException {
        public IllegalLeasableState(final String message) {
            super(message);
        }

        public IllegalLeasableState(final Throwable cause) {
            super(cause);
        }
    }
}