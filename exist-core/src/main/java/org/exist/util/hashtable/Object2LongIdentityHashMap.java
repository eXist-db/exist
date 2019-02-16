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

/**
 * A hashtable which maps object keys to long values.
 *
 * Keys are compared by their object identity, i.e. two objects are equal
 * if object1 == object2.
 *
 * @author Stephan Körnig
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
@NotThreadSafe
public class Object2LongIdentityHashMap<K> extends Object2LongHashMap<K> {

    Object2LongIdentityHashMap() {
        super();
    }

    public Object2LongIdentityHashMap(final int iSize) {
        super(iSize);
    }

    @Override
    public long get(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return -1; // key does not exist
        }
        else if (keys[idx] == key) {
            return values[idx];
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return -1; // key not found
            } else if (keys[idx] == key) {
                return values[idx];
            }
        }
        return -1;
    }

    @Override
    public boolean containsKey(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return false; // key does not exist
        }
        else if (keys[idx] == key) {
            return true;
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return false; // key not found
            } else if (keys[idx] == key) {
                return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    @Override
    public long remove(final K key) {
        int idx = hash(key) % tabSize;
        if (idx < 0) {
            idx *= -1;
        }
        if (keys[idx] == null) {
            return -1; // key does not exist
        } else if (keys[idx] == key) {
            keys[idx] = (K) REMOVED;
            --items;
            return values[idx];
        }
        final int rehashVal = rehash(idx);
        for (int i = 0; i < tabSize; i++) {
            idx = (idx + rehashVal) % tabSize;
            if (keys[idx] == null) {
                return -1; // key not found
            } else if (keys[idx] == key) {
                keys[idx] = (K) REMOVED;
                --items;
                return values[idx];
            }
        }
        return -1;
    }

    @Override
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
        } else if (keys[idx] == key) {
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
            } else if (keys[idx] == key) {
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
}
