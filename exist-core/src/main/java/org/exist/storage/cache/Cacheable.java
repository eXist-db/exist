/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage.cache;

/**
 * Implemented by all objects that should be stored into a cache.
 * 
 * Each object should provide a unique key, an internal reference counter,
 * and a timestamp marker (used to measure how long the object has stayed
 * in the cache). It depends on the concrete cache implementation if and how
 * these fields are used.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public interface Cacheable {

	int MAX_REF = 10000;
	
	/**
	 * Get a unique key for the object.
	 * 
	 * Usually this is the page number.
	 * 
	 * @return unique key
	 */
	long getKey();
	
	/**
	 * Get the current reference count.
	 * 
	 * @return The count value. 
	 */
	int getReferenceCount();

	/**
	 * Increase the reference count of this object by one
	 * and return it.
	 * 
	 * @return the reference count
	 */
	int incReferenceCount();
	
	/**
	 * Decrease the reference count of this object by one
	 * and return it.
	 * 
	 * @return the reference count
	 */
	int decReferenceCount();
	
	/**
	 * Set the reference count of this object.
	 * 
	 * @param count A reference count
	 */
	void setReferenceCount(int count);
	
	/**
	 * Set the timestamp marker.
	 * 
	 * @param timestamp A timestamp marker
	 */
	void setTimestamp(int timestamp);
	
	/**
	 * Get the current timestamp marker.
	 * 
	 * @return timestamp marker
	 */
	int getTimestamp();

	/**
	 * Called before the object is released by the
	 * cache. The object should prepare to be garbage
	 * collected. All unwritten data should be flushed
	 * to disk.
	 * @param syncJournal the journal to sync
	 * @return true if sync was successful
	 */
	boolean sync(boolean syncJournal);
	
	/**
	 * Is it safe to unload the Cacheable from the cache?
	 * 
	 * Called before an object is actually removed. Return
	 * false to avoid being removed.
	 * 
	 * @return A boolean where true indicates it can be unloaded.
	 */
	boolean allowUnload();

	/**
	 * Indicates whether the cacheable is dirty
	 *
	 * @return true if the cacheable is dirty
	 */
	boolean isDirty();
}
