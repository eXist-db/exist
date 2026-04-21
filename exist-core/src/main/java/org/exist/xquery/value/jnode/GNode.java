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
package org.exist.xquery.value.jnode;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.List;

/**
 * GNode — the general node interface for XQuery 4.0.
 *
 * Unifies XML nodes and JSON nodes under a common interface for
 * node identity, ordering, and navigation. In the QT4 type system:
 * <ul>
 *   <li>{@code gnode()} matches any node (XML or JSON)</li>
 *   <li>{@code node()} matches only XML nodes</li>
 *   <li>{@code json-node()} matches only JSON nodes</li>
 * </ul>
 *
 * <p>Currently implemented by {@link JNode}. XML nodes in eXist-db
 * ({@code NodeProxy}, {@code NodeImpl}) do not implement this interface
 * directly — they are checked via {@code Type.isNodeType()} instead.
 * A future refactoring could add GNode adapters for XML nodes to enable
 * unified dispatch in mixed XML/JSON sequences.</p>
 *
 * <p><strong>Known limitation:</strong> In mixed sequences containing both
 * XML and JSON nodes, axis navigation dispatches based on the first item's
 * type. A sequence starting with an XML node will skip JNodes and vice versa.
 * This affects the rare case of {@code ($xml, $json)/child::gnode()}.</p>
 *
 * @see <a href="https://qt4cg.org/specifications/xpath-datamodel-40/Overview.html#gnodes">
 *      XDM 4.0 §6 General Nodes</a>
 */
public interface GNode extends Item {

    /**
     * Returns the parent node, or null for root nodes.
     */
    GNode getGNodeParent();

    /**
     * Returns the child nodes.
     */
    List<? extends GNode> getGNodeChildren() throws XPathException;

    /**
     * Returns the root of this node's tree.
     */
    GNode getGNodeRoot();

    /**
     * Returns true if this is the same node as the other (identity, not equality).
     */
    boolean isSameNode(GNode other);

    /**
     * Returns true if this node has children.
     */
    boolean hasGNodeChildren();

    /**
     * Returns the JSON kind type constant (from Type.java).
     * For XML nodes, returns Type.NODE or the specific XML node type.
     */
    int getNodeKind();

    /**
     * Returns the underlying value for serialization/atomization.
     */
    Sequence getGNodeValue();
}
