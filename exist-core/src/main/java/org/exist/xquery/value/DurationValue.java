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
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DurationValue extends ComputableValue {

    public final static int YEAR = 0;
    public final static int MONTH = 1;
    public final static int DAY = 2;
    public final static int HOUR = 3;
    public final static int MINUTE = 4;
    public final static int SIGN = 5;
    protected static final BigInteger
            TWELVE = BigInteger.valueOf(12),
            TWENTY_FOUR = BigInteger.valueOf(24),
            SIXTY = BigInteger.valueOf(60);
    protected static final BigDecimal
            SIXTY_DECIMAL = BigDecimal.valueOf(60),
            ZERO_DECIMAL = BigDecimal.ZERO;
    protected static final Duration CANONICAL_ZERO_DURATION =
            TimeUtils.getInstance().newDuration(true, null, null, null, null, null, ZERO_DECIMAL);
    protected final Duration duration;
    private Duration canonicalDuration;

    public DurationValue(final Duration duration) {
        this(null, duration);
    }

    public DurationValue(final Expression expression, Duration duration) {
        super(expression);
        this.duration = duration;
    }

    public DurationValue(final String str) throws XPathException {
        this(null, str);
    }

    public DurationValue(final Expression expression, String str) throws XPathException {
        super(expression);
        try {
            this.duration = TimeUtils.getInstance().newDuration(StringValue.trimWhitespace(str));
        } catch (final IllegalArgumentException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(this.getItemType()) +
                    " from \"" + str + "\"");
        }
    }

    /**
     * Create a new duration value of the most specific type allowed by the fields set in the given
     * duration object.  If no fields are set, return a xs:dayTimeDuration.
     *
     * @param duration the duration to wrap
     * @return a new instance of the most specific subclass of <code>DurationValue</code>
     */
    public static DurationValue wrap(Duration duration) {
        try {
            return new DayTimeDurationValue(duration);
        } catch (final XPathException e) {
            try {
                return new YearMonthDurationValue(duration);
            } catch (final XPathException e2) {
                return new DurationValue(duration);
            }
        }
    }

    private static BigInteger nullIfZero(BigInteger x) {
        if (BigInteger.ZERO.compareTo(x) == Constants.EQUAL) {
            x = null;
        }
        return x;
    }

    private static BigInteger zeroIfNull(BigInteger x) {
        if (x == null) {
            x = BigInteger.ZERO;
        }
        return x;
    }

    private static BigDecimal nullIfZero(BigDecimal x) {
        if (ZERO_DECIMAL.compareTo(x) == Constants.EQUAL) {
            x = null;
        }
        return x;
    }

    private static BigDecimal zeroIfNull(BigDecimal x) {
        if (x == null) {
            x = ZERO_DECIMAL;
        }
        return x;
    }

    public static boolean areReallyEqual(Duration duration1, Duration duration2) {
        final boolean secondsEqual = zeroIfNull((BigDecimal) duration1.getField(DatatypeConstants.SECONDS)).compareTo(
                zeroIfNull((BigDecimal) duration2.getField(DatatypeConstants.SECONDS))) == Constants.EQUAL;
        return secondsEqual &&
                duration1.getMinutes() == duration2.getMinutes() &&
                duration1.getHours() == duration2.getHours() &&
                duration1.getDays() == duration2.getDays() &&
                duration1.getMonths() == duration2.getMonths() &&
                duration1.getYears() == duration2.getYears();
    }

    public DurationValue wrap() {
        return wrap(duration);
    }

    public Duration getCanonicalDuration() {
        canonicalize();
        return canonicalDuration;
    }

    public int getType() {
        return Type.DURATION;
    }

    protected DurationValue createSameKind(Duration d) throws XPathException {
        return new DurationValue(getExpression(), d);
    }

    public DurationValue negate() throws XPathException {
        return createSameKind(duration.negate());
    }

    public String getStringValue() {
        canonicalize();
        return canonicalDuration.toString();
    }

    private void canonicalize() {
        if (canonicalDuration != null) {
            return;
        }

        BigInteger years, months, days, hours, minutes;
        BigDecimal seconds;
        BigInteger[] r;

        r = monthsValue().divideAndRemainder(TWELVE);
        years = nullIfZero(r[0]);
        months = nullIfZero(r[1]);

        // TODO: replace following segment with this for JDK 1.5
//		BigDecimal[] rd = secondsValue().divideAndRemainder(SIXTY_DECIMAL);
//		seconds = nullIfZero(rd[1]);
//		r = rd[0].toBigInteger().divideAndRemainder(SIXTY);

        // segment to be replaced:
        final BigDecimal secondsValue = secondsValue();
        final BigDecimal m = secondsValue.divide(SIXTY_DECIMAL, 0, RoundingMode.DOWN);
        seconds = nullIfZero(secondsValue.subtract(SIXTY_DECIMAL.multiply(m)));
        r = m.toBigInteger().divideAndRemainder(SIXTY);

        minutes = nullIfZero(r[1]);
        r = r[0].divideAndRemainder(TWENTY_FOUR);
        hours = nullIfZero(r[1]);
        days = nullIfZero(r[0]);

        if (years == null && months == null && days == null && hours == null && minutes == null && seconds == null) {
            canonicalDuration = canonicalZeroDuration();
        } else {
            canonicalDuration = TimeUtils.getInstance().newDuration(
                    duration.getSign() >= 0,
                    years, months, days, hours, minutes, seconds);
        }
    }

    protected BigDecimal secondsValue() {
        return
                new BigDecimal(
                        zeroIfNull((BigInteger) duration.getField(DatatypeConstants.DAYS))
                                .multiply(TWENTY_FOUR)
                                .add(zeroIfNull((BigInteger) duration.getField(DatatypeConstants.HOURS)))
                                .multiply(SIXTY)
                                .add(zeroIfNull((BigInteger) duration.getField(DatatypeConstants.MINUTES)))
                                .multiply(SIXTY)
                ).add(zeroIfNull((BigDecimal) duration.getField(DatatypeConstants.SECONDS)));
    }

    protected BigDecimal secondsValueSigned() {
        BigDecimal x = secondsValue();
        if (duration.getSign() < 0) {
            x = x.negate();
        }
        return x;
    }

    protected BigInteger monthsValue() {
        return
                zeroIfNull((BigInteger) duration.getField(DatatypeConstants.YEARS))
                        .multiply(TWELVE)
                        .add(zeroIfNull((BigInteger) duration.getField(DatatypeConstants.MONTHS)));
    }

    protected BigInteger monthsValueSigned() {
        BigInteger x = monthsValue();
        if (duration.getSign() < 0) {
            x = x.negate();
        }
        return x;
    }

    protected Duration canonicalZeroDuration() {
        return CANONICAL_ZERO_DURATION;
    }

    public int getPart(int part) {
        int r;
        switch (part) {
            case YEAR:
                r = duration.getYears();
                break;
            case MONTH:
                r = duration.getMonths();
                break;
            case DAY:
                r = duration.getDays();
                break;
            case HOUR:
                r = duration.getHours();
                break;
            case MINUTE:
                r = duration.getMinutes();
                break;
            case SIGN:
                return duration.getSign();
            default:
                throw new IllegalArgumentException("Invalid argument to method getPart");
        }
        return r * duration.getSign();
    }

    public double getSeconds() {
        final Number n = duration.getField(DatatypeConstants.SECONDS);
        return n == null ? 0 : n.doubleValue() * duration.getSign();
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        canonicalize();
        switch (requiredType) {
            case Type.ITEM:
            case Type.ANY_ATOMIC_TYPE:
            case Type.DURATION:
                return new DurationValue(getExpression(), canonicalDuration);
            case Type.YEAR_MONTH_DURATION:
                if (canonicalDuration.getField(DatatypeConstants.YEARS) != null ||
                        canonicalDuration.getField(DatatypeConstants.MONTHS) != null) {
                    return new YearMonthDurationValue(getExpression(), TimeUtils.getInstance().newDurationYearMonth(
                            canonicalDuration.getSign() >= 0,
                            (BigInteger) canonicalDuration.getField(DatatypeConstants.YEARS),
                            (BigInteger) canonicalDuration.getField(DatatypeConstants.MONTHS)));
                } else {
                    return new YearMonthDurationValue(getExpression(), YearMonthDurationValue.CANONICAL_ZERO_DURATION);
                }
            case Type.DAY_TIME_DURATION:
                if (canonicalDuration.isSet(DatatypeConstants.DAYS) ||
                        canonicalDuration.isSet(DatatypeConstants.HOURS) ||
                        canonicalDuration.isSet(DatatypeConstants.MINUTES) ||
                        canonicalDuration.isSet(DatatypeConstants.SECONDS)) {
                    return new DayTimeDurationValue(getExpression(), TimeUtils.getInstance().newDuration(
                            canonicalDuration.getSign() >= 0,
                            null,
                            null,
                            (BigInteger) canonicalDuration.getField(DatatypeConstants.DAYS),
                            (BigInteger) canonicalDuration.getField(DatatypeConstants.HOURS),
                            (BigInteger) canonicalDuration.getField(DatatypeConstants.MINUTES),
                            (BigDecimal) canonicalDuration.getField(DatatypeConstants.SECONDS)));
                } else {
                    return new DayTimeDurationValue(getExpression(), DayTimeDurationValue.CANONICAL_ZERO_DURATION);
                }
            case Type.STRING:
                canonicalize();
                return new StringValue(getExpression(), getStringValue());
            case Type.UNTYPED_ATOMIC:
                canonicalize();
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "Type error: cannot cast ' + Type.getTypeName(getType()) 'to "
                                + Type.getTypeName(requiredType));
        }
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        switch (operator) {
            case EQ: {
                if (!(DurationValue.class.isAssignableFrom(other.getClass()))) {
                    throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operand type: " + Type.getTypeName(other.getType()));
                }
                //TODO : upgrade so that P365D is *not* equal to P1Y
                boolean r = duration.equals(((DurationValue) other).duration);
                //confirm strict equality to work around the JDK standard behaviour
                if (r) {
                    r = r & areReallyEqual(getCanonicalDuration(), ((DurationValue) other).getCanonicalDuration());
                }
                return r;
            }
            case NEQ: {
                if (!(DurationValue.class.isAssignableFrom(other.getClass()))) {
                    throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operand type: " + Type.getTypeName(other.getType()));
                }
                //TODO : upgrade so that P365D is *not* equal to P1Y
                boolean r = duration.equals(((DurationValue) other).duration);
                //confirm strict equality to work around the JDK standard behaviour
                if (r) {
                    r = r & areReallyEqual(getCanonicalDuration(), ((DurationValue) other).getCanonicalDuration());
                }
                return !r;
            }
            case LT:
            case LTEQ:
            case GT:
            case GTEQ:
                throw new XPathException(getExpression(), ErrorCodes.XPTY0004, Type.getTypeName(other.getType()) + " type can not be ordered");
            default:
                throw new IllegalArgumentException("Unknown comparison operator");
        }
    }

    public int compareTo(Collator collator, AtomicValue other) throws XPathException {
        if (!(DurationValue.class.isAssignableFrom(other.getClass()))) {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operand type: " + Type.getTypeName(other.getType()));
        }
        //TODO : what to do with the collator ?
        return duration.compare(((DurationValue) other).duration);
    }

    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public ComputableValue mult(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public ComputableValue div(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "invalid operation on " + Type.getTypeName(this.getType()));
    }

    public int conversionPreference(Class<?> target) {
        if (target.isAssignableFrom(getClass())) {
            return 0;
        }
        if (target.isAssignableFrom(Duration.class)) {
            return 1;
        }
        return Integer.MAX_VALUE;
    }

    @Override
    public <T> T toJavaObject(Class<T> target) throws XPathException {
        if (target.isAssignableFrom(getClass())) {
            return (T) this;
        } else if (target.isAssignableFrom(Duration.class)) {
            return (T) duration;
        }
        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.FORG0006, "value of type " + Type.getTypeName(getType()) +
                " has no boolean value.");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (DurationValue.class.isAssignableFrom(obj.getClass())) {
            return duration.equals(((DurationValue) obj).duration);
        }
        return false;
    }
}
