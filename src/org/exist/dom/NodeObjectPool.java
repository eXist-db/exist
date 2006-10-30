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

import org.apache.commons.pool.impl.StackKeyedObjectPool;

/**
 * An object pool for reusable node objects. Usually, node objects
 * are only held in memory for a short time. By reusing these objects, 
 * we can save many object creations.
 * 
 * @author wolf
 */
public class NodeObjectPool extends StackKeyedObjectPool {
	
	private static NodeObjectPool instance = null;
	
	public final static NodeObjectPool getInstance() {
		if(instance == null)
			instance = new NodeObjectPool();
		return instance;
	}
	
	public NodeObjectPool() {
		super(new PoolableNodeObjectFactory(), 10, 50);
	}
	
	public NodeImpl borrowNode(Class clazz) {
		try {
			NodeImpl node = (NodeImpl)borrowObject(clazz);
//			System.out.println(clazz.getName() + ": " + getNumActive(clazz) + " / " + getNumIdle(clazz));
			return node;
		} catch (Exception e) {
			throw new IllegalStateException("Could not create node: " + e.getMessage());
		}
	}
	
	public void returnNode(NodeImpl node) {
		try {
			returnObject(node.getClass(), node);
//			System.out.println("Returning object " + node.getClass().getName() + ": " + getNumIdle(node.getClass()));
		} catch (Exception e) {
			throw new IllegalStateException("Error while returning node: " + e.getMessage());
		}
	}
}
