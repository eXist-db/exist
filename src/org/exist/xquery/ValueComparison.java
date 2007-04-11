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

import java.text.Collator;
import java.util.Iterator;

import org.exist.dom.ContextItem;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ValueComparison extends GeneralComparison {

	/**
	 * @param context
	 * @param relation
	 */
	public ValueComparison(XQueryContext context, int relation) {
		super(context, relation);
	}

	/**
	 * @param context
	 * @param left
	 * @param right
	 * @param relation
	 */
	public ValueComparison(XQueryContext context, Expression left, Expression right, int relation) {
		super(context, left, right, relation);
	}

	protected Sequence genericCompare(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled())
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "genericCompare");  
		Sequence ls = getLeft().eval(contextSequence, contextItem);
		Sequence rs = getRight().eval(contextSequence, contextItem);
		if(ls.isEmpty() || rs.isEmpty())
			return Sequence.EMPTY_SEQUENCE;
		if (ls.hasOne() && rs.hasOne()) {
            AtomicValue lv, rv;
			lv = ls.itemAt(0).atomize();
			rv = rs.itemAt(0).atomize();
            Collator collator = getCollator(contextSequence);
			return BooleanValue.valueOf(compareValues(collator, lv, rv, Constants.TRUNC_NONE, relation));
		} 
        throw new XPathException(getASTNode(), "Type error: sequence with more than one item is not allowed here");
	}

	protected Sequence nodeSetCompare(NodeSet nodes, Sequence contextSequence) throws XPathException {		
        if (context.getProfiler().isEnabled())
            context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare");		
		NodeSet result = new ExtArrayNodeSet();
        Collator collator = getCollator(contextSequence);
        if (contextSequence != null && !contextSequence.isEmpty()) {
            for (Iterator i = nodes.iterator(); i.hasNext();) {
                NodeProxy current = (NodeProxy) i.next();
                ContextItem context = current.getContext();
                if (context==null) {
                   throw new XPathException(getASTNode(),"Context is missing for node set comparison");
                }
                do {
                    AtomicValue lv = current.atomize();
                    Sequence rs = getRight().eval(context.getNode().toSequence());                    
                    if (!rs.hasOne())
                        throw new XPathException(getASTNode(),
                                "Type error: sequence with less or more than one item is not allowed here");                    
                    if (compareValues(collator, lv, rs.itemAt(0).atomize(), Constants.TRUNC_NONE, Constants.EQ))
                        result.add(current);
                } while ((context = context.getNextDirect()) != null);
            }
        } else {
            Sequence rs = getRight().eval(null);
            if (!rs.hasOne())
                throw new XPathException(getASTNode(),
                        "Type error: sequence with less or more than one item is not allowed here");
            AtomicValue rv = rs.itemAt(0).atomize();
            for (Iterator i = nodes.iterator(); i.hasNext();) {
                NodeProxy current = (NodeProxy) i.next();
                AtomicValue lv = current.atomize();
                if (compareValues(collator, lv, rv, Constants.TRUNC_NONE, Constants.EQ))
                    result.add(current);
            }
        }
        return result;
	}
    
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(" ").display(Constants.VOPS[relation]).display(" ");
        getRight().dump(dumper);
    }
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append(getLeft().toString());
        result.append(" ").append(Constants.VOPS[relation]).append(" ");
        result.append(getRight().toString());
        return result.toString();
    }        
}
