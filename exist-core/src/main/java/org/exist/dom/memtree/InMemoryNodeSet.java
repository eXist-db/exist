/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Set;

/**
 * DOCUMENT ME!
 */
public class InMemoryNodeSet extends ValueSequence {

    public static final InMemoryNodeSet EMPTY = new InMemoryNodeSet(0);

    public InMemoryNodeSet() {
        super();
    }

    public InMemoryNodeSet(final int initialSize) {
        super(initialSize);
    }

    public InMemoryNodeSet(final Sequence otherSequence) throws XPathException {
        super(otherSequence);
        final Set<DocumentImpl> docs = new HashSet<>();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if(node.getNodeType() == Node.DOCUMENT_NODE) {
                docs.add((DocumentImpl)node);
            } else {
                docs.add(node.getOwnerDocument());
            }
        }
        for(final DocumentImpl doc : docs) {
            doc.expand();
        }
    }

    @Override
    public Sequence getAttributes(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getDescendantAttributes(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendantAttributes(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildren(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectChildren(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getChildrenForParent(final NodeImpl parent) {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if(node.getNodeId().isChildOf(parent.getNodeId())) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getDescendants(final boolean includeSelf, final NodeTest test)
        throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendants(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getAncestors(final boolean includeSelf, final NodeTest test)
        throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAncestors(includeSelf, test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getParents(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            final NodeImpl parent = (NodeImpl) node.selectParentNode();
            if(parent != null && test.matches(parent)) {
                nodes.add(parent);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getSelf(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) || test.matches(node)) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    @Override
    public Sequence getPrecedingSiblings(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectPrecedingSiblings(test, nodes);
        }
        return nodes;
    }

    @Override
    public Sequence getFollowingSiblings(final NodeTest test) throws XPathException {
        final InMemoryNodeSet nodes = new InMemoryNodeSet();
        for(int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectFollowingSiblings(test, nodes);
        }
        return nodes;
    }
}
