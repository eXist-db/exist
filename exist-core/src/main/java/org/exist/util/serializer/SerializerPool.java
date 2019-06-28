/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.util.serializer;

import org.apache.commons.pool.KeyedPoolableObjectFactory;
import org.apache.commons.pool.impl.StackKeyedObjectPool;
import org.exist.storage.serializers.Serializer;

/**
 * @author wolf
 *
 */
public class SerializerPool extends StackKeyedObjectPool {

    private final static SerializerPool instance = new SerializerPool(new SerializerObjectFactory(), 10, 1);
    
    public final static SerializerPool getInstance() {
        return instance;
    }
    
    /**
     * @param factory the object factory
     * @param max the maximum size of the pool
     * @param init the initial size of the pool
     */
    public SerializerPool(KeyedPoolableObjectFactory factory, int max, int init) {
        super(factory, max, init);
    }
    
    public synchronized Object borrowObject(Object key) {
        try {
            return super.borrowObject(key);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while creating serializer: " + e.getMessage());
        }
    }
    
    public DOMStreamer borrowDOMStreamer(Serializer delegate) {
        try {
            final ExtendedDOMStreamer serializer = (ExtendedDOMStreamer)borrowObject(DOMStreamer.class);
            serializer.setSerializer(delegate);
            return serializer;
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public synchronized void returnObject(Object obj) {
        if (obj == null)
            {return;}
        try {
            super.returnObject(obj.getClass(), obj);
        } catch (final Exception e) {
            throw new IllegalStateException("Error while returning serializer: " + e.getMessage());
        }
    }
}
