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
import java.text.Collator;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author wolf
 */
public class DecimalValue extends NumericValue {

	BigDecimal value;

	public DecimalValue(BigDecimal decimal) {
		this.value = decimal;
	}

	public DecimalValue(String str) throws XPathException {
		try {
			value = new BigDecimal(str);
		} catch (NumberFormatException e) {
			throw new XPathException(
				"Type error: " + str + " cannot be cast to a decimal");
		}
	}

	public DecimalValue(double val) {
		value = new BigDecimal(val);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DECIMAL;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		String s = value.toString();
		while (s.length() > 0  && s.indexOf('.') != Constants.STRING_NOT_FOUND && 
				s.charAt(s.length() - 1) == '0') {
			s = s.substring(0, s.length() - 1);
		}
		if (s.length() > 0  &&  s.charAt(s.length() - 1 ) == '.')
			s = s.substring(0, s.length() - 1);
		return s;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.NUMBER :
			case Type.DECIMAL :
				return this;
			case Type.DOUBLE :
				return new DoubleValue(value.doubleValue());
			case Type.FLOAT :
				return new FloatValue(value.floatValue());
			case Type.STRING :
				return new StringValue(getStringValue());
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
			case Type.POSITIVE_INTEGER :
				return new IntegerValue(value.longValue(), requiredType);
			case Type.BOOLEAN :
				return value.signum() == 0 ? BooleanValue.FALSE : BooleanValue.TRUE;
			default :
				throw new XPathException(
					"cannot convert double value '"
						+ value
						+ "' into "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return value.signum() != 0;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#negate()
	 */
	public NumericValue negate() throws XPathException {
		return new DecimalValue(value.negate());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() throws XPathException {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_CEILING));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#floor()
	 */
	public NumericValue floor() throws XPathException {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_FLOOR));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round()
	 */
	public NumericValue round() throws XPathException {
		switch (value.signum()) {
			case -1 :
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_DOWN));
			case 0 :
				return this;
			case 1 :
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_UP));
			default :
				return this;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
	 */
	public NumericValue round(IntegerValue precision) throws XPathException {
		int pre = precision.getInt();
		if ( pre >= 0 ) 
			return new DecimalValue(value.setScale(pre, BigDecimal.ROUND_HALF_EVEN));
		else 
			return new DecimalValue(
						value.movePointRight(pre).
						setScale(0, BigDecimal.ROUND_HALF_EVEN).
						movePointLeft(pre));
	}

	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#minus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DECIMAL:
				return new DecimalValue(value.subtract(((DecimalValue) other).value));
			case Type.INTEGER:
				return minus((ComputableValue) other.convertTo(getType()));
			default:
				return ((ComputableValue) convertTo(other.getType())).minus(other);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#plus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DECIMAL:
				return new DecimalValue(value.add(((DecimalValue) other).value));
			case Type.INTEGER:
				return plus((ComputableValue) other.convertTo(getType()));
			default:
				return ((ComputableValue) convertTo(other.getType())).plus(other);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mult(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DECIMAL:
				return new DecimalValue(value.multiply(((DecimalValue) other).value));
			case Type.INTEGER:
				return mult((ComputableValue) other.convertTo(getType()));
			case Type.DAY_TIME_DURATION:
			case Type.YEAR_MONTH_DURATION:
				return other.mult(this);
			default:
				return ((ComputableValue) convertTo(other.getType())).mult(other);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#div(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DECIMAL:
				// arbitrarily set precision to 20 spots after the decimal point, since XQuery says it's "implementation-dependent"
				// TODO: find a better algorithm for deciding the result's precision?
				//return new DecimalValue(value.divide(((DecimalValue) other).value, 20, BigDecimal.ROUND_HALF_UP));
				return new DecimalValue(value.divide(((DecimalValue) other).value, BigDecimal.ROUND_HALF_UP));
			case Type.INTEGER:
				return div((ComputableValue) other.convertTo(getType()));
			default:
				return ((ComputableValue) convertTo(other.getType())).div(other);
		}
	}

	public IntegerValue idiv(NumericValue other) throws XPathException {
		ComputableValue result = div(other);
		return new IntegerValue(((IntegerValue)result.convertTo(Type.INTEGER)).getLong());		
		/*
		if (Type.subTypeOf(other.getType(), Type.DECIMAL)) {
			try {
				return new IntegerValue(value.divide(((DecimalValue) other).value, 0, BigDecimal.ROUND_DOWN).toBigInteger(), Type.INTEGER);
			} catch (ArithmeticException e) {
				throw new XPathException("division by zero", e);
			}
		}
		throw new XPathException("idiv called with incompatible argument type: " + getType() + " vs " + other.getType());
		*/
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			BigDecimal quotient =
				value.divide(((DecimalValue) other).value, BigDecimal.ROUND_DOWN);
			BigDecimal remainder =
				value.subtract(quotient.multiply(((DecimalValue) other).value));
			return new DecimalValue(remainder);
		} else
			return ((NumericValue) convertTo(other.getType())).mod(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#abs(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue abs() throws XPathException {
		return new DecimalValue(value.abs());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			return new DecimalValue(value.max(((DecimalValue) other).value));
		} else
			return new DecimalValue(
				value.max(((DecimalValue) other.convertTo(Type.DECIMAL)).value));
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			return new DecimalValue(value.min(((DecimalValue) other).value));
		} else {
			System.out.println(other.getClass().getName());
			return new DecimalValue(
				value.min(((DecimalValue) other.convertTo(Type.DECIMAL)).value));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(DecimalValue.class))
			return 0;
		if (javaClass == BigDecimal.class)
			return 1;
		if (javaClass == Long.class || javaClass == long.class)
			return 4;
		if (javaClass == Integer.class || javaClass == int.class)
			return 5;
		if (javaClass == Short.class || javaClass == short.class)
			return 6;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 7;
		if (javaClass == Double.class || javaClass == double.class)
			return 2;
		if (javaClass == Float.class || javaClass == float.class)
			return 3;
		if (javaClass == String.class)
			return 8;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 9;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(DecimalValue.class))
			return this;
		else if(target == BigDecimal.class)
			return value;
		else if (target == Double.class || target == double.class)
			return new Double(value.doubleValue());
		else if (target == Float.class || target == float.class)
			return new Float(value.floatValue());
		else if (target == Long.class || target == long.class) {
			return new Long( ((IntegerValue) convertTo(Type.LONG)).getValue() );
		} else if (target == Integer.class || target == int.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.INT);
			return new Integer((int) v.getValue());
		} else if (target == Short.class || target == short.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
			return new Short((short) v.getValue());
		} else if (target == Byte.class || target == byte.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
			return new Byte((byte) v.getValue());
		} else if (target == String.class)
			return getStringValue();
		else if (target == Boolean.class)
			return Boolean.valueOf(effectiveBooleanValue());

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}
}
