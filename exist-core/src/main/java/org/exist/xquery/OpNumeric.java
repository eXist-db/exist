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

import java.util.Map;
import java.util.TreeMap;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.persistent.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.xquery.Constants.ArithmeticOperator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * numeric operation on two operands by +, -, *, div, mod etc..
 *
 */
public class OpNumeric extends BinaryOp {

    protected final ArithmeticOperator operator;
    protected int returnType = Type.ANY_ATOMIC_TYPE;
    protected NodeSet temp = null;
    protected DBBroker broker;

    public OpNumeric(final XQueryContext context, final ArithmeticOperator operator) {
        super(context);
        this.operator = operator;
    }

    public OpNumeric(final XQueryContext context, Expression left, Expression right, final ArithmeticOperator operator) {
        super(context);
        this.operator = operator;
        if (Type.subTypeOfUnion(left.returnsType(), Type.NUMERIC) && Type.subTypeOfUnion(right.returnsType(), Type.NUMERIC)) {

            // 1) Type Promotion, see: https://www.w3.org/TR/xpath-31#promotion
            final Tuple2<Expression, Expression> promotedExpressions = promoteNumericTypes(context, left, right);
            left = promotedExpressions._1;
            right = promotedExpressions._2;

            int ltype = left.returnsType();
            int rtype = right.returnsType();

            final boolean ltypeDerivesFromRtype = derivesFrom(ltype, rtype);
            final boolean rtypeDerivesFromLtype = derivesFrom(rtype, ltype);

            // 2) Type Substitution, see: https://www.w3.org/TR/xpath-31/#dt-subtype-substitution
            if (ltypeDerivesFromRtype && rtypeDerivesFromLtype) {
                returnType = ltype;
            } else if (ltypeDerivesFromRtype) {
                right = new UntypedValueCheck(context, ltype, right);
                rtype = right.returnsType();
                returnType = ltype;
            } else if (rtypeDerivesFromLtype) {
                left = new UntypedValueCheck(context, rtype, left);
                ltype = left.returnsType();
                returnType = rtype;
            }

            if (operator == ArithmeticOperator.DIVISION && ltype == Type.INTEGER && rtype == Type.INTEGER) {
                returnType = Type.DECIMAL;
            } else if (operator == ArithmeticOperator.DIVISION_INTEGER) {
                returnType = Type.INTEGER;
            }
        } else {
            int ltype = left.returnsType();
            int rtype = right.returnsType();
            if (Type.subTypeOfUnion(ltype, Type.NUMERIC)) {
                ltype = Type.NUMERIC;
            }
            if (Type.subTypeOfUnion(rtype, Type.NUMERIC)) {
                rtype = Type.NUMERIC;
            }
            final OpEntry entry = OP_TYPES.get(new OpEntry(operator, ltype, rtype));
            if (entry != null) {
                returnType = entry.typeResult;
            } else if (ltype == Type.NUMERIC || rtype == Type.NUMERIC) {
                // if one of both operands returns a number, we can safely assume
                // the return type of the whole expression will be a number
                if(operator == ArithmeticOperator.DIVISION) {
                    returnType = Type.DECIMAL;
                }else {
                    returnType = Type.NUMERIC;
                }
            }
        }
        add(left);
        add(right);
    }

    /**
     * Implementation of <a href="https://www.w3.org/TR/xpath-31/#dt-subtype-substitution">XPath 3.1 - 2.5.5 SequenceType Matching</a>.
     *
     * @param actualType The actual type, a.k.a. AT.
     * @param expectedType The expected type, a.k.a. ET.
     *
     * @return true if AT derives from ET, false otherwise.
     */
    static boolean derivesFrom(final int actualType, final int expectedType) {

        // AT is ET
        if (actualType == expectedType) {
            return true;
        }

        // ET is the base type of AT
        if (Type.subTypeOf(actualType, expectedType)) {
            return true;
        }

        // ET is a pure union type of which AT is a member type
        if (Type.hasMember(expectedType, actualType)) {
            return true;
        }

        // There is a type MT such that derives-from(AT, MT) and derives-from(MT, ET)

        // iterate through AT's super-types
        int t;
        for (t = actualType; t != Type.ITEM && t != Type.ANY_TYPE; t = Type.getSuperType(t)) {
            // is the super-type of AT a subtype of ET
            if (Type.subTypeOf(t, expectedType)) {
                return true;
            }
        }

        // Otherwise, derives-from(AT,ET) return false
        return false;
    }

    /**
     * Implementation of <a href="https://www.w3.org/TR/xpath-31/#promotion">XPath 3.1 - B.1 Type Promotion</a>.
     *
     * @param context The XQuery Context.
     * @param left The left operand to a numeric operation.
     * @param right The right operand to a numeric operation.
     *
     * @return the promoted expression(s), or the original expression(s) if no type promotion(s) was applicable.
     */
    static Tuple2<Expression, Expression> promoteNumericTypes(final XQueryContext context, Expression left, Expression right) {
        left = promoteFloatTypeToDouble(context, right, left);
        right = promoteFloatTypeToDouble(context, left, right);

        left = promoteDecimalTypesToFloat(context, right, left);
        right = promoteDecimalTypesToFloat(context, left, right);

        left = promoteDecimalTypesToDouble(context, right, left);
        right = promoteDecimalTypesToDouble(context, left, right);

        return Tuple(left, right);
    }

    private static Expression promoteFloatTypeToDouble(final XQueryContext context, final Expression test, Expression expr) {
        if (test.returnsType() == Type.DOUBLE && expr.returnsType() == Type.FLOAT) {
            expr = new CastExpression(context, expr, Type.DOUBLE, expr.getCardinality());
        }
        return expr;
    }

    private static Expression promoteDecimalTypesToFloat(final XQueryContext context, final Expression test, Expression expr) {
        if (test.returnsType() == Type.FLOAT && Type.subTypeOf(expr.returnsType(), Type.DECIMAL)) {
            expr = new CastExpression(context, expr, Type.FLOAT, expr.getCardinality());
        }
        return expr;
    }

    private static Expression promoteDecimalTypesToDouble(final XQueryContext context, final Expression test, Expression expr) {
        if (test.returnsType() == Type.DOUBLE && Type.subTypeOf(expr.returnsType(), Type.DECIMAL)) {
            expr = new CastExpression(context, expr, Type.DOUBLE, expr.getCardinality());
        }
        return expr;
    }

    @Override
    public int getDependencies() {
        return getLeft().getDependencies() | getRight().getDependencies();
    }

    public int returnsType() {
        return returnType;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        contextInfo.setStaticReturnType(returnType);
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence lseq = Atomize.atomize(getLeft().eval(contextSequence, contextItem));
        final Sequence rseq = Atomize.atomize(getRight().eval(contextSequence, contextItem));
        if (lseq.hasMany())
            {throw new XPathException(this, ErrorCodes.XPTY0004,
                "Too many operands at the left of " + operator.symbol);}
        if (rseq.hasMany())
            {throw new XPathException(this, ErrorCodes.XPTY0004,
                "Too many operands at the right of " + operator.symbol);}
        Sequence result;
        if (rseq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else if (lseq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
            Item lvalue = lseq.itemAt(0);
            Item rvalue = rseq.itemAt(0);
            try {
                if (lvalue.getType() == Type.UNTYPED_ATOMIC || lvalue.getType() == Type.ANY_ATOMIC_TYPE)
                    {lvalue = lvalue.convertTo(Type.NUMERIC);}
                if (rvalue.getType() == Type.UNTYPED_ATOMIC || rvalue.getType() == Type.ANY_ATOMIC_TYPE)
                    {rvalue = rvalue.convertTo(Type.NUMERIC);}
                if (!(lvalue instanceof ComputableValue))
                    {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                        Type.getTypeName(lvalue.getType()) + "(" + lvalue + ")' can not be an operand for " +
                        operator.symbol);}
                if (!(rvalue instanceof ComputableValue))
                    {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                        Type.getTypeName(rvalue.getType()) + "(" + rvalue + ")' can not be an operand for " +
                        operator.symbol);}
                //TODO : move to implementations
                if (operator == ArithmeticOperator.DIVISION_INTEGER) {
                    if (!Type.subTypeOfUnion(lvalue.getType(), Type.NUMERIC))
                        {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                            Type.getTypeName(lvalue.getType()) + "(" + lvalue + ")' can not be an operand for " + operator.symbol);}
                    if (!Type.subTypeOfUnion(rvalue.getType(), Type.NUMERIC))
                        {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                            Type.getTypeName(rvalue.getType()) + "(" + rvalue + ")' can not be an operand for " + operator.symbol);}
                    //If the divisor is (positive or negative) zero, then an error is raised [err:FOAR0001]
                    if (((NumericValue)rvalue).isZero())
                        {throw new XPathException(this, ErrorCodes.FOAR0001, "Division by zero");}
                    //If either operand is NaN then an error is raised [err:FOAR0002].
                    if (((NumericValue)lvalue).isNaN())
                        {throw new XPathException(this, ErrorCodes.FOAR0002, "Division of " +
                            Type.getTypeName(lvalue.getType()) + "(" + lvalue + ")'");}
                    //If either operand is NaN then an error is raised [err:FOAR0002].
                    if (((NumericValue)rvalue).isNaN())
                        {throw new XPathException(this, ErrorCodes.FOAR0002, "Division of " + 
                            Type.getTypeName(rvalue.getType()) + "(" + rvalue + ")'");}
                    //If $arg1 is INF or -INF then an error is raised [err:FOAR0002].
                    if (((NumericValue)lvalue).isInfinite())
                        {throw new XPathException(this, ErrorCodes.FOAR0002, "Division of " +
                            Type.getTypeName(lvalue.getType()) + "(" + lvalue + ")'");}
                    result = ((NumericValue) lvalue).idiv((NumericValue) rvalue);
                } else {
                    result = applyOperator((ComputableValue) lvalue, (ComputableValue) rvalue);
                }
                //TODO : type-checks on MOD operator : maybe the same ones than above -pb
            } catch (final XPathException e) {
                e.setLocation(line, column);
                throw e;
            }
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}
        //Sets the return type if not already set
        if (returnType == Type.ANY_ATOMIC_TYPE)
            //TODO : refine previously set type ? -pb
            {returnType = result.getItemType();}
        return result;
    }

    public ComputableValue applyOperator(ComputableValue left, ComputableValue right)
            throws XPathException {
        switch (operator) {
            case SUBTRACTION: return left.minus(right);
            case ADDITION: return left.plus(right);
            case MULTIPLICATION: return left.mult(right);
            case DIVISION: return left.div(right);
            case MODULUS: {
                if (!Type.subTypeOfUnion(left.getType(), Type.NUMERIC))
                    {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                        Type.getTypeName(left.getType()) + "(" + left + ")' is not numeric");}
                if (!Type.subTypeOfUnion(right.getType(), Type.NUMERIC))
                    {throw new XPathException(this, ErrorCodes.XPTY0004, "'" +
                        Type.getTypeName(right.getType()) + "(" + right + ")' is not numeric");}
                return ((NumericValue) left).mod((NumericValue) right);
        }
        default:
            throw new RuntimeException("Unknown numeric operator " + operator);
        }
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        getLeft().dump(dumper);
        dumper.display(' ').display(operator.symbol).display(' ');
        getRight().dump(dumper);
    }

    @Override
    public String toString() {
        return getLeft().toString() + ' ' + operator.symbol + ' ' + getRight();
    }

    // excerpt from operator mapping table in XQuery 1.0 section B.2
    // http://www.w3.org/TR/xquery/#mapping
    private static final OpEntry[] OP_TABLE = {
        new OpEntry(ArithmeticOperator.ADDITION,     Type.NUMERIC,                Type.NUMERIC,                Type.NUMERIC),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE,                  Type.YEAR_MONTH_DURATION,   Type.DATE),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.YEAR_MONTH_DURATION,   Type.DATE,                  Type.DATE),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE,                  Type.DAY_TIME_DURATION,     Type.DATE),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DAY_TIME_DURATION,     Type.DATE,                  Type.DATE),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.TIME,                  Type.DAY_TIME_DURATION,     Type.TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DAY_TIME_DURATION,     Type.TIME,                  Type.TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE_TIME,             Type.YEAR_MONTH_DURATION,   Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.YEAR_MONTH_DURATION,   Type.DATE_TIME,             Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE_TIME,             Type.DAY_TIME_DURATION,     Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DAY_TIME_DURATION,     Type.DATE_TIME,             Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE_TIME_STAMP,       Type.YEAR_MONTH_DURATION,   Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.YEAR_MONTH_DURATION,   Type.DATE_TIME_STAMP,       Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DATE_TIME_STAMP,       Type.DAY_TIME_DURATION,     Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DAY_TIME_DURATION,     Type.DATE_TIME_STAMP,       Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION),
        new OpEntry(ArithmeticOperator.ADDITION,     Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.NUMERIC,                Type.NUMERIC,                Type.NUMERIC),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE,                  Type.DATE,                  Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE,                  Type.YEAR_MONTH_DURATION,   Type.DATE),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE,                  Type.DAY_TIME_DURATION,     Type.DATE),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.TIME,                  Type.TIME,                  Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.TIME,                  Type.DAY_TIME_DURATION,     Type.TIME),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME,             Type.DATE_TIME,             Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME,             Type.YEAR_MONTH_DURATION,   Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME,             Type.DAY_TIME_DURATION,     Type.DATE_TIME),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME_STAMP,       Type.DATE_TIME_STAMP,       Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME_STAMP,       Type.YEAR_MONTH_DURATION,   Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DATE_TIME_STAMP,       Type.DAY_TIME_DURATION,     Type.DATE_TIME_STAMP),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION),
        new OpEntry(ArithmeticOperator.SUBTRACTION,    Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.MULTIPLICATION,     Type.NUMERIC,                Type.NUMERIC,                Type.NUMERIC),
        new OpEntry(ArithmeticOperator.MULTIPLICATION,     Type.YEAR_MONTH_DURATION,   Type.NUMERIC,                Type.YEAR_MONTH_DURATION),
        new OpEntry(ArithmeticOperator.MULTIPLICATION,     Type.NUMERIC,                Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION),
        new OpEntry(ArithmeticOperator.MULTIPLICATION,     Type.DAY_TIME_DURATION,     Type.NUMERIC,                Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.MULTIPLICATION,     Type.NUMERIC,                Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.DIVISION_INTEGER,     Type.NUMERIC,                Type.NUMERIC,                Type.INTEGER),
        new OpEntry(ArithmeticOperator.DIVISION,      Type.NUMERIC,                Type.NUMERIC,                Type.NUMERIC),  // except for integer -> decimal
        new OpEntry(ArithmeticOperator.DIVISION,      Type.YEAR_MONTH_DURATION,   Type.NUMERIC,                Type.YEAR_MONTH_DURATION),
        new OpEntry(ArithmeticOperator.DIVISION,      Type.DAY_TIME_DURATION,     Type.NUMERIC,                Type.DAY_TIME_DURATION),
        new OpEntry(ArithmeticOperator.DIVISION,      Type.YEAR_MONTH_DURATION,   Type.YEAR_MONTH_DURATION,   Type.DECIMAL),
        new OpEntry(ArithmeticOperator.DIVISION,      Type.DAY_TIME_DURATION,     Type.DAY_TIME_DURATION,     Type.DECIMAL),
        new OpEntry(ArithmeticOperator.MODULUS,      Type.NUMERIC,                Type.NUMERIC,                Type.NUMERIC)
    };

    private static class OpEntry implements Comparable<OpEntry> {
        public final ArithmeticOperator op;
        public final int typeA, typeB, typeResult;

        public OpEntry(final ArithmeticOperator op, final int typeA, final int typeB) {
            this(op, typeA, typeB, Type.ANY_ATOMIC_TYPE);
        }

        public OpEntry(final ArithmeticOperator op, final int typeA, final int typeB, final int typeResult) {
            this.op = op;
            this.typeA = typeA;
            this.typeB = typeB;
            this.typeResult = typeResult;
        }

        @Override
        public int compareTo(final OpEntry that) {
            if (this.op != that.op) {
                return this.op.ordinal() - that.op.ordinal();
            } else if (this.typeA != that.typeA) {
                return this.typeA - that.typeA;
            } else if (this.typeB != that.typeB) {
                return this.typeB - that.typeB;
            } else {
                return 0;
            }
   	    }

        @Override
        public boolean equals(final Object o) {
            try {
                final OpEntry that = (OpEntry) o;
                return this.op == that.op && this.typeA == that.typeA &&
                    this.typeB == that.typeB;
            } catch (final ClassCastException e) {
                return false;
            }
        }
    }

    private static final Map<OpEntry, OpEntry> OP_TYPES = new TreeMap<>();

    static {
        for (final OpEntry entry : OP_TABLE) {
            OP_TYPES.put(entry, entry);
        }
    }
}
