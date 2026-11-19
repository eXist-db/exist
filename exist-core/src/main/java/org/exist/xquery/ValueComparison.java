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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.ibm.icu.text.Collator;
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.ExtArrayNodeSet;
import java.util.HashSet;
import java.util.Set;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

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

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence result = super.eval(contextSequence, contextItem);
        if (inPredicate && result instanceof final NodeSet selected) {
            checkCardinalityOfSelected(selected);
        }
        return result;
    }

    /**
     * In a predicate over stored nodes, the comparison is decided for the left operand's nodes one by one, and those
     * that match are mapped back to their context nodes: nothing checks how many items the operand has for a context
     * node. XQuery 3.1 §2.3.4 requires that check for a context node the comparison selects, whose operand was
     * evaluated, and allows skipping the others; so only the selected context nodes are checked.
     *
     * <p>Not covered yet (#7002): where a range index answers the comparison, it is replaced by a range lookup or, with
     * an index configured with fields, by a field lookup, and this method is not reached; where the legacy index
     * pre-selects it, the operand's step holds only the matching nodes, so a context node's other items are not
     * seen.</p>
     *
     * @param selected the left operand's nodes that matched, carrying their context nodes
     * @throws XPathException XPTY0004 if a selected context node has more than one item in the left operand
     */
    private void checkCardinalityOfSelected(final NodeSet selected) throws XPathException {
        // Keyed by document and node id, not looked up in a node set: a node set sorts before it answers a lookup, so a
        // lookup after each addition sorts again and again, and takes quadratic time in the number of nodes.
        final Set<String> contextKeys = new HashSet<>();
        final NodeSet contextNodes = new NewArrayNodeSet();
        for (final NodeProxy node : selected) {
            for (ContextItem item = node.getContext(); item != null; item = item.getNextDirect()) {
                if (item.getContextId() == contextId && contextKeys.add(key(item.getNode()))) {
                    contextNodes.add(item.getNode());
                }
            }
        }
        if (contextNodes.isEmpty()) {
            return;
        }
        // one evaluation over all selected context nodes, as nodeSetCompare does over the whole context
        final Set<String> seen = new HashSet<>();
        for (final SequenceIterator i = getLeft().eval(contextNodes, null).iterate(); i.hasNext(); ) {
            if (i.nextItem() instanceof final NodeProxy operandNode) {
                for (ContextItem item = operandNode.getContext(); item != null; item = item.getNextDirect()) {
                    if (item.getContextId() == contextId && !seen.add(key(item.getNode()))) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Type error: sequence with more than one item is not allowed here");
                    }
                }
            }
        }
    }

    private static String key(final NodeProxy node) {
        return node.getOwnerDocument().getDocId() + "/" + node.getNodeId();
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
	public static boolean compareAtomic(@Nullable final Collator collator, AtomicValue lv, AtomicValue rv, final StringTruncationOperator truncation, final Comparison relation) throws XPathException {
		final Tuple2<AtomicValue, AtomicValue> comparableLvRv = makeComparable(lv, rv, truncation);
		lv = comparableLvRv._1;
		rv = comparableLvRv._2;

		return switch (truncation) {
			case RIGHT -> lv.startsWith(collator, rv);
			case LEFT -> lv.endsWith(collator, rv);
			case BOTH -> lv.contains(collator, rv);
			default -> lv.compareTo(collator, relation, rv);
		};
	}

	/**
	 * Cast the atomic operands into a comparable type
	 * and compare them.
	 *
	 * @param collator the collator to use
	 * @param lv left hand operand value
	 * @param rv right hand operand value
	 * @return the result of the comparison
	 * @throws XPathException in case of dynamic error
	 */
	public static int compareAtomic(@Nullable final Collator collator, AtomicValue lv, AtomicValue rv) throws XPathException {
		final Tuple2<AtomicValue, AtomicValue> comparableLvRv = makeComparable(lv, rv, StringTruncationOperator.NONE);
		lv = comparableLvRv._1;
		rv = comparableLvRv._2;

		return lv.compareTo(collator, rv);
	}

	/**
	 * Cast the atomic operands into a comparable type.
	 *
	 * @param lv left hand operand value
	 * @param rv right hand operand value
	 * @param truncation should strings be truncated before comparison
	 *                   ({@link StringTruncationOperator#NONE} by default)
	 * @return the result of the comparison
	 * @throws XPathException in case of dynamic error
	 */
	private static Tuple2<AtomicValue, AtomicValue> makeComparable(AtomicValue lv, AtomicValue rv, final StringTruncationOperator truncation) throws XPathException {
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
					if (rtype == Type.FLOAT) {
						lv = lv.convertTo(Type.FLOAT);
					} else if (rtype == Type.DOUBLE) {
						lv = lv.convertTo(Type.DOUBLE);
					}
				} else if (rtype == Type.DECIMAL) {
					if (ltype == Type.FLOAT) {
						rv = rv.convertTo(Type.FLOAT);
					} else if (ltype == Type.DOUBLE) {
						rv = rv.convertTo(Type.DOUBLE);
					}
				} else {
					//A value of type xs:float (or any type derived by restriction from xs:float)
					//can be promoted to the type xs:double.
					//The result is the xs:double value that is the same as the original value.
					if (ltype == Type.FLOAT && rtype == Type.DOUBLE) {
						lv = lv.convertTo(Type.DOUBLE);
					}
					if (rtype == Type.FLOAT && ltype == Type.DOUBLE) {
						rv = rv.convertTo(Type.DOUBLE);
					}
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

			return Tuple(lv, rv);
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
