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
 * @author wolf
 */
@NotThreadSafe
public class Object2ObjectHashMap<K, V> extends AbstractHashtable<K, V> {
    protected K[] keys;
    protected V[] values;

    @SuppressWarnings("unchecked")
    Object2ObjectHashMap() {
        super();
        keys = (K[]) new Object[tabSize];
        values = (V[]) new Object[tabSize];
    }

    /**
     * @param iSize The initial size of the hash map
     */
    @SuppressWarnings("unchecked")
    public Object2ObjectHashMap(final int iSize) {
        super(iSize);
        keys = (K[]) new Object[tabSize];
        values = (V[]) new Object[tabSize];
    }

    @SuppressWarnings("unchecked")
    public void clean() {
        keys = (K[]) new Object[tabSize];
        values = (V[]) new Object[tabSize];
        items = 0;
    }

    /**
     * Puts a new key/value pair into the hashtable.
     * If the key does already exist, just the value is updated.
     *
     * @param key The key
     * @param value The value
     */
    @SuppressWarnings("unchecked")
    public void put(final K key, final V value) {
        try {
            insert(key, value);
        } catch (final HashSetOverflowException e) {
            final K[] copyKeys = keys;
            final V[] copyValues = values;
            // enlarge the table with a prime value
            tabSize = (int) nextPrime(tabSize + tabSize / 2);
            keys = (K[]) new Object[tabSize];
            values = (V[]) new Object[tabSize];
            items = 0;

            for (int k = 0; k < copyValues.length; k++) {
                if (copyKeys[k] != null && copyKeys[k] != REMOVED) {
                    put(copyKeys[k], copyValues[k]);
                }
            }
            put(key, value);
        }
    }

    public V get(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return null; // key does not exist
        }
        else if (keys[idx].equals(key)) {
            return values[idx];
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return null; // key not found
            } else if (keys[idx].equals(key)) {
                return values[idx];
            }
        }
        return null;
    }

    public int getIndex(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return -1; // key does not exist
        }
        else if (keys[idx].equals(key)) {
            return idx;
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return -1; // key not found
            } else if (keys[idx].equals(key)) {
                return idx;
            }
        }
        return -1;
    }

    @SuppressWarnings("unchecked")
    public V remove(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return null; // key does not exist
        } else if (keys[idx].equals(key)) {
            keys[idx] = (K) REMOVED;
            --items;
            final V oldVal = values[idx];
            values[idx] = null;
            return oldVal;
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return null; // key not found
            } else if (keys[idx].equals(key)) {
                keys[idx] = (K) REMOVED;
                --items;
                final V oldVal = values[idx];
                values[idx] = null;
                return oldVal;
            }
        }
        return null;
    }

    private void insert(final K key, final V value) throws HashSetOverflowException {
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

    private int rehash(final int iVal) {
        int retVal = (iVal + iVal / 2) % tabSize;
        if (retVal == 0) {
            retVal = 1;
        }
        return retVal;
    }

    private static int hash(final Object o) {
        return o.hashCode();
    }

    @Override
    public Iterator<K> iterator() {
        return new Object2ObjectIterator<>(IteratorType.KEYS);
    }

    @Override
    public Iterator<V> valueIterator() {
        return new Object2ObjectIterator<>(IteratorType.VALUES);
    }

    public class Object2ObjectIterator<T> extends AbstractHashSetIterator<T> {
        private int idx = 0;

        public Object2ObjectIterator(final IteratorType type) {
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

        @Override
        @SuppressWarnings("unchecked")
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
                    return (T) values[idx++];
            }

            throw new IllegalStateException("This never happens");
        }
    }
}
