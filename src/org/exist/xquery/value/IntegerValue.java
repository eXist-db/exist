/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Collator;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

/** [Definition:]   integer is <i>derived</i> from decimal by fixing the value of <i>fractionDigits<i> to be 0. 
 * This results in the standard mathematical concept of the integer numbers. 
 * The <i>value space</i> of integer is the infinite set {...,-2,-1,0,1,2,...}. 
 * The <i>base type</i> of integer is decimal.
 * cf http://www.w3.org/TR/xmlschema-2/#integer 
 */
public class IntegerValue extends NumericValue {

    //TODO this class should be split into numerous sub classes for each xs: type with proper
    //inheritance as defined by http://www.w3.org/TR/xmlschema-2/#built-in-datatypes
    
	public final static IntegerValue ZERO = new IntegerValue(0);
        
	private static final BigInteger ZERO_BIGINTEGER = new BigInteger("0");
	private static final BigInteger ONE_BIGINTEGER = new BigInteger("1");
	private static final BigInteger MINUS_ONE_BIGINTEGER = new BigInteger("-1");
	
    private static final BigInteger LARGEST_LONG  = new BigInteger("9223372036854775807");
	private static final BigInteger SMALLEST_LONG  = new BigInteger("-9223372036854775808");
	
    private static final BigInteger LARGEST_UNSIGNED_LONG = new BigInteger("18446744073709551615");
        
    private static final BigInteger LARGEST_INT  = new BigInteger("2147483647");
	private static final BigInteger SMALLEST_INT  = new BigInteger("-2147483648");
        
    private static final BigInteger LARGEST_UNSIGNED_INT = new BigInteger("4294967295");
	
    private static final BigInteger LARGEST_SHORT = new BigInteger("32767");
	private static final BigInteger SMALLEST_SHORT = new BigInteger("-32768");
	
    private static final BigInteger LARGEST_UNSIGNED_SHORT = new BigInteger("65535");
        
    private static final BigInteger LARGEST_BYTE = new BigInteger("127");
	private static final BigInteger SMALLEST_BYTE = new BigInteger("-128");
        
    private static final BigInteger LARGEST_UNSIGNED_BYTE = new BigInteger("255");
	
	private BigInteger value;
	// 	private long value;

	//should default type be NUMBER or LONG ? -shabanovd
	private int type = Type.INTEGER;

	public IntegerValue(long value) {
		this.value = BigInteger.valueOf(value); // new BigInteger(value);
	}

	public IntegerValue(long value, int type) throws XPathException {
		this(value);
		this.type = type;
		if (!checkType(value, type))
			throw new XPathException(
				"Value is not a valid integer for type " + Type.getTypeName(type));
	}

	public IntegerValue(String stringValue) throws XPathException {
		try {
			value = new BigInteger(StringValue.trimWhitespace(stringValue)); // Long.parseLong(stringValue);
		} catch (NumberFormatException e) {
				throw new XPathException(ErrorCodes.FORG0001,
					"failed to convert '" + stringValue + "' to an integer: " + e.getMessage(), e);
//			}
		}
	}

	public IntegerValue(String stringValue, int requiredType) throws XPathException {
		this.type = requiredType;
		try {
			value =  new BigInteger(StringValue.trimWhitespace(stringValue)); // Long.parseLong(stringValue);
			if (!(checkType(value, type)))
				throw new XPathException(ErrorCodes.FORG0001, "can not convert '" + 
						stringValue + "' to " + Type.getTypeName(type));
		} catch (NumberFormatException e) {
			throw new XPathException(ErrorCodes.FORG0001, "can not convert '" + 
					stringValue + "' to " + Type.getTypeName(type));
		}
	}

	/**
	 * @param value
	 * @param requiredType
	 */
	public IntegerValue(BigInteger value, int requiredType) {
		this.value = value;
		type = requiredType;
	}

	/**
	 * @param integer
	 */
	public IntegerValue(BigInteger integer) {
		this.value = integer;
	}

	/**
	 * @param value2
	 * @param type2
	 * @throws XPathException
	 */
	private boolean checkType(BigInteger value2, int type2) throws XPathException {
            switch (type) {
		
                case Type.LONG :
                    // jmv: add test since now long is not the default implementation anymore:
                    return value.compareTo(SMALLEST_LONG) >= 0 &&
                            value.compareTo(LARGEST_LONG ) <= 0;
                    
                case Type.UNSIGNED_LONG :
                    return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                            value.compareTo(LARGEST_UNSIGNED_LONG ) <= 0;
                    
		case Type.INTEGER :
		case Type.DECIMAL :
			return true;
		
		case Type.POSITIVE_INTEGER :
			return value.compareTo(ZERO_BIGINTEGER) == 1; // >0
		case Type.NON_NEGATIVE_INTEGER :
			return value.compareTo(MINUS_ONE_BIGINTEGER) == 1; // > -1
		
		case Type.NEGATIVE_INTEGER :
			return value.compareTo(ZERO_BIGINTEGER) == -1 ; // <0
		case Type.NON_POSITIVE_INTEGER :
			return value.compareTo(ONE_BIGINTEGER) == -1; // <1
		
		case Type.INT :
			return value.compareTo(SMALLEST_INT) >= 0 &&
				value.compareTo(LARGEST_INT) <= 0;
                    
                case Type.UNSIGNED_INT:
                    return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                            value.compareTo(LARGEST_UNSIGNED_INT) <= 0;
                        
		case Type.SHORT :
			return value.compareTo(SMALLEST_SHORT) >= 0 &&
				value.compareTo(LARGEST_SHORT) <= 0;
                    
                case Type.UNSIGNED_SHORT :
			return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
				value.compareTo(LARGEST_UNSIGNED_SHORT) <= 0;
                    
		case Type.BYTE :
			return value.compareTo(SMALLEST_BYTE) >= 0 &&
					value.compareTo(LARGEST_BYTE) <= 0;
                    
                case Type.UNSIGNED_BYTE :
                        return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
				value.compareTo(LARGEST_UNSIGNED_BYTE) <= 0;
            }
            
            throw new XPathException("Unknown type: " + Type.getTypeName(type));
	}

	private final static boolean checkType(long value, int type) throws XPathException {
		switch (type) {
			case Type.LONG :
			case Type.INTEGER :
			case Type.DECIMAL :
				return true;
			case Type.NON_POSITIVE_INTEGER :
				return value < 1;
			case Type.NEGATIVE_INTEGER :
				return value < 0;
			case Type.INT :
				return value >= -4294967295L && value <= 4294967295L;
			case Type.SHORT :
				return value >= -65535 && value <= 65535;
			case Type.BYTE :
				return value >= -255 && value <= 255;
			case Type.NON_NEGATIVE_INTEGER :
				return value > -1;
			case Type.UNSIGNED_LONG :
				return value > -1;
			case Type.UNSIGNED_INT:
				return value > -1 && value <= 4294967295L;
			case Type.UNSIGNED_SHORT :
				return value > -1 && value <= 65535;
			case Type.UNSIGNED_BYTE :
				return value > -1 && value <= 255;
			case Type.POSITIVE_INTEGER :
				return value > 0; // jmv >= 0;
		}
		throw new XPathException("Unknown type: " + Type.getTypeName(type));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#getType()
	 */
	public int getType() {
		return type;
	}

	public boolean hasFractionalPart() {
		return false;
	};
	
	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	public long getValue() {
		return value.longValue();
	}

	public void setValue(long value) {
		this.value = BigInteger.valueOf(value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return // Long.toString(value);
		value.toString();
	}
	
	public boolean isNaN() {
		return false;
	}

	public boolean isInfinite() {
		return false;
	}

	public boolean isZero() {
		return value.signum() == 0;
		//return value.compareTo(ZERO_BIGINTEGER) == Constants.EQUAL;
	};	
    
    public boolean isNegative() {
        return value.signum()<0;
    }

    public boolean isPositive() {
        return value.signum()>0;
    }

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		if (this.type == requiredType)
			return this;
		
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
			case Type.DECIMAL :
				return new DecimalValue(new BigDecimal(value));
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());				
			case Type.NUMBER :
			case Type.LONG :
			case Type.INTEGER :
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
			case Type.INT :
			case Type.SHORT :
			case Type.BYTE :
			case Type.NON_NEGATIVE_INTEGER :
			case Type.UNSIGNED_LONG :
			case Type.UNSIGNED_INT :
			case Type.UNSIGNED_SHORT :
			case Type.UNSIGNED_BYTE :
			case Type.POSITIVE_INTEGER :
				return new IntegerValue(value, requiredType);
			case Type.DOUBLE :
				return new DoubleValue(value.doubleValue());
			case Type.FLOAT:
			    return new FloatValue(value.floatValue());
			case Type.STRING :
				return new StringValue(getStringValue());
			case Type.BOOLEAN :
				return (value.compareTo(ZERO_BIGINTEGER) == 0 ) ? BooleanValue.FALSE : BooleanValue.TRUE;
			default :
				throw new XPathException(ErrorCodes.FORG0001,
					"cannot convert '" 
                    +  Type.getTypeName(this.getType()) 
                    + " (" 
                    + value 
                    + ")' into " 
                    + Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getInt()
	 */
	public int getInt() throws XPathException {
		return value.intValue(); // (int) value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getLong()
	 */
	public long getLong() throws XPathException {
		return value.longValue();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#getDouble()
	 */
	public double getDouble() throws XPathException {
		return value.doubleValue(); // (double) value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#floor()
	 */
	public NumericValue floor() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round()
	 */
	public NumericValue round() throws XPathException {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.IntegerValue)
	 */
	public NumericValue round(IntegerValue precision) throws XPathException {
		if (precision == null) return round();
		
		if ( precision.getInt()<=0 )
			return (IntegerValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.INTEGER);
		else
			return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#minus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			// return new IntegerValue(value - ((IntegerValue) other).value, type);
			return new IntegerValue( value.subtract( ((IntegerValue) other).value ), type );
		else
			return ((ComputableValue) convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#plus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			// return new IntegerValue(value + ((IntegerValue) other).value, type);
			return new IntegerValue( value.add( ((IntegerValue) other).value ), type );
		else
			return ((ComputableValue) convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mult(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		if(Type.subTypeOf(other.getType(), Type.INTEGER))
		    return new IntegerValue( value.multiply( ((IntegerValue) other).value ), type );
        else if(Type.subTypeOf(other.getType(), Type.DURATION))
            return other.mult(this);
        else
            return ((ComputableValue) convertTo(other.getType())).mult(other);
	}

	/** The div operator performs floating-point division according to IEEE 754.
	 * @see org.exist.xquery.value.NumericValue#idiv(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		if (other instanceof IntegerValue) {
			if (((IntegerValue) other).isZero())
				throw new XPathException(ErrorCodes.FOAR0001, "division by zero");
			//http://www.w3.org/TR/xpath20/#mapping : numeric; but xs:decimal if both operands are xs:integer
			BigDecimal d = new BigDecimal(value);			 
			BigDecimal od = new BigDecimal(((IntegerValue) other).value);
			int scale = Math.max(18, Math.max(d.scale(), od.scale()));	
			return new DecimalValue(d.divide(od, scale, BigDecimal.ROUND_HALF_DOWN));
		} else
			//TODO : review type promotion
			return ((ComputableValue) convertTo(other.getType())).div(other);
	}

	public IntegerValue idiv(NumericValue other) throws XPathException {
		if (other.isZero())
			//If the divisor is (positive or negative) zero, then an error is raised [err:FOAR0001]
		    throw new XPathException(ErrorCodes.FOAR0001, "division by zero");		
		ComputableValue result = div(other);
		return new IntegerValue(((IntegerValue)result.convertTo(Type.INTEGER)).getLong());		
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
			if( other.isZero() )
				throw new XPathException(ErrorCodes.FOAR0001, "division by zero");

			// long ov = ((IntegerValue) other).value.longValue();
			BigInteger ov =  ((IntegerValue) other).value;
			return new IntegerValue(value.remainder(ov), type);
		} else
			return ((NumericValue) convertTo(other.getType())).mod(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#unaryMinus()
	 */
	public NumericValue negate() throws XPathException {
		return new IntegerValue(value.negate());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#abs()
	 */
	public NumericValue abs() throws XPathException {
		// return new IntegerValue(Math.abs(value), type);
		return new IntegerValue( value.abs(), type);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#max(org.exist.xquery.value.AtomicValue)
	 */
	public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
		if(Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue( value.max( ((IntegerValue) other).value) );
		else
			return ((NumericValue) convertTo(other.getType())).max(collator, other);
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if(Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue( value.min( ((IntegerValue) other).value) );
		else
			return ((NumericValue) convertTo(other.getType())).min(collator, other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class<?> javaClass) {
		if(javaClass.isAssignableFrom(IntegerValue.class)) return 0;
		if(javaClass == Long.class || javaClass == long.class) return 1;
		if(javaClass == Integer.class || javaClass == int.class) return 2;
		if(javaClass == Short.class || javaClass == short.class) return 3;
		if(javaClass == Byte.class || javaClass == byte.class) return 4;
		if(javaClass == Double.class || javaClass == double.class) return 5;
		if(javaClass == Float.class || javaClass == float.class) return 6;
		if(javaClass == String.class) return 7;
		if(javaClass == Boolean.class || javaClass == boolean.class) return 8;
		if(javaClass == Object.class) return 20;
		
		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
        @Override
	public <T> T toJavaObject(final Class<T> target) throws XPathException {
		if(target.isAssignableFrom(IntegerValue.class)) {
			return (T)this;
                } else if(target == Long.class || target == long.class) {
			// ?? jmv: return new Long(value);
			return (T)Long.valueOf(value.longValue());
                } else if(target == Integer.class || target == int.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.INT);
			return (T)Integer.valueOf((int)v.value.intValue());
		} else if(target == Short.class || target == short.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.SHORT);
			return (T)Short.valueOf((short)v.value.shortValue());
		} else if(target == Byte.class || target == byte.class) {
			final IntegerValue v = (IntegerValue)convertTo(Type.BYTE);
			return (T)Byte.valueOf((byte)v.value.byteValue());
		} else if(target == Double.class || target == double.class) {
			final DoubleValue v = (DoubleValue)convertTo(Type.DOUBLE);
			return (T)new Double(v.getValue());
		} else if(target == Float.class || target == float.class) {
			final FloatValue v = (FloatValue)convertTo(Type.FLOAT);
			return (T)new Float(v.value);
		} else if(target == Boolean.class || target == boolean.class) {
			return (T)new BooleanValue(effectiveBooleanValue());
                } else if(target == String.class) {
			return (T)value.toString();
                } else if(target == BigInteger.class) {
                    return (T)new BigInteger(value.toByteArray());
                } else if(target == Object.class) {
			return (T)value; // Long(value);
                }
		
		throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
			" to Java object of type " + target.getName());
	}
	
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue)o;
        if(Type.subTypeOf(other.getType(), Type.INTEGER))
            return value.compareTo(((IntegerValue)other).value);
        else
            return getType() > other.getType() ? 1 : -1;
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
