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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Collator;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DurationValue extends ComputableValue {

	public final static int YEAR = 0;
	public final static int MONTH = 1;
	public final static int DAY = 2;
	public final static int HOUR = 3;
	public final static int MINUTE = 4;
	
	protected final Duration duration;
	private Duration canonicalDuration;
	
	protected static final BigInteger
		TWELVE = BigInteger.valueOf(12),
		TWENTY_FOUR = BigInteger.valueOf(24),
		SIXTY = BigInteger.valueOf(60);
	protected static final BigDecimal
		SIXTY_DECIMAL = BigDecimal.valueOf(60),
		ZERO_DECIMAL = BigDecimal.valueOf(0);	// TODO: replace with BigDecimal.ZERO in JDK 1.5
	
	private static final Duration CANONICAL_ZERO_DURATION =
		TimeUtils.getInstance().newDuration(true, null, null, null, null, null, ZERO_DECIMAL);
	
	public DurationValue(Duration duration) throws XPathException {
		this.duration = duration;		
	}
	
	public DurationValue(String str) throws XPathException {
		this.duration = TimeUtils.getInstance().newDuration(str);
	}
	
	public Duration getCanonicalDuration() {
		canonicalize();
		return canonicalDuration;
	}
	
	public int getType() {
		return Type.DURATION;
	}
	
	protected DurationValue createSameKind(Duration d) throws XPathException {
		return new DurationValue(d);
	}
	
	public DurationValue negate() throws XPathException {
		return createSameKind(duration.negate());
	}

	public String getStringValue() {
		canonicalize();
		return canonicalDuration.toString();
	}
	
	private BigInteger nullIfZero(BigInteger x) {
		if (BigInteger.ZERO.compareTo(x) == Constants.EQUAL) x = null;		
		return x;
	}
	
	private BigInteger zeroIfNull(BigInteger x) {
		if (x == null) x = BigInteger.ZERO;
		return x;
	}
	
	private BigDecimal nullIfZero(BigDecimal x) {
		if (ZERO_DECIMAL.compareTo(x) == Constants.EQUAL) x = null;
		return x;
	}
	
	private BigDecimal zeroIfNull(BigDecimal x) {
		if (x == null) x = ZERO_DECIMAL;
		return x;
	}
	
	private void canonicalize() {
		if (canonicalDuration != null)
			return;
		
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
		BigDecimal secondsValue = secondsValue();		
		BigDecimal m = secondsValue.divide(SIXTY_DECIMAL, 0, BigDecimal.ROUND_DOWN);
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
		if (duration.getSign() < 0) x = x.negate();
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
		if (duration.getSign() < 0) x = x.negate();
		return x;
	}
	
	protected Duration canonicalZeroDuration() {
		return CANONICAL_ZERO_DURATION;
	}
	
	public int getPart(int part) {
		int r;
		switch(part) {
			case YEAR: r = duration.getYears(); break;
			case MONTH: r = duration.getMonths(); break;
			case DAY: r = duration.getDays(); break;
			case HOUR: r = duration.getHours(); break;
			case MINUTE: r = duration.getMinutes(); break;
			default:
				throw new IllegalArgumentException("Invalid argument to method getPart");
		}
		return r * duration.getSign();
	}
	
	public double getSeconds() {
		Number n = duration.getField(DatatypeConstants.SECONDS);
		return n == null ? 0 : n.doubleValue() * duration.getSign();
	}
	
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ITEM:
			case Type.ATOMIC:
			case Type.DURATION:
				return this;
			case Type.YEAR_MONTH_DURATION:
				if (duration.getField(DatatypeConstants.YEARS) == null && 
					duration.getField(DatatypeConstants.MONTHS) == null)
					return new YearMonthDurationValue(YearMonthDurationValue.CANONICAL_ZERO_DURATION);					
				else return new YearMonthDurationValue(TimeUtils.getInstance().newDurationYearMonth(
						duration.getSign() >= 0,
						(BigInteger) duration.getField(DatatypeConstants.YEARS),
						(BigInteger) duration.getField(DatatypeConstants.MONTHS)));
			case Type.DAY_TIME_DURATION:
				if (duration.getField(DatatypeConstants.DAYS) == null && 
					duration.getField(DatatypeConstants.HOURS) == null && 
					duration.getField(DatatypeConstants.MINUTES) == null && 
					duration.getField(DatatypeConstants.SECONDS) == null)
					return new DayTimeDurationValue(DayTimeDurationValue.CANONICAL_ZERO_DURATION);					
				else return new DayTimeDurationValue(TimeUtils.getInstance().newDuration(
						duration.getSign() >= 0,
						null,
						null,
						(BigInteger) duration.getField(DatatypeConstants.DAYS),
						(BigInteger) duration.getField(DatatypeConstants.HOURS),
						(BigInteger) duration.getField(DatatypeConstants.MINUTES),
						(BigDecimal) duration.getField(DatatypeConstants.SECONDS)));
			case Type.STRING:
				return new StringValue(getStringValue());
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());
			default:
				throw new XPathException(
					"Type error: cannot cast xs:duration to "
					+ Type.getTypeName(requiredType));
		}
	}

	public boolean compareTo(Collator collator, int operator, AtomicValue other)
		throws XPathException {
		throw new XPathException("xs:duration values cannot be compared with an operator. Use xdt:yearMonthDuration or xdt:dayTimeDuration instead");
	}

	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (!(other.getClass().isAssignableFrom(DurationValue.class))) 
			throw new XPathException("FORG0006: invalid operand type: " + Type.getTypeName(other.getType()));
		//TODO : what to do with the collator ?
		return duration.compare(((DurationValue)other).duration);
	}

	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (!(other.getClass().isAssignableFrom(DurationValue.class))) 
			throw new XPathException("FORG0006: invalid operand type: " + Type.getTypeName(other.getType()));
		if (duration.compare(((DurationValue)other).duration) > 0)
			return new DurationValue(getCanonicalDuration());			
		return new DurationValue(((DurationValue)other).getCanonicalDuration());
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (!(other.getClass().isAssignableFrom(DurationValue.class))) 
			throw new XPathException("FORG0006: invalid operand type: " + Type.getTypeName(other.getType()));
		if (duration.compare(((DurationValue)other).duration) < 0)			
			return new DurationValue(getCanonicalDuration());
		return new DurationValue(((DurationValue)other).getCanonicalDuration());
	}

	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (!(other instanceof DurationValue)) 
			throw new XPathException("FORG0006: invalid operand type: " + Type.getTypeName(other.getType()));		
		return new DurationValue(getCanonicalDuration().add(((DurationValue)other).getCanonicalDuration()));		
	}
	
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (!(other instanceof DurationValue)) 
			throw new XPathException("FORG0006: invalid operand type: " + Type.getTypeName(other.getType()));		
		return new DurationValue(getCanonicalDuration().subtract(((DurationValue)other).getCanonicalDuration()));
	}
	
	public ComputableValue mult(ComputableValue other) throws XPathException {	
		BigDecimal factor = numberToBigDecimal(other, "FORG0006: invalid operand type: ");
		return new DurationValue(getCanonicalDuration().multiply(factor));				
	}

	public ComputableValue div(ComputableValue other) throws XPathException {	
		BigDecimal factor = numberToBigDecimal(other, "FORG0006: invalid operand type: ");		
		return new DurationValue(getCanonicalDuration().multiply(BigDecimal.valueOf(1).divide(factor)));				
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

	public int conversionPreference(Class target) {
		if (target.isAssignableFrom(getClass())) return 0;
		if (target.isAssignableFrom(Duration.class)) return 1;
		return Integer.MAX_VALUE;
	}

	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(getClass())) return this;
		if (target.isAssignableFrom(Duration.class)) return duration;
		throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) + " to Java object of type " + target.getName());
	}
}
