/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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

package org.exist.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.lock.ManagedLock;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

/**
 * Simple container for a List of ManagedLocks
 * which allows ARM (Automatic Resource Management)
 * via {@link AutoCloseable}
 *
 * Locks will be released in the reverse order to which they
 * are provided
 */
public class ManagedLocks<T extends ManagedLock> implements Iterable<T>, AutoCloseable {

    private final static Logger LOG = LogManager.getLogger(ManagedLocks.class);

    private final List<T> managedLocks;

    /**
     * @param managedLocks A list of ManagedLocks which should
     *   be in the same order that they were acquired
     */
    public ManagedLocks(final java.util.List<T> managedLocks) {
        this.managedLocks = managedLocks;
    }

    /**
     * @param managedLocks An array / var-args of ManagedLocks
     *   which should be in the same order that they were acquired
     */
    public ManagedLocks(final T... managedLocks) {
        this.managedLocks = Arrays.asList(managedLocks);
    }

    @Override
    public Iterator<T> iterator() {
        return new ManagedLockIterator();
    }

    private class ManagedLockIterator implements Iterator<T> {
        private final Iterator<T> iterator = managedLocks.iterator();

        @Override
        public boolean hasNext() {
            return iterator.hasNext();
        }

        @Override
        public T next() {
            return iterator.next();
        }
    }

    @Override
    public void close() {
        closeAll(managedLocks);
    }

    /**
     * Closes all the locks in the provided list.
     *
     * Locks will be closed in reverse (acquisition) order.
     *
     * If a {@link RuntimeException} occurs when closing
     * any lock. The first exception will be recorded and
     * lock closing will continue. After all locks are closed
     * the first encountered exception is rethrown.
     *
     * @param <T> The type of the ManagedLocks
     * @param managedLocks A list of locks, the list should be ordered in lock acquisition order.
     */
    public static <T extends ManagedLock> void closeAll(final List<T> managedLocks) {
        RuntimeException firstException = null;

        for(int i = managedLocks.size() - 1; i >= 0; i--) {
            final T managedLock = managedLocks.get(i);
            try {
                managedLock.close();
            } catch (final RuntimeException e) {
                LOG.error(e);
                if(firstException == null) {
                    firstException = e;
                }
            }
        }

        if(firstException != null) {
            throw firstException;
        }
    }
}
