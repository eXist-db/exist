
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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

public class FunCount extends Function {

    public FunCount(BrokerPool pool) {
		super(pool, "count");
    }

    public int returnsType() {
		return Constants.TYPE_NUM;
    }

    public DocumentSet preselect(DocumentSet in_docs) {
		return getArgument(0).preselect(in_docs);
    }

    public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet,
    	NodeProxy contextNode) {
    	if(contextNode != null) {
    		contextSet = new SingleNodeSet(contextNode);
    	}
		NodeSet temp = (NodeSet)getArgument(0).eval(context, docs, contextSet).getNodeList();
		return new ValueNumber(temp.getLength());
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("count(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
    }
}
			  
