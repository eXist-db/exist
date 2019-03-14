/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2010 The eXist Project
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

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.jcip.annotations.ThreadSafe;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.CDATASectionImpl;
import org.exist.dom.persistent.CommentImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.persistent.ProcessingInstructionImpl;
import org.exist.dom.persistent.TextImpl;
import org.w3c.dom.Node;

/**
 * A pool of node objects. Storing a resource creates many, short-lived DOM node
 * objects. To reduce garbage collection, we use a pool to cache a certain number
 * of objects.
 */
@ThreadSafe
public class NodePool {

    public static final int MAX_OBJECTS = 20;
    private static final ThreadLocal<NodePool> pools = new PoolThreadLocal();

    private int maxActive;
    private final Int2ObjectMap<Pool> poolMap = new Int2ObjectOpenHashMap<>(17);

    private NodePool(final int maxObjects) {
        this.maxActive = maxObjects;
    }

    public static NodePool getInstance() {
        return pools.get();
    }

    public NodeImpl borrowNode(final short key) {
        Pool pool = poolMap.get(key);
        if (pool == null) {
            pool = new Pool();
            poolMap.put(key, pool);
        }
        return pool.borrowNode(key);
    }

    public void returnNode(final NodeImpl node) {
        final Pool pool = poolMap.get(node.getNodeType());
        if (pool != null) {
            pool.returnNode(node);
        }
    }

    public int getSize(final short key) {
        final Pool pool = poolMap.get(key);
        return pool.stack.size();
    }

    private NodeImpl makeObject(final short key) {
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

    private static class PoolThreadLocal extends ThreadLocal<NodePool> {

        @Override
        protected NodePool initialValue() {
            return new NodePool(MAX_OBJECTS);
        }
    }

    private class Pool {

        private LinkedList<NodeImpl> stack = new LinkedList<>();

        public NodeImpl borrowNode(final short key) {
            if (stack.isEmpty()) {
                return makeObject(key);
            }
            return stack.removeLast();
        }

        public void returnNode(final NodeImpl node) {
            // Only cache up to maxActive nodes
            if (stack.size() < maxActive) {
                stack.addLast(node);
            }
        }
    }
}
