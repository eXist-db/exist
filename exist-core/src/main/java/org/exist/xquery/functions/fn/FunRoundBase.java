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

import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.math.RoundingMode;
import java.util.Objects;

/**
 * Base class for rounding mode functions
 *
 * Implements the eval function which knows how to round,
 * but defers to the subclass for the {@link RoundingMode} to use.
 */
abstract class FunRoundBase extends BasicFunction {

    public FunRoundBase(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public int returnsType() {
        return Type.NUMERIC;
    }

    abstract protected RoundingMode getFunctionRoundingMode(NumericValue value);

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item item = args[0].itemAt(0);
        final NumericValue value;
        if (item instanceof NumericValue) {
            value = (NumericValue) item;
        } else {
            value = (NumericValue) item.convertTo(Type.NUMERIC);
        }

        final RoundingMode roundingMode = getFunctionRoundingMode(value);

        if (args.length > 1) {
            final Item precisionItem = args[1].itemAt(0);
            if (precisionItem instanceof IntegerValue precision) {
                return convertValue(precision, value, roundingMode, this);
            }
        }

        return convertValue(IntegerValue.ZERO, value, roundingMode, this);
    }

    /**
     * Apply necessary conversions to/from decimal to perform rounding in decimal
     *
     * @param precision precision of rounding
     * @param value to round
     * @param roundingMode mode to round in
     * @return rounded value in decimal converted back to the input type
     * @throws XPathException if a conversion goes wrong (it shouldn't)
     */
    private static Sequence convertValue(final IntegerValue precision, final NumericValue value, final RoundingMode roundingMode, final Expression expression) throws XPathException {

        if (value.isInfinite() || value.isNaN()) {
            return value;
        }

        final DecimalValue decimal = (DecimalValue)value.convertTo(Type.DECIMAL);
        // (AP) This is as much precision as we can need, and prevents overflows scaling BigInteger
        final IntegerValue usePrecision = truncatePrecision(decimal, precision, expression);
        final DecimalValue rounded = (DecimalValue) Objects.requireNonNull(decimal).round(usePrecision, roundingMode);

        if (value.isNegative() && rounded.isZero()) {
            //Extreme care!! (AP) -0 as DecimalValue will not be negative, -0.0f and -0.0d will be negative.
            //So we need to test that original value to decide whether to negate a "zero" result.
            //DecimalValue(s) are not necessarily normalized, but the 0-test will work..
            switch (value.getType()) {
                case Type.DOUBLE:
                    return new DoubleValue(expression, -0.0d);
                case Type.FLOAT:
                    return new FloatValue(expression, -0.0f);
                default:
                    break;
            }
        }

        return rounded.convertTo(value.getType());
    }

    private static IntegerValue truncatePrecision(final DecimalValue decimal, final IntegerValue precision, final Expression expression) {

        final IntegerValue decimalPrecision = new IntegerValue(expression, decimal.getValue().precision());
        if (decimalPrecision.compareTo(precision) < 0) {
            return decimalPrecision;
        }
        return precision;
    }
}
