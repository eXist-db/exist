/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.util.hashtable;

import net.jcip.annotations.NotThreadSafe;

import java.util.Iterator;

/**
 * A hashtable which maps object keys to long values.
 *
 * Keys are compared by their object equality, i.e. two objects are equal
 * if object1.equals(object2).
 *
 * @author Stephan Körnig
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@NotThreadSafe
public class Object2LongHashMap<K> extends AbstractHashtable<K, Long> {
    protected K[] keys;
    protected long[] values;

    @SuppressWarnings("unchecked")
    Object2LongHashMap() {
        super();
        keys = (K[]) new Object[tabSize];
        values = new long[tabSize];
    }

    @SuppressWarnings("unchecked")
    public Object2LongHashMap(final int iSize) {
        super(iSize);
        keys = (K[]) new Object[tabSize];
        values = new long[tabSize];
    }

    /**
     * Puts a new key/value pair into the hashtable.
     * If the key does already exist, just the value is updated.
     *
     * @param key
     * @param value
     */
    @SuppressWarnings("unchecked")
    public void put(final K key, final long value) {
        try {
            insert(key, value);
        } catch (final HashSetOverflowException e) {
            final K[] copyKeys = keys;
            final long[] copyValues = values;
            // enlarge the table with a prime value
            tabSize = (int) nextPrime(tabSize + tabSize / 2);
            keys = (K[]) new Object[tabSize];
            values = new long[tabSize];
            items = 0;

            for (int k = 0; k < copyValues.length; k++) {
                if (copyKeys[k] != null && copyKeys[k] != REMOVED) {
                    put(copyKeys[k], copyValues[k]);
                }
            }
            put(key, value);
        }
    }

    public long get(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return -1; // key does not exist
        }
        else if (keys[idx].equals(key)) {
            return values[idx];
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return -1; // key not found
            } else if (keys[idx] != REMOVED && keys[idx].equals(key)) {
                return values[idx];
            }
        }
        return -1;
    }

    public boolean containsKey(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return false; // key does not exist
        }
        else if (keys[idx].equals(key)) {
            return true;
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return false; // key not found
            } else if (keys[idx].equals(key)) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public long remove(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return -1; // key does not exist
        } else if (keys[idx].equals(key)) {
            keys[idx] = (K) REMOVED;
            --items;
            return values[idx];
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return -1; // key not found
            } else if (keys[idx].equals(key)) {
                keys[idx] = (K) REMOVED;
                --items;
                return values[idx];
            }
        }
        return -1;
    }

    @Override
    public Iterator<K> iterator() {
        return new Object2LongIterator<>(IteratorType.KEYS);
    }

    @Override
    public Iterator<Long> valueIterator() {
        return new Object2LongIterator<>(IteratorType.VALUES);
    }

    public Iterator<K> stableIterator() {
        return new Object2LongStableIterator<>(IteratorType.KEYS);
    }

    protected void insert(final K key, final long value) throws HashSetOverflowException {
        if (key == null) {
            throw new IllegalArgumentException("Illegal value: null");
        }
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        int bucket = -1;
        // look for an empty bucket
        if (keys[idx] == null) {
            keys[idx] = key;
            values[idx] = value;
            ++items;
            return;
        } else if (keys[idx] == REMOVED) {
            // remember the bucket, but continue to check
            // for duplicate keys
            bucket = idx;
        } else if (keys[idx].equals(key)) {
            // duplicate value
            values[idx] = value;
            return;
        }
        final int rehashVal = rehash(idx);
        int rehashCnt = 1;
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == REMOVED) {
                bucket = idx;
            } else if (keys[idx] == null) {
                if (bucket > -1) {
                    // store key into the empty bucket first found
                    idx = bucket;
                }
                keys[idx] = key;
                values[idx] = value;
                ++items;
                return;
            } else if (keys[idx].equals(key)) {
                // duplicate value
                values[idx] = value;
                return;
            }
            ++rehashCnt;
        }
        // should never happen, but just to be sure:
        // if the key has not been inserted yet, do it now
        if (bucket > -1) {
            keys[bucket] = key;
            values[bucket] = value;
            ++items;
            return;
        }
        throw new HashSetOverflowException();
    }

    int rehash(final int iVal) {
        int retVal = (iVal + iVal / 2) % tabSize;
        if (retVal == 0) {
            retVal = 1;
        }
        return retVal;
    }

    static int hash(final Object o) {
        return o.hashCode();
    }

    public class Object2LongIterator<T> extends AbstractHashSetIterator<T> {
        private int idx = 0;

        public Object2LongIterator(final IteratorType type) {
            super(type);
        }

        @Override
        public boolean hasNext() {
            if (idx == tabSize) {
                return false;
            }
            while (keys[idx] == null || keys[idx] == REMOVED) {
                ++idx;
                if (idx == tabSize) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (idx == tabSize) {
                return null;
            }
            while (keys[idx] == null || keys[idx] == REMOVED) {
                ++idx;
                if (idx == tabSize) {
                    return null;
                }
            }
            switch (returnType) {
                case KEYS:
                    return (T) keys[idx++];
                case VALUES:
                    return (T) Long.valueOf(values[idx++]);
            }

            throw new IllegalStateException("This never happens");
        }
    }

    public class Object2LongStableIterator<T> extends AbstractHashSetIterator<T> {
        private final K[] mKeys;
        private final long[] mValues;
        private int idx = 0;

        @SuppressWarnings("unchecked")
        public Object2LongStableIterator(final IteratorType type) {
            super(type);
            mKeys = (K[]) new Object[tabSize];
            System.arraycopy(keys, 0, mKeys, 0, tabSize);
            mValues = new long[tabSize];
            System.arraycopy(values, 0, mValues, 0, tabSize);
        }

        @Override
        public boolean hasNext() {
            if (idx == tabSize) {
                return false;
            }
            while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
                ++idx;
                if (idx == tabSize) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        @Override
        public T next() {
            if (idx == tabSize) {
                return null;
            }
            while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
                ++idx;
                if (idx == tabSize) {
                    return null;
                }
            }
            switch (returnType) {
                case KEYS:
                    return (T) mKeys[idx++];
                case VALUES:
                    return (T) Long.valueOf(mValues[idx++]);
            }

            throw new IllegalStateException("This never happens");
        }
    }
}
