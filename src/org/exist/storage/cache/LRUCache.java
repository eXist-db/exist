/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.storage.cache;

import java.util.Iterator;
import java.util.LinkedList;

import org.exist.util.hashtable.Long2ObjectHashMap;

/**
 * @author wolf
 */
public class LRUCache implements Cache {

	private int max;
	private LinkedList stack = new LinkedList();
	private Long2ObjectHashMap map;
	
	private int hits = 0;
	private int misses = 0;
	
	public LRUCache(int size) {
		this.max = size;
		this.map = new Long2ObjectHashMap(size);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable, int)
	 */
	public void add(Cacheable item, int initialRefCount) {
		add(item);
	}
		
	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#add(org.exist.storage.cache.Cacheable)
	 */
	public void add(Cacheable item) {
		if(stack.size() == max) {
			removeOne();
		}
		map.put(item.getKey(), item);
		stack.addLast(item);
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(org.exist.storage.cache.Cacheable)
	 */
	public Cacheable get(Cacheable item) {
		return get(item.getKey());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#get(long)
	 */
	public Cacheable get(long key) {
		Cacheable obj = (Cacheable) map.get(key);
		if(obj == null)
			++misses;
		else
			++hits;
		return obj;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#remove(org.exist.storage.cache.Cacheable)
	 */
	public void remove(Cacheable item) {
		stack.remove(item);
		map.remove(item.getKey());
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#flush()
	 */
	public void flush() {
		Cacheable next;
		for(Iterator i = stack.iterator(); i.hasNext(); ) {
			next = (Cacheable)i.next();
			next.sync();
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getBuffers()
	 */
	public int getBuffers() {
		return max;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getUsedBuffers()
	 */
	public int getUsedBuffers() {
		return stack.size();
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getHits()
	 */
	public int getHits() {
		return hits;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#getFails()
	 */
	public int getFails() {
		return misses;
	}

	/* (non-Javadoc)
	 * @see org.exist.storage.cache.Cache#setFileName(java.lang.String)
	 */
	public void setFileName(String fileName) {
	}
	
	private final void removeOne() {
		Cacheable first = (Cacheable)stack.removeFirst();
		if(!stack.contains(first)) {
			map.remove(first.getKey());
			first.sync();
		}
	}
}
