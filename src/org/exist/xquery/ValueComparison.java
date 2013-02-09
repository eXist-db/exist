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
import org.exist.xquery.value.Type;

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
            {context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "genericCompare");}  
		final Sequence ls = getLeft().eval(contextSequence, contextItem);
		final Sequence rs = getRight().eval(contextSequence, contextItem);
		if(ls.isEmpty() || rs.isEmpty())
			{return Sequence.EMPTY_SEQUENCE;}
		if (ls.hasOne() && rs.hasOne()) {
            AtomicValue lv, rv;
			lv = ls.itemAt(0).atomize();
			rv = rs.itemAt(0).atomize();
            final Collator collator = getCollator(contextSequence);
			return BooleanValue.valueOf(compareAtomic(collator, lv, rv, Constants.TRUNC_NONE, relation));
		} 
        throw new XPathException(this, "Type error: sequence with more than one item is not allowed here");
	}

	protected Sequence nodeSetCompare(NodeSet nodes, Sequence contextSequence) throws XPathException {		
        if (context.getProfiler().isEnabled())
            {context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare");}		
		final NodeSet result = new ExtArrayNodeSet();
        final Collator collator = getCollator(contextSequence);
        if (contextSequence != null && !contextSequence.isEmpty()) {
            for (final Iterator<NodeProxy> i = nodes.iterator(); i.hasNext();) {
                final NodeProxy current = i.next();
                ContextItem context = current.getContext();
                if (context==null) {
                   throw new XPathException(this,"Context is missing for node set comparison");
                }
                do {
                    final AtomicValue lv = current.atomize();
                    final Sequence rs = getRight().eval(context.getNode().toSequence());                    
                    if (!rs.hasOne())
                        {throw new XPathException(this,
                                "Type error: sequence with less or more than one item is not allowed here");}                    
                    if (compareAtomic(collator, lv, rs.itemAt(0).atomize(), Constants.TRUNC_NONE, relation))
                        {result.add(current);}
                } while ((context = context.getNextDirect()) != null);
            }
        } else {
            final Sequence rs = getRight().eval(null);
            if (!rs.hasOne())
                {throw new XPathException(this,
                        "Type error: sequence with less or more than one item is not allowed here");}
            final AtomicValue rv = rs.itemAt(0).atomize();
            for (final Iterator<NodeProxy> i = nodes.iterator(); i.hasNext();) {
                final NodeProxy current = i.next();
                final AtomicValue lv = current.atomize();
                if (compareAtomic(collator, lv, rv, Constants.TRUNC_NONE, Constants.EQ))
                    {result.add(current);}
            }
        }
        return result;
	}
	
    /**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 */
	public static boolean compareAtomic(Collator collator, AtomicValue lv, AtomicValue rv, int truncation, int relation) throws XPathException {
		int ltype = lv.getType();
		int rtype = rv.getType();
		if (ltype == Type.UNTYPED_ATOMIC) {
			//If the atomized operand is of type xs:untypedAtomic, it is cast to xs:string.
			lv = lv.convertTo(Type.STRING);
		} 
		if (rtype == Type.UNTYPED_ATOMIC) {
			//If the atomized operand is of type xs:untypedAtomic, it is cast to xs:string.
			rv = rv.convertTo(Type.STRING);
		}
		ltype = lv.getType();
		rtype = rv.getType();
		final int ctype = Type.getCommonSuperType(ltype, rtype);
		//Next, if possible, the two operands are converted to their least common type 
		//by a combination of type promotion and subtype substitution.
		if (ctype == Type.NUMBER) {
			//Numeric type promotion:

			//A value of type xs:decimal (or any type derived by restriction from xs:decimal) 
			//can be promoted to either of the types xs:float or xs:double. The result of this promotion is created by casting the original value to the required type. This kind of promotion may cause loss of precision.
			if (ltype == Type.DECIMAL) {
				if (rtype == Type.FLOAT)
					{lv = lv.convertTo(Type.FLOAT);}
				else if (rtype == Type.DOUBLE)
					{lv = lv.convertTo(Type.DOUBLE);}				
			} else if (rtype == Type.DECIMAL) {
				if (ltype == Type.FLOAT)
					{rv = rv.convertTo(Type.FLOAT);}
				else if (ltype == Type.DOUBLE)
					{rv = rv.convertTo(Type.DOUBLE);}				
			} else {
				//A value of type xs:float (or any type derived by restriction from xs:float) 
				//can be promoted to the type xs:double. 
				//The result is the xs:double value that is the same as the original value.
				if (ltype == Type.FLOAT && rtype == Type.DOUBLE)
					{lv = lv.convertTo(Type.DOUBLE);}
				if (rtype == Type.FLOAT && ltype == Type.DOUBLE)
					{rv = rv.convertTo(Type.DOUBLE);}
			}
		} else {
			lv = lv.convertTo(ctype);
			rv = rv.convertTo(ctype);
		}

		// if truncation is set, we always do a string comparison
        if (truncation != Constants.TRUNC_NONE) {
        	//TODO : log this ?
            lv = lv.convertTo(Type.STRING);
        }
		switch(truncation) {
			case Constants.TRUNC_RIGHT:
				return lv.startsWith(collator, rv);
			case Constants.TRUNC_LEFT:
				return lv.endsWith(collator, rv);
			case Constants.TRUNC_BOTH:
				return lv.contains(collator, rv);
			default:
				return lv.compareTo(collator, relation, rv);
		}
	}	
    
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(" ").display(Constants.VOPS[relation]).display(" ");
        getRight().dump(dumper);
    }
    
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(getLeft().toString());
        result.append(" ").append(Constants.VOPS[relation]).append(" ");
        result.append(getRight().toString());
        return result.toString();
    }        
}
