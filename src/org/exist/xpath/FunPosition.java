
/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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
 * $Id$
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xpath.value.IntegerValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;

/**
 * xpath-library function: position()
 *
 */
public class FunPosition extends Function {

    public FunPosition() {
        super("position");
    }

    public int returnsType() {
        return Type.INTEGER;
    }

    public DocumentSet preselect(DocumentSet in_docs, StaticContext context) {
        return in_docs;
    }

    public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
    	Item contextItem) throws XPathException {
    	int  count = 1;
    	switch(contextSequence.getItemType()) {
    		case Type.NODE:
    			NodeProxy contextNode = (NodeProxy)contextItem;
    			NodeSet contextSet = (NodeSet)contextSequence;	
	        	DocumentImpl doc = contextNode.getDoc();
		        NodeSet set = ((ArraySet)contextSet).getSiblings(doc, contextNode.getGID());
		        // determine position of current node in the set
		        NodeProxy p;
		        for(Iterator i = set.iterator(); i.hasNext(); count++) {
		            p = (NodeProxy)i.next();
		            if(p.gid == contextNode.gid && contextNode.doc.getDocId() == contextNode.doc.getDocId())
		                return new IntegerValue(count);
		        }
		        break;
		     default:
		     	for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); count++) {
		     		if(i.nextItem() == contextItem)
		     			return new IntegerValue(count);
		     	}
		     	break;
    	}
        return new IntegerValue(-1);
    }

    public String pprint() {
        return "position()";
    }
}
