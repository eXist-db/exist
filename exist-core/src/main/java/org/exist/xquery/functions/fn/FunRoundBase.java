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
import java.util.Map;
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

    private static final Map<String, String> ROUNDING_MODE_MAP = Map.of(
            "floor", "FLOOR",
            "ceiling", "CEILING",
            "toward-zero", "DOWN",
            "away-from-zero", "UP",
            "half-to-floor", "HALF_FLOOR",
            "half-to-ceiling", "HALF_CEILING",
            "half-toward-zero", "HALF_DOWN",
            "half-away-from-zero", "HALF_UP",
            "half-to-even", "HALF_EVEN"
    );

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

        // Determine rounding mode: 3-arg form overrides the function default
        final RoundingMode roundingMode;
        if (args.length > 2 && !args[2].isEmpty()) {
            roundingMode = parseRoundingMode(args[2].getStringValue(), value);
        } else {
            roundingMode = getFunctionRoundingMode(value);
        }

        if (args.length > 1 && !args[1].isEmpty()) {
            final Item precisionItem = args[1].itemAt(0);
            if (precisionItem instanceof IntegerValue precision) {
                return convertValue(precision, value, roundingMode, this);
            }
        }

        return convertValue(IntegerValue.ZERO, value, roundingMode, this);
    }

    private RoundingMode parseRoundingMode(final String mode, final NumericValue value) throws XPathException {
        // XQ4 rounding modes that map directly to Java RoundingMode
        switch (mode) {
            case "floor": return RoundingMode.FLOOR;
            case "ceiling": return RoundingMode.CEILING;
            case "toward-zero": return RoundingMode.DOWN;
            case "away-from-zero": return RoundingMode.UP;
            case "half-to-even": return RoundingMode.HALF_EVEN;
            case "half-away-from-zero": return RoundingMode.HALF_UP;
            case "half-toward-zero": return RoundingMode.HALF_DOWN;
            // half-to-floor and half-to-ceiling need special handling based on sign
            case "half-to-floor":
                return value.isNegative() ? RoundingMode.HALF_UP : RoundingMode.HALF_DOWN;
            case "half-to-ceiling":
                return value.isNegative() ? RoundingMode.HALF_DOWN : RoundingMode.HALF_UP;
            default:
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Unknown rounding mode: '" + mode + "'");
        }
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
