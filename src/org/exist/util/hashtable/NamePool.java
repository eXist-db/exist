/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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

import org.exist.dom.QName;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class NamePool extends AbstractHashtable {
	
    private final static QName REMOVED = new QName("REMOVED", "");
    
	private QName[] values;
	
	public NamePool() {
		super();
		values = new QName[tabSize];
	}
	
	/**
	 * Create a new symbol table using the specified size.
	 * 
	 * The actual size will be next prime number following
	 * iSize * 1.5.
	 * 
	 * @param iSize
	 */
	public NamePool(int iSize) {
		super(iSize);
		values = new QName[tabSize];
	}
	
	public synchronized int size() {
		return items;
	}
	
	public synchronized Object get(int pos) {
		Object obj = values[pos];
		return obj == null || obj == REMOVED ? null : obj;
	}
	
	public synchronized int add(QName value) {
		try {
			return insert(value);
		} catch(HashtableOverflowException e) {
			throw new RuntimeException("The size of the internal name pool " +
					"is exceeded!");
		}
	}
	
	protected int insert(QName value) throws HashtableOverflowException {
		if (value == null)
			throw new IllegalArgumentException("Illegal value: null");
		int idx = hash(value) % tabSize;
		if (idx < 0)
			idx *= -1;
		int bucket = -1;
		// look for an empty bucket
		if (values[idx] == null) {
			values[idx] = value;
			++items;
			return idx;
		} else if (values[idx] == REMOVED) {
			// remember the bucket, but continue to check
			// for duplicate keys
			bucket = idx;
		} else if (eq(values[idx], value)) {
			// duplicate value
			return idx;
		}
		int rehashVal = rehash(idx);
		int rehashCnt = 1;
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == REMOVED) {
				bucket = idx;
			} else if (values[idx] == null) {
				if (bucket > -1) {
					// store key into the empty bucket first found
					idx = bucket;
				}
				values[idx] = value;
				++items;
				return idx;
			} else if (eq(values[idx], value)) {
				// duplicate value
				return idx;
			}
			++rehashCnt;
		}
		// should never happen, but just to be sure:
		// if the key has not been inserted yet, do it now
		if (bucket > -1) {
			values[bucket] = value;
			++items;
			return bucket;
		}
		throw new HashtableOverflowException();
	}

	public synchronized void remove(QName value) {
		int idx = hash(value) % tabSize;
		if (idx < 0)
			idx *= -1;
		if (values[idx] == null) {
			return; // key does not exist
		} else if (eq(values[idx], value)) {
			values[idx] = REMOVED;
			--items;
			return;
		}
		int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return; // key not found
			} else if (eq(values[idx], value)) {
				values[idx] = REMOVED;
				--items;
				return;
			}
		}
	}
	
    private final static boolean eq(QName q1, QName q2) {
        if (q1.getNameType() == q2.getNameType())
            return q1.equals(q2);
        return false;
    }
    
	protected int rehash(int iVal) {
		int retVal = (iVal + iVal / 2) % tabSize;
		if (retVal == 0)
			retVal = 1;
		return retVal;
	}

	protected final static int hash(Object o) {
		return o.hashCode();
	}

	/* (non-Javadoc)
	 * @see org.exist.util.hashtable.AbstractHashtable#iterator()
	 */
	public Iterator iterator() {
		return new SymbolTableIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.util.hashtable.AbstractHashtable#valueIterator()
	 */
	public Iterator valueIterator() {
		return new SymbolTableIterator();
	}
	
	private class SymbolTableIterator implements Iterator {
		
		private int idx = 0;
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			if (idx == tabSize)
				return false;
			while (values[idx] == null || values[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					return false;
			}
			return true;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public Object next() {
			if (idx == tabSize)
				return null;
			while (values[idx] == null || values[idx] == REMOVED) {
				++idx;
				if (idx == tabSize)
					return null;
			}
			return values[idx++];
		}
		
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			throw new UnsupportedOperationException("Remove is not implemented");
		}
	}
}
