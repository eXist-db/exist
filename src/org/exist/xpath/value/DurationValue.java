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

import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public class DurationValue extends ComputableValue {

	public final static int YEAR = 0;
	public final static int MONTH = 1;
	public final static int DAY = 2;
	public final static int HOUR = 3;
	public final static int MINUTE = 4;
	public final static int SECOND = 5;
	public final static int MILLISECOND = 6;
	
	protected boolean negative = false;
	protected int year = 0;
	protected int month = 0;
	protected int day = 0;
	protected int hour = 0;
	protected int minute = 0;
	protected int second = 0;
	protected int millisecond = 0;
	
	protected DurationValue() {
	}
	
	public DurationValue(DurationValue other) throws XPathException {
		this.negative = other.negative;
		this.year = other.year;
		this.month = other.month;
		this.day = other.day;
		this.hour = other.hour;
		this.minute = other.minute;
		this.second = other.second;
		this.millisecond = other.millisecond;
	}
	
	public DurationValue(String str) throws XPathException {
		// format is: [+|-]PnYnMnDTnHnMnS
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
				case 'Y':
					if(state != 1 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": Y is not allowed to occur here");
					year = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case 'M':
					if(value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": M is not allowed to occur here");
					if(state == 5 || state == 6)
						minute = Integer.parseInt(value);
					else if(state == 1 || state == 2)
						month = Integer.parseInt(value);
					else
						throw new XPathException("Type error in xs:duration: " + str +  ": M is not allowed to occur here");
					state++;
					value = null;
					break;
				case 'D':
					if(state > 3 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": D is not allowed to occur here");
					day = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case 'T':
					state = 5;
					break;
				case 'H':
					if(state != 5 || value == null)
						throw new XPathException("Type error in xs:duration: " + str +  ": H is not allowed to occur here");
					hour = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case 'S':
					if(value == null || state > 8)
						throw new XPathException("Type error in xs:duration: " + str +  ": S is not allowed to occur here");
					if(state == 8) {
						while (value.length() < 3) value += "0";
						if (value.length() > 3) value = value.substring(0, 3);
						millisecond = Integer.parseInt(value);
					} else
						second = Integer.parseInt(value);
					value = null;
					state++;
					break;
				case '.':
					if(state > 7)
						throw new XPathException("Type error in xs:duration: " + str +  ": . is not allowed to occur here");
					second = Integer.parseInt(value);
					value = null;
					state = 8;
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
			}
			p++;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DURATION;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		StringBuffer buf = new StringBuffer();
		if(negative)
			buf.append('-');
		buf.append('P');
		if(year > 0) buf.append(year).append('Y');
		if(month > 0) buf.append(month).append('M');
		if(day > 0) buf.append(day).append('D');
		if(hour > 0 || minute > 0 || second > 0)
			buf.append('T');
		if(hour > 0) buf.append(hour).append('H');
		if(minute > 0) buf.append(minute).append('M');
		if(second > 0) buf.append(second).append('S');
		return buf.toString();
	}

	public int getPart(int part) {
		switch(part) {
			case YEAR:
				return year;
			case MONTH:
				return month;
			case DAY:
				return day;
			case HOUR:
				return hour;
			case MINUTE:
				return minute;
			case SECOND:
				return second;
			case MILLISECOND:
				return millisecond;
			default:
				throw new IllegalArgumentException("Invalid argument to method getPart");
		}
	}
	
	public double getSeconds() {
		double value = (double)second;
		value += ((double)millisecond / 1000);
		return value;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ITEM:
			case Type.ATOMIC:
			case Type.DURATION:
				return this;
			case Type.YEAR_MONTH_DURATION:
				return new YearMonthDurationValue(this);
			case Type.DAY_TIME_DURATION:
				return new DayTimeDurationValue(this);
			case Type.STRING:
				return new StringValue(getStringValue());
			default:
				throw new XPathException(
					"Type error: cannot cast xs:duration to "
					+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other)
		throws XPathException {
		throw new XPathException("xs:duration values cannot be compared. Use xdt:yearMonthDuration or xdt:dayTimeDuration instead");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(org.exist.xpath.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		throw new XPathException("xs:duration values cannot be compared. Use xdt:yearMonthDuration or xdt:dayTimeDuration instead");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		throw new XPathException("xs:duration values cannot be compared. Use xdt:yearMonthDuration or xdt:dayTimeDuration instead");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#min(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue min(AtomicValue other) throws XPathException {
		throw new XPathException("xs:duration values cannot be compared. Use xdt:yearMonthDuration or xdt:dayTimeDuration instead");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		throw new XPathException("subtraction is not supported for type xs:duration");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		throw new XPathException("addition is not supported for type xs:duration");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		throw new XPathException("multiplication is not supported for type xs:duration");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.ComputableValue#div(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		throw new XPathException("division is not supported for type xs:duration");
	}
}
