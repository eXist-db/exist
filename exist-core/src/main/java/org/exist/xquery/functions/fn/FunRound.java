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
import org.exist.xquery.value.*;

import java.math.RoundingMode;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;

/**
 * Implement fn:round() function
 *
 * Shares a base class and evaluator with {@link FunRoundHalfToEven}
 * They differ only in the rounding mode used.
 */
public class FunRound extends FunRoundBase {

	private static final String FN_NAME = "round";
	private static final String description = "The function returns the nearest (that is, numerically closest) " +
			"value to $arg that is a multiple of ten to the power of minus $precision. " +
			"If two such values are equally near (for example, if the fractional part in $arg is exactly .5), " +
			"the function returns the one that is closest to positive infinity. " +
			"For the four types xs:float, xs:double, xs:decimal and xs:integer, " +
			"it is guaranteed that if the type of $arg is an instance of type T " +
			"then the result will also be an instance of T. " +
			"The result may also be an instance of a type derived from one of these four by restriction. " +
			"For example, if $arg is an instance of xs:decimal and $precision is less than one, " +
			"then the result may be an instance of xs:integer. " +
			"The single-argument version of this function produces the same result " +
			"as the two-argument version with $precision=0 (that is, it rounds to a whole number). " +
			"When $arg is of type xs:float and xs:double: " +
			"If $arg is NaN, positive or negative zero, or positive or negative infinity, " +
			"then the result is the same as the argument. " +
			"For other values, the argument is cast to xs:decimal " +
			"using an implementation of xs:decimal that imposes no limits on the number of digits " +
			"that can be represented. The function is applied to this xs:decimal value, " +
			"and the resulting xs:decimal is cast back to xs:float or xs:double as appropriate " +
			"to form the function result. If the resulting xs:decimal value is zero, " +
			"then positive or negative zero is returned according to the sign of $arg.";
	private static final FunctionReturnSequenceType returnType = new FunctionReturnSequenceType(Type.NUMERIC, Cardinality.ZERO_OR_ONE, "the rounded value");

	public static final FunctionSignature[] FN_ROUND_SIGNATURES = {
			functionSignature(FN_NAME, FunRound.description, FunRound.returnType,
					optParam("arg", Type.NUMERIC, "The input number")),
			functionSignature(FN_NAME, FunRound.description, FunRound.returnType,
					optParam("arg", Type.NUMERIC, "The input number"),
					optParam("precision", Type.INTEGER, "The input number"))
	};

	public FunRound(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.NUMERIC;
	}

	/**
	 * Work out the rounding mode for a particular value using fn:round
	 *
	 * @param value that has to be rounded
	 * @return the rounding mode to use on this value
	 */
	@Override protected final RoundingMode getFunctionRoundingMode(final NumericValue value) {

		if (value.isNegative()) {
			return RoundingMode.HALF_DOWN;
		} else {
			return RoundingMode.HALF_UP;
		}
	}
}
