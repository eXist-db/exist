
/* eXist Native XML Database
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
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

/**
 * xpath-library function: position()
 *
 */
public class FunPosition extends Function {

    public FunPosition(BrokerPool pool) {
        super(pool, "position");
    }

    public int returnsType() {
        return Constants.TYPE_NUM;
    }

    public DocumentSet preselect(DocumentSet in_docs) {
        return in_docs;
    }

    public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
    	NodeProxy contextNode) {
        DocumentImpl doc = contextNode.getDoc();
        NodeSet set = ((ArraySet)contextSet).getChildren(doc, contextNode.getGID());
        // determine position of current node in the set
        NodeProxy p;
        double count = 1.0;
        for(Iterator i = set.iterator(); i.hasNext(); count++) {
            p = (NodeProxy)i.next();
            if(p.gid == contextNode.gid && contextNode.doc.getDocId() == contextNode.doc.getDocId())
                return new ValueNumber(count);
        }
        return new ValueNumber(-1);
    }

    public String pprint() {
        return "position()";
    }
}
