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

import org.exist.util.hashtable.Long2ObjectHashMap.Long2ObjectIterator;

public class SequencedLongHashMap extends AbstractHashtable {

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

	private final static Entry REMOVED_ENTRY = new Entry(0, null);
	
	protected long[] keys;
	protected Entry[] values;
	
	private Entry first = null;
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

	public void put(long key, Object value) {
		Entry entry = null;
		try {
			entry = insert(key, value);
		} catch (HashtableOverflowException e) {
			Entry[] copyValues = values;
			// enlarge the table with a prime value
			tabSize = (int) nextPrime(tabSize + tabSize / 2);
			keys = new long[tabSize];
			values = new Entry[tabSize];
			items = 0;

			try {
				for (int k = 0; k < copyValues.length; k++) {
					if (copyValues[k] != null && copyValues[k] != REMOVED_ENTRY)
						insert(copyValues[k].key, copyValues[k].value);
				}
				entry = (Entry)insert(key, value);
			} catch (HashtableOverflowException e1) {
			}
		}
		
		if(first == null) {
			first = entry;
			last = first;
		} else {
			last.next = entry;
			entry.prev = last;
			last = entry;
		}
	}
	
	protected Entry insert(long key, Object value) throws HashtableOverflowException {
		if (value == null)
			throw new IllegalArgumentException("Illegal value: null");
		int idx = hash(key) % tabSize;
		if(idx < 0)
			idx *= -1;
        int bucket = -1;
		// look for an empty bucket
		if (values[idx] == null) {
			keys[idx] = key;
			values[idx] = new Entry(key, value);
			++items;
			return values[idx];
		} else if (values[idx] == REMOVED_ENTRY) {
            // remember the bucket, but continue to check
            // for duplicate keys
            bucket = idx;
        } else if (keys[idx] == key) {
			// duplicate value
        	Entry dup = values[idx];
			dup.value = value;
			removeEntry(dup);
			return dup;
		}
		int rehashVal = rehash(idx);
		int rehashCnt = 1;
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
            if(values[idx] == REMOVED_ENTRY) {
            	if(bucket == -1)
            		bucket = idx;
			} else if (values[idx] == null) {
                if(bucket > -1) {
                    // store key into the empty bucket first found
                    idx = bucket;
                }
				keys[idx] = key;
				values[idx] = new Entry(key, value);
				++items;
				return values[idx];
			} else if(keys[idx] == key) {
				// duplicate value
				Entry dup = values[idx];
				dup.value = value;
				removeEntry(dup);
				return dup;
			}
			++rehashCnt;
		}
        // if the key has not been inserted yet, do it now
        if(bucket > -1) {
            keys[bucket] = key;
            values[bucket] = new Entry(key, value);
            ++items;
            return values[bucket];
        }
		throw new HashtableOverflowException();
	}
	
	public Object get(long key) {
		int idx = hash(key) % tabSize;
		if(idx < 0)
			idx *= -1;
		if (values[idx] == null)
			return null; // key does not exist
		else if (keys[idx] == key) {
			if(values[idx] == REMOVED)
				return null;
			return values[idx].value;
		}
		int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return null; // key not found
			} else if (keys[idx] == key) {
				if(values[idx] == REMOVED)
					return null;
				return values[idx].value;
			}
		}
		return null;
	}
	
	public Entry getFirstEntry() {
		return first;
	}
	
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
		} else if (keys[idx] == key) {
			if(values[idx] == REMOVED_ENTRY)
				return null;	// key has already been removed
			Entry o = values[idx];
			values[idx] = REMOVED_ENTRY;
			--items;
			return o;
		}
		int rehashVal = rehash(idx);
		for (int i = 0; i < tabSize; i++) {
			idx = (idx + rehashVal) % tabSize;
			if (values[idx] == null) {
				return null; // key not found
			} else if (keys[idx] == key) {
				if(values[idx] == REMOVED_ENTRY)
					return null;	// key has already been removed
				Entry o = values[idx];
				values[idx] = REMOVED_ENTRY;
				--items;
				return o;
			}
		}
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
		final Entry head = first;
		removeFromHashtable(first.key);
		removeEntry(first);
		return head;
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
		entry.prev = null;
		entry.next = null;
	}
	
	protected int rehash(int iVal) {
		int retVal = (iVal + iVal / 2) % tabSize;
		if (retVal == 0)
			retVal = 1;
		return retVal;
	}

	protected final static int hash(long l) {
		return (int) (l ^ (l >>> 32));
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
