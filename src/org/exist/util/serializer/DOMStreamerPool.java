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
import org.exist.storage.serializers.Serializer;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class DOMStreamerPool extends StackObjectPool {

	private final static DOMStreamerPool instance =
		new DOMStreamerPool(new DOMStreamerObjectFactory(), 10, 2);
	
	public final static DOMStreamerPool getInstance() {
		return instance;
	}
	
	protected DOMStreamerPool(PoolableObjectFactory factory, int maxIdle, int initIdleCapacity) {
		super(factory, maxIdle, initIdleCapacity);
	}

	public DOMStreamer borrowDOMStreamer() {
		try {
			return (DOMStreamer)borrowObject();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public DOMStreamer borrowDOMStreamer(Serializer delegate) {
	    try {
			ExtendedDOMStreamer serializer = (ExtendedDOMStreamer)borrowObject();
			serializer.setSerializer(delegate);
			return serializer;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	public void returnDOMStreamer(DOMStreamer streamer) {
		if(streamer == null)
			return;
		try {
			returnObject(streamer);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
}
