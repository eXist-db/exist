/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.dom;

import org.apache.commons.pool.BaseKeyedPoolableObjectFactory;

/**
 * @author wolf
 */
public class PoolableNodeObjectFactory extends BaseKeyedPoolableObjectFactory {
	
	/**
	 * 
	 */
	public PoolableNodeObjectFactory() {
		super();
	}
	
	/* (non-Javadoc)
	 * @see org.apache.commons.pool.KeyedPoolableObjectFactory#makeObject(java.lang.Object)
	 */
	public Object makeObject(Object key) throws Exception {
		if(key == ElementImpl.class)
			return new ElementImpl();
		else if(key == TextImpl.class)
			return new TextImpl();
		else if(key == AttrImpl.class)
			return new AttrImpl();
		else if(key == ProcessingInstructionImpl.class)
			return new ProcessingInstructionImpl();
		else if(key == CommentImpl.class)
			return new CommentImpl();
		throw new IllegalStateException("Unable to create object of type " + key.getClass().getName());
	}
}
