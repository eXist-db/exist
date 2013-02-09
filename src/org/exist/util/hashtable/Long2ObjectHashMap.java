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

import java.util.Iterator;

/**
 * A hashtable which maps long keys to object values.
 * 
 * @author Stephan Körnig
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class Long2ObjectHashMap<V> extends AbstractHashtable<Long, V> {
	 
	protected long[] keys;
	protected V[] values;

	public Long2ObjectHashMap() {
		super();
		keys = new long[tabSize];
		values = (V[]) new Object[tabSize];
	}

	public Long2ObjectHashMap(int iSize) {
		super(iSize);
		keys = new long[tabSize];
		values = (V[]) new Object[tabSize];
	}

	/**
	 * Puts a new key/value pair into the hashtable.
	 * 
	 * If the key does already exist, just the value is updated.
	 * 
	 * @param key
	 * @param value
	 */
	public void put(long key, V value) {
		try {
			insert(key, value);
		} catch (final HashtableOverflowException e) {
			final long[] copyKeys = keys;
			final V[] copyValues = values;
			// enlarge the table with a prime value
			tabSize = (int) nextPrime(tabSize + tabSize / 2);
			keys = new long[tabSize];
			values = (V[]) new Object[tabSize];
			items = 0;

			for (int k = 0; k < copyValues.length; k++) {
				if (copyValues[k] != null && copyValues[k] != REMOVED)
					{put(copyKeys[k], copyValues[k]);}
			}
			put(key, value);
		}
	}

	public V get(long key) {
		int idx = hash(key) % tabSize;
		if(idx < 0)
			{idx *= -1;}
		if (values[idx] == null)
			{return null;} // key does not exist
		else if (keys[idx] == key) {
			if(values[idx] == REMOVED)
				{return null;}
			return values[idx];
		}
		final int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return null; // key not found
			} else if (keys[idx] == key) {
				if(values[idx] == REMOVED)
					{return null;}
				return values[idx];
			}
		}
		return null;
	}
	
	public V remove(long key) {
		int idx = hash(key) % tabSize;
		if(idx < 0)
			{idx *= -1;}
		if (values[idx] == null) {
			return null; // key does not exist
		} else if (keys[idx] == key) {
			if(values[idx] == REMOVED)
				{return null;}	// key has already been removed
			final V o = values[idx];
			values[idx] = (V) REMOVED;
			--items;
			return o;
		}
		final int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return null; // key not found
			} else if (keys[idx] == key) {
				if(values[idx] == REMOVED)
					{return null;}	// key has already been removed
				final V o = values[idx];
				values[idx] = (V) REMOVED;
				--items;
				return o;
			}
		}
		return null;
	}

    public void clear() {
        for (int i = 0; i < values.length; i++) {
            values[i] = null;
        }
        items = 0;
    }
    
	public Iterator<Long> iterator() {
		return new Long2ObjectIterator(IteratorType.KEYS);
	}
	
	public Iterator<V> valueIterator() {
		return new Long2ObjectIterator(IteratorType.VALUES);
	}
	
	protected V insert(long key, V value) throws HashtableOverflowException {
		if (value == null)
			{throw new IllegalArgumentException("Illegal value: null");}
		int idx = hash(key) % tabSize;
		if(idx < 0)
			{idx *= -1;}
        int bucket = -1;
		// look for an empty bucket
		if (values[idx] == null) {
			keys[idx] = key;
			values[idx] = value;
			++items;
			return null;
		} else if (values[idx] == REMOVED) {
            // remember the bucket, but continue to check
            // for duplicate keys
            bucket = idx;
        } else if (keys[idx] == key) {
			// duplicate value
        	final V dup = values[idx];
			values[idx] = value;
			return dup;
		}
		final int rehashVal = rehash(idx);
		int rehashCnt = 1;
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
            if(values[idx] == REMOVED) {
            	if(bucket == -1)
            		{bucket = idx;}
			} else if (values[idx] == null) {
                if(bucket > -1) {
                    // store key into the empty bucket first found
                    idx = bucket;
                }
				keys[idx] = key;
				values[idx] = value;
				++items;
				return null;
			} else if(keys[idx] == key) {
				// duplicate value
				final V dup = values[idx];
				values[idx] = value;
				return dup;
			}
			++rehashCnt;
		}
        // should never happen, but just to be sure:
        // if the key has not been inserted yet, do it now
        if(bucket > -1) {
            keys[bucket] = key;
            values[bucket] = value;
            ++items;
            return null;
        }
		throw new HashtableOverflowException();
	}

	protected int rehash(int iVal) {
		int retVal = (iVal + iVal / 2) % tabSize;
		if (retVal == 0)
			{retVal = 1;}
		return retVal;
	}

	protected final static int hash(long l) {
		return (int) (l ^ (l >>> 32));
	}
	
	protected class Long2ObjectIterator<T> extends HashtableIterator<T> {
		
		int idx = 0;
		
		public Long2ObjectIterator(IteratorType type) {
			super(type);
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			if (idx == tabSize)
				{return false;}
			while(values[idx] == null || values[idx] == REMOVED) {
				++idx;
				if(idx == tabSize)
					{return false;}
			}
			return true;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public T next() {
			if(idx == tabSize)
				{return null;}
			while(values[idx] == null || values[idx] == REMOVED) {
				++idx;
				if(idx == tabSize)
					{return null;}
			}
			switch(returnType) {
				case VALUES: return (T) values[idx++];
				case KEYS: return (T) Long.valueOf(keys[idx++]);
			}
			throw new IllegalStateException("This never happens");
		}
	}
}
