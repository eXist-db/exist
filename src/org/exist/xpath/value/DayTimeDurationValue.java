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

import org.exist.xpath.Constants;
import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public class DayTimeDurationValue extends DurationValue {

	public DayTimeDurationValue(DurationValue other) throws XPathException {
		this.day = other.day;
		this.hour = other.hour;
		this.minute = other.minute;
		this.second = other.second;
		this.millisecond = other.millisecond;
	}

	public DayTimeDurationValue(long millis) {
		if(millis < 0) {
			negative = true;
			millis = millis * -1;
		}
		if(millis > 1000) {
			second = (int)(millis / 1000);
			millisecond = (int)(millis % 1000);
		} else
			millisecond = (int)millis;
		normalize();
	}
	
	public DayTimeDurationValue(String str) throws XPathException {
		// format is: [+|-]PnDTnHnMnS
		if(str.length() < 3)
			throw new XPathException("Type error: xs:duration should start with [+|-]P");
		char ch;
		StringBuffer buf = new StringBuffer();
		int p = 0;
		int state = 0;
		String value = null;
		while(p < str.length()) {
			ch = str.charAt(p);
			switch(ch) {
				case '-':
					if(state > 0)
						throw new XPathException("Type error in xs:duration: " + str + ": - is not allowed here");
					negative = true;
					break;
				case 'P':
					if(state > 0)
						throw new XPathException("Type error in xs:duration: " + str + ": P is not allowed here");
					state++;
					break;
				case 'D':
					if(state != 1 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": D is not allowed to occur here");
					day = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case 'T':
					state = 2;
					break;
				case 'H':
					if(state != 2 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": H is not allowed to occur here");
					hour = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case 'M':
					if(state > 3 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": M is not allowed to occur here");
					minute = Integer.parseInt(value);
					state++;
					value = null;
					break;
				case 'S':
					if(value == null || state > 5)
						throw new XPathException("Type error in xs:duration: " + str +  ": S is not allowed to occur here");
					if(state == 5) {
						while (value.length() < 3) value += "0";
						if (value.length() > 3) value = value.substring(0, 3);
						millisecond = Integer.parseInt(value);
					} else
						second = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case '.':
					if(state > 4)
						throw new XPathException("Type error in xs:duration: " + str +  ": . is not allowed to occur here");
					second = Integer.parseInt(value);
					value = null;
					state = 5;
					break;
				case '1': case '2': case '3': case '4': case '5': case '6': case '7':
				case '8': case '9': case '0':
					if(state < 1)
						throw new XPathException("Type error in xs:duration: " + str + ": numeric value not allowed at this position");
					buf.append(ch);
					p++;
					while(p < str.length()) {
						ch = str.charAt(p);
						if(ch == '1' || ch == '2' || ch == '3' || ch == '4' || ch == '5' || ch == '6' || ch == '7' ||
								ch == '8' || ch == '9' || ch == '0')
							buf.append(ch);
						else {
							value = buf.toString();
							buf.setLength(0);
							p--;
							break;
						}
						p++;
					}
					break;
				default:
					throw new XPathException("Type error in xs:duration: " + str + ": invalid character: " + ch);
			}
			p++;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.DurationValue#getType()
	 */
	public int getType() {
		return Type.DAY_TIME_DURATION;
	}
	
	public double getValue() {
		double value = day;
		value = value * 24 + hour;
		value = value * 60 + minute;
		value = value * 60 + second + ((double)millisecond / 1000);
		if(negative)
			value *= -1;
		return value;
	}
	
	public long getValueInMilliseconds() {
		long value = day;
		value = value * 24 + hour;
		value = value * 60 + minute;
		value = value * 60 + second;
		value = value * 1000 + millisecond;
		return (negative ? -value : value);
	}
	
	public void normalize() {
		if(millisecond >= 1000) {
			second += (millisecond / 1000);
			millisecond = millisecond % 1000;
		}
		if(second > 60) {
			minute += (second / 60);
			second = second % 60;
		}
		if(minute > 60) {
			hour += (minute / 60);
			minute = minute % 60;
		}
		if(hour > 24) {
			day += (hour / 60);
			hour = hour % 60;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		StringBuffer buf = new StringBuffer();
		if(negative)
			buf.append('-');
		buf.append('P');
		if(day > 0) buf.append(day).append('D');
		if(hour > 0 || minute > 0 || second > 0)
			buf.append('T');
		if(hour > 0) buf.append(hour).append('H');
		if(minute > 0) buf.append(minute).append('M');
		if(second > 0 || millisecond > 0) {
			buf.append(second);
			if(millisecond > 0)
				buf.append('.').append(millisecond);
			buf.append('S');
		} 
		return buf.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.DurationValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ITEM:
			case Type.ATOMIC:
			case Type.DAY_TIME_DURATION:
				return this;
			case Type.STRING:
				return new StringValue(getStringValue());
			case Type.DURATION:
				return new DurationValue(this);
			default:
				throw new XPathException(
					"Type error: cannot cast xs:dayTimeDuration to "
					+ Type.getTypeName(requiredType));
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION) {
			double v1 = getValue();
			double v2 = ((DayTimeDurationValue)other).getValue();
			switch (operator) {
				case Constants.EQ :
					return v1 == v2;
				case Constants.NEQ :
					return v1 != v2;
				case Constants.LT :
					return v1 < v2;
				case Constants.LTEQ :
					return v1 <= v2;
				case Constants.GT :
					return v1 > v2;
				case Constants.GTEQ :
					return v1 >= v2;
				default :
					throw new XPathException("Unknown operator type in comparison");
			}
		} else
			throw new XPathException(
					"Type error: cannot compare xs:dayTimeDuration to "
					+ Type.getTypeName(other.getType()));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(org.exist.xpath.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION) {
			double v1 = getValue(); 
			double v2 = ((DayTimeDurationValue)other).getValue();
			if(v1 == v2)
				return 0;
			else if(v1 < v2)
				return -1;
			else
				return 1;
		} else
			throw new XPathException(
					"Type error: cannot compare xs:dayTimeDuration to "
					+ Type.getTypeName(other.getType()));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION)
			return compareTo(other) > 0 ? this : other;
		else
			return compareTo(other.convertTo(Type.DAY_TIME_DURATION)) > 0
			? this
			: other;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue min(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DAY_TIME_DURATION)
			return compareTo(other) <  0 ? this : other;
		else
			return compareTo(other.convertTo(Type.DAY_TIME_DURATION)) < 0
			? this
			: other;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.DurationValue#plus(org.exist.xpath.value.ComputableValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		long delta;
		switch(other.getType()) {
			case Type.DAY_TIME_DURATION:
				return new DayTimeDurationValue(getValueInMilliseconds() + ((DayTimeDurationValue)other).getValueInMilliseconds());
			case Type.TIME:
				delta = getValueInMilliseconds() + ((TimeValue)other).calendar.getTimeInMillis();
				return new TimeValue(delta, ((TimeValue)other).tzOffset);
			case Type.DATE_TIME:
				delta = getValueInMilliseconds() + ((DateTimeValue)other).calendar.getTimeInMillis();
				return new DateTimeValue(delta, ((DateTimeValue)other).tzOffset);
			case Type.DATE:
				delta = getValueInMilliseconds() + ((DateValue)other).calendar.getTimeInMillis();
				return new DateTimeValue(delta, ((DateValue)other).tzOffset);
			default:
				throw new XPathException("Operand to plus should be of type xdt:dayTimeDuration, xs:time, " +
					"xs:date or xs:dateTime; got: " +
					Type.getTypeName(other.getType()));
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.DurationValue#minus(org.exist.xpath.value.ComputableValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if(other.getType() == Type.DAY_TIME_DURATION)
			return new DayTimeDurationValue(getValueInMilliseconds() - ((DayTimeDurationValue)other).getValueInMilliseconds());
		else
			throw new XPathException("Operand to minus should be of type xdt:dayTimeDuration; got: " +
				Type.getTypeName(other.getType()));
	}
}
