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
 * A pool for byte arrays.
 *
 * This pool is primarily used while parsing documents: serializing the
 * DOM nodes generates a lot of small byte chunks. Only byte arrays
 * with length &lt; MAX are kept in the pool. Large arrays are rarely
 * reused.
 */
@ThreadSafe
public class ByteArrayPool {

    private static final int POOL_SIZE = 32;
    private static final int MAX = 128;
    private static final ThreadLocal<byte[][]> pools_ = new PoolThreadLocal();
    private static AtomicInteger slot_ = new AtomicInteger();

    private ByteArrayPool() {
    }

    public static byte[] getByteArray(final int size) {
        final byte[][] pool = pools_.get();
        if (size < MAX) {
            for (int i = pool.length; i-- > 0; ) {
                if (pool[i] != null && pool[i].length == size) {
                    //System.out.println("found byte[" + size + "]");
                    final byte[] b = pool[i];
                    pool[i] = null;
                    return b;
                }
            }
        }
        return new byte[size];
    }

    public static void releaseByteArray(final byte[] b) {
        if (b == null || b.length > MAX) {
            return;
        }
        //System.out.println("releasing byte[" + b.length + "]");
        final byte[][] pool = pools_.get();
        for (int i = pool.length; i-- > 0; ) {
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

    private static final class PoolThreadLocal extends ThreadLocal<byte[][]> {

        @Override
        protected byte[][] initialValue() {
            return new byte[POOL_SIZE][];
        }

    }
}
