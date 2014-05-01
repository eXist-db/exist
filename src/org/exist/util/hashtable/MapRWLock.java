/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.util.hashtable;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.ReadLock;
import java.util.concurrent.locks.ReentrantReadWriteLock.WriteLock;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MapRWLock<K, V> implements Map<K, V> {

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final ReadLock readLock = lock.readLock();
    private final WriteLock writeLock = lock.writeLock();
    
    private Map<K, V> map;
    
    public MapRWLock(Map<K, V> map) {
    	this.map = map;
    }

    @Override
	public int size() {
        readLock.lock();
        try {
            return map.size();
        } finally {
            readLock.unlock();
        }
	}

    @Override
	public boolean isEmpty() {
        readLock.lock();
        try {
            return map.isEmpty();
        } finally {
            readLock.unlock();
        }
	}

    @Override
	public boolean containsKey(Object key) {
        readLock.lock();
        try {
            return map.containsKey(key);
        } finally {
            readLock.unlock();
        }
	}

    @Override
	public boolean containsValue(Object value) {
        readLock.lock();
        try {
            return map.containsValue(value);
        } finally {
            readLock.unlock();
        }
	}

    @Override
	public V get(Object key) {
        readLock.lock();
        try {
            return map.get(key);
        } finally {
            readLock.unlock();
        }
	}

    @Override
	public V put(K key, V value) {
        writeLock.lock();
        try {
            return map.put(key, value);
        } finally {
            writeLock.unlock();
        }
	}

    @Override
	public V remove(Object key) {
        writeLock.lock();
        try {
            return map.remove(key);
        } finally {
            writeLock.unlock();
        }
	}

    @Override
	public void putAll(Map<? extends K, ? extends V> m) {
        writeLock.lock();
        try {
            map.putAll(m);
        } finally {
            writeLock.unlock();
        }
	}

    @Override
	public void clear() {
        writeLock.lock();
        try {
            map.clear();
        } finally {
            writeLock.unlock();
        }
	}

    @Override
	public Set<K> keySet() {
    	throw new UnsupportedOperationException("keySet method is not atomic operation.");
	}

    @Override
	public Collection<V> values() {
    	throw new UnsupportedOperationException("values method is not atomic operation.");
	}

    @Override
	public Set<java.util.Map.Entry<K, V>> entrySet() {
    	throw new UnsupportedOperationException("entrySet method is not atomic operation.");
	}
    
    public void readOperation(final LongOperation<K, V> readOp) {
        readLock.lock();
        try {
            readOp.execute(map);
        } finally {
            readLock.unlock();
        }
    }
    
    public void writeOperation(final LongOperation<K, V> writeOp) {
        writeLock.lock();
        try {
        	writeOp.execute(map);
        } finally {
        	writeLock.unlock();
        }
    }

    public interface LongOperation<K, V> {
    	public void execute(Map<K, V> map);
    }

}
