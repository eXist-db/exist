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

import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.SimpleTimeZone;

import org.apache.oro.text.perl.Perl5Util;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class TimeValue extends AbstractDateTimeValue {

	//	time format is: hh:mm:ss[.fff*][([+|-]hh:mm | Z)]
	private static final String regex =
		"/([0-2]\\d):([0-6][0-9]):([0-6][0-9])(\\.(\\d{1,3}))?(.*)/";
	private static final String tzre = "/(\\+|-)?([0-1]\\d):(\\d{2})/";

	public TimeValue() {
		calendar = new GregorianCalendar();
		tzOffset =
			(calendar.get(Calendar.ZONE_OFFSET) + calendar.get(Calendar.DST_OFFSET))
				/ 60000;
		calendar.set(Calendar.YEAR, 2000);
		calendar.set(Calendar.MONTH, 0);
		calendar.set(Calendar.DATE, 1);
		date = calendar.getTime();
	}

	public TimeValue(Calendar cal, int timezone) {
		tzOffset = timezone;
		explicitTimeZone = true;
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		calendar = new GregorianCalendar(zone);
		calendar.setLenient(false);
		calendar.set(Calendar.YEAR, 2000);
		calendar.set(Calendar.MONTH, 0);
		calendar.set(Calendar.DATE, 1);
		calendar.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY));
		calendar.set(Calendar.MINUTE, cal.get(Calendar.MINUTE));
		calendar.set(Calendar.SECOND, cal.get(Calendar.SECOND));
		calendar.set(Calendar.MILLISECOND, cal.get(Calendar.MILLISECOND));

		calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		date = calendar.getTime();
	}

	public TimeValue(long milliseconds, int timezone) {
		tzOffset = timezone;
		explicitTimeZone = true;
		SimpleTimeZone zone = new SimpleTimeZone(tzOffset * 60000, "LLL");
		calendar = new GregorianCalendar(zone);
		calendar.setLenient(false);
		calendar.setTimeInMillis(milliseconds);
		calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		date = calendar.getTime();
	}

	public TimeValue(String timeValue) throws XPathException {
		Perl5Util util = new Perl5Util();
		if (!util.match(regex, timeValue))
			throw new XPathException(
				"Type error: string " + timeValue + " cannot be cast into an xs:time");
		String part = util.group(1);
		int hour = Integer.parseInt(part);
		part = util.group(2);
		int minutes = Integer.parseInt(part);
		part = util.group(3);
		int seconds = Integer.parseInt(part);
		part = util.group(5);
		int millis = 0;
		if (part != null) {
			if (part.length() < 3)
				part += "00";
			if (part.length() > 3)
				part = part.substring(0, 3);
			millis = Integer.parseInt(part);
		}
		part = util.group(6);
		if (part != null && part.length() > 0 && (!part.equals("Z"))) {
			if (!util.match(tzre, part))
				throw new XPathException("Type error: error in  timezone: " + part);
			explicitTimeZone = true;
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
		calendar.set(2000, 0, hour == 0 ? 2 : 1, hour, minutes, seconds);
		calendar.set(Calendar.MILLISECOND, millis);
		if (explicitTimeZone)
			calendar.set(Calendar.ZONE_OFFSET, tzOffset * 60000);
		try {
			date = calendar.getTime();
		} catch (Exception e) {
			throw new XPathException(
				"Type error: string " + timeValue + " cannot be cast into an xs:time");
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.TIME;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		StringBuffer buf = new StringBuffer();
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
			case Type.TIME :
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
			case Type.DATE_TIME :
				return new DateTimeValue(calendar, tzOffset);
			case Type.STRING :
				return new StringValue(getStringValue());
			default :
				throw new XPathException(
					"Type error: cannot cast xs:time to "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.TIME) {
			System.out.println(
				date.getTime() + " eq " + ((TimeValue) other).date.getTime());
			int cmp = date.compareTo(((TimeValue) other).date);
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
				"Type error: cannot compare xs:time to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		if (other.getType() == Type.TIME) {
			return date.compareTo(((TimeValue) other).date);
		} else
			throw new XPathException(
				"Type error: cannot compare xs:time to "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (other.getType() == Type.TIME)
			return date.compareTo(((TimeValue) other).date) > 0 ? this : other;
		else
			return date.compareTo(((TimeValue) other.convertTo(Type.TIME)).date) > 0
				? this
				: other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue min(AtomicValue other) throws XPathException {
		if (other.getType() == Type.TIME)
			return date.compareTo(((TimeValue) other).date) < 0 ? this : other;
		else
			return date.compareTo(((TimeValue) other.convertTo(Type.TIME)).date) < 0
				? this
				: other;
	}

	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (other.getType() == Type.TIME) {
			TimeValue otherTime = (TimeValue) other;
			long delta =
				calendar.getTimeInMillis() - otherTime.calendar.getTimeInMillis();
			return new DayTimeDurationValue(delta);
		} else if (other.getType() == Type.DAY_TIME_DURATION) {
			long newMillis =
				calendar.getTimeInMillis()
					- ((DayTimeDurationValue) other).getValueInMilliseconds();
			return new TimeValue(newMillis, tzOffset);
		} else
			throw new XPathException(
				"Operand to minus should be of type xs:time or xdt:dayTimeDuration; got: "
					+ Type.getTypeName(other.getType()));
	}

	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION) {
			long newMillis =
				calendar.getTimeInMillis()
					+ ((DayTimeDurationValue) other).getValueInMilliseconds();
			return new TimeValue(newMillis, tzOffset);
		} else
			throw new XPathException(
				"Operand to plus should be of type xdt:dayTimeDuration; got: "
					+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.ComputableValue#mult(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		throw new XPathException("Multiplication is not defined for xs:time values");
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.ComputableValue#div(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		throw new XPathException("Division is not defined for xs:time values");
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(TimeValue.class))
			return 0;
		if (javaClass == Date.class)
			return 1;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(TimeValue.class))
			return this;
		else if (target == Date.class)
			return calendar.getTime();
		else if (target == Object.class)
			return this;

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}
}
