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
package org.exist.xquery.value;

import java.text.Collator;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

import org.apache.oro.text.perl.Perl5Util;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * Represents a value of type xs:dateTime.
 * 
 * TODO: This is in large parts copied from Saxon and needs to be revised.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class DateTimeValue extends AbstractDateTimeValue {

	// dateTime format is: [+|-]yyyy-mm-ddThh:mm:ss[.fff*][([+|-]hh:mm | Z)]
	private static final String regex =
		"/(\\+|-)?(\\d{4})-([0-1]\\d)-(\\d{2})T([0-2]\\d):([0-6][0-9]):([0-6][0-9])(\\.(\\d{1,3}))?(.*)/";
	private static final String tzre = "/(\\+|-)?([0-1]\\d):(\\d{2})/";

	public DateTimeValue() {
		calendar = new GregorianCalendar();
		tzOffset =
			(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
			/ 60000;
		date = calendar.getTime();
	}
	
	public DateTimeValue(GregorianCalendar cal, int timezone) {
		calendar = (GregorianCalendar)cal.clone();
		tzOffset = timezone;
		explicitTimeZone = true;
		date = calendar.getTime();
	}
	
	public DateTimeValue(long milliseconds, int timezone) {
		tzOffset = timezone;
		explicitTimeZone = true;
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		calendar = new GregorianCalendar(zone);
		calendar.setLenient(false);
		calendar.setTimeInMillis(milliseconds);
		calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		date = calendar.getTime();
	}
	
	public DateTimeValue(long milliseconds) {
		calendar = new GregorianCalendar();
		tzOffset =
			(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
			/ 60000;
		calendar.setTimeInMillis(milliseconds);
		date = calendar.getTime();
	}
	
	public DateTimeValue(String dateTime) throws XPathException {
		Perl5Util util = new Perl5Util();
		if (!util.match(regex, dateTime))
			throw new XPathException(
				"Type error: string " + dateTime + " cannot be cast into an xs:dateTime");
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
		part = util.group(5);
		int hour = Integer.parseInt(part);
		part = util.group(6);
		int minutes = Integer.parseInt(part);
		part = util.group(7);
		int seconds = Integer.parseInt(part);
		part = util.group(9);
		int millis = 0;
		if (part != null) {
			if (part.length() < 3)
				part += "00";
			if (part.length() > 3)
				part = part.substring(0, 3);
			millis = Integer.parseInt(part);
		}
		part = util.group(10);
		if (part != null && part.length() > 0) {
			explicitTimeZone = true;
			if (part.equals("Z")) {
				tzOffset = 0;
			} else {
				if (!util.match(tzre, part))
					throw new XPathException("Type error: error in  timezone: " + part);
				part = util.group(2);
				tzOffset = Integer.parseInt(part) * 60;
				part = util.group(3);
				if (part != null) {
					int tzminute = Integer.parseInt(part);
					tzOffset += tzminute;
				}
				part = util.group(1);
				if (part.equals("-"))
					tzOffset *= -1;
			}
		}
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		if(explicitTimeZone)
			calendar = new GregorianCalendar(zone);
		else {
			calendar = new GregorianCalendar();
			tzOffset = (calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET)) / 60000;
		}
		calendar.setLenient(false);
		calendar.set(year, month - 1, day, hour, minutes, seconds);
		calendar.set(Calendar.MILLISECOND, millis);
		if(explicitTimeZone)
			calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		try {
			date = calendar.getTime();
		} catch (Exception e) {
			throw new XPathException(
				"Type error: string " + dateTime + " cannot be cast into an xs:dateTime");
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DATE_TIME;
	}

	public DateTimeValue adjustToTimezone(int offset) {
		Date date = calendar.getTime();
		return new DateTimeValue(date.getTime(), offset);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getStringValue()
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
		buf.append('T');
		formatString(buf, calendar.get(Calendar.HOUR_OF_DAY), 2);
		buf.append(':');
		formatString(buf, calendar.get(Calendar.MINUTE), 2);
		buf.append(':');
		formatString(buf, calendar.get(Calendar.SECOND), 2);
		int millis = calendar.get(Calendar.MILLISECOND);
		if (millis != 0) {
			buf.append('.');
			String m = calendar.get(Calendar.MILLISECOND) + "";
			while (m.length() < 3)
				m = "0" + m;
			while (m.endsWith("0"))
				m = m.substring(0, m.length() - 1);
			buf.append(m);
		}
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
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.DATE_TIME :
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
			case Type.DATE :
				return new DateValue(calendar, tzOffset);
			case Type.TIME :
				return new TimeValue(calendar, tzOffset);
			case Type.STRING :
				return new StringValue(getStringValue());
			default :
				throw new XPathException(
					"Type error: cannot cast xs:dateTime to "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE_TIME) {
			int cmp = date.compareTo(((DateTimeValue) other).date);
			switch(operator) {
				case Constants.EQ:
					return cmp == 0;
				case Constants.NEQ:
					return cmp != 0;
				case Constants.LT:
					return cmp < 0;
				case Constants.LTEQ:
					return cmp <= 0;
				case Constants.GT:
					return cmp > 0;
				case Constants.GTEQ:
					return cmp >= 0;
				default:
					throw new XPathException("Unknown operator type in comparison");
			}
		} else
			throw new XPathException(
				"Type error: cannot compare xs:dateTime to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE_TIME)
			return date.compareTo(((DateTimeValue) other).date);
		else
			throw new XPathException(
				"Type error: cannot compare xs:dateTime to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE_TIME)
			return date.compareTo(((DateTimeValue) other).date) > 0 ? this : other;
		else
			return date.compareTo(((DateTimeValue) other.convertTo(Type.DATE_TIME)).date) > 0 ? this : other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DATE_TIME)
			return date.compareTo(((DateTimeValue) other).date) < 0 ? this : other;
		else
			return date.compareTo(((DateTimeValue) other.convertTo(Type.DATE_TIME)).date) < 0 ? this : other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.ComputableValue#minus(org.exist.xquery.value.ComputableValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DATE_TIME:
				return new DayTimeDurationValue(date.getTime() - ((DateTimeValue) other).date.getTime());
			case Type.YEAR_MONTH_DURATION:
				return ((YearMonthDurationValue) other).negate().plus(this);
			case Type.DAY_TIME_DURATION:
				return ((DayTimeDurationValue) other).negate().plus(this);
			default:
				throw new XPathException(
						"Operand to minus should be of type xs:dateTime, xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
							+ Type.getTypeName(other.getType()));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.ComputableValue#plus(org.exist.xquery.value.ComputableValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.YEAR_MONTH_DURATION:
				return other.plus(this);
			case Type.DAY_TIME_DURATION:
				return other.plus(this);
			default:
				throw new XPathException(
						"Operand to plus should be of type xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
							+ Type.getTypeName(other.getType()));
		}
	}

}
