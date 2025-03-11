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
package org.exist.xquery.functions.math;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.xquery.ErrorCodes.ERROR;

/**
 * Class containing math functions that accept one parameter.
 *
 * @author Dannes Wessels
 */
public class OneParamFunctions extends BasicFunction {

    //private static final Logger logger = LogManager.getLogger(OneParamFunctions.class);

    public static final String ACOS = "acos";
    public static final String ASIN = "asin";
    public static final String ATAN = "atan";
    public static final String COS = "cos";
    public static final String EXP = "exp";
    public static final String EXP10 = "exp10";
    public static final String LOG = "log";
    public static final String LOG10 = "log10";
    public static final String SIN = "sin";
    public static final String SQRT = "sqrt";
    public static final String TAN = "tan";

    public final static FunctionSignature FNS_ACOS = new FunctionSignature(
            new QName(ACOS, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the arc cosine of the argument, the result being in the range zero to +π radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the result")
    );

    public final static FunctionSignature FNS_ASIN = new FunctionSignature(
            new QName(ASIN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the arc sine of the argument, the result being in the range -π/2 to +π/2 radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "result")
    );

    public final static FunctionSignature FNS_ATAN = new FunctionSignature(
            new QName(ATAN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the arc tangent of the argument, the result being in the range -π/2 to +π/2 radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the result")
    );

    public final static FunctionSignature FNS_COS = new FunctionSignature(
            new QName(COS, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the cosine of the argument, expressed in radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the cosine")
    );

    public final static FunctionSignature FNS_EXP = new FunctionSignature(
            new QName(EXP, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Calculates e (the Euler Constant) raised to the power of $arg",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "e (the Euler Constant) raised to the power of a value or expression")
    );

    public final static FunctionSignature FNS_EXP10 = new FunctionSignature( // NEW
            new QName(EXP10, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Calculates 10 raised to the power of $arg",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "e (the Euler Constant) raised to the power of a value or expression")
    );

    public final static FunctionSignature FNS_LOG = new FunctionSignature(
            new QName(LOG, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the natural logarithm of the argument.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the log")
    );

    public final static FunctionSignature FNS_LOG10 = new FunctionSignature( // NEW
            new QName(LOG10, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the base-ten logarithm of the argument.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the log")
    );

    public final static FunctionSignature FNS_SIN = new FunctionSignature(
            new QName(SIN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the sine of the argument, expressed in radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the sine")
    );

    public final static FunctionSignature FNS_SQRT = new FunctionSignature(
            new QName(SQRT, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the non-negative square root of the argument.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The input number")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the square root of $x")
    );

    public final static FunctionSignature FNS_TAN = new FunctionSignature(
            new QName(TAN, MathModule.NAMESPACE_URI, MathModule.PREFIX),
            "Returns the tangent of the argument, expressed in radians.",
            new SequenceType[]{new FunctionParameterSequenceType("arg", Type.DOUBLE, Cardinality.ZERO_OR_ONE, "The radians")},
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the tangent")
    );

    public OneParamFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        // Fetch data
        final Sequence firstArgument = args[0];

        if (firstArgument.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;

        } else {
            final NumericValue value = (NumericValue) firstArgument.itemAt(0).convertTo(Type.DOUBLE);
            final String functionName = getSignature().getName().getLocalPart();

            final double calcValue = switch (functionName) {
                case ACOS -> Math.acos(value.getDouble());
                case ASIN -> Math.asin(value.getDouble());
                case ATAN -> Math.atan(value.getDouble());
                case COS -> Math.cos(value.getDouble());
                case EXP -> Math.exp(value.getDouble());
                case EXP10 -> Math.pow(10.0d, value.getDouble());
                case LOG -> Math.log(value.getDouble());
                case LOG10 -> Math.log10(value.getDouble());
                case SIN -> Math.sin(value.getDouble());
                case SQRT -> Math.sqrt(value.getDouble());
                case TAN -> Math.tan(value.getDouble());
                case null -> throw new XPathException(this, ERROR, "Function " + functionName + " not found.");
                default -> 0;
            };
            return new DoubleValue(this, calcValue);
        }

    }

}
