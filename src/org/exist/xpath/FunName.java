/* eXist Open Source Native XML Database
 * Copyright (C) 2000-01,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * 
 * $Id:
 */

package org.exist.xpath;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * xpath-library function: string(object)
 *
 */
public class FunName extends Function {

    public FunName(BrokerPool pool) {
        super(pool, "name");
    }
	
    public int returnsType() {
        return Constants.TYPE_STRING;
    }
	
    public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
        Node n;
        if(getArgumentCount() > 0) {
            NodeSet set = new SingleNodeSet(node);
            NodeSet result = (NodeSet)getArgument(0).eval(docs, set, null).getNodeList();
            n = result.item(0);
        } else
            n = node.getNode();
        switch(n.getNodeType()) {
        case Node.ELEMENT_NODE:
            return new ValueString(((Element)n).getLocalName());
        case Node.ATTRIBUTE_NODE:
            return new ValueString(((Attr)n).getName());
        default:
            return new ValueString("");
        }
    }

    public String pprint() {
        StringBuffer buf = new StringBuffer();
        buf.append("name(");
        if(getArgumentCount() > 0)
            buf.append(getArgument(0).pprint());
        buf.append(")");
        return buf.toString();
    }
}
