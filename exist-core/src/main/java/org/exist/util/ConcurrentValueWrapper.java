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

import com.evolvedbinary.j8fu.function.Consumer2E;
import com.evolvedbinary.j8fu.function.ConsumerE;
import net.jcip.annotations.ThreadSafe;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedLock;

import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A wrapper which allows read or modify operations
 * in a concurrent and thread-safe manner
 * to an underlying value.
 *
 * @param <T> The type of the underlying value
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
@ThreadSafe
public class ConcurrentValueWrapper<T> {
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final T value;

    protected ConcurrentValueWrapper(final T value) {
        this.value = value;
    }

    /**
     * Read from the value.
     *
     * @param <U> the return type.
     *
     * @param readFn A function which reads the value
     *     and returns a result.
     *
     * @return the result of the {@code readFn}.
     */
    public <U> U read(final Function<T, U> readFn) {
        try (final ManagedLock<ReadWriteLock> readLock = ManagedLock.acquire(lock, LockMode.READ_LOCK)) {
            return readFn.apply(value);
        }
    }

    /**
     * Write to the value.
     *
     * @param writeFn A function which writes to the value.
     */
    public void write(final Consumer<T> writeFn) {
        try (final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            writeFn.accept(value);
        }
    }

    /**
     * Write to the value and return a result.
     *
     * @param <U> the return type.
     *
     * @param writeFn A function which writes to the value
     *     and returns a result.
     *
     * @return the result of the write function.
     */
    public <U> U writeAndReturn(final Function<T, U> writeFn) {
        try (final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            return writeFn.apply(value);
        }
    }

    /**
     * Write to the value.
     *
     * @param writeFn A function which writes to the value.
     *
     * @param <E> An exception which may be thrown by the {@code writeFn}.
     *
     * @throws E if an exception is thrown by the {@code writeFn}.
     */
    public final <E extends Throwable> void writeE(final ConsumerE<T, E> writeFn) throws E {
        try (final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            writeFn.accept(value);
        }
    }

    /**
     * Write to the value.
     *
     * @param writeFn A function which writes to the value.
     *
     * @param <E1> An exception which may be thrown by the {@code writeFn}.
     * @param <E2> An exception which may be thrown by the {@code writeFn}.
     *
     * @throws E1 if an exception is thrown by the {@code writeFn}.
     * @throws E2 if an exception is thrown by the {@code writeFn}.
     */
    public final <E1 extends Exception, E2 extends Exception> void write2E(final Consumer2E<T, E1, E2> writeFn) throws E1, E2 {
        try (final ManagedLock<ReadWriteLock> writeLock = ManagedLock.acquire(lock, LockMode.WRITE_LOCK)) {
            writeFn.accept(value);
        }
    }
}
