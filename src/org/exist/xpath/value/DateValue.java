/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xpath.value;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

import org.apache.oro.text.perl.Perl5Util;
import org.exist.xpath.Constants;
import org.exist.xpath.XPathException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class DateValue extends AbstractDateTimeValue {

	//	dateTime format is: [+|-]yyyy-mm-dd[([+|-]hh:mm | Z)]
	private static final String regex = "/(\\+|-)?(\\d{4})-([0-1]\\d)-(\\d{2})(.*)/";
	private static final String tzre = "/(\\+|-)?([0-1]\\d):(\\d{2})/";

	public DateValue() {
		calendar = new GregorianCalendar();
		tzOffset =
			(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
				/ 60000;
		date = calendar.getTime();
	}

	public DateValue(String dateString) throws XPathException {
		Perl5Util util = new Perl5Util();
		if (!util.match(regex, dateString))
			throw new XPathException(
				"Type error: string "
					+ dateString
					+ " cannot be cast into an xs:date");
		String part = util.group(1);
		int era = 1;
		if (part != null && part.equals("-"))
			era = -1;
		part = util.group(2);
		int year = Integer.parseInt(part) * era;
		part = util.group(3);
		int month = Integer.parseInt(part);
		part = util.group(4);
		int day = Integer.parseInt(part);
		int tz = 0;
		part = util.group(10);
		if (part != null && part.length() > 0 && (!part.equals("Z"))) {
			if (!util.match(tzre, part))
				throw new XPathException("Type error: error in  timezone: " + part);
			part = util.group(2);
			tz = Integer.parseInt(part) * 60;
			part = util.group(3);
			if (part != null) {
				int tzminute = Integer.parseInt(part);
				tz += tzminute;
			}
			part = util.group(1);
			if (part.equals("-"))
				tz *= -1;
		}
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		if (explicitTimeZone)
			calendar = new GregorianCalendar(zone);
		else {
			calendar = new GregorianCalendar();
			tzOffset =
				(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
					/ 60000;
		}
		calendar.setLenient(false);
		calendar.set(year, month - 1, day);
		if (explicitTimeZone)
			calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		System.out.println(calendar.getTimeZone().getDisplayName());
		try {
			date = calendar.getTime();
		} catch (Exception e) {
			throw new XPathException(
				"Type error: string " + dateString + " cannot be cast into an xs:date");
		}
	}

	public DateValue(Calendar cal, int timezone) {
		tzOffset = timezone;
		explicitTimeZone = true;
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		calendar = new GregorianCalendar(zone);
		calendar.setLenient(false);
		calendar.set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DATE));
		calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		date = calendar.getTime();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DATE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		StringBuffer buf = new StringBuffer();
		int year = calendar.get(Calendar.YEAR);
		if (year < 0) {
			buf.append('-');
			year *= -1;
		}
		formatString(
			buf,
			year,
			(year > 9999 ? (calendar.get(Calendar.YEAR) + "").length() : 4));
		buf.append('-');
		formatString(buf, calendar.get(Calendar.MONTH) + 1, 2);
		buf.append('-');
		formatString(buf, calendar.get(Calendar.DATE), 2);
		if (tzOffset == 0) {
			buf.append('Z');
		} else {
			buf.append((tzOffset < 0 ? "-" : "+"));
			int tzo = tzOffset;
			if (tzo < 0)
				tzo = -tzo;
			int tzhours = tzo / 60;
			formatString(buf, tzhours, 2);
			buf.append(':');
			int tzminutes = tzo % 60;
			formatString(buf, tzminutes, 2);
		}
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.DATE :
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
			case Type.DATE_TIME :
				return new DateTimeValue(calendar, tzOffset);
			case Type.STRING :
				return new StringValue(getStringValue());
			default :
				throw new XPathException(
					"Type error: cannot cast xs:date to "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE) {
			int cmp = date.compareTo(((DateValue) other).date);
			switch (operator) {
				case Constants.EQ :
					return cmp == 0;
				case Constants.NEQ :
					return cmp != 0;
				case Constants.LT :
					return cmp < 0;
				case Constants.LTEQ :
					return cmp <= 0;
				case Constants.GT :
					return cmp > 0;
				case Constants.GTEQ :
					return cmp >= 0;
				default :
					throw new XPathException("Unknown operator type in comparison");
			}
		} else
			throw new XPathException(
				"Type error: cannot compare xs:date to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(org.exist.xpath.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE)
			return date.compareTo(((DateValue) other).date);
		else
			throw new XPathException(
				"Type error: cannot compare xs:date to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE)
			return date.compareTo(((DateValue) other).date) > 0 ? this : other;
		else
			return date.compareTo(((DateValue) other.convertTo(Type.DATE)).date) > 0
				? this
				: other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#min(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue min(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE)
			return date.compareTo(((DateValue) other).date) < 0 ? this : other;
		else
			return date.compareTo(((DateValue) other.convertTo(Type.DATE)).date) < 0
				? this
				: other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		Calendar ncal;
		switch(other.getType()) {
			case Type.YEAR_MONTH_DURATION:
				YearMonthDurationValue value = (YearMonthDurationValue)other;
				ncal = (Calendar)calendar.clone();
				ncal.add(Calendar.YEAR, (value.negative ? value.year : -value.year));
				ncal.add(Calendar.MONTH, (value.negative ? value.month : -value.month));
				return new DateValue(ncal, tzOffset);
			case Type.DAY_TIME_DURATION:
				DayTimeDurationValue dtv = (DayTimeDurationValue)other;
				ncal = (Calendar)calendar.clone();
				ncal.add(Calendar.DATE, dtv.day);
				return new DateValue(ncal, tzOffset);
			case Type.DATE:
				return new DayTimeDurationValue(calendar.getTimeInMillis() - ((DateValue)other).calendar.getTimeInMillis()); 
			default:
				throw new XPathException(
					"Operand to plus should be of type xdt:yearMonthDuration or xdt:dayTimeDuration; got: "
					+ Type.getTypeName(other.getType()));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		Calendar ncal;
		switch(other.getType()) {
			case Type.YEAR_MONTH_DURATION:
				YearMonthDurationValue value = (YearMonthDurationValue)other;
				ncal = (Calendar)calendar.clone();
				ncal.add(Calendar.YEAR, (value.negative ? -value.year : value.year));
				ncal.add(Calendar.MONTH, (value.negative ? -value.month : value.month));
				return new DateValue(ncal, tzOffset);
			case Type.DAY_TIME_DURATION:
				DayTimeDurationValue dtv = (DayTimeDurationValue)other;
				ncal = (Calendar)calendar.clone();
				ncal.add(Calendar.DATE, (dtv.negative ? -dtv.day : dtv.day));
				return new DateValue(ncal, tzOffset);
			default:
				throw new XPathException(
					"Operand to plus should be of type xdt:yearMonthDuration or xdt:dayTimeDuration; got: "
					+ Type.getTypeName(other.getType()));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#div(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		// TODO Auto-generated method stub
		return null;
	}

}
