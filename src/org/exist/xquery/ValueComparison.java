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
			return BooleanValue.valueOf(compareAtomic(collator, lv, rv, Constants.TRUNC_NONE, relation));
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
                    if (compareAtomic(collator, lv, rs.itemAt(0).atomize(), Constants.TRUNC_NONE, Constants.EQ))
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
                if (compareAtomic(collator, lv, rv, Constants.TRUNC_NONE, Constants.EQ))
                    result.add(current);
            }
        }
        return result;
	}
	
    /**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 */
	public static boolean compareAtomic(Collator collator, AtomicValue lv, AtomicValue rv, int truncation, int relation) throws XPathException {
		
		//TODo : refactor casting according to the specs ; right now, copied from GeneralComparison		
		
		int ltype = lv.getType();
		int rtype = rv.getType();
		if (ltype == Type.UNTYPED_ATOMIC) {
			//If one of the atomic values is an instance of xdt:untypedAtomic
			//and the other is an instance of a numeric type,
			//then the xdt:untypedAtomic value is cast to the type xs:double.
			if (Type.subTypeOf(rtype, Type.NUMBER)) {
			    //if(isEmptyString(lv))
			    //    return false;
				lv = lv.convertTo(Type.DOUBLE);
			//If one of the atomic values is an instance of xdt:untypedAtomic
			//and the other is an instance of xdt:untypedAtomic or xs:string,
			//then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
			} else if (rtype == Type.UNTYPED_ATOMIC || rtype == Type.STRING) {
				lv = lv.convertTo(Type.STRING);
				//if (rtype == Type.UNTYPED_ATOMIC)
					//rv = rv.convertTo(Type.STRING);
				//If one of the atomic values is an instance of xdt:untypedAtomic
				//and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type,
				//then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
			} else
				lv = lv.convertTo(rtype);
		} 
		if (rtype == Type.UNTYPED_ATOMIC) {
			//If one of the atomic values is an instance of xdt:untypedAtomic
			//and the other is an instance of a numeric type,
			//then the xdt:untypedAtomic value is cast to the type xs:double.
			if (Type.subTypeOf(ltype, Type.NUMBER)) {
			    //if(isEmptyString(lv))
			    //    return false;
				rv = rv.convertTo(Type.DOUBLE);
			//If one of the atomic values is an instance of xdt:untypedAtomic
			//and the other is an instance of xdt:untypedAtomic or xs:string,
			//then the xdt:untypedAtomic value (or values) is (are) cast to the type xs:string.
			} else if (ltype == Type.UNTYPED_ATOMIC || ltype == Type.STRING) {
				rv = rv.convertTo(Type.STRING);
				//if (ltype == Type.UNTYPED_ATOMIC)
				//	lv = lv.convertTo(Type.STRING);
			//If one of the atomic values is an instance of xdt:untypedAtomic
			//and the other is not an instance of xs:string, xdt:untypedAtomic, or any numeric type,
			//then the xdt:untypedAtomic value is cast to the dynamic type of the other value.
			} else
				rv = rv.convertTo(ltype);
		}
		/*
		if (backwardsCompatible) {
			if (!"".equals(lv.getStringValue()) && !"".equals(rv.getStringValue())) {
				// in XPath 1.0 compatible mode, if one of the operands is a number, cast
				// both operands to xs:double
				if (Type.subTypeOf(ltype, Type.NUMBER)
					|| Type.subTypeOf(rtype, Type.NUMBER)) {
						lv = lv.convertTo(Type.DOUBLE);
						rv = rv.convertTo(Type.DOUBLE);
				}
			}
		}
		*/
        // if truncation is set, we always do a string comparison
        if (truncation != Constants.TRUNC_NONE) {
        	//TODO : log this ?
            lv = lv.convertTo(Type.STRING);
        }
//			System.out.println(
//				lv.getStringValue() + Constants.OPS[relation] + rv.getStringValue());
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
        StringBuffer result = new StringBuffer();
        result.append(getLeft().toString());
        result.append(" ").append(Constants.VOPS[relation]).append(" ");
        result.append(getRight().toString());
        return result.toString();
    }        
}
