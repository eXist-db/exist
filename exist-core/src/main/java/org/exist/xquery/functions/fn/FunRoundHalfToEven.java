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

import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Type;

import java.math.RoundingMode;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * Implements the fn:round-half-to-even() function.
 *
 * Shares a base class and evaluator with {@link FunRound}
 * They differ only in the rounding mode used.
 *
 * @author wolf
 *
 */
public class FunRoundHalfToEven extends FunRoundBase {

	private static final String FN_NAME = "round-half-to-even";

	protected static final String FUNCTION_DESCRIPTION_1_PARAM = 
        "The value returned is the nearest (that is, numerically closest) " +
		"value to $arg that is a multiple of ten to the power of minus 0. ";
	protected static final String FUNCTION_DESCRIPTION_2_PARAM = 
		"The value returned is the nearest (that is, numerically closest) " +
		"value to $arg that is a multiple of ten to the power of minus " +
		"$precision. ";
    protected static final String FUNCTION_DESCRIPTION_COMMON = 
        "If two such values are equally near (e.g. if the " +
		"fractional part in $arg is exactly .500...), the function returns " +
		"the one whose least significant digit is even.\n\nIf the type of " +
		"$arg is one of the four numeric types xs:float, xs:double, " +
		"xs:decimal or xs:integer the type of the result is the same as " +
		"the type of $arg. If the type of $arg is a type derived from one " +
		"of the numeric types, the result is an instance of the " +
		"base numeric type.\n\n" +
		"The three argument version of the function with $precision = 0 " +
        "produces the same result as the two argument version.\n\n" +
		"For arguments of type xs:float and xs:double, if the argument is " +
		"NaN, positive or negative zero, or positive or negative infinity, " +
		"then the result is the same as the argument. In all other cases, " +
		"the argument is cast to xs:decimal, the function is applied to this " +
		"xs:decimal value, and the resulting xs:decimal is cast back to " +
		"xs:float or xs:double as appropriate to form the function result. " +
		"If the resulting xs:decimal value is zero, then positive or negative " +
		"zero is returned according to the sign of the original argument.\n\n" +
		"Note that the process of casting to xs:decimal " +
		"may result in an error [err:FOCA0001].\n\n" +
		"If $arg is of type xs:float or xs:double, rounding occurs on the " +
		"value of the mantissa computed with exponent = 0.";
	
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.NUMERIC, Cardinality.ZERO_OR_ONE, "the rounded value");

	public static final FunctionSignature[] FN_ROUND_HALF_TO_EVEN_SIGNATURES = {
			functionSignature(FN_NAME, FunRoundHalfToEven.FUNCTION_DESCRIPTION_1_PARAM + FunRoundHalfToEven.FUNCTION_DESCRIPTION_COMMON, FunRoundHalfToEven.RETURN_TYPE,
					optParam("arg", Type.NUMERIC, "The input number")),
			functionSignature(FN_NAME, FunRoundHalfToEven.FUNCTION_DESCRIPTION_2_PARAM + FunRoundHalfToEven.FUNCTION_DESCRIPTION_COMMON, RETURN_TYPE,
					optParam("arg", Type.NUMERIC, "The input number"),
					optParam("precision", Type.INTEGER, "Precision to round to"))
	};

	public FunRoundHalfToEven(final XQueryContext context,
							  final FunctionSignature signature) {
		super(context, signature);
	}

	/**
	 * Work out the rounding mode for a particular value using fn:round-half-to-even
	 *
	 * @param value that has to be rounded
	 * @return the rounding mode to use on this value
	 */
	@Override protected final RoundingMode getFunctionRoundingMode(final NumericValue value) {

		return RoundingMode.HALF_EVEN;
	}
}
