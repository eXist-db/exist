/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;


/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class DescendantOrSelfSelector extends DescendantSelector {

    public DescendantOrSelfSelector(NodeSet contextSet, int contextId) {
        super(contextSet, contextId);
    }

    public NodeProxy match(DocumentImpl doc, NodeId nodeId) {
        final NodeProxy contextNode = context.parentWithChild(doc, nodeId, false, true);
        if(contextNode == null)
            {return null;}
        final NodeProxy p = new NodeProxy(doc, nodeId);
        if (Expression.NO_CONTEXT_ID != contextId) {
            p.deepCopyContext(contextNode, contextId);
        } else
            {p.copyContext(contextNode);}
        return p;
    }
}