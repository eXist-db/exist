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

package org.exist.xquery.value;

import org.exist.util.FastStringBuffer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class YearMonthDurationValue extends OrderedDurationValue {

    public static final Duration CANONICAL_ZERO_DURATION =
            TimeUtils.getInstance().newDuration(true, null, BigInteger.ZERO, null, null, null, null);

    YearMonthDurationValue(Duration duration) throws XPathException {
        super(duration);
        if (!duration.equals(DurationValue.CANONICAL_ZERO_DURATION)) {
            if (duration.isSet(DatatypeConstants.DAYS) ||
                    duration.isSet(DatatypeConstants.HOURS) ||
                    duration.isSet(DatatypeConstants.MINUTES) ||
                    //Always set !
                    //!duration.getField(DatatypeConstants.SECONDS).equals(BigInteger.ZERO))
                    duration.isSet(DatatypeConstants.SECONDS)) {
                throw new XPathException(ErrorCodes.XPTY0004, "The value '" + duration + "' is not an " + Type.getTypeName(getType()) +
                        " since it specifies days, hours, minutes or seconds values");
            }
        }
    }

    public YearMonthDurationValue(String str) throws XPathException {
        this(createDurationYearMonth(str));
    }

    private static Duration createDurationYearMonth(String str) throws XPathException {
        try {
            return TimeUtils.getInstance().newDurationYearMonth(StringValue.trimWhitespace(str));
        } catch (final IllegalArgumentException e) {
            throw new XPathException(ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(Type.YEAR_MONTH_DURATION) +
                    " from \"" + str + "\"");
        }
    }

    public DurationValue wrap() {
        return this;
    }

    protected Duration canonicalZeroDuration() {
        return CANONICAL_ZERO_DURATION;
    }

    public int getValue() {
        return duration.getSign() * (duration.getYears() * 12 + duration.getMonths());
    }

    public int getType() {
        return Type.YEAR_MONTH_DURATION;
    }

    public String getStringValue() {
        final FastStringBuffer sb = new FastStringBuffer(32);
        if (getCanonicalDuration().getSign() < 0) {
            sb.append('-');
        }
        sb.append('P');
        if (getCanonicalDuration().getYears() != 0) {
            sb.append(getCanonicalDuration().getYears() + "Y");
        }
        if (getCanonicalDuration().getMonths() != 0 || getCanonicalDuration().getYears() == 0) {
            sb.append(getCanonicalDuration().getMonths() + "M");
        }
        return sb.toString();
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ITEM:
            case Type.ATOMIC:
            case Type.YEAR_MONTH_DURATION:
                return this;
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.DURATION:
                return new DurationValue(TimeUtils.getInstance().newDuration(
                        duration.getSign() >= 0,
                        (BigInteger) duration.getField(DatatypeConstants.YEARS),
                        (BigInteger) duration.getField(DatatypeConstants.MONTHS),
                        null, null, null, null
                ));
            case Type.DAY_TIME_DURATION:
                return new DayTimeDurationValue(DayTimeDurationValue.CANONICAL_ZERO_DURATION);
            //case Type.DOUBLE:
            //return new DoubleValue(monthsValueSigned().doubleValue());
            //return new DoubleValue(Double.NaN);
            //case Type.DECIMAL:
            //return new DecimalValue(monthsValueSigned().doubleValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            default:
                throw new XPathException(ErrorCodes.XPTY0004,
                        "cannot cast 'xs:yearMonthDuration(\"" + getStringValue() +
                                "\")' to " + Type.getTypeName(requiredType));
        }
    }

    protected DurationValue createSameKind(Duration dur) throws XPathException {
        return new YearMonthDurationValue(dur);
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        try {
            if (other.getType() == Type.TIME) {
                throw new IllegalArgumentException();
            }
            return super.plus(other);
        } catch (final IllegalArgumentException e) {
            throw new XPathException(
                    "Operand to plus should be of type xdt:yearMonthDuration, xs:date, "
                            + "or xs:dateTime; got: "
                            + Type.getTypeName(other.getType()));
        }
    }

    public ComputableValue mult(ComputableValue other) throws XPathException {
        if (other instanceof NumericValue) {
            //If $arg2 is NaN an error is raised [err:FOCA0005]
            if (((NumericValue) other).isNaN()) {
                throw new XPathException(ErrorCodes.FOCA0005, "Operand is not a number");
            }
            //If $arg2 is positive or negative infinity, the result overflows
            if (((NumericValue) other).isInfinite()) {
                throw new XPathException(ErrorCodes.FODT0002, "Multiplication by infinity overflow");
            }
        }
        final BigDecimal factor = numberToBigDecimal(other, "Operand to mult should be of numeric type; got: ");

        final boolean isFactorNegative = factor.signum() < 0;

        final YearMonthDurationValue product = fromDecimalMonths(
                new BigDecimal(monthsValueSigned())
                        .multiply(factor.abs())
                        .setScale(0, (isFactorNegative) ? BigDecimal.ROUND_HALF_DOWN : BigDecimal.ROUND_HALF_UP)
        );

        if (isFactorNegative) {
            return product.negate();
        }

        return product;
    }

    public ComputableValue div(ComputableValue other) throws XPathException {
        if (other.getType() == Type.YEAR_MONTH_DURATION) {
            return new IntegerValue(getValue()).div(new IntegerValue(((YearMonthDurationValue) other).getValue()));
        }
        if (other instanceof NumericValue) {
            if (((NumericValue) other).isNaN()) {
                throw new XPathException(ErrorCodes.FOCA0005, "Operand is not a number");
            }
            if (((NumericValue) other).isInfinite()) {
                return new YearMonthDurationValue("P0M");
            }
            //If $arg2 is positive or negative zero, the result overflows and is handled as discussed in 10.1.1 Limits and Precision
            if (((NumericValue) other).isZero()) {
                throw new XPathException(ErrorCodes.FODT0002, "Division by zero overflow");
            }
        }
        final BigDecimal divisor = numberToBigDecimal(other, "Can not divide xdt:yearMonthDuration by '" + Type.getTypeName(other.getType()) + "'");

        final boolean isDivisorNegative = divisor.signum() < 0;

        final YearMonthDurationValue quotient = fromDecimalMonths(
                new BigDecimal(monthsValueSigned())
                        .divide(divisor.abs(), 0, (isDivisorNegative) ? BigDecimal.ROUND_HALF_DOWN : BigDecimal.ROUND_HALF_UP));

        if (isDivisorNegative) {
            return quotient.negate();
        }

        return new YearMonthDurationValue(quotient.getCanonicalDuration());
    }

    private YearMonthDurationValue fromDecimalMonths(BigDecimal x) throws XPathException {
        return new YearMonthDurationValue(TimeUtils.getInstance().newDurationYearMonth(
                x.signum() >= 0, null, x.toBigInteger()));
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(ErrorCodes.FORG0006,
                "value of type " + Type.getTypeName(getType()) +
                        " has no boolean value.");
    }
}
