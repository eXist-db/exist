
/* eXist Open Source Native XML Database
 * Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeIDSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public class OpOr extends BinaryOp {

    private static Category LOG = Category.getInstance(OpOr.class.getName());

    public OpOr(BrokerPool pool) {
        super(pool);
    }

    public DocumentSet preselect(DocumentSet in_docs) {
        if(getLength() == 0)
            return in_docs;
        DocumentSet out_docs = getExpression(0).preselect(in_docs);
        for(int i = 1; i < getLength(); i++)
            out_docs = out_docs.union(getExpression(i).preselect(in_docs));
        return out_docs;
    }

    public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
        if(getLength() == 0)
            return new ValueNodeSet(context);
        NodeSet rr, rl = (NodeSet)getExpression(0).eval(docs, context, null).getNodeList();
        if(context != null)
            rl = getParents(rl, context);
        for(int i = 1; i < getLength(); i++) {
            rr = (NodeSet)getExpression(i).eval(docs, context, null).getNodeList();
            if(context != null)
                rr = getParents(rr, context);
            rl = rl.union(rr);
        }
        return new ValueNodeSet(rl);
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
		buf.append(getExpression(0).pprint());
		for(int i = 1; i < getLength(); i++) {
			buf.append(" or ");
			buf.append(getExpression(i).pprint());
		}
        return buf.toString();
    }

    protected static final NodeIDSet getParents(NodeSet child,
            NodeSet parents) {
        NodeIDSet result = new NodeIDSet();
        long pid;
        NodeProxy l, parent;
        for(Iterator i = child.iterator(); i.hasNext(); ) {
            l = (NodeProxy)i.next();
            parent = parents.parentWithChild(l.doc, l.getGID(), true, true);
            if(parent != null) result.add(parent);
        }
        return result;
    }
}
