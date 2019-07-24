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

import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.QName.IllegalQNameException;
import org.w3c.dom.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class NamedNodeMapImpl implements NamedNodeMap {

    // NamedNodeMap is used by attributes, and it is often
    // rare that an element has more then 10 attributes
    private static final int DEFAULT_SIZE = 10;

    private final IndexedHashMap<QName, Node> namedNodes = new IndexedHashMap<>(DEFAULT_SIZE);
    private final Document ownerDocument;
    private final boolean attributesOnly;

    public NamedNodeMapImpl(final Document ownerDocument, final boolean attributesOnly) {
        this.ownerDocument = ownerDocument;
        this.attributesOnly = attributesOnly;
    }

    @Override
    public Node getNamedItem(final String name) {
        try {
            return getNamedItem(new QName(name));
        } catch (final QName.IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "Invalid name");
        }
    }

    @Override
    public Node getNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return getNamedItem(new QName(localName, namespaceURI));
    }

    private Node getNamedItem(final QName qname) {
        return namedNodes.get(qname);
    }

    @Override
    public Node setNamedItem(final Node arg) throws DOMException {
        try {
            return setNamedItem(new QName(arg.getNodeName()), arg);
        } catch (final QName.IllegalQNameException e) {
        throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "Invalid name");
        }
    }

    @Override
    public Node setNamedItemNS(final Node arg) throws DOMException {
        return setNamedItem(new QName(arg.getLocalName(), arg.getNamespaceURI()), arg);
    }

    /**
     * Adds an INode to the NamedNodeMap
     *
     * The INode#getQName method is called
     * to get the name for the map item
     * @param arg INode to add
     * @return The previous node of the same name if it exists
     */
    public Node setNamedItem(final INode arg) throws DOMException {
        return setNamedItem(arg.getQName(), arg);
    }

    private Node setNamedItem(final QName qname, final Node arg) {
        if((arg.getNodeType() == Node.DOCUMENT_NODE && arg != ownerDocument) || arg.getOwnerDocument() != ownerDocument) {
            throw new DOMException(DOMException.WRONG_DOCUMENT_ERR, "Owning document IDs do not match");
        }

        // NOTE the Type.Namespace is needed below to cope with eXist-db's {@link org.exist.dom.memtree.NamespaceNode}
        if(attributesOnly &&
                (arg.getNodeType() != Node.ATTRIBUTE_NODE && arg.getNodeType() != NodeImpl.NAMESPACE_NODE)) {
            throw new DOMException(DOMException.HIERARCHY_REQUEST_ERR,
                    "This map is for attributes, but setNamedItem was called on a: " + arg.getClass().getName());
        }

        return namedNodes.put(qname, arg);
    }

    @Override
    public Node removeNamedItem(final String name) throws DOMException {
        try {
            return removeNamedItem(new QName(name));
        } catch (final IllegalQNameException e) {
            throw new DOMException(DOMException.INVALID_CHARACTER_ERR, "Invalid name");
        }
    }

    @Override
    public Node removeNamedItemNS(final String namespaceURI, final String localName) throws DOMException {
        return removeNamedItem(new QName(localName, namespaceURI));
    }

    private Node removeNamedItem(final QName qname) throws DOMException {
        final Node previous = namedNodes.remove(qname);
        if(previous != null) {
            return previous;
        } else {
            throw new DOMException(DOMException.NOT_FOUND_ERR, "No such named value is present in the map");
        }
    }

    @Override
    public Node item(final int index) {
        if(index >= namedNodes.size()) {
            return null;
        }

        return namedNodes.get(index);
    }

    @Override
    public int getLength() {
        return namedNodes.size();
    }

    private static final class IndexedHashMap<K, V> {
        private final Map<K, V> map;
        private final List<K> keys;

        public IndexedHashMap(final int initialSize) {
            this.map = new HashMap<>(initialSize);
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
