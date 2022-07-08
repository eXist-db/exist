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
package org.exist.xquery;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;

/**
 * @author wolf
 */
public class SelfSelector implements NodeSelector {

    private int contextId;
    private NodeSet context;

    public SelfSelector(NodeSet contextSet, int contextId) {
        this.context = contextSet;
        this.contextId = contextId;
    }

    public NodeProxy match(DocumentImpl doc, NodeId nodeId) {
        final NodeProxy p = new NodeProxy(null, doc, nodeId);
        final NodeProxy contextNode = context.get(doc, nodeId);
        if (contextNode != null) {
            if (Expression.NO_CONTEXT_ID != contextId) {
                p.deepCopyContext(contextNode, contextId);
            } else {
            	p.addContextNode(contextId, p);
            }
            p.addMatches(contextNode);
            return p;
        } else {
        	return null;
        }
    }
}
