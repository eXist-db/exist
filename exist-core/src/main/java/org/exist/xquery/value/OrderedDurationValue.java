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
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
abstract class OrderedDurationValue extends DurationValue {

    OrderedDurationValue(final Duration duration) throws XPathException {
        this(null, duration);
    }

    OrderedDurationValue(final Expression expression, Duration duration) throws XPathException {
        super(expression, duration);
    }

    @Override
    public boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other)
            throws XPathException {
        if (other.isEmpty()) {
            return false;
        }
        final Boolean mixedSubtypeResult = compareMixedSubtypes(operator, other);
        if (mixedSubtypeResult != null) {
            return mixedSubtypeResult;
        }
        validateOrderingComparable(operator, other);
        return resultMatchesOperator(compareTo(collator, other), operator);
    }

    /**
     * Mixed duration subtypes (e.g., xs:yearMonthDuration vs xs:dayTimeDuration):
     * equality comparisons return false (they can never be equal);
     * ordering operators (lt/gt/le/ge) are not defined and raise XPTY0004.
     * Returns null when the subtypes are not mixed and the caller should
     * continue with normal comparison.
     */
    private Boolean compareMixedSubtypes(final Comparison operator, final AtomicValue other) throws XPathException {
        if (!Type.subTypeOf(other.getType(), Type.DURATION)
                || getType() == other.getType()
                || getType() == Type.DURATION
                || other.getType() == Type.DURATION) {
            return null;
        }
        return switch (operator) {
            case EQ -> Boolean.FALSE;
            case NEQ -> Boolean.TRUE;
            default -> throw new XPathException(getExpression(), ErrorCodes.XPTY0004,
                    "cannot compare " + Type.getTypeName(getType()) + " to "
                            + Type.getTypeName(other.getType()));
        };
    }

    /** Ordering operators (LT/GT/LTEQ/GTEQ) are not defined for unordered xs:duration. */
    private void validateOrderingComparable(final Comparison operator, final AtomicValue other) throws XPathException {
        if (operator == Comparison.EQ || operator == Comparison.NEQ) {
            return;
        }
        if (getType() == Type.DURATION) {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004,
                    "cannot compare unordered " + Type.getTypeName(getType()) + " to "
                            + Type.getTypeName(other.getType()));
        }
        if (other.getType() == Type.DURATION) {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004,
                    "cannot compare " + Type.getTypeName(getType()) + " to unordered "
                            + Type.getTypeName(other.getType()));
        }
    }

    private boolean resultMatchesOperator(final int r, final Comparison operator) throws XPathException {
        return switch (operator) {
            case EQ -> r == DatatypeConstants.EQUAL;
            case NEQ -> r != DatatypeConstants.EQUAL;
            case LT -> r == DatatypeConstants.LESSER;
            case LTEQ -> r == DatatypeConstants.LESSER || r == DatatypeConstants.EQUAL;
            case GT -> r == DatatypeConstants.GREATER;
            case GTEQ -> r == DatatypeConstants.GREATER || r == DatatypeConstants.EQUAL;
            default -> throw new XPathException(getExpression(), "Unknown operator type in comparison");
        };
    }

    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (other.isEmpty()) {
            return Constants.INFERIOR;
        }
        if (Type.subTypeOf(other.getType(), Type.DURATION)) {
            //Take care : this method doesn't seem to take ms into account
            final int r = duration.compare(((DurationValue) other).duration);
            //compare fractional seconds to work around the JDK standard behaviour
            if (r == DatatypeConstants.EQUAL &&
                    duration.getField(DatatypeConstants.SECONDS) != null &&
                    (((DurationValue) other).duration).getField(DatatypeConstants.SECONDS) != null) {
                if (((BigDecimal) duration.getField(DatatypeConstants.SECONDS)).compareTo(
                        ((BigDecimal) (((DurationValue) other).duration).getField(DatatypeConstants.SECONDS))) == DatatypeConstants.EQUAL) {
                    return Constants.EQUAL;
                }
                return (((BigDecimal) duration.getField(DatatypeConstants.SECONDS)).compareTo(
                        ((BigDecimal) (((DurationValue) other).duration).getField(DatatypeConstants.SECONDS)))) == DatatypeConstants.LESSER ?
                        Constants.INFERIOR : Constants.SUPERIOR;
            }
            if (r == DatatypeConstants.INDETERMINATE) {
                throw new IllegalStateException(
                        "indeterminate order between totally ordered duration values " + this + " and " + other);
            }
            return r;
        }
        throw new XPathException(getExpression(), 
                "Type error: cannot compare " + Type.getTypeName(getType()) + " to "
                        + Type.getTypeName(other.getType()));
    }

    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() != getType()) {
            throw new XPathException(getExpression(), "cannot obtain maximum across different non-numeric data types");
        }
        return compareTo(null, other) > 0 ? this : other;
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() != getType()) {
            throw new XPathException(getExpression(), "cannot obtain minimum across different non-numeric data types");
        }
        return compareTo(null, other) < 0 ? this : other;
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        return switch (other.getType()) {
            case Type.DAY_TIME_DURATION -> {
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                final DayTimeDurationValue sum = new DayTimeDurationValue(getExpression(), result);
                sum.checkDayTimeOverflow(sum.secondsValueSigned());
                yield sum;
            }
            case Type.YEAR_MONTH_DURATION -> {
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                final YearMonthDurationValue sum = new YearMonthDurationValue(getExpression(), result);
                sum.checkYearMonthOverflow(sum.monthsValueSigned());
                yield sum;
            }
            case Type.DURATION -> {
                final Duration a = getCanonicalDuration();
                final Duration b = ((DurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                yield new DurationValue(getExpression(), result);
            }
            case Type.TIME, Type.DATE_TIME, Type.DATE_TIME_STAMP, Type.DATE ->
                    addDurationToDate((AbstractDateTimeValue) other);
            default -> throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot add " +
                    Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                    Type.getTypeName(getType()) + "('" + getStringValue() + "')");
        };
    }

    private ComputableValue addDurationToDate(AbstractDateTimeValue date) throws XPathException {
        date.checkYearOverflow(date.calendar);
        checkDateArithMagnitude(secondsValueSigned());
        final XMLGregorianCalendar gc = (XMLGregorianCalendar) date.calendar.clone();
        gc.add(duration);
        // For xs:time the year/month/day are FIELD_UNDEFINED; the legacy
        // BC-year shift below must not run, since FIELD_UNDEFINED is
        // Integer.MIN_VALUE and setYear(MIN_VALUE - 1) would int-overflow
        // to MAX_VALUE, falsely tripping the eon overflow check.
        final int gcYear = gc.getYear();
        if (gcYear != DatatypeConstants.FIELD_UNDEFINED && gcYear < 0) {
            gc.setYear(gcYear - 1);
        }
        date.checkYearOverflow(gc);
        return date.createSameKind(gc);
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        return switch (other.getType()) {
            case Type.DAY_TIME_DURATION -> {
                if (getType() != other.getType()) {
                    throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Tried to subtract " +
                            Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                            Type.getTypeName(getType()) + "('" + getStringValue() + "')");
                }
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
                final DayTimeDurationValue diff = new DayTimeDurationValue(getExpression(), result);
                diff.checkDayTimeOverflow(diff.secondsValueSigned());
                yield diff;
            }
            case Type.YEAR_MONTH_DURATION -> {
                if (getType() != other.getType()) {
                    throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Tried to subtract " +
                            Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                            Type.getTypeName(getType()) + "('" + getStringValue() + "')");
                }
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
                final YearMonthDurationValue diff = new YearMonthDurationValue(getExpression(), result);
                diff.checkYearMonthOverflow(diff.monthsValueSigned());
                yield diff;
            }
            default -> throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Cannot subtract " +
                    Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                    Type.getTypeName(getType()) + "('" + getStringValue() + "')");
        };
    }

    /**
     * Convert the given value to a big decimal if it's a number, keeping as much precision
     * as possible.
     *
     * @param x                      a value to convert to a big decimal
     * @param exceptionMessagePrefix the beginning of the message to throw in an exception, will be suffixed with the type of the value given
     * @return the big decimal equivalent of the value
     * @throws XPathException if the value is not of a numeric type
     */
    protected BigDecimal numberToBigDecimal(ComputableValue x, String exceptionMessagePrefix) throws XPathException {
        if (!Type.subTypeOfUnion(x.getType(), Type.NUMERIC)) {
            throw new XPathException(getExpression(), exceptionMessagePrefix + Type.getTypeName(x.getType()));
        }
        if (((NumericValue) x).isInfinite() || ((NumericValue) x).isNaN()) {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Tried to convert '" + x + "' to BigDecimal");
        }

        if (x.conversionPreference(BigDecimal.class) < Integer.MAX_VALUE) {
            return x.toJavaObject(BigDecimal.class);
        } else {
            return new BigDecimal(((NumericValue) x).getDouble());
        }
    }
}