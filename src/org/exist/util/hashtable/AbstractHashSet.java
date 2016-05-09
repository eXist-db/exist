/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 * Abstract base class for all hashset implementations.
 */
@NotThreadSafe
public abstract class AbstractHashSet<K> implements Iterable<K> {

    private static final int DEFAULT_SIZE = 1031; // must be a prime number

    // marker for removed objects
    protected final static Object REMOVED = new Object();

    protected int tabSize;
    protected int items;

    private int maxRehash = 0;

    /**
     * Create a new hashset with default size (1031).
     */
    AbstractHashSet() {
        items = 0;
        tabSize = DEFAULT_SIZE;
    }

    /**
     * Create a new hashtable using the specified size.
     *
     * The actual size will be next prime number following
     * iSize * 1.5.
     *
     * @param iSize Initial size of the hash set
     */
    public AbstractHashSet(int iSize) {
        items = 0;
        if (iSize < 1) {
            tabSize = DEFAULT_SIZE;
        } else {
            if (!isPrime(iSize)) {
                iSize = (iSize * 3) / 2;
                iSize = (int) nextPrime((long) iSize);
            }
            tabSize = iSize;
        }
    }

    public int size() {
        return items;
    }

    public boolean isEmpty() {
        return items == 0;
    }

    private static boolean isPrime(final long number) {
        if (number < 2) {
            return false;
        } else if (number == 2) {
            return true;
        } else if (number % 2 == 0) {
            return false;
        } else if (number == 3) {
            return true;
        } else if (number % 3 == 0) {
            return false;
        }

        int y = 2;
        final int x = (int) Math.sqrt(number);
        for (int i = 5; i <= x; i += y, y = 6 - y) {
            if (number % i == 0) {
                return false;
            }
        }

        return true;
    }

    static long nextPrime(final long iVal) {
        long retval = iVal;
        for (; ; ) {
            ++retval;
            if (isPrime(retval)) {
                return retval;
            }
        }
    }

    public int getMaxRehash() {
        return maxRehash;
    }


    enum IteratorType {KEYS, VALUES}

    abstract class AbstractHashSetIterator<T> implements Iterator<T> {
        protected final IteratorType returnType;

        AbstractHashSetIterator(final IteratorType type) {
            this.returnType = type;
        }
    }

    public final static class HashSetOverflowException extends Exception {
        private static final long serialVersionUID = -4679763007424266920L;

        public HashSetOverflowException() {
            super();
        }
    }
}
