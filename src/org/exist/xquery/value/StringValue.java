/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.value;

import org.apache.oro.text.perl.Perl5Util;
import org.exist.dom.QName;
import org.exist.util.XMLChar;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public class StringValue extends AtomicValue {

	public final static StringValue EMPTY_STRING = new StringValue("");

	protected int type = Type.STRING;

	protected String value;

	public StringValue(String stringValue, int type) throws XPathException {
		this.type = type;
        stringValue = expand(stringValue);
		if(type == Type.STRING)
			this.value = stringValue;
		else if(type == Type.NORMALIZED_STRING)
			this.value = normalizeWhitespace(stringValue); 
		else {
			this.value = collapseWhitespace(stringValue);
			checkType();
		}
	}

	public StringValue(String stringValue) {
        try {
            value = expand(stringValue);
        } catch(XPathException e) {
            value = stringValue;
        }
	}

	private void checkType() throws XPathException {
		switch(type) {
			case Type.NORMALIZED_STRING:
			case Type.TOKEN:
				return;
			case Type.LANGUAGE:
				Perl5Util util = new Perl5Util();
				String regex =
					"/(([a-z]|[A-Z])([a-z]|[A-Z])|" // ISO639Code
					+ "([iI]-([a-z]|[A-Z])+)|"     // IanaCode
					+ "([xX]-([a-z]|[A-Z])+))"     // UserCode
					+ "(-([a-z]|[A-Z])+)*/";        // Subcode
				if (!util.match(regex, value))
					throw new XPathException(
						"Type error: string "
						+ value
						+ " is not valid for type xs:language");
				return;
			case Type.NAME:
				if(!QName.isQName(value))
					throw new XPathException("Type error: string " + value + " is not a valid xs:Name");
				return;
			case Type.NCNAME:
			case Type.ID:
			case Type.IDREF:
			case Type.ENTITY:
				if(!XMLChar.isValidNCName(value))
					throw new XPathException("Type error: string " + value + " is not a valid " + Type.getTypeName(type));
			case Type.NMTOKEN:
				if(!XMLChar.isValidNmtoken(value))
					throw new XPathException("Type error: string " + value + " is not a valid xs:NMTOKEN");
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.STRING;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return value;
	}

	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.STRING :
				return this;
			case Type.NORMALIZED_STRING:
			case Type.TOKEN:
			case Type.LANGUAGE:
			case Type.NMTOKEN:
			case Type.NAME:
			case Type.NCNAME:
			case Type.ID:
			case Type.IDREF:
			case Type.ENTITY:
				return new StringValue(value, requiredType);
			case Type.ANY_URI :
				return new AnyURIValue(value);
			case Type.BOOLEAN :
				if (value.equals("0") || value.equals("false"))
					return BooleanValue.FALSE;
				else if (value.equals("1") || value.equals("true"))
					return BooleanValue.TRUE;
				else
					throw new XPathException(
						"cannot convert string '" + value + "' to boolean");
			case Type.FLOAT :
			case Type.DOUBLE :
			case Type.NUMBER :
				return new DoubleValue(value);
			case Type.DECIMAL :
				return new DecimalValue(value);
			case Type.INTEGER :
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
			case Type.POSITIVE_INTEGER :
			case Type.LONG :
			case Type.INT :
			case Type.SHORT :
			case Type.BYTE :
			case Type.NON_NEGATIVE_INTEGER :
			case Type.UNSIGNED_LONG :
			case Type.UNSIGNED_INT :
			case Type.UNSIGNED_SHORT :
			case Type.UNSIGNED_BYTE :
				return new IntegerValue(value, requiredType);
			case Type.DATE_TIME :
				return new DateTimeValue(value);
			case Type.TIME :
				return new TimeValue(value);
			case Type.DATE :
				return new DateValue(value);
			case Type.DURATION :
				return new DurationValue(value);
			case Type.YEAR_MONTH_DURATION :
				return new YearMonthDurationValue(value);
			case Type.DAY_TIME_DURATION :
				return new DayTimeDurationValue(value);
			default :
				throw new XPathException(
					"cannot convert string '"
						+ value
						+ "' to "
						+ Type.getTypeName(requiredType));
		}
	}

	public int conversionPreference(Class javaClass) {
		if(javaClass.isAssignableFrom(StringValue.class)) return 0;
		if(javaClass == String.class || javaClass == CharSequence.class) return 1;
		if(javaClass == Character.class || javaClass == char.class) return 2;
		if(javaClass == Double.class || javaClass == double.class) return 10;
		if(javaClass == Float.class || javaClass == float.class) return 11;
		if(javaClass == Long.class || javaClass == long.class) return 12;
		if(javaClass == Integer.class || javaClass == int.class) return 13;
		if(javaClass == Short.class || javaClass == short.class) return 14;
		if(javaClass == Byte.class || javaClass == byte.class) return 15;
		if(javaClass == Boolean.class || javaClass == boolean.class) return 16;
		if(javaClass == Object.class) return 20;
		
		return Integer.MAX_VALUE;
	}
	
	public Object toJavaObject(Class target) throws XPathException {
		if(target.isAssignableFrom(StringValue.class))
			return this;
		else if(target == Object.class || target == String.class || target == CharSequence.class)
			return value;
		else if(target == double.class || target == Double.class) {
			DoubleValue v = (DoubleValue)convertTo(Type.DOUBLE);
			return new Double(v.getValue());
		} else if(target == float.class || target == Float.class) {
			FloatValue v = (FloatValue)convertTo(Type.FLOAT);
			return new Float(v.value);
		} else if(target == long.class || target == Long.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.LONG);
			return new Long(v.getInt());
		} else if(target == int.class || target == Integer.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.INT);
			return new Integer(v.getInt());
		} else if(target == short.class || target == Short.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.SHORT);
			return new Short((short)v.getInt());
		} else if(target == byte.class || target == Byte.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.BYTE);
			return new Byte((byte)v.getInt());
		} else if(target == boolean.class || target == Boolean.class) {
			return new Boolean(effectiveBooleanValue());
		} else if(target == char.class || target == Character.class) {
			if(value.length() > 1 || value.length() == 0)
				throw new XPathException("cannot convert string with length = 0 or length > 1 to Java character");
			return new Character(value.charAt(0));
		}
		
		throw new XPathException("cannot convert value of type " + Type.getTypeName(type) +
			" to Java object of type " + target.getName());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.STRING)) {
			boolean substringCompare = false;
			if (operator == Constants.EQ) {
				String otherVal = other.getStringValue();
				int truncation = Constants.TRUNC_NONE;
				if (otherVal.length() > 0 && otherVal.charAt(0) == '%') {
					otherVal = otherVal.substring(1);
					truncation = Constants.TRUNC_LEFT;
				}
				if (otherVal.length() > 1
					&& otherVal.charAt(otherVal.length() - 1) == '%') {
					otherVal = otherVal.substring(0, otherVal.length() - 1);
					truncation =
						(truncation == Constants.TRUNC_LEFT)
							? Constants.TRUNC_BOTH
							: Constants.TRUNC_RIGHT;
				}
				switch (truncation) {
					case Constants.TRUNC_BOTH :
						return value.indexOf(otherVal) > -1;
					case Constants.TRUNC_LEFT :
						return value.startsWith(otherVal);
					case Constants.TRUNC_RIGHT :
						return value.endsWith(otherVal);
					case Constants.TRUNC_NONE :
						return value.equals(otherVal);
				}
			}
			int cmp = value.compareTo(other.getStringValue());
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
					throw new XPathException("Type error: cannot apply operand to string value");
			}
		}
		throw new XPathException(
			"Type error: operands are not comparable; expected xs:string; got "
				+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(org.exist.xpath.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		return value.compareTo(other.getStringValue());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return value.length() > 0;
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return value;
	}

	public final static String normalizeWhitespace(CharSequence seq) {
		StringBuffer copy = new StringBuffer(seq.length());
		char ch;
		for (int i = 0; i < seq.length(); i++) {
			ch = seq.charAt(i);
			switch (ch) {
				case '\n' :
				case '\r' :
				case '\t' :
					copy.append(' ');
					break;
				default :
					copy.append(ch);
			}
		}
		return copy.toString();
	}

	public static String collapseWhitespace(CharSequence in) {
		if (in.length() == 0) {
			return in.toString();
		}
		StringBuffer sb = new StringBuffer(in.length());
		boolean inWhitespace = true;
		int i = 0;
		for (; i < in.length(); i++) {
			char c = in.charAt(i);
			switch (c) {
				case '\n' :
				case '\r' :
				case '\t' :
				case ' ' :
					if (inWhitespace) {
						// remove the whitespace
					} else {
						sb.append(' ');
						inWhitespace = true;
					}
					break;
				default :
					sb.append(c);
					inWhitespace = false;
					break;
			}
		}
		if (sb.charAt(sb.length() - 1) == ' ') {
			sb.deleteCharAt(sb.length() - 1);
		}
		return sb.toString();
	}

	public final static String expand(CharSequence seq) throws XPathException {
		StringBuffer buf = new StringBuffer(seq.length());
		StringBuffer entityRef = null;
		char ch;
		for (int i = 0; i < seq.length(); i++) {
			ch = seq.charAt(i);
			switch (ch) {
				case '&' :
					if (entityRef == null)
						entityRef = new StringBuffer();
					else
						entityRef.setLength(0);
					boolean found = false;
					for (int j = i + 1; j < seq.length(); j++) {
						ch = seq.charAt(j);
						if (ch != ';')
							entityRef.append(ch);
						else {
							found = true;
							i = j;
							break;
						}
					}
					if (found) {
						buf.append(expandEntity(entityRef.toString()));
					} else {
						buf.append('&');
					}
					break;
				default :
					buf.append(ch);
			}
		}
		return buf.toString();
	}

	private final static char expandEntity(String buf) throws XPathException {
		if (buf.equals("amp"))
			return '&';
		else if (buf.equals("lt"))
			return '<';
		else if (buf.equals("gt"))
			return '>';
		else if (buf.equals("quot"))
			return '"';
		else if (buf.equals("apos"))
			return '\'';
		else if (buf.length() > 1 && buf.charAt(0) == '#')
			return expandCharRef(buf.substring(1));
		else
			throw new XPathException("Unknown entity reference: " + buf);
	}

	private final static char expandCharRef(String buf) throws XPathException {
		try {
			if (buf.length() > 1 && buf.charAt(0) == 'x') {
				// Hex
				return (char) Integer.parseInt(buf.substring(1), 16);
			} else
				return (char) Integer.parseInt(buf);
		} catch (NumberFormatException e) {
			throw new XPathException("Unknown character reference: " + buf);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.STRING))
			return value.compareTo(((StringValue) other).value) > 0 ? this : other;
		else
			return value.compareTo(((StringValue) other.convertTo(getType())).value) > 0
				? this
				: other;
	}

	public AtomicValue min(AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.STRING))
			return value.compareTo(((StringValue) other).value) < 0 ? this : other;
		else
			return value.compareTo(((StringValue) other.convertTo(getType())).value) < 0
				? this
				: other;
	}
}
