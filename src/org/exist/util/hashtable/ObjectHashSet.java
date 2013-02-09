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
public class ObjectHashSet<K> extends AbstractHashSet<K> {

	protected K[] keys;
	
	/**
	 * 
	 */
	public ObjectHashSet() {
		super();
		keys = (K[]) new Object[tabSize];
	}

	/**
	 * @param iSize
	 */
	public ObjectHashSet(int iSize) {
		super(iSize);
		keys = (K[]) new Object[tabSize];
	}

	public void add(K key) {
		try {
			insert(key);
		} catch (final HashtableOverflowException e) {
			final K[] copyKeys = keys;
			// enlarge the table with a prime value
			tabSize = (int) nextPrime(tabSize + tabSize / 2);
			keys = (K[]) new Object[tabSize];
			items = 0;

			for (int k = 0; k < copyKeys.length; k++) {
				if (copyKeys[k] != null && copyKeys[k] != REMOVED)
					{add(copyKeys[k]);}
			}
			add(key);
		}
	}
	
	protected void insert(K key) throws HashtableOverflowException {
		if (key == null)
			{throw new IllegalArgumentException("Illegal value: null");}
		int idx = hash(key) % tabSize;
		if (idx < 0)
			{idx *= -1;}
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
		throw new HashtableOverflowException();
	}
	
	public boolean contains(K key) {
		int idx = hash(key) % tabSize;
		if (idx < 0)
			{idx *= -1;}
		if (keys[idx] == null)
			{return false;} // key does not exist
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
	
	public K remove(K key) {
		int idx = hash(key) % tabSize;
		if (idx < 0)
			{idx *= -1;}
		if (keys[idx] == null) {
			return null; // key does not exist
		} else if (keys[idx].equals(key)) {
			key = keys[idx];
			keys[idx] = (K) REMOVED;
			--items;
			return key;
		}
		final int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (keys[idx] == null) {
				return null; // key not found
			} else if (keys[idx].equals(key)) {
				key = keys[idx];
				keys[idx] = (K) REMOVED;
				--items;
				return key;
			}
		}
		return null;
	}
	
	protected int rehash(int iVal) {
		int retVal = (iVal + iVal / 2) % tabSize;
		if (retVal == 0)
			{retVal = 1;}
		return retVal;
	}

	protected final static int hash(Object o) {
		return o.hashCode();
	}

    public List<K> keys() {
        final ArrayList<K> list = new ArrayList<K>(items);
        for (int i = 0; i < tabSize; i++) {
            if (keys[i] != null && keys[i] != REMOVED)
                {list.add(keys[i]);}
        }
        return Collections.unmodifiableList(list);
    }

    /* (non-Javadoc)
      * @see org.exist.util.hashtable.AbstractHashtable#iterator()
      */
	public Iterator<K> iterator() {
		return new ObjectHashSetIterator();
	}

	public Iterator<K> stableIterator() {
		return new ObjectHashSetStableIterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.hashtable.AbstractHashtable#valueIterator()
	 */
	public Iterator valueIterator() {
		return null;
	}
	
	protected class ObjectHashSetIterator implements Iterator<K> {

		int idx = 0;

		public ObjectHashSetIterator() {
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			if (idx == tabSize)
				{return false;}
			while (keys[idx] == null || keys[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					{return false;}
			}
			return true;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public K next() {
			if (idx == tabSize)
				{return null;}
			while (keys[idx] == null || keys[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					{return null;}
			}
			return keys[idx++];
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
		}
	}
	
	protected class ObjectHashSetStableIterator implements Iterator<K> {

		int idx = 0;
		K mKeys[];
		
		public ObjectHashSetStableIterator() {
			mKeys = (K[]) new Object[tabSize];
			System.arraycopy(keys, 0, mKeys, 0, tabSize);
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			if (idx == tabSize)
				{return false;}
			while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					{return false;}
			}
			return true;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public K next() {
			if (idx == tabSize)
				{return null;}
			while (mKeys[idx] == null || mKeys[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					{return null;}
			}
			return mKeys[idx++];
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
		}
	}
}
