package org.exist.xquery.value;

import java.math.BigDecimal;
import java.text.Collator;

import javax.xml.datatype.*;
import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
abstract class OrderedDurationValue extends DurationValue {

	OrderedDurationValue(Duration duration) throws XPathException {
		super(duration);
	}

	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		int r = compareTo(collator, other);
		switch (operator) {
			case Constants.EQ :
				return r == 0;
			case Constants.NEQ :
				return r != 0;
			case Constants.LT :
				return r < 0;
			case Constants.LTEQ :
				return r <= 0;
			case Constants.GT :
				return r > 0;
			case Constants.GTEQ :
				return r >= 0;
			default :
				throw new XPathException("Unknown operator type in comparison");
		}
	}

	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == getType()) {
			int r = duration.compare(((OrderedDurationValue) other).duration);
			if (r == DatatypeConstants.INDETERMINATE) throw new RuntimeException("indeterminate order between totally ordered duration values " + this + " and " + other);
			return r;
		}
		throw new XPathException(
				"Type error: cannot compare " + Type.getTypeName(getType()) + " to "
				+ Type.getTypeName(other.getType()));
	}

	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() != getType()) throw new XPathException("cannot obtain maximum across different non-numeric data types");
		return compareTo(null, other) > 0 ? this : other;
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() != getType()) throw new XPathException("cannot obtain minimum across different non-numeric data types");
		return compareTo(null, other) < 0 ? this : other;
	}

	public ComputableValue plus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DAY_TIME_DURATION: {
				//if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
				Duration a = getCanonicalDuration();
				Duration b = ((OrderedDurationValue) other).getCanonicalDuration();	
				Duration result = createSameKind(a.add(b)).getCanonicalDuration();
				//TODO : move instantiation to the right place
				return new DayTimeDurationValue(result); }				
			case Type.YEAR_MONTH_DURATION: {
				//if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
				Duration a = getCanonicalDuration();
				Duration b = ((OrderedDurationValue) other).getCanonicalDuration();	
				Duration result = createSameKind(a.add(b)).getCanonicalDuration();
				//TODO : move instantiation to the right place
				return new YearMonthDurationValue(result); }
			case Type.DURATION: {
				//if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
				Duration a = getCanonicalDuration();
				Duration b = ((DurationValue) other).getCanonicalDuration();	
				Duration result = createSameKind(a.add(b)).getCanonicalDuration();
				//TODO : move instantiation to the right place
				return new DurationValue(result); }
			case Type.TIME:
			case Type.DATE_TIME:
			case Type.DATE:
				AbstractDateTimeValue date = (AbstractDateTimeValue) other;
				XMLGregorianCalendar gc = (XMLGregorianCalendar) date.calendar.clone();
				gc.add(duration);
				return date.createSameKind(gc);
			default:
				throw new IllegalArgumentException();		// caught and converted to XPathException in subclass
		}
	}

	public ComputableValue minus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
		case Type.DAY_TIME_DURATION: {
			if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
			Duration a = getCanonicalDuration();
			Duration b = ((OrderedDurationValue) other).getCanonicalDuration();	
			Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
			return new DayTimeDurationValue(result); }				
		case Type.YEAR_MONTH_DURATION: {
			if (getType() != other.getType()) throw new IllegalArgumentException();	// not a match after all
			Duration a = getCanonicalDuration();
			Duration b = ((OrderedDurationValue) other).getCanonicalDuration();	
			Duration result = createSameKind(a.subtract(b)).getCanonicalDuration();
			return new YearMonthDurationValue(result); }
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
			throw new IllegalArgumentException();		// caught and converted to XPathException in subclass	
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
	 * @param x a value to convert to a big decimal
	 * @param exceptionMessagePrefix the beginning of the message to throw in an exception, will be suffixed with the type of the value given
	 * @return the big decimal equivalent of the value
	 * @throws XPathException if the value is not of a numeric type
	 */
	protected BigDecimal numberToBigDecimal(ComputableValue x, String exceptionMessagePrefix) throws XPathException {
		if (!Type.subTypeOf(x.getType(), Type.NUMBER)) {
			throw new XPathException(exceptionMessagePrefix + Type.getTypeName(x.getType()));
		}
		BigDecimal val =
			x.conversionPreference(BigDecimal.class) < Integer.MAX_VALUE
			? (BigDecimal) x.toJavaObject(BigDecimal.class)
			: new BigDecimal(((NumericValue) x).getDouble());
		return val;
	}

}
