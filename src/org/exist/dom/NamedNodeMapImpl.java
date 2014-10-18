/* eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 * $Id:
 */
package org.exist.dom;

import org.exist.util.hashtable.Object2ObjectHashMap;
import org.w3c.dom.DOMException;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class NamedNodeMapImpl implements NamedNodeMap {

    // NamedNodeMap is used by attributes, and it is often
    // rare that an element has more then 10 attributes
    private static final int DEFAULT_SIZE = 10;

    private final IndexedHashMap<QName, Node> namedNodes = new IndexedHashMap<>(DEFAULT_SIZE);

    @Override
    public Node getNamedItem(final String name) {
        return namedNodes.get(new QName(name));
    }

    @Override
    public Node setNamedItem(final Node arg) throws DOMException {
        return namedNodes.put(new QName(arg.getNodeName()), arg);
    }

    /**
     * Adds an INode to the NamedNodeMap
     *
     * The INode#getQName method is called
     * to get the name for the map item
     *
     * @return The previous node of the same name if it exists
     */
    public Node setNamedItem(final INode arg) throws DOMException {
        return namedNodes.put(arg.getQName(), arg);
    }

    @Override
    public Node removeNamedItem(final String name) throws DOMException {
        return remove(new QName(name));
    }

    @Override
    public Node item(final int index) {
        return namedNodes.get(index);
    }

    @Override
    public int getLength() {
        return namedNodes.size();
    }

    @Override
    public Node getNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return namedNodes.get(new QName(localName, namespaceURI));
    }

    @Override
    public Node setNamedItemNS(final Node arg) throws DOMException {
        return namedNodes.put(new QName(arg.getLocalName(), arg.getNamespaceURI()), arg);
    }

    @Override
    public Node removeNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return remove(new QName(localName, namespaceURI));
    }

    private Node remove(final QName qname) throws DOMException {
        final Node previous = namedNodes.remove(qname);
        if(previous != null) {
            return previous;
        } else {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "No such named value is present in the map");
        }
    }

    private static final class IndexedHashMap<K, V> {
        private final Object2ObjectHashMap<K, V> map;
        private final List<K> keys;

        public IndexedHashMap(final int initialSize) {
            this.map = new Object2ObjectHashMap<>(initialSize);
            this.keys = new ArrayList<>(initialSize);
        }

        public final V put(final K key, final V value) {
            final V current = map.get(key);
            map.put(key, value);
            if(current == null) {
                keys.add(key);
            }
            return current;
        }

        public final V get(final K key) {
            return map.get(key);
        }

        public final V get(final int index) {
            return map.get(keys.get(index));
        }

        /**
         * @return The removed value or null if there is
         *  no value for the key in the nap
         */
        public final V remove(final K key) {
            if(keys.remove(key)) {
                return map.remove(key);
            } else {
                return null;
            }
        }

        public final int size() {
            return keys.size();
        }
    }
}
