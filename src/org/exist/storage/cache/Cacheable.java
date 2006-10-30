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
package org.exist.storage.cache;

/**
 * Implemented by all objects that should be stored into a cache.
 * 
 * Each object should provide a unique key, an internal reference counter,
 * and a timestamp marker (used to measure how long the object has stayed
 * in the cache). It depends on the concrete cache implementation if and how
 * these fields are used.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public interface Cacheable {

	public final static int MAX_REF = 10000;
	
	/**
	 * Get a unique key for the object.
	 * 
	 * Usually this is the page number.
	 * 
	 * @return unique key
	 */
	public long getKey();
	
	/**
	 * Get the current reference count.
	 * 
	 * @return The count value. 
	 */
	public int getReferenceCount();

	/**
	 * Increase the reference count of this object by one
	 * and return it.
	 * 
	 * @return the reference count
	 */
	public int incReferenceCount();
	
	/**
	 * Decrease the reference count of this object by one
	 * and return it.
	 * 
	 * @return the reference count
	 */
	public int decReferenceCount();
	
	/**
	 * Set the reference count of this object.
	 * 
	 * @param count
	 */
	public void setReferenceCount(int count);
	
	/**
	 * Set the timestamp marker.
	 * 
	 * @param timestamp
	 */
	public void setTimestamp(int timestamp);
	
	/**
	 * Get the current timestamp marker.
	 * 
	 * @return timestamp marker
	 */
	public int getTimestamp();

	/**
	 * Called before the object is released by the
	 * cache. The object should prepare to be garbage
	 * collected. All unwritten data should be flushed
	 * to disk.
	 */
	public boolean sync(boolean syncJournal);
	
	/**
	 * Is it safe to unload the Cacheable from the cache?
	 * 
	 * Called before an object is actually removed. Return
	 * false to avoid being removed.
	 * 
	 * @return A boolean where true indicates it can be unloaded.
	 */
	public boolean allowUnload();
	
	public boolean isDirty();
}
