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
package org.exist.util.serializer;

import org.apache.commons.pool.PoolableObjectFactory;
import org.apache.commons.pool.impl.StackObjectPool;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class SAXSerializerPool extends StackObjectPool {

	private final static SAXSerializerPool instance =
		new SAXSerializerPool(new SAXSerializerObjectFactory(), 10, 1);

	public final static SAXSerializerPool getInstance() {
		return instance;
	}
	
	public SAXSerializerPool(PoolableObjectFactory factory, int maxIdle, int initIdleCapacity) {
		super(factory, maxIdle, initIdleCapacity);
	}
	
	public SAXSerializer borrowSAXSerializer() {
		try {
			return (SAXSerializer)borrowObject();
		} catch (Exception e) {
			throw new IllegalStateException("error while creating SAXSerializer");
		}
	}
	
	public void returnSAXSerializer(SAXSerializer serializer) {
		try {
			returnObject(serializer);
		} catch (Exception e) {
			throw new IllegalStateException("error while releasing SAXSerializer");
		}
	}
}
