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

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class YearMonthDurationValue extends OrderedDurationValue {

	private static final Duration CANONICAL_ZERO_DURATION =
		TimeUtils.getInstance().newDuration(true, null, BigInteger.ZERO, null, null, null, null);
	
	YearMonthDurationValue(Duration duration) throws XPathException {
		super(duration);
		if (
				duration.isSet(DatatypeConstants.DAYS) ||
				duration.isSet(DatatypeConstants.HOURS) ||
				duration.isSet(DatatypeConstants.MINUTES) ||
				duration.isSet(DatatypeConstants.SECONDS)
				)
			throw new XPathException("the value '" + duration + "' is not an xdt:yearMonthDuration since it specified days, hours, minutes or seconds values");
	}

	public YearMonthDurationValue(String str) throws XPathException {
		this(TimeUtils.getInstance().newDurationYearMonth(str));
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

	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ITEM :
			case Type.ATOMIC :
			case Type.YEAR_MONTH_DURATION :
				return this;
			case Type.STRING :
				return new StringValue(getStringValue());
			case Type.DURATION :
				return new DurationValue(TimeUtils.getInstance().newDuration(
						duration.getSign() >= 0,
						(BigInteger) duration.getField(DatatypeConstants.YEARS),
						(BigInteger) duration.getField(DatatypeConstants.MONTHS),
						null, null, null, null
				));
			case Type.DOUBLE:
				//return new DoubleValue(monthsValueSigned().doubleValue());
				return new DoubleValue(Double.NaN);
			case Type.DECIMAL:
				return new DecimalValue(monthsValueSigned().doubleValue());				
			default :
				throw new XPathException(
					"Type error: cannot cast xs:yearMonthDuration to "
						+ Type.getTypeName(requiredType));
		}
	}

	protected DurationValue createSameKind(Duration dur) throws XPathException {
		return new YearMonthDurationValue(dur);
	}
	
	public ComputableValue plus(ComputableValue other) throws XPathException {
		try {
			if (other.getType() == Type.TIME) throw new IllegalArgumentException();
			return super.plus(other);
		} catch (IllegalArgumentException e) {
			throw new XPathException(
					"Operand to plus should be of type xdt:yearMonthDuration, xs:date, "
						+ "or xs:dateTime; got: "
						+ Type.getTypeName(other.getType()));
		}
	}
	
	public ComputableValue mult(ComputableValue other) throws XPathException {
		BigDecimal factor = numberToBigDecimal(other, "Operand to mult should be of numeric type; got: ");
		return fromDecimalMonths(
			new BigDecimal(monthsValueSigned())
			.multiply(factor)
			.setScale(0, BigDecimal.ROUND_HALF_UP)
		);
	}

	public ComputableValue div(ComputableValue other) throws XPathException {
		if (other.getType() == Type.YEAR_MONTH_DURATION) {
			return new IntegerValue(getValue()).div(new IntegerValue(((YearMonthDurationValue) other).getValue()));
		}
		BigDecimal divisor = numberToBigDecimal(other, "Operand to div should be of xdt:yearMonthDuration or numeric type; got: ");
		return fromDecimalMonths(
			new BigDecimal(monthsValueSigned())
			.divide(divisor, 0, BigDecimal.ROUND_HALF_UP)
		);
	}
	
	private YearMonthDurationValue fromDecimalMonths(BigDecimal x) throws XPathException {
		return new YearMonthDurationValue(TimeUtils.getInstance().newDurationYearMonth(
				x.signum() >= 0, null, x.toBigInteger()));
	}
	
}
