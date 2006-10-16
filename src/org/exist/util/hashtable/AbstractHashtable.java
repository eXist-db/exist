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

/**
 * Abstract base class for all hashtable implementations.
 * 
 * @author Stephan Körnig
 * @author Wolfgang Meier
 */
public abstract class AbstractHashtable {

	private final int defaultSize = 1031; // must be a prime number

	// marker for removed objects
	protected final static Object REMOVED = new Object();
	
	protected int tabSize;
	protected int items;

	protected int maxRehash = 0;
	
	/**
	 * Create a new hashtable with default size (1031).
	 */
	protected AbstractHashtable() {
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
	protected AbstractHashtable(int iSize) {
		items = 0;
		if (iSize < 1)
			tabSize = defaultSize;
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
	
	public abstract Iterator iterator();
	public abstract Iterator valueIterator();
	
	public final static boolean isPrime(long number) {
		if (number < 2) return false;
		if (number == 2) return true;
		if (number % 2 == 0) return false;
		if (number == 3) return true;
		if (number % 3 == 0) return false;
 
		int y = 2;
		int x = (int) Math.sqrt(number);
 
		for (int i = 5; i <= x; i += y, y = 6 - y) {
			if (number % i == 0)
				return false;
		}
 
		return true;
	}

	public final static long nextPrime(long iVal) {
		long retval = iVal;
		for (;;) {
			++retval;
			if (isPrime(retval))
				return retval;
		}
	}
	
	public int getMaxRehash() {
		return maxRehash;
	}
	
	protected abstract class HashtableIterator implements Iterator {
		
		public final static int KEYS = 0;
		public final static int VALUES = 1;
		
		int returnType = KEYS;
		
		public HashtableIterator(int type) {
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
		
		public HashtableOverflowException() {
			super();
		}
	}
}
