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

import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.FloatingPointConverter;
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
public class DayTimeDurationValue extends OrderedDurationValue {

    public static final Duration CANONICAL_ZERO_DURATION =
            TimeUtils.getInstance().newDuration(true, null, null, null, null, null, ZERO_DECIMAL);

    DayTimeDurationValue(final Duration duration) throws XPathException {
        this(null, duration);
    }

    DayTimeDurationValue(final Expression expression, Duration duration) throws XPathException {
        super(expression, duration);
        if (duration.isSet(DatatypeConstants.YEARS) || duration.isSet(DatatypeConstants.MONTHS)) {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "the value '" + duration + "' is not an xdt:dayTimeDuration since it specifies year or month values");
        }
    }

    public DayTimeDurationValue(long millis) throws XPathException {
        this(null, millis);
    }

    public DayTimeDurationValue(final Expression expression, long millis) throws XPathException {
        this(expression, TimeUtils.getInstance().newDurationDayTime(millis));
    }

    public DayTimeDurationValue(String str) throws XPathException {
        this(null, str);
    }

    public DayTimeDurationValue(final Expression expression, String str) throws XPathException {
        this(expression, createDurationDayTime(StringValue.trimWhitespace(str), expression));
    }

    private static Duration createDurationDayTime(String str) throws XPathException {
        return createDurationDayTime(str, null);
    }

    private static Duration createDurationDayTime(String str, final Expression expression) throws XPathException {
        try {
            return TimeUtils.getInstance().newDurationDayTime(str);
        } catch (final IllegalArgumentException e) {
            throw new XPathException(expression, ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(Type.DAY_TIME_DURATION) +
                    " from \"" + str + "\"");
        }
    }

    public DurationValue wrap() {
        return this;
    }

    public int getType() {
        return Type.DAY_TIME_DURATION;
    }

    public double getValue() {
        double value = duration.getDays();
        value = value * 24 + duration.getHours();
        value = value * 60 + duration.getMinutes();
        final Number n = duration.getField(DatatypeConstants.SECONDS);
        value = value * 60 + (n == null ? 0 : n.doubleValue());
        return value * duration.getSign();
    }

    public long getValueInMilliseconds() {
        return (long) (getValue() * 1000);
    }

    protected Duration canonicalZeroDuration() {
        return CANONICAL_ZERO_DURATION;
    }

    public String getStringValue() {
        final Duration canonicalDuration = getCanonicalDuration();

        final int d = canonicalDuration.getDays();
        final int h = canonicalDuration.getHours();
        final int m = canonicalDuration.getMinutes();
        Number s = canonicalDuration.getField(DatatypeConstants.SECONDS);
        if (s == null) {
            s = 0;
        }

        //Copied from Saxon 8.6.1
        final FastStringBuffer sb = new FastStringBuffer(32);
        if (canonicalDuration.getSign() < 0) {
            sb.append('-');
        }
        sb.append('P');
        if (d != 0) {
            sb.append(d + "D");
        }
        if (d == 0 || h != 0 || m != 0 || s.intValue() != 0) {
            sb.append('T');
        }
        if (h != 0) {
            sb.append(h + "H");
        }
        if (m != 0) {
            sb.append(m + "M");
        }
        if ((s.intValue() != 0) || (d == 0 && m == 0 && h == 0)) {
            //TODO : ugly -> factorize
            //sb.append(Integer.toString(s.intValue()));
            //double ms = s.doubleValue() - s.intValue();
            //if (ms != 0.0) {
            //	sb.append(".");
            //	sb.append(Double.toString(ms).substring(2));
            //}
            //0 is a dummy parameter
            FloatingPointConverter.appendFloat(sb, s.floatValue(), false);
            sb.append("S");
            /*
            if (micros == 0) {
                sb.append(s + "S");
            } else {
                long ms = (s * 1000000) + micros;
                String mss = ms + "";
                if (s == 0) {
                    mss = "0000000" + mss;
                    mss = mss.substring(mss.length()-7);
                }
                sb.append(mss.substring(0, mss.length()-6));
                sb.append('.');
                int lastSigDigit = mss.length()-1;
                while (mss.charAt(lastSigDigit) == '0') {
                    lastSigDigit--;
                }
                sb.append(mss.substring(mss.length()-6, lastSigDigit+1));
                sb.append('S');
            }
            */
        }
        //End of copy        
        return sb.toString();

    }

    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ITEM:
            case Type.ANY_ATOMIC_TYPE:
            case Type.DAY_TIME_DURATION:
                return new DayTimeDurationValue(getExpression(), getCanonicalDuration());
            case Type.STRING: {
                final DayTimeDurationValue dtdv = new DayTimeDurationValue(getExpression(), getCanonicalDuration());
                return new StringValue(getExpression(), dtdv.getStringValue());
            }
            case Type.DURATION:
                return new DurationValue(getExpression(), TimeUtils.getInstance().newDuration(
                        duration.getSign() >= 0, null, null,
                        (BigInteger) duration.getField(DatatypeConstants.DAYS),
                        (BigInteger) duration.getField(DatatypeConstants.HOURS),
                        (BigInteger) duration.getField(DatatypeConstants.MINUTES),
                        (BigDecimal) duration.getField(DatatypeConstants.SECONDS)));
            case Type.YEAR_MONTH_DURATION:
                return new YearMonthDurationValue(getExpression(), YearMonthDurationValue.CANONICAL_ZERO_DURATION);
            //case Type.DOUBLE:
            //return new DoubleValue(monthsValueSigned().doubleValue());
            //return new DoubleValue(Double.NaN);
            //case Type.DECIMAL:
            //return new DecimalValue(monthsValueSigned().doubleValue());
            case Type.UNTYPED_ATOMIC: {
                final DayTimeDurationValue dtdv = new DayTimeDurationValue(getExpression(), getCanonicalDuration());
                return new UntypedAtomicValue(getExpression(), dtdv.getStringValue());
            }
            default:
                throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot cast '" +
                        Type.getTypeName(this.getItemType()) + "(\"" + getStringValue() + "\")' to " +
                        Type.getTypeName(requiredType));
        }
    }

    protected DurationValue createSameKind(Duration dur) throws XPathException {
        return new DayTimeDurationValue(getExpression(), dur);
    }
	
	/*
	public ComputableValue plus(ComputableValue other) throws XPathException {
		try {
			return super.plus(other);
		} catch (IllegalArgumentException e) {
				throw new XPathException(getExpression(), "Operand to plus should be of type xdt:dayTimeDuration, xs:time, " +
					"xs:date or xs:dateTime; got: " +
					Type.getTypeName(other.getType()));
		}
	}
	*/

    public ComputableValue mult(ComputableValue other) throws XPathException {
        if (other instanceof NumericValue) {
            //If $arg2 is NaN an error is raised [err:FOCA0005]
            if (((NumericValue) other).isNaN()) {
                throw new XPathException(getExpression(), ErrorCodes.FOCA0005, "Operand is not a number");
            }
            //If $arg2 is positive or negative infinity, the result overflows
            if (((NumericValue) other).isInfinite()) {
                throw new XPathException(getExpression(), ErrorCodes.FODT0002, "Multiplication by infinity overflow");
            }
        }
        final BigDecimal factor = numberToBigDecimal(other, "Operand to mult should be of numeric type; got: ");
        final boolean isFactorNegative = factor.signum() < 0;
        final DayTimeDurationValue product = new DayTimeDurationValue(getExpression(), duration.multiply(factor.abs()));
        if (isFactorNegative) {
            return new DayTimeDurationValue(getExpression(), product.negate().getCanonicalDuration());
        }
        return new DayTimeDurationValue(getExpression(), product.getCanonicalDuration());

    }

    public ComputableValue div(ComputableValue other) throws XPathException {
        if (other.getType() == Type.DAY_TIME_DURATION) {
            final DecimalValue a = new DecimalValue(getExpression(), secondsValueSigned());
            final DecimalValue b = new DecimalValue(getExpression(), ((DayTimeDurationValue) other).secondsValueSigned());
            return new DecimalValue(getExpression(), a.value.divide(b.value, 20, RoundingMode.HALF_UP));
        }
        if (other instanceof NumericValue) {
            if (((NumericValue) other).isNaN()) {
                throw new XPathException(getExpression(), ErrorCodes.FOCA0005, "Operand is not a number");
            }
            //If $arg2 is positive or negative infinity, the result is a zero-length duration
            if (((NumericValue) other).isInfinite()) {
                return new DayTimeDurationValue(getExpression(), "PT0S");
            }
            //If $arg2 is positive or negative zero, the result overflows and is handled as discussed in 10.1.1 Limits and Precision
            if (((NumericValue) other).isZero()) {
                throw new XPathException(getExpression(), ErrorCodes.FODT0002, "Division by zero");
            }
        }
        final BigDecimal divisor = numberToBigDecimal(other, "Operand to div should be of xdt:dayTimeDuration or numeric type; got: ");
        final boolean isDivisorNegative = divisor.signum() < 0;
        final BigDecimal secondsValueSigned = secondsValueSigned();
        final DayTimeDurationValue quotient = fromDecimalSeconds(secondsValueSigned.divide(divisor.abs(), Math.max(Math.max(3, secondsValueSigned.scale()), divisor.scale()), RoundingMode.HALF_UP));
        if (isDivisorNegative) {
            return new DayTimeDurationValue(getExpression(), quotient.negate().getCanonicalDuration());
        }
        return new DayTimeDurationValue(getExpression(), quotient.getCanonicalDuration());
    }

    private DayTimeDurationValue fromDecimalSeconds(BigDecimal x) throws XPathException {
        return new DayTimeDurationValue(getExpression(), TimeUtils.getInstance().newDuration(
                x.signum() >= 0, null, null, null, null, null, x.abs()));
    }

    public boolean effectiveBooleanValue() throws XPathException {
        throw new XPathException(getExpression(), ErrorCodes.FORG0006, "value of type " + Type.getTypeName(getType()) +
                " has no boolean value.");
    }
}
