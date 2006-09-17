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
 * A double-linked hash map additionally providing access to entries in the order in which 
 * they were added. 
 * 
 * If a duplicate entry is added, the old entry is removed from the list and appended to the end. The
 * map thus implements a "Last Recently Used" (LRU) behaviour.
 */
import java.util.Iterator;

import org.exist.util.hashtable.Long2ObjectHashMap.Long2ObjectIterator;

public class SequencedLongHashMap extends AbstractHashtable {

    /**
     * Represents an entry in the map. Each entry
     * has a link to the next and previous entries in
     * the order in which they were inserted.
     * 
     * @author wolf
     */
	public final static class Entry {
		
		long key;
		Object value;
		
        /** points to the next entry in insertion order. */
		Entry next = null;
        
        /** points to the previous entry in insertion order. */
		Entry prev = null;
		
        /** points to the prev entry if more than one key maps
         * to the same bucket in the table.
         */ 
        Entry prevDup = null;
        
        /** points to the next entry if more than one key maps
         * to the same bucket in the table.
         */
        Entry nextDup = null;
        
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
        
        public String toString() {
            return Long.toString(key);
        }
	}
	
	protected long[] keys;
	protected Entry[] values;
	
    /** points to the first entry inserted. */
	private Entry first = null;
    
    /** points to the last inserted entry. */
	private Entry last = null;
 
	public SequencedLongHashMap() {
		super();
		keys = new long[tabSize];
		values = new Entry[tabSize];
	}

	public SequencedLongHashMap(int iSize) {
		super(iSize);
		keys = new long[tabSize];
		values = new Entry[tabSize];
	}

    /**
     * Add a new entry for the key.
     * 
     * @param key
     * @param value
     */
	public void put(long key, Object value) {
		Entry entry = insert(key, value);
		
		if(first == null) {
			first = entry;
			last = first;
		} else {
			last.next = entry;
			entry.prev = last;
			last = entry;
		}
	}
	
	protected Entry insert(long key, Object value) {
		if (value == null)
			throw new IllegalArgumentException("Illegal value: null");
		int idx = hash(key) % tabSize;
		if(idx < 0)
			idx *= -1;
		// look for an empty bucket
		if (values[idx] == null) {
			keys[idx] = key;
			values[idx] = new Entry(key, value);
			++items;
			return values[idx];
        }
        
        Entry next = values[idx];
        while (next != null) {
            if (next.key == key) {
                // duplicate value
                next.value = value;
                removeEntry(next);
                return next;
            }
            next = next.nextDup;
        }
        
        // add a new entry to the chain
        next = new Entry(key, value);
        next.nextDup = values[idx];
        values[idx].prevDup = next;
        values[idx] = next;
        ++items;
        return next;
	}
	
    /**
     * Returns the value for key or null if the key
     * is not in the map.
     * 
     * @param key
     */
	public Object get(long key) {
		int idx = hash(key) % tabSize;
		if(idx < 0)
			idx *= -1;
		if (values[idx] == null)
			return null; // key does not exist
		
		Entry next = values[idx];
        while (next != null) {
            if (next.key == key)
                return next.value;
            next = next.nextDup;
        }
        return null;
	}
	
    /**
     * Returns the first entry added to the map.
     */
	public Entry getFirstEntry() {
		return first;
	}
	
    /**
     * Remove the entry specified by key from the map.
     * 
     * @param key
     */
	public Object remove(long key) {
		Entry entry = removeFromHashtable(key);
		if(entry != null) {
			removeEntry(entry);
			return entry.value;
		} else
			return null;
	}
	
	private Entry removeFromHashtable(long key) {
		int idx = hash(key) % tabSize;
		if(idx < 0)
			idx *= -1;
		if (values[idx] == null) {
			return null; // key does not exist
		}
        
		Entry next = values[idx];
        while (next != null) {
            if (next.key == key) {
                if (next.prevDup == null) {
                    values[idx] = next.nextDup;
                    if (values[idx] != null)
                        values[idx].prevDup = null;
                } else {
                    next.prevDup.nextDup = next.nextDup;
                    if (next.nextDup != null)
                        next.nextDup.prevDup = next.prevDup;
                }
                --items;
                return next;
            }
            next = next.nextDup;
        }
		return null;
	}
	
	/**
	 * Remove the first entry added to the map.
	 */
	public Object removeFirst() {
		if(first == null)
			return null;
		final Entry head = first;
		removeFromHashtable(first.key);
		removeEntry(first);
		return head;
	}
	
    /**
     * Remove an entry.
     * 
     * @param entry
     */
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
		entry.prev = null;
		entry.next = null;
	}
	
    /**
     * Clear the map.
     */
    public void clear() {
        for (int i = 0; i < tabSize; i++)
            values[i] = null;
        items = 0;
        first = null;
        last = null;
    }

	protected final static int hash(long l) {
		return (int) (l ^ (l >>> 32));
	}
	
	/**
	 * Returns an iterator over all keys in the
	 * order in which they were inserted.
	 */
	public Iterator iterator() {
		return new SequencedLongIterator(Long2ObjectIterator.KEYS);
	}
	
    /**
     * Returns an iterator over all values in the order
     * in which they were inserted.
     */
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