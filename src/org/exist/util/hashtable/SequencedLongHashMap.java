/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.util.hashtable;

/**
 * A hash map additionally providing access to entries in the order in which 
 * they were added. All entries are kept in a linked list. 
 * 
 * If a duplicate entry is added, the old entry is removed from the list and appended to the end. The
 * map thus implements a "Last Recently Used" behaviour.
 */
import java.util.Iterator;

public class SequencedLongHashMap extends Long2ObjectHashMap {

	public final static class Entry {
		
		long key;
		Object value;
		
		Entry next = null;
		Entry prev = null;
		
		public Entry(long key, Object value) {
			this.key = key;
			this.value = value;
		}
        
        public Entry getNext() {
            return next;
        }
        
        public long getKey() {
            return key;
        }
        
        public Object getValue() {
            return value;
        }
	}
	
	private Entry first = null;
	private Entry last = null;
 
	public SequencedLongHashMap() {
		super();
	}

	public SequencedLongHashMap(int iSize) {
		super(iSize);
	}

	public void put(long key, Object value) {
		Entry entry = new Entry(key, value);
		Entry duplicate = null;
		try {
			Object old = insert(key, entry);
			if(old != null && !(old instanceof Entry))
				throw new RuntimeException("Found old object: " + old.getClass().getName());
			duplicate = (Entry)old;
		} catch (HashtableOverflowException e) {
			throw new RuntimeException(e);
//			long[] copyKeys = keys;
//			Object[] copyValues = values;
//			// enlarge the table with a prime value
//			tabSize = (int) nextPrime(tabSize + tabSize / 2);
//			keys = new long[tabSize];
//			values = new Object[tabSize];
//			items = 0;
//
//			try {
//				for (int k = 0; k < copyValues.length; k++) {
//					if (copyValues[k] != null && copyValues[k] != REMOVED)
//						insert(copyKeys[k], copyValues[k]);
//				}
//				duplicate = (Entry)insert(key, entry);
//			} catch (HashtableOverflowException e1) {
//			}
		}
		if(duplicate != null)
			removeEntry(duplicate);
		if(first == null) {
			first = entry;
			last = first;
		} else {
			last.next = entry;
			entry.prev = last;
			last = entry;
		}
	}
	
	public Object get(long key) {
		Entry entry = (Entry) super.get(key);
		return entry == null ? null : entry.value;
	}
	
	public Entry getFirstEntry() {
		return first;
	}
	
	public Object remove(long key) {
		Entry entry = (Entry) super.remove(key);
		if(entry != null) {
			removeEntry(entry);
			return entry.value;
		} else
			return null;
	}
	
	/**
	 * Remove the first entry added to the map.
	 * 
	 * @return
	 */
	public Object removeFirst() {
		if(first == null)
			return null;
		super.remove(first.key);
		Entry head = first;
		first = head.next;
		if(head != null)
			head.prev = null;
		return head.value;
	}
	
	public void removeEntry(Entry entry) {
		if(entry.prev == null) {
			if(entry.next == null) {
				first = null;
				last = null;
			} else {
				entry.next.prev = null;
				first = entry.next;
			}
		} else {
			entry.prev.next = entry.next;
			if(entry.next == null)
				last = entry.prev;
			else
				entry.next.prev = entry.prev;
		}
	}
	
	/**
	 * Returns an iterator over all entries in the
	 * order in which they were inserted.
	 */
	public Iterator iterator() {
		return new SequencedLongIterator(Long2ObjectIterator.KEYS);
	}
	
	public Iterator valueIterator() {
		return new SequencedLongIterator(Long2ObjectIterator.VALUES);
	}
	
	protected class SequencedLongIterator extends HashtableIterator {
		
		private Entry current;
		
		public SequencedLongIterator(int type) {
			super(type);
			current = first;
		}
		
		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return current != null;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.util.hashtable.Long2ObjectHashMap.Long2ObjectIterator#next()
		 */
		public Object next() {
			if(current == null)
				return null;
			Entry next = current;
			current = current.next;
			if(returnType == VALUES) {
				return next.value;
			} else
				return new Long(next.key);
		}
	}
}
