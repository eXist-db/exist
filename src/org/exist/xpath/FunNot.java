/* eXist Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.xpath;

import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.dom.TreeNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.util.LongLinkedList;

public class FunNot extends Function {

    public FunNot() {
        super("not");
    }

    public int returnsType() {
        return Constants.TYPE_NODELIST;
    }

    public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
        return getArgument(0).preselect(in_docs, context);
    }

    public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
    	NodeProxy contextNode) throws XPathException {
        NodeSet result = new TreeNodeSet();
        Expression path = getArgument(0);
        result.addAll(contextSet);
        NodeProxy current;
        if(inPredicate)
        	for(Iterator i = result.iterator(); i.hasNext(); ) {
        		current = (NodeProxy)i.next();
        		current.addContextNode(current);
        	}
        // evaluate argument expression
        NodeSet nodes = (NodeSet)path.eval(context, docs, contextSet, contextNode).getNodeList();
        NodeProxy parent;
        long pid;
        LongLinkedList contextNodes;
        LongLinkedList.ListItem next;
        // iterate through nodes and remove hits from result
        for(Iterator i = nodes.iterator(); i.hasNext(); ) {
            current = (NodeProxy)i.next();
			contextNodes = current.getContext();
			if(contextNodes == null) {
				LOG.warn("context node is missing!");
				break;
			}
			for(Iterator j = contextNodes.iterator(); j.hasNext(); ) {
				next = (LongLinkedList.ListItem)j.next();
				if((parent = result.get(current.doc, next.l)) != null)
                result.remove(parent);
        	}
        }
        return new ValueNodeSet(result);
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append("not(");
        buf.append(getArgument(0).pprint());
        buf.append(')');
        return buf.toString();
    }
}
