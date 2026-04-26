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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:op (XQuery 4.0).
 *
 * Returns a function reference for a named operator.
 */
public class FnOp extends BasicFunction {

    private static final QName PARAM_A = new QName("a", javax.xml.XMLConstants.NULL_NS_URI);
    private static final QName PARAM_B = new QName("b", javax.xml.XMLConstants.NULL_NS_URI);

    public static final FunctionSignature FN_OP = new FunctionSignature(
            new QName("op", Function.BUILTIN_FUNCTION_NS),
            "Returns a function that applies a given operator.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("operator", Type.STRING, Cardinality.EXACTLY_ONE, "The operator name")
            },
            new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.EXACTLY_ONE, "a function implementing the operator"));

    public FnOp(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String operator = args[0].getStringValue();

        // Validate operator name
        if (!isValidOperator(operator)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Unknown operator: " + operator);
        }

        // Create a UserDefinedFunction with 2 parameters ($a, $b)
        final FunctionSignature opSig = new FunctionSignature(
                new QName("op#" + operator, Function.BUILTIN_FUNCTION_NS),
                new SequenceType[] {
                        new FunctionParameterSequenceType("a", Type.ITEM, Cardinality.ZERO_OR_MORE, "left operand"),
                        new FunctionParameterSequenceType("b", Type.ITEM, Cardinality.ZERO_OR_MORE, "right operand")
                },
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result"));

        final UserDefinedFunction func = new UserDefinedFunction(context, opSig);
        func.addVariable(PARAM_A);
        func.addVariable(PARAM_B);

        // Set the body to an expression that evaluates the operator
        func.setFunctionBody(new OperatorExpression(context, operator));

        final FunctionCall call = new FunctionCall(context, func);
        call.setLocation(getLine(), getColumn());

        return new FunctionReference(this, call);
    }

    private boolean isValidOperator(final String op) {
        switch (op) {
            case ",": case "and": case "or":
            case "+": case "-": case "*": case "div": case "idiv": case "mod":
            case "=": case "<": case "<=": case ">": case ">=": case "!=":
            case "eq": case "lt": case "le": case "gt": case "ge": case "ne":
            case "<<": case ">>": case "precedes": case "follows":
            case "precedes-or-is": case "follows-or-is":
            case "is": case "is-not":
            case "||": case "|": case "union": case "except": case "intersect":
            case "to": case "otherwise":
                return true;
            default:
                return false;
        }
    }

    /**
     * Expression that evaluates an operator on two variables $a and $b
     * from the local variable context.
     */
    private static class OperatorExpression extends AbstractExpression {

        private final String operator;

        OperatorExpression(final XQueryContext context, final String operator) {
            super(context);
            this.operator = operator;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence a = context.resolveVariable(PARAM_A).getValue();
            final Sequence b = context.resolveVariable(PARAM_B).getValue();

            switch (operator) {
                // Arithmetic
                case "+": return arithmetic(a, b, "plus");
                case "-": return arithmetic(a, b, "minus");
                case "*": return arithmetic(a, b, "mult");
                case "div": return arithmetic(a, b, "div");
                case "idiv": return arithmetic(a, b, "idiv");
                case "mod": return arithmetic(a, b, "mod");

                // General comparison
                case "=": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.EQ);
                case "!=": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.NEQ);
                case "<": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.LT);
                case "<=": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.LTEQ);
                case ">": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.GT);
                case ">=": return generalCompare(a, b, org.exist.xquery.Constants.Comparison.GTEQ);

                // Value comparison
                case "eq": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.EQ);
                case "ne": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.NEQ);
                case "lt": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.LT);
                case "le": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.LTEQ);
                case "gt": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.GT);
                case "ge": return valueCompare(a, b, org.exist.xquery.Constants.Comparison.GTEQ);

                // Boolean
                case "and": return BooleanValue.valueOf(a.effectiveBooleanValue() && b.effectiveBooleanValue());
                case "or": return BooleanValue.valueOf(a.effectiveBooleanValue() || b.effectiveBooleanValue());

                // String concatenation
                case "||": return new StringValue(this, a.getStringValue() + b.getStringValue());

                // Sequence
                case ",": return opComma(a, b);
                case "|":
                case "union": return opVenn(a, b, "union");
                case "except": return opVenn(a, b, "except");
                case "intersect": return opVenn(a, b, "intersect");
                case "to": return opTo(a, b);
                case "otherwise": return a.isEmpty() ? b : a;

                // Node comparison
                case "is": return nodeIs(a, b);
                case "is-not": return nodeIsNot(a, b);
                case "<<":
                case "precedes": return nodePrecedes(a, b);
                case ">>":
                case "follows": return nodeFollows(a, b);
                case "precedes-or-is": return nodePrecedesOrIs(a, b);
                case "follows-or-is": return nodeFollowsOrIs(a, b);

                default:
                    throw new XPathException(this, ErrorCodes.FOJS0005, "Unknown operator: " + operator);
            }
        }

        private Sequence arithmetic(final Sequence a, final Sequence b, final String op) throws XPathException {
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final ComputableValue left = toComputable(a.itemAt(0).atomize());
            final ComputableValue right = toComputable(b.itemAt(0).atomize());
            switch (op) {
                case "plus": return left.plus(right);
                case "minus": return left.minus(right);
                case "mult": return left.mult(right);
                case "div": return left.div(right);
                case "idiv": return ((org.exist.xquery.value.NumericValue) left).idiv((org.exist.xquery.value.NumericValue) right);
                case "mod": return ((org.exist.xquery.value.NumericValue) left).mod((org.exist.xquery.value.NumericValue) right);
                default: throw new IllegalStateException();
            }
        }

        private Sequence generalCompare(final Sequence a, final Sequence b,
                final org.exist.xquery.Constants.Comparison comp) throws XPathException {
            // General comparison: existential semantics — true if any pair matches
            if (a.isEmpty() || b.isEmpty()) {
                return BooleanValue.FALSE;
            }
            final com.ibm.icu.text.Collator collator = context.getDefaultCollator();
            for (int i = 0; i < a.getItemCount(); i++) {
                final org.exist.xquery.value.AtomicValue lv = a.itemAt(i).atomize();
                for (int j = 0; j < b.getItemCount(); j++) {
                    final org.exist.xquery.value.AtomicValue rv = b.itemAt(j).atomize();
                    if (ValueComparison.compareAtomic(collator, lv, rv,
                            org.exist.xquery.Constants.StringTruncationOperator.NONE, comp)) {
                        return BooleanValue.TRUE;
                    }
                }
            }
            return BooleanValue.FALSE;
        }

        private Sequence valueCompare(final Sequence a, final Sequence b,
                final org.exist.xquery.Constants.Comparison comp) throws XPathException {
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            if (a.getItemCount() > 1 || b.getItemCount() > 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Value comparison requires singleton operands");
            }
            final org.exist.xquery.value.AtomicValue lv = a.itemAt(0).atomize();
            final org.exist.xquery.value.AtomicValue rv = b.itemAt(0).atomize();
            final com.ibm.icu.text.Collator collator = context.getDefaultCollator();
            return BooleanValue.valueOf(ValueComparison.compareAtomic(collator, lv, rv,
                    org.exist.xquery.Constants.StringTruncationOperator.NONE, comp));
        }

        private Sequence opComma(final Sequence a, final Sequence b) throws XPathException {
            final ValueSequence result = new ValueSequence(a.getItemCount() + b.getItemCount());
            result.addAll(a);
            result.addAll(b);
            return result;
        }

        private Sequence opVenn(final Sequence a, final Sequence b, final String op) throws XPathException {
            // Check that operands are nodes
            for (int i = 0; i < a.getItemCount(); i++) {
                if (!(a.itemAt(i) instanceof org.w3c.dom.Node)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Set operation requires node operands, got " + Type.getTypeName(a.itemAt(i).getType()));
                }
            }
            for (int i = 0; i < b.getItemCount(); i++) {
                if (!(b.itemAt(i) instanceof org.w3c.dom.Node)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Set operation requires node operands, got " + Type.getTypeName(b.itemAt(i).getType()));
                }
            }
            try {
                switch (op) {
                    case "union": return a.toNodeSet().union(b.toNodeSet());
                    case "except": return a.toNodeSet().except(b.toNodeSet());
                    case "intersect": return a.toNodeSet().intersection(b.toNodeSet());
                    default: throw new IllegalStateException();
                }
            } catch (final XPathException e) {
                throw new XPathException(this, ErrorCodes.XPTY0004, e.getMessage());
            }
        }

        private Sequence opTo(final Sequence a, final Sequence b) throws XPathException {
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final long start = ((IntegerValue) a.itemAt(0)).getLong();
            final long end = ((IntegerValue) b.itemAt(0)).getLong();
            if (start > end) {
                return Sequence.EMPTY_SEQUENCE;
            }
            final ValueSequence result = new ValueSequence((int) (end - start + 1));
            for (long i = start; i <= end; i++) {
                result.add(new IntegerValue(this, i));
            }
            return result;
        }

        private void checkNodeOperands(final Sequence a, final Sequence b) throws XPathException {
            if (!a.isEmpty() && !(a.itemAt(0) instanceof org.w3c.dom.Node)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Node comparison requires node operands, got " + Type.getTypeName(a.itemAt(0).getType()));
            }
            if (!b.isEmpty() && !(b.itemAt(0) instanceof org.w3c.dom.Node)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Node comparison requires node operands, got " + Type.getTypeName(b.itemAt(0).getType()));
            }
        }

        private Sequence nodeIs(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(a.itemAt(0).equals(b.itemAt(0)));
        }

        private Sequence nodeIsNot(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(!a.itemAt(0).equals(b.itemAt(0)));
        }

        private ComputableValue toComputable(final org.exist.xquery.value.AtomicValue v) throws XPathException {
            if (v instanceof ComputableValue) {
                return (ComputableValue) v;
            }
            // Untyped atomic → promote to xs:double for arithmetic
            if (v.getType() == Type.UNTYPED_ATOMIC) {
                return (ComputableValue) v.convertTo(Type.DOUBLE);
            }
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Cannot use " + Type.getTypeName(v.getType()) + " in arithmetic");
        }

        private int nodeCompare(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            final Item left = a.itemAt(0);
            final Item right = b.itemAt(0);
            if (left instanceof org.exist.dom.persistent.NodeProxy && right instanceof org.exist.dom.persistent.NodeProxy) {
                return ((org.exist.dom.persistent.NodeProxy) left).compareTo((org.exist.dom.persistent.NodeProxy) right);
            }
            // For in-memory nodes, compare using NodeId if available
            if (left instanceof org.exist.dom.memtree.NodeImpl && right instanceof org.exist.dom.memtree.NodeImpl) {
                final org.exist.dom.memtree.NodeImpl leftNode = (org.exist.dom.memtree.NodeImpl) left;
                final org.exist.dom.memtree.NodeImpl rightNode = (org.exist.dom.memtree.NodeImpl) right;
                return Integer.compare(leftNode.getNodeNumber(), rightNode.getNodeNumber());
            }
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Node comparison requires node operands");
        }

        private Sequence nodePrecedes(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(nodeCompare(a, b) < 0);
        }

        private Sequence nodeFollows(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(nodeCompare(a, b) > 0);
        }

        private Sequence nodePrecedesOrIs(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(nodeCompare(a, b) <= 0);
        }

        private Sequence nodeFollowsOrIs(final Sequence a, final Sequence b) throws XPathException {
            checkNodeOperands(a, b);
            if (a.isEmpty() || b.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return BooleanValue.valueOf(nodeCompare(a, b) >= 0);
        }

        @Override
        public int returnsType() {
            return Type.ITEM;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            // nothing to analyze
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display("op(\"" + operator + "\")");
        }
    }
}
