/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
 *  http://exist-db.org
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
package org.exist.xquery.functions.text;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FilterNested extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
    	new QName("filter-nested", TextModule.NAMESPACE_URI, TextModule.PREFIX),
    		"Filters out all nodes in the node set, which do have descendant nodes in the same node set.  " +
            "This is useful if you do a combined query like //(a|b)[. &= $terms] and some 'b' nodes are nested " +
            "within 'a' nodes, but you only want to see the innermost matches, i.e. the 'b' nodes, not the 'a' nodes " +
            "containing 'b' nodes.",
            new SequenceType[]{
    			new FunctionParameterSequenceType("node-set", Type.NODE, Cardinality.ZERO_OR_MORE, "The node set")},
    		new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "a node set containing nodes that do not have descendent nodes."));

    public FilterNested(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}
        final ExtArrayNodeSet inSet = (ExtArrayNodeSet) args[0].toNodeSet();
        final NodeSet filtered = new ExtArrayNodeSet();
        for (final NodeProxy p : inSet) {
            if (inSet.hasDescendantsInSet(p.getDocument(), p.getNodeId(), false, -1) == null)
                {filtered.add(p);}
        }
        return filtered;
    }
}