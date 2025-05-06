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

package org.exist.xquery.functions.fn.transform;

import net.sf.saxon.s9api.XdmNode;
import org.exist.xquery.value.NodeValue;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.List;

public class TreeUtils {

    private TreeUtils() {
        super();
    }

    static StringBuilder pathTo(final Node node) {
        if (node instanceof Document) {
            final Document document = (Document) node;
            return new StringBuilder().append(document.getDocumentURI());
        }
        final List<Node> priors = new ArrayList<>();
        Node prev = node;
        while (prev != null) {
            priors.add(prev);
            prev = prev.getPreviousSibling();
        }
        final Node parent = priors.get(0).getParentNode();
        final StringBuilder sb;
        if (parent == null || parent instanceof Document) {
            sb = new StringBuilder();
        } else {
            sb = pathTo(parent).append('/');
        }
        for (final Node prior : priors) {
            sb.append(((NodeValue)prior).getQName()).append(';');
        }

        return sb;
    }

    static List<Integer> treeIndex(final Node node) {
        final Node parent = node.getParentNode();
        if (parent == null) {
          final List<Integer> index = new ArrayList<>();
          // The root element always index 0 within the document node.
          // Some node implementations (e.g., org.exist.dom.memtree.NodeImpl) do not always have an associated document.
          // In this case, the nodeIndex must get an extra 0 index to be valid for xdmDocument.
          if (! (node instanceof Document)) {
            index.add(0);
          }
          return index;
        }
        final List<Integer> index = treeIndex(parent);
        Node sibling = previousSiblingNotAttribute(node);
        int position = 0;
        while (sibling != null) {
            position += 1;
            sibling = previousSiblingNotAttribute(sibling);
        }
        index.add(position);

        return index;
    }

    /**
     * A org.exist.dom.persistent.StoredNode returns attributes of an element as previous siblings of the element's children.
     * This is not compatible with the way xdmNodeAtIndex works, so we need to compensate for this.
     * @param node
     * @return the previous sibling of `node` that is not an attribute.
     */
    private static Node previousSiblingNotAttribute(Node node) {
      Node sibling = node.getPreviousSibling();
      if (sibling instanceof Attr) {
        return null;
      }
      return sibling;
    }

    static XdmNode xdmNodeAtIndex(final XdmNode xdmNode, final List<Integer> index) {
        if (index.isEmpty()) {
            return xdmNode;
        } else {
            int i = 0;
            for (final XdmNode child : xdmNode.children()) {
                if (i++ == index.get(0)) {
                    return xdmNodeAtIndex(child, index.subList(1,index.size()));
                }
            }
        }
        return null;
    }
}
