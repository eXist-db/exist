/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 */
package org.exist.util;

import net.jcip.annotations.ThreadSafe;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * A pool for char arrays.
 *
 * This pool is used by class XMLString. Whenever an XMLString needs to
 * reallocate the backing char[], the old array is released into the pool. However,
 * only char[] with length &lt; MAX are kept in the pool. Larger char[] are rarely reused.
 *
 * The pool is bound to the current thread.
 */
@ThreadSafe
public class CharArrayPool {

    private static final int POOL_SIZE = 128;
    private static final int MAX = 128;
    private static final ThreadLocal<char[][]> pools_ = new PoolThreadLocal();
    private static final AtomicInteger slot_ = new AtomicInteger();

    private CharArrayPool() {
    }

    public static char[] getCharArray(final int size) {
        if (MAX > size) {
            final char[][] pool = pools_.get();
            for (int i = 0; i < pool.length; i++) {
                if (pool[i] != null && pool[i].length == size) {
                    final char[] b = pool[i];
                    pool[i] = null;
                    return b;
                }
            }
        }
        return new char[size];
    }

    public static void releaseCharArray(final char[] b) {
        if (b == null || b.length > MAX) {
            return;
        }
        final char[][] pool = pools_.get();
        for (int i = 0; i < pool.length; i++) {
            if (pool[i] == null) {
                pool[i] = b;
                return;
            }
        }

        int s = slot_.incrementAndGet();
        if (s < 0) {
            s = -s;
        }
        pool[s % pool.length] = b;
    }

    private static final class PoolThreadLocal extends ThreadLocal<char[][]> {
        @Override
        protected char[][] initialValue() {
            return new char[POOL_SIZE][];
        }
    }
}
