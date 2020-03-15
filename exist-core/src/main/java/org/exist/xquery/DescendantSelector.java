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
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class DescendantSelector implements NodeSelector {

    protected NodeSet context;
    protected int contextId;

    public DescendantSelector(NodeSet contextSet, int contextId) {
        this.context = contextSet;
        this.contextId = contextId;
    }

    public NodeProxy match(DocumentImpl doc, NodeId nodeId) {
        final NodeProxy p = new NodeProxy(doc, nodeId);
        final NodeProxy contextNode = context.parentWithChild(doc, nodeId, false, false);
        if (contextNode == null)
            {return null;}
        if (Expression.NO_CONTEXT_ID != contextId) {
            p.deepCopyContext(contextNode, contextId);
        } else
            {p.copyContext(contextNode);}
        return p;
    }
}
