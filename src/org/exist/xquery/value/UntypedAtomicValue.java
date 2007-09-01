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

import java.text.Collator;

import org.exist.util.Collations;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class UntypedAtomicValue extends AtomicValue {

	private String value;

	public UntypedAtomicValue(String value) {
		this.value = value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		//return Type.ATOMIC;
		return Type.UNTYPED_ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		return value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.UNTYPED_ATOMIC :
				return this;
			case Type.STRING :
				return new StringValue(value);
            case Type.ANY_URI :
                return new AnyURIValue(value);
			case Type.BOOLEAN : 
                String trimmed = StringValue.trimWhitespace(value);
                if (trimmed.equals("0") || trimmed.equals("false"))
                    return BooleanValue.FALSE;
                else if (trimmed.equals("1") || trimmed.equals("true"))
                    return BooleanValue.TRUE;
                else
					throw new XPathException("FORG0001: cannot cast '" + 
							Type.getTypeName(this.getItemType()) + "(\"" + getStringValue() + "\")' to " +
							Type.getTypeName(requiredType));
			case Type.FLOAT :
				return new FloatValue(value);
			case Type.DOUBLE :
				return new DoubleValue(this);
			case Type.NUMBER :
				//TODO : more complicated
				return new DoubleValue(this);
			case Type.DECIMAL :
				return new DecimalValue(getStringValue());
			case Type.INTEGER :
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
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
			case Type.BASE64_BINARY :
				return new Base64Binary(value);
            case Type.HEX_BINARY :
                return new HexBinary(value);
			case Type.DATE_TIME :
				return new DateTimeValue(value);
			case Type.TIME :
				return new TimeValue(value);
			case Type.DATE :	
				return new DateValue(value);  
            case Type.GYEAR :
            	return new GYearValue(value);         		
            case Type.GMONTH :
                return new GMonthValue(value);
            case Type.GDAY :
                return new GDayValue(value);
            case Type.GYEARMONTH :
            	return new GYearMonthValue(value);  
            case Type.GMONTHDAY :
                return new GMonthDayValue(value);
			case Type.DURATION :
				return new DurationValue(value);
			case Type.YEAR_MONTH_DURATION :
				return new YearMonthDurationValue(value);
			case Type.DAY_TIME_DURATION :
				DayTimeDurationValue dtdv = new DayTimeDurationValue(value);
				return new DayTimeDurationValue(dtdv.getCanonicalDuration());
			default :
				throw new XPathException("FORG0001: cannot cast '" + 
						Type.getTypeName(this.getItemType()) + "(\"" + getStringValue() + "\")' to " +
						Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
	public boolean compareTo(Collator collator, int operator, AtomicValue other) throws XPathException {
		if (other.isEmpty())
			return false;
		if (Type.subTypeOf(other.getType(), Type.STRING) ||
				Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC)) {
			int cmp = Collations.compare(collator, value, other.getStringValue());
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
			"Type error: operands are not comparable; expected xdt:untypedAtomic; got "
				+ Type.getTypeName(other.getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		return Collations.compare(collator, value, other.getStringValue());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC))
			return Collations.compare(collator, value, ((UntypedAtomicValue) other).value) > 0 ? this : other;
		else
			return Collations.compare(collator, value, other.getStringValue()) > 0
			? this
			: other;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#min(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.UNTYPED_ATOMIC))
			return Collations.compare(collator, value, ((UntypedAtomicValue) other).value) < 0 ? this : other;
		else
			return Collations.compare(collator, value, other.getStringValue()) < 0
				? this
				: other;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#startsWith(org.exist.xquery.value.AtomicValue)
	 */
	public boolean startsWith(Collator collator, AtomicValue other) throws XPathException {
		return Collations.startsWith(collator, value, other.getStringValue());
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#endsWith(org.exist.xquery.value.AtomicValue)
	 */
	public boolean endsWith(Collator collator, AtomicValue other) throws XPathException {
		return Collations.endsWith(collator, value, other.getStringValue());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#contains(org.exist.xquery.value.AtomicValue)
	 */
	public boolean contains(Collator collator, AtomicValue other) throws XPathException {
		return Collations.indexOf(collator, value, other.getStringValue()) != Constants.STRING_NOT_FOUND;
	}	
	
	public boolean effectiveBooleanValue() throws XPathException {
		// If its operand is a singleton value of type xs:string, xs:anyURI, xs:untypedAtomic, 
		//or a type derived from one of these, fn:boolean returns false if the operand value has zero length; otherwise it returns true.
		return value.length() > 0;
	}	

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(StringValue.class))
			return 0;
		if (javaClass == String.class || javaClass == CharSequence.class)
			return 1;
		if (javaClass == Character.class || javaClass == char.class)
			return 2;
		if (javaClass == Double.class || javaClass == double.class)
			return 10;
		if (javaClass == Float.class || javaClass == float.class)
			return 11;
		if (javaClass == Long.class || javaClass == long.class)
			return 12;
		if (javaClass == Integer.class || javaClass == int.class)
			return 13;
		if (javaClass == Short.class || javaClass == short.class)
			return 14;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 15;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 16;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(UntypedAtomicValue.class))
			return this;
		else if (
			target == Object.class
				|| target == String.class
				|| target == CharSequence.class)
			return value;
		else if (target == double.class || target == Double.class) {
			DoubleValue v = (DoubleValue) convertTo(Type.DOUBLE);
			return new Double(v.getValue());
		} else if (target == float.class || target == Float.class) {
			FloatValue v = (FloatValue) convertTo(Type.FLOAT);
			return new Float(v.value);
		} else if (target == long.class || target == Long.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.LONG);
			return new Long(v.getInt());
		} else if (target == int.class || target == Integer.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.INT);
			return new Integer(v.getInt());
		} else if (target == short.class || target == Short.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
			return new Short((short) v.getInt());
		} else if (target == byte.class || target == Byte.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
			return new Byte((byte) v.getInt());
		} else if (target == boolean.class || target == Boolean.class) {
			return Boolean.valueOf(effectiveBooleanValue());
		} else if (target == char.class || target == Character.class) {
			if (value.length() > 1 || value.length() == 0)
				throw new XPathException("cannot convert string with length = 0 or length > 1 to Java character");
			return new Character(value.charAt(0));
		}

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}

}
