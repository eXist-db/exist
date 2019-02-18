/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.xquery;

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * This class is used to hold an intermediate result that can be cached.
 * Caching results is effective if a subexpression is executed more than once
 * and the current evaluation context doesn't change between invocations.
 * 
 * @author wolf
 */
public class CachedResult {

	final protected Sequence cachedResult;
	final protected Sequence cachedContext;
	final protected Item cachedItem;	
	final protected int timestamp;
	
	public CachedResult(Sequence context, Item contextItem, Sequence result) {
		this.cachedContext = context;
		this.cachedResult = result;
		this.cachedItem = contextItem;
		this.timestamp = context.getState();
	}
	
	public Sequence getResult() {
		return cachedResult;
	}
	
	public boolean isValid(Sequence context, Item contextItem) {
        if (context == null)
            {return false;}
		if(Type.subTypeOf(context.getItemType(), Type.NODE) &&
			cachedContext == context && cachedItem == contextItem) {
			if(context.hasChanged(timestamp)) {
				return false;
			} else {
				cachedResult.setIsCached(true);
				return true;
			}
		}
		return false;
	}
}
