/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
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
public class ChildSelector implements NodeSelector {

    private NodeSet context;
    private int contextId;

    public ChildSelector(NodeSet contextSet, int contextId) {
        this.context = contextSet;
        this.contextId = contextId;
    }

    public NodeProxy match(DocumentImpl doc, NodeId nodeId) {
        final NodeProxy contextNode = context.parentWithChild(doc, nodeId, true, false);
        if (contextNode == null)
           {return null;}
        final NodeProxy p = new NodeProxy(doc, nodeId);
        if (Expression.NO_CONTEXT_ID != contextId) {
            p.deepCopyContext(contextNode, contextId);
        } else
            {p.copyContext(contextNode);}
        return p;
    }
}
