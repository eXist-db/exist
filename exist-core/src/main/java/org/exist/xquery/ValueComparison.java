/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import com.ibm.icu.text.Collator;
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class ValueComparison extends GeneralComparison {

	/**
	 * Construct a ValueComparison using the given relation.
	 *
	 * @param context current context
	 * @param relation the relation to compare by
	 */
	public ValueComparison(XQueryContext context, Comparison relation) {
		super(context, relation);
	}

	/**
	 * Construct a ValueComparison comparing the given expressions using the given relation
	 *
	 * @param context current context
	 * @param left left hand operand
	 * @param right right hand operand
	 * @param relation the relation to compare by
	 */
	public ValueComparison(XQueryContext context, Expression left, Expression right, Comparison relation) {
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
			final AtomicValue lv = ls.itemAt(0).atomize();
			final AtomicValue rv = rs.itemAt(0).atomize();
            final Collator collator = getCollator(contextSequence);
			return BooleanValue.valueOf(compareAtomic(collator, lv, rv, StringTruncationOperator.NONE, relation));
		}
        throw new XPathException(this, ErrorCodes.XPTY0004, "Type error: sequence with more than one item is not allowed here");
	}

	protected Sequence nodeSetCompare(NodeSet nodes, Sequence contextSequence) throws XPathException {		
        if (context.getProfiler().isEnabled())
            {context.getProfiler().message(this, Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "nodeSetCompare");}		
		final NodeSet result = new ExtArrayNodeSet();
        final Collator collator = getCollator(contextSequence);
        if (contextSequence != null && !contextSequence.isEmpty()) {
            for (final NodeProxy current : nodes) {
                ContextItem context = current.getContext();
                if (context == null) {
                    throw new XPathException(this, ErrorCodes.XPDY0002, "Context is missing for node set comparison");
                }
                do {
                    final AtomicValue lv = current.atomize();
                    final Sequence rs = getRight().eval(context.getNode().toSequence(), null);
                    if (!rs.hasOne()) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
								"Type error: sequence with less or more than one item is not allowed here");
                    }
                    if (compareAtomic(collator, lv, rs.itemAt(0).atomize(), StringTruncationOperator.NONE, relation)) {
                        result.add(current);
                    }
                } while ((context = context.getNextDirect()) != null);
            }
        } else {
            final Sequence rs = getRight().eval(null, null);
            if (!rs.hasOne())
                {throw new XPathException(this, ErrorCodes.XPTY0004,
						"Type error: sequence with less or more than one item is not allowed here");}
            final AtomicValue rv = rs.itemAt(0).atomize();
            for (final NodeProxy current : nodes) {
                final AtomicValue lv = current.atomize();
                if (compareAtomic(collator, lv, rv, StringTruncationOperator.NONE, Comparison.EQ)) {
                    result.add(current);
                }
            }
        }
        return result;
	}
	
    /**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 *
	 * @param collator the collator to use
	 * @param lv left hand operand value
	 * @param rv right hand operand value
	 * @param truncation should strings be truncated before comparison
	 *                   ({@link StringTruncationOperator#NONE} by default)
	 * @param relation the relation to compare by
	 * @return the result of the comparison
	 * @throws XPathException in case of dynamic error
	 */
	public static boolean compareAtomic(Collator collator, AtomicValue lv, AtomicValue rv, StringTruncationOperator truncation, Comparison relation) throws XPathException {
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
		if (ctype == Type.NUMERIC) {
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
        if (truncation != StringTruncationOperator.NONE) {
        	//TODO : log this ?
            lv = lv.convertTo(Type.STRING);
        }
        return switch (truncation) {
            case RIGHT -> lv.startsWith(collator, rv);
            case LEFT -> lv.endsWith(collator, rv);
            case BOTH -> lv.contains(collator, rv);
            default -> lv.compareTo(collator, relation, rv);
        };
	}	
    
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(" ").display(relation.valueComparisonSymbol).display(" ");
        getRight().dump(dumper);
    }
    
    public String toString() {
		return getLeft().toString() +	" " + relation.valueComparisonSymbol + " " + getRight().toString();
    }
}
