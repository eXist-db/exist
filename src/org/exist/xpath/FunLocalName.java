/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.w3c.dom.Node;

/**
 * xpath-library function: local-name(object)
 *
 */
public class FunLocalName extends Function {

    public FunLocalName() {
        super("local-name");
    }
	
    public int returnsType() {
        return Constants.TYPE_STRING;
    }
	
    public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
    	NodeProxy contextNode) throws XPathException {
        Node n = null;
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
        if(getArgumentCount() > 0) {
            NodeSet result = (NodeSet)getArgument(0).eval(context, docs, contextSet).getNodeList();
            if(result.getLength() > 0)
            	n = result.item(0);
        } else if(contextSet.getLength() > 0)
            n = contextSet.item(0);
        else
        	return new ValueString("");
        if(n != null) {
	        switch(n.getNodeType()) {
	        	case Node.ELEMENT_NODE:
	            	return new ValueString(n.getLocalName());
	        	case Node.ATTRIBUTE_NODE:
	            	return new ValueString(n.getLocalName());
	        	default:
	            	return new ValueString("");
	        }
        } return new ValueString("");
    }
}
