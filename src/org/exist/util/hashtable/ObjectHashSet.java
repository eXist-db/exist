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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * A hash set on objects. Objects are compared for equality by
 * calling Object.equals().
 * calling Object.equals().
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@NotThreadSafe
public class ObjectHashSet<K> extends AbstractHashSet<K> {
    protected K[] keys;

    @SuppressWarnings("unchecked")
    ObjectHashSet() {
        super();
        this.keys = (K[]) new Object[tabSize];
    }

    /**
     * @param iSize The initial size of the hash set
     */
    @SuppressWarnings("unchecked")
    public ObjectHashSet(final int iSize) {
        super(iSize);
        this.keys = (K[]) new Object[tabSize];
    }

    @SuppressWarnings("unchecked")
    public void add(final K key) {
        try {
            insert(key);
        } catch (final HashSetOverflowException e) {
            final K[] copyKeys = keys;
            // enlarge the table with a prime value
            tabSize = (int) nextPrime(tabSize + tabSize / 2);
            keys = (K[]) new Object[tabSize];
            items = 0;

            for (int k = 0; k < copyKeys.length; k++) {
                if (copyKeys[k] != null && copyKeys[k] != REMOVED) {
                    add(copyKeys[k]);
                }
            }
            add(key);
        }
    }

    protected void insert(final K key) throws HashSetOverflowException {
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
            ++items;
            return;
        } else if (keys[idx] == REMOVED) {
            // remember the bucket, but continue to check
            // for duplicate keys
            bucket = idx;
        } else if (keys[idx].equals(key)) {
            // duplicate value
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
                ++items;
                return;
            } else if (keys[idx].equals(key)) {
                // duplicate value
                return;
            }
            ++rehashCnt;
        }
        // should never happen, but just to be sure:
        // if the key has not been inserted yet, do it now
        if (bucket > -1) {
            keys[bucket] = key;
            ++items;
            return;
        }
        throw new HashSetOverflowException();
    }

    public boolean contains(final K key) {
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
    public K remove(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return null; // key does not exist
        } else if (keys[idx].equals(key)) {
            final K prevKey = keys[idx];
            keys[idx] = (K)REMOVED;
            --items;
            return prevKey;
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return null; // key not found
            } else if (keys[idx].equals(key)) {
                final K prevKey = keys[idx];
                keys[idx] = (K)REMOVED;
                --items;
                return prevKey;
            }
        }
        return null;
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

    public List<K> keys() {
        final ArrayList<K> list = new ArrayList<>(items);
        for (int i = 0; i < tabSize; i++) {
            if (keys[i] != null && keys[i] != REMOVED) {
                list.add(keys[i]);
            }
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public Iterator<K> iterator() {
        return new ObjectHashSetIterator();
    }

    public Iterator<K> stableIterator() {
        return new ObjectHashSetStableIterator();
    }

    public class ObjectHashSetIterator implements Iterator<K> {
        private int idx = 0;

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
        public K next() {
            if (idx == tabSize) {
                return null;
            }
            while (keys[idx] == null || keys[idx] == REMOVED) {
                ++idx;
                if (idx == tabSize) {
                    return null;
                }
            }
            return keys[idx++];
        }
    }

    public class ObjectHashSetStableIterator implements Iterator<K> {
        private final K mKeys[];
        private int idx = 0;

        @SuppressWarnings("unchecked")
        public ObjectHashSetStableIterator() {
            this.mKeys = (K[]) new Object[tabSize];
            System.arraycopy(keys, 0, mKeys, 0, tabSize);
        }

        @Override
        public boolean hasNext() {
            if (idx == mKeys.length) {
                return false;
            }
            while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
                ++idx;
                if (idx == mKeys.length) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public K next() {
            if (idx == mKeys.length) {
                return null;
            }
            while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
                ++idx;
                if (idx == mKeys.length) {
                    return null;
                }
            }
            return mKeys[idx++];
        }
    }
}
