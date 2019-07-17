/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.ngram.utils;

import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.NodeSetIterator;
import org.exist.xquery.XPathException;

import java.util.function.Function;

public final class NodeSets {

    private NodeSets() {
    }

    /**
     * Builds a new NodeSet by applying a function to all NodeProxys of the supplied NodeSet and returning all non-null
     * results.
     * 
     * @param nodes
     *            the NodeSet containig the NodeProys to be transformed
     * @param transform
     *            the function to be applied to all NodeProxys in nodes
     * @return a new NodeSet containing the non-null results of f applied to the NodeProxys in nodes
     *
     * @throws XPathException if an error occurs with the query.
     */
    public static NodeSet transformNodes(final NodeSet nodes, final Function<NodeProxy, NodeProxy> transform) throws XPathException {
        final NodeSet result = new ExtArrayNodeSet();
        for(final NodeSetIterator iterator = nodes.iterator(); iterator.hasNext();) {
            final NodeProxy node = transform.apply(iterator.next());
            if (node != null) {
                result.add(node);
            }
        }
        result.iterate(); // ensure result is ready to use
        return result;
    }

    public static NodeSet getNodesMatchingAtStart(final NodeSet nodes, final int expressionId) throws XPathException {
        return transformNodes(nodes, proxy ->
                NodeProxies.transformOwnMatches(
                        proxy,
                        match -> match.filterOffsetsStartingAt(0),
                        expressionId
                )
        );
    }

    public static NodeSet getNodesMatchingAtEnd(final NodeSet nodes, final int expressionId) throws XPathException {
        return transformNodes(nodes, proxy ->
                NodeProxies.transformOwnMatches(
                        proxy,
                        match -> match.filterOffsetsEndingAt(proxy.getNodeValue().length()),
                        expressionId
                )
        );
    }

}
