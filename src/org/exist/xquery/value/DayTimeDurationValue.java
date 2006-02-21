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

import java.math.*;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DayTimeDurationValue extends OrderedDurationValue {

	private static final Duration CANONICAL_ZERO_DURATION =
		TimeUtils.getInstance().newDuration(true, null, null, null, null, null, ZERO_DECIMAL);
	
	DayTimeDurationValue(Duration duration) throws XPathException {
		super(duration);
		if (duration.isSet(DatatypeConstants.YEARS) || duration.isSet(DatatypeConstants.MONTHS))
			throw new XPathException("the value '" + duration + "' is not an xdt:dayTimeDuration since it specifies year or month values");
	}

	public DayTimeDurationValue(long millis) throws XPathException {
		this(TimeUtils.getInstance().newDurationDayTime(millis));
	}
	
	public DayTimeDurationValue(String str) throws XPathException {
		this(TimeUtils.getInstance().newDurationDayTime(str));
	}

	public int getType() {
		return Type.DAY_TIME_DURATION;
	}
	
	public double getValue() {
		double value = duration.getDays();
		value = value * 24 + duration.getHours();
		value = value * 60 + duration.getMinutes();
		Number n = duration.getField(DatatypeConstants.SECONDS);
		value = value * 60 + (n == null ? 0 : n.doubleValue());
		return value * duration.getSign();
	}
	
	public long getValueInMilliseconds() {
		return (long) (getValue() * 1000);
	}
	
	protected Duration canonicalZeroDuration() {
		return CANONICAL_ZERO_DURATION;
	}
	
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ITEM:
			case Type.ATOMIC:
			case Type.DAY_TIME_DURATION:
				return this;
			case Type.STRING:
				return new StringValue(getStringValue());
			case Type.DURATION:
				return new DurationValue(TimeUtils.getInstance().newDuration(
						duration.getSign() >= 0, null, null,
						(BigInteger) duration.getField(DatatypeConstants.DAYS),
						(BigInteger) duration.getField(DatatypeConstants.HOURS),
						(BigInteger) duration.getField(DatatypeConstants.MINUTES),
						(BigDecimal) duration.getField(DatatypeConstants.SECONDS)));
			default:
				throw new XPathException(
					"Type error: cannot cast xs:dayTimeDuration to "
					+ Type.getTypeName(requiredType));
		}
	}
	
	protected DurationValue createSameKind(Duration dur) throws XPathException {
		return new DayTimeDurationValue(dur);
	}
	
	public ComputableValue plus(ComputableValue other) throws XPathException {
		try {
			return super.plus(other);
		} catch (IllegalArgumentException e) {
				throw new XPathException("Operand to plus should be of type xdt:dayTimeDuration, xs:time, " +
					"xs:date or xs:dateTime; got: " +
					Type.getTypeName(other.getType()));
		}
	}
	
	public ComputableValue mult(ComputableValue other) throws XPathException {
		BigDecimal factor = numberToBigDecimal(other, "Operand to mult should be of numeric type; got: ");
		return new DayTimeDurationValue(duration.multiply(factor));
	}

	public ComputableValue div(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION) {		
			DecimalValue a = new DecimalValue(secondsValueSigned());
			DecimalValue b = new DecimalValue(((DayTimeDurationValue)other).secondsValueSigned());
			return new DecimalValue(a.value.divide(b.value, 20, BigDecimal.ROUND_HALF_UP));
		}
		BigDecimal divisor = numberToBigDecimal(other, "Operand to div should be of xdt:dayTimeDuration or numeric type; got: ");
		BigDecimal secondsValueSigned = secondsValueSigned();
		return fromDecimalSeconds(secondsValueSigned.divide(divisor, Math.max(Math.max(3, secondsValueSigned.scale()), divisor.scale()), BigDecimal.ROUND_HALF_UP));
	}

	private DayTimeDurationValue fromDecimalSeconds(BigDecimal x) throws XPathException {
		return new DayTimeDurationValue(TimeUtils.getInstance().newDuration(
				x.signum() >= 0, null, null, null, null, null, x));
	}

}
