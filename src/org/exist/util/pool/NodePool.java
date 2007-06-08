/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.util.pool;

import java.util.LinkedList;

import org.exist.dom.AttrImpl;
import org.exist.dom.CDATASectionImpl;
import org.exist.dom.CommentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeImpl;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.TextImpl;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.w3c.dom.Node;

/**
 * A pool of node objects. Storing a resource creates many, short-lived DOM node
 * objects. To reduce garbage collection, we use a pool to cache a certain number
 * of objects.
 */
public class NodePool {

    public final static int MAX_OBJECTS = 20;

    public static NodePool getInstance() {
        return (NodePool) pools.get();
    }

    private static class PoolThreadLocal extends ThreadLocal {

        protected Object initialValue() {
           return new NodePool(MAX_OBJECTS);
        }
    }

    private static ThreadLocal pools = new PoolThreadLocal();
    

    private int maxActive;
    private Int2ObjectHashMap poolMap = new Int2ObjectHashMap(17);

    public NodePool(int maxObjects) {
        this.maxActive = maxObjects;
    }

    public NodeImpl borrowNode(short key) {
        Pool pool = (Pool) poolMap.get(key);
        if (pool == null) {
            pool = new Pool();
            poolMap.put(key, pool);
        }
        return pool.borrowNode(key);
    }

    public void returnNode(NodeImpl node) {
        Pool pool = (Pool) poolMap.get(node.getNodeType());
        if (pool != null)
            pool.returnNode(node);
    }

    public int getSize(short key) {
        Pool pool = (Pool) poolMap.get(key);
        return pool.stack.size();
    }
    
    private NodeImpl makeObject(short key) {
        switch (key) {
            case Node.ELEMENT_NODE:
                return new ElementImpl();
            case Node.TEXT_NODE:
                return new TextImpl();
            case Node.ATTRIBUTE_NODE:
                return new AttrImpl();
            case Node.CDATA_SECTION_NODE:
            	return new CDATASectionImpl();
            case Node.PROCESSING_INSTRUCTION_NODE:
                return new ProcessingInstructionImpl();
            case Node.COMMENT_NODE:
                return new CommentImpl();
        }
        throw new IllegalStateException("Unable to create object of type " + key);
    }

    private class Pool {

        private LinkedList stack = new LinkedList();

        public NodeImpl borrowNode(short key) {
            if (stack.isEmpty()) {
                return makeObject(key);
            }
            return (NodeImpl) stack.removeLast();
        }

        public void returnNode(NodeImpl node) {
            // Only cache up to maxActive nodes
            if (stack.size() < maxActive)
                stack.addLast(node);
        }
    }
}
