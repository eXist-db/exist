/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import java.math.BigDecimal;
import java.text.Collator;
import java.util.regex.Matcher;

import org.exist.util.FastStringBuffer;
import org.exist.util.FloatingPointConverter;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public class DoubleValue extends NumericValue {

	public final static DoubleValue ZERO = new DoubleValue(0.0E0);
	public final static DoubleValue POSITIVE_INFINITY = new DoubleValue(Double.POSITIVE_INFINITY);
	public final static DoubleValue NEGATIVE_INFINITY = new DoubleValue(Double.NEGATIVE_INFINITY);
	public final static DoubleValue NaN = new DoubleValue(Double.NaN);

	private double value;

	public DoubleValue(double value) {
		this.value = value;
	}

	public DoubleValue(AtomicValue otherValue) throws XPathException {
		try {
			if (otherValue.getStringValue().equals("INF"))
				value = Double.POSITIVE_INFINITY;
			else if (otherValue.getStringValue().equals("-INF"))
				value = Double.NEGATIVE_INFINITY;
			else if (otherValue.getStringValue().equals("NaN"))
				value = Double.NaN;
			else					
				value = Double.parseDouble(otherValue.getStringValue());
		} catch (NumberFormatException e) {
			throw new XPathException("FORG0001: cannot construct " + Type.getTypeName(this.getItemType()) +
					" from '" + Type.getTypeName(otherValue.getItemType()) + "(\"" + 
					otherValue.getStringValue() + "\")'");					
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DOUBLE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	//	public String getStringValue() throws XPathException {
	//		return Double.toString(value);
	//	}
	
	//Copied from Saxon 8.6.1
	static java.util.regex.Pattern nonExponentialPattern =
        java.util.regex.Pattern.compile(
                "(-?[0-9])([0-9]+?)(0*)\\.([0-9]*)");
	//End of copy

	public String getStringValue() {
		/*
		if (value == Float.POSITIVE_INFINITY)
			return "INF"; ;
		if (value == Float.NEGATIVE_INFINITY)
			return "-INF";		
		String s = String.valueOf(value);
		s = s.replaceAll("\\.0+$", "");		
		return s;
		/*
		int e = s.indexOf('E');
		if (e == Constants.STRING_NOT_FOUND) {
			if (s.equals("Infinity")) {
				return "INF";
			} else if (s.equals("-Infinity")) {
				return "-INF";
			}
			// For some reason, Double.toString() in Java can return strings such as "0.0040"
			// so we remove any trailing zeros
			while (s.charAt(len - 1) == '0' && s.charAt(len - 2) != '.') {
				s = s.substring(0, --len);
			}
			return s;
		}
		int exp = Integer.parseInt(s.substring(e + 1));
		String sign;
		if (s.charAt(0) == '-') {
			sign = "-";
			s = s.substring(1);
			--e;
		} else
			sign = "";
		int nDigits = e - 2;
		if (exp >= nDigits) {
			return sign + s.substring(0, 1) + s.substring(2, e) + zeros(exp - nDigits);
		} else if (exp > 0) {
			return sign
				+ s.substring(0, 1)
				+ s.substring(2, 2 + exp)
				+ "."
				+ s.substring(2 + exp, e);
		} else {
			while (s.charAt(e - 1) == '0')
				e--;
			return sign + "0." + zeros(-1 - exp) + s.substring(0, 1) + s.substring(2, e);
		}
		*/
		
		/*
		String javaString = String.valueOf(value);
		//Copied from Saxon-B 8.6.1
        if (value==0.0) {
            if (javaString.charAt(0) == '-') {
                return "-0";
            } else {
                return "0";
            }
        }
        if (Double.isInfinite(value)) {
            return (value > 0 ? "INF" : "-INF");
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        final double absval = Math.abs(value);
        String s = javaString;
        if (absval < 1.0e-6 || absval >= 1.0e+6) {
            if (s.indexOf('E')<0) {
                // need to use scientific notation, but Java isn't using it
                // (Java's cutoff is 1.0E7, while XPath's is 1.0E6)
                // So we have for example -2000000.0 rather than -2.0e6
                FastStringBuffer sb = new FastStringBuffer(32);
                Matcher matcher = nonExponentialPattern.matcher(s);
                if (matcher.matches()) {
                    sb.append(matcher.group(1));
                    sb.append('.');
                    sb.append(matcher.group(2));
                    final String fraction = matcher.group(4);
                    if ("0".equals(fraction)) {
                        sb.append("E" + (matcher.group(2).length() + matcher.group(3).length()));
                        return sb.toString();
                    } else {
                        sb.append(matcher.group(3));
                        sb.append(matcher.group(4));
                        sb.append("E" + (matcher.group(2).length() + matcher.group(3).length()));
                        return sb.toString();
                    }
                } else {
                    // fallback, this shouldn't happen
                    return s;
                }
            } else {
                return s;
            }
        }
        int len = s.length();
        if (s.endsWith("E0")) {
            s = s.substring(0, len - 2);
        }
        if (s.endsWith(".0")) {
            return s.substring(0, len - 2);
        }
        int e = s.indexOf('E');
        if (e < 0) {
            // For some reason, Double.toString() in Java can return strings such as "0.0040"
            // so we remove any trailing zeros
            while (s.charAt(len - 1) == '0' && s.charAt(len - 2) != '.') {
                s = s.substring(0, --len);
            }
            return s;
        }
        int exp = Integer.parseInt(s.substring(e + 1));
        String sign;
        if (s.charAt(0) == '-') {
            sign = "-";
            s = s.substring(1);
            --e;
        } else {
            sign = "";
        }
        int nDigits = e - 2;
        if (exp >= nDigits) {
            return sign + s.substring(0, 1) + s.substring(2, e) + zeros(exp - nDigits);
        } else if (exp > 0) {
            return sign + s.substring(0, 1) + s.substring(2, 2 + exp) + '.' + s.substring(2 + exp, e);
        } else {
            while (s.charAt(e-1) == '0') e--;
            return sign + "0." + zeros(-1 - exp) + s.substring(0, 1) + s.substring(2, e);
        }
        //End of copy
         */

		FastStringBuffer sb = new FastStringBuffer(20);
		//0 is a dummy parameter
		FloatingPointConverter.appendDouble(sb, value).getNormalizedString(0);	
		return sb.toString();		
	}

	static private String zeros(int n) {
		char[] buf = new char[n];
		for (int i = 0; i < n; i++)
			buf[i] = '0';
		return new String(buf);
	}

	public double getValue() {
		return value;
	}

	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#isNaN()
	 */
	public boolean isNaN() {
		return Double.isNaN(value);
	}

	public boolean isInfinite() {
		return Double.isInfinite(value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.NUMBER :
			case Type.DOUBLE :
				return this;
			case Type.FLOAT :
				//if (Float.compare(value, 0.0f) && (value < Float.MIN_VALUE || value > Float.MAX_VALUE)
				//	throw new XPathException("Value is out of range for type xs:float");
				//return new FloatValue((float) value);
				return new FloatValue(new Float(value).floatValue());
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());
			case Type.STRING :
				return new StringValue(getStringValue());
			case Type.DECIMAL :
				if (isNaN())
					throw new XPathException("FORG0001: can not convert " + 
							Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
							Type.getTypeName(requiredType));
				if (isInfinite())
					throw new XPathException("FORG0001: can not convert " + 
							Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
							Type.getTypeName(requiredType));
				return new DecimalValue(new BigDecimal(value));
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
				if (isNaN())
					throw new XPathException("FORG0001: can not convert " + 
							Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
							Type.getTypeName(requiredType));
				if (Double.isInfinite(value))
					throw new XPathException("FORG0001: can not convert " + 
							Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
							Type.getTypeName(requiredType));
				return new IntegerValue((long) value, requiredType);
			case Type.BOOLEAN :
				return new BooleanValue(this.effectiveBooleanValue());				
			default :
				throw new XPathException("FORG0001: cannot cast '" + 
						Type.getTypeName(this.getItemType()) + "(\"" + getStringValue() + "\")' to " +
						Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		if (value == 0.0)
			return false;
		if (isNaN())
			return false;
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getDouble()
	 */
	public double getDouble() throws XPathException {
		return value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getInt()
	 */
	public int getInt() throws XPathException {
		return (int) Math.round(value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getLong()
	 */
	public long getLong() throws XPathException {
		return (long) Math.round(value);
	}

	public void setValue(double val) {
		value = val;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() throws XPathException {
		return new DoubleValue(Math.ceil(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#floor()
	 */
	public NumericValue floor() throws XPathException {
		return new DoubleValue(Math.floor(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round()
	 */
	public NumericValue round() throws XPathException {
		if (isNaN())
			return DoubleValue.NaN;
		if (isInfinite() && value > 0)
			return DoubleValue.POSITIVE_INFINITY;
		if (isInfinite() && value < 0)
			return DoubleValue.POSITIVE_INFINITY;		
		return new DoubleValue(Math.round(value));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
	 */
	public NumericValue round(IntegerValue precision) throws XPathException {
		/* use the decimal rounding method */
		return (DoubleValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.DOUBLE);
	}
	

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#minus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(value - ((DoubleValue) other).value);
		else
			return minus((ComputableValue) other.convertTo(getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#plus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(value + ((DoubleValue) other).value);
		else
			return plus((ComputableValue) other.convertTo(getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mult(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		switch(other.getType()) {
			case Type.DOUBLE:
				return new DoubleValue(value * ((DoubleValue) other).value);
			case Type.DAY_TIME_DURATION:
			case Type.YEAR_MONTH_DURATION:
				return other.mult(this);
			default:
				return mult((ComputableValue) other.convertTo(getType()));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#div(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(value / ((DoubleValue) other).value);
		else
			return div((ComputableValue) other.convertTo(getType()));
	}

	public IntegerValue idiv(NumericValue other) throws XPathException {
		ComputableValue result = div(other);
		return new IntegerValue(((IntegerValue)result.convertTo(Type.INTEGER)).getLong());		
		/*
		if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
			double result = value / ((DoubleValue) other).value;
			if (result == Double.NaN || result == Double.POSITIVE_INFINITY || result == Double.NEGATIVE_INFINITY)
				throw new XPathException("illegal arguments to idiv");
			return new IntegerValue(new BigDecimal(result).toBigInteger(), Type.INTEGER);
		}
		throw new XPathException("idiv called with incompatible argument type: " + getType() + " vs " + other.getType());
		*/
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(value % ((DoubleValue) other).value);
		else
			return mod((NumericValue) other.convertTo(getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#negate()
	 */
	public NumericValue negate() throws XPathException {
		return new DoubleValue(-value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#abs()
	 */
	public NumericValue abs() throws XPathException {
		return new DoubleValue(Math.abs(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(Math.max(value, ((DoubleValue) other).value));
		else
			return new DoubleValue(
				Math.max(value, ((DoubleValue) other.convertTo(getType())).value));
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.DOUBLE))
			return new DoubleValue(Math.min(value, ((DoubleValue) other).value));
		else
			return new DoubleValue(
				Math.min(value, ((DoubleValue) other.convertTo(getType())).value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(DoubleValue.class))
			return 0;
		if (javaClass == Long.class || javaClass == long.class)
			return 3;
		if (javaClass == Integer.class || javaClass == int.class)
			return 4;
		if (javaClass == Short.class || javaClass == short.class)
			return 5;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 6;
		if (javaClass == Double.class || javaClass == double.class)
			return 1;
		if (javaClass == Float.class || javaClass == float.class)
			return 2;
		if (javaClass == String.class)
			return 7;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 8;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(DoubleValue.class))
			return this;
		else if (target == Double.class || target == double.class)
			return new Double(value);
		else if (target == Float.class || target == float.class)
			return new Float(value);
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

    /** size writen by {@link #serialize(byte[] data, int offset)} */
	public int getSerializedSize() {
		return 1 + 8;
	}
	
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue)o;
        if(Type.subTypeOf(other.getType(), Type.DOUBLE))
            return Double.compare(value, ((DoubleValue)other).value);
        else
            return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;
    }
}
