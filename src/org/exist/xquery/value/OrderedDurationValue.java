package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigDecimal;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
abstract class OrderedDurationValue extends DurationValue {

    OrderedDurationValue(Duration duration) throws XPathException {
        super(duration);
    }

    @Override
    public boolean compareTo(Collator collator, Comparison operator, AtomicValue other) throws XPathException {
        if (other.isEmpty()) {
            return false;
        }
        final int r = compareTo(collator, other);
        if (operator != Comparison.EQ && operator != Comparison.NEQ) {
            if (getType() == Type.DURATION) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        "cannot compare unordered " + Type.getTypeName(getType()) + " to "
                                + Type.getTypeName(other.getType()));
            }
            if (other.getType() == Type.DURATION) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        "cannot compare " + Type.getTypeName(getType()) + " to unordered "
                                + Type.getTypeName(other.getType()));
            }
            if (Type.getCommonSuperType(getType(), other.getType()) == Type.DURATION) {
                throw new XPathException(ErrorCodes.XPTY0004,
                        "cannot compare " + Type.getTypeName(getType()) + " to "
                                + Type.getTypeName(other.getType()));
            }
        }
        switch (operator) {
            case EQ:
                return r == DatatypeConstants.EQUAL;
            case NEQ:
                return r != DatatypeConstants.EQUAL;
            case LT:
                return r == DatatypeConstants.LESSER;
            case LTEQ:
                return r == DatatypeConstants.LESSER || r == DatatypeConstants.EQUAL;
            case GT:
                return r == DatatypeConstants.GREATER;
            case GTEQ:
                return r == DatatypeConstants.GREATER || r == DatatypeConstants.EQUAL;
            default:
                throw new XPathException("Unknown operator type in comparison");
        }
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
                throw new RuntimeException("indeterminate order between totally ordered duration values " + this + " and " + other);
            }
            return r;
        }
        throw new XPathException(
                "Type error: cannot compare " + Type.getTypeName(getType()) + " to "
                        + Type.getTypeName(other.getType()));
    }

    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() != getType()) {
            throw new XPathException("cannot obtain maximum across different non-numeric data types");
        }
        return compareTo(null, other) > 0 ? this : other;
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (other.getType() != getType()) {
            throw new XPathException("cannot obtain minimum across different non-numeric data types");
        }
        return compareTo(null, other) < 0 ? this : other;
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DAY_TIME_DURATION: {
                //if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                //TODO : move instantiation to the right place
                return new DayTimeDurationValue(result);
            }
            case Type.YEAR_MONTH_DURATION: {
                //if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                //TODO : move instantiation to the right place
                return new YearMonthDurationValue(result);
            }
            case Type.DURATION: {
                //if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
                final Duration a = getCanonicalDuration();
                final Duration b = ((DurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.add(b)).getCanonicalDuration();
                //TODO : move instantiation to the right place
                return new DurationValue(result);
            }
            case Type.TIME:
            case Type.DATE_TIME:
            case Type.DATE:
                final AbstractDateTimeValue date = (AbstractDateTimeValue) other;
                final XMLGregorianCalendar gc = (XMLGregorianCalendar) date.calendar.clone();
                gc.add(duration);
                //Shift one year
                if (gc.getYear() < 0) {
                    gc.setYear(gc.getYear() - 1);
                }
                return date.createSameKind(gc);
            default:
                throw new XPathException(ErrorCodes.XPTY0004, "cannot add " +
                        Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                        Type.getTypeName(getType()) + "('" + getStringValue() + "')");
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DAY_TIME_DURATION: {
                if (getType() != other.getType()) {
                    throw new XPathException(ErrorCodes.XPTY0004, "Tried to substract " +
                            Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                            Type.getTypeName(getType()) + "('" + getStringValue() + "')");
                }
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
                return new DayTimeDurationValue(result);
            }
            case Type.YEAR_MONTH_DURATION: {
                if (getType() != other.getType()) {
                    throw new XPathException(ErrorCodes.XPTY0004, "Tried to substract " +
                            Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                            Type.getTypeName(getType()) + "('" + getStringValue() + "')");
                }
                final Duration a = getCanonicalDuration();
                final Duration b = ((OrderedDurationValue) other).getCanonicalDuration();
                final Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
                return new YearMonthDurationValue(result);
            }
        /*
		case Type.TIME:
		case Type.DATE_TIME:
		case Type.DATE:
			AbstractDateTimeValue date = (AbstractDateTimeValue) other;
			XMLGregorianCalendar gc = (XMLGregorianCalendar) date.calendar.clone();
			gc.substract(duration);
			return date.createSameKind(gc);
		*/
            default:
                throw new XPathException(ErrorCodes.XPTY0004, "Cannot substract " +
                        Type.getTypeName(other.getType()) + "('" + other.getStringValue() + "') from " +
                        Type.getTypeName(getType()) + "('" + getStringValue() + "')");
        }
		/*
		if(other.getType() == getType()) {
			return createSameKind(duration.subtract(((OrderedDurationValue)other).duration));
		}
		throw new XPathException("Operand to minus should be of type " + Type.getTypeName(getType()) + "; got: " +
			Type.getTypeName(other.getType()));
		*/
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
        if (!Type.subTypeOf(x.getType(), Type.NUMBER)) {
            throw new XPathException(exceptionMessagePrefix + Type.getTypeName(x.getType()));
        }
        if (((NumericValue) x).isInfinite() || ((NumericValue) x).isNaN()) {
            throw new XPathException(ErrorCodes.XPTY0004, "Tried to convert '" + x + "' to BigDecimal");
        }

        if (x.conversionPreference(BigDecimal.class) < Integer.MAX_VALUE) {
            return x.toJavaObject(BigDecimal.class);
        } else {
            return new BigDecimal(((NumericValue) x).getDouble());
        }
    }
}