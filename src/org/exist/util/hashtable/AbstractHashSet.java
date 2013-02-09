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

import java.util.Iterator;

/**
 * Abstract base class for all hashset implementations.
 *
 */
public abstract class AbstractHashSet<K> {

	private static final int defaultSize = 1031; // must be a prime number

	// marker for removed objects
	protected final static Object REMOVED = new Object();
	
	protected int tabSize;
	protected int items;

	protected int maxRehash = 0;
	
	/**
	 * Create a new hashset with default size (1031).
	 */
	protected AbstractHashSet() {
		items = 0;
		tabSize = defaultSize;
	}
	
	/**
	 * Create a new hashtable using the specified size.
	 * 
	 * The actual size will be next prime number following
	 * iSize * 1.5.
	 * 
	 * @param iSize
	 */
	protected AbstractHashSet(int iSize) {
		items = 0;
		if (iSize < 1)
			{tabSize = defaultSize;}
		else {
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
	
	public abstract Iterator<K> iterator();
	
	public final static boolean isPrime(long number) {
		if (number < 2) {return false;}
		if (number == 2) {return true;}
		if (number % 2 == 0) {return false;}
		if (number == 3) {return true;}
		if (number % 3 == 0) {return false;}
 
		int y = 2;
		final int x = (int) Math.sqrt(number);
 
		for (int i = 5; i <= x; i += y, y = 6 - y) {
			if (number % i == 0)
				{return false;}
		}
 
		return true;
	}

	public final static long nextPrime(long iVal) {
		long retval = iVal;
		for (;;) {
			++retval;
			if (isPrime(retval))
				{return retval;}
		}
	}
	
	public int getMaxRehash() {
		return maxRehash;
	}
	

	protected static enum IteratorType {KEYS, VALUES};

	protected abstract class HashtableIterator<T> implements Iterator<T> {	
		
		protected final IteratorType returnType;
		
		public HashtableIterator(IteratorType type) {
			this.returnType = type;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	protected final static class HashtableOverflowException extends Exception {
		
		private static final long serialVersionUID = -4679763007424266920L;

		public HashtableOverflowException() {
			super();
		}
	}

}
