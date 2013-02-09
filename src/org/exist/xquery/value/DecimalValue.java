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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Collator;
import java.util.regex.Pattern;

import org.exist.util.FastStringBuffer;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

/**
 * @author wolf
 */
public class DecimalValue extends NumericValue {
    // i × 10^-n where i, n = integers  and n >= 0
    // All ·minimally conforming· processors ·must· support decimal numbers 
    // with a minimum of 18 decimal digits (i.e., with a ·totalDigits· of 18)	
	@SuppressWarnings("unused")
	private static final BigDecimal ZERO_BIGDECIMAL = new BigDecimal("0");
	//Copied from Saxon 8.6.1    
	private static final int DIVIDE_PRECISION = 18;
	//Copied from Saxon 8.7
	private static final Pattern decimalPattern = Pattern.compile("(\\-|\\+)?((\\.[0-9]+)|([0-9]+(\\.[0-9]*)?))");
	//Copied from Saxon 8.8
    private static boolean stripTrailingZerosMethodUnavailable = false;
    private static Method stripTrailingZerosMethod = null;
    private static final Object[] EMPTY_OBJECT_ARRAY = {};
    public static final BigInteger BIG_INTEGER_TEN = BigInteger.valueOf(10);
	
	BigDecimal value;

	public DecimalValue(BigDecimal decimal) {
		this.value = stripTrailingZeros(decimal);		
	}

	public DecimalValue(String str) throws XPathException {
        str = StringValue.trimWhitespace(str);
		try {
			if (!decimalPattern.matcher(str).matches()) {
				throw new XPathException(ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(this.getItemType()) +
						" from \"" + str + "\"");            
			}
			value = stripTrailingZeros(new BigDecimal(str));
		} catch (final NumberFormatException e) {
			throw new XPathException(ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(this.getItemType()) +
					" from \"" + getStringValue() + "\"");					
		}
	}

	public DecimalValue(double doubleValue) {
		value = stripTrailingZeros(new BigDecimal(doubleValue));
	}

    public BigDecimal getValue() {
        return value;
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
		/*
		String s = value.toString();
		while (s.length() > 0  && s.indexOf('.') != Constants.STRING_NOT_FOUND && 
				s.charAt(s.length() - 1) == '0') {
			s = s.substring(0, s.length() - 1);
		}
		if (s.length() > 0  &&  s.charAt(s.length() - 1 ) == '.')
			s = s.substring(0, s.length() - 1);
		return s;
		*/
		
		//Copied from Saxon 8.8
        // Can't use the plain BigDecimal#toString() under JDK 1.5 because this produces values like "1E-5".
        // JDK 1.5 offers BigDecimal#toPlainString() which might do the job directly
        int scale = value.scale();
        if (scale == 0) {
            return value.toString();
        } else if (scale < 0) {
            final String s = value.abs().unscaledValue().toString();

            final FastStringBuffer sb = new FastStringBuffer(s.length() + (-scale) + 2);
            if (value.signum() < 0) {
                sb.append('-');
            }
            sb.append(s);
            //Provisional hack : 10.0 mod 10.0 to trigger the bug
            if (!"0".equals(s)){
	            for (int i=0; i<(-scale); i++) {
	                sb.append('0');
	            }
            }
            return sb.toString();
        } else {
            final String s = value.abs().unscaledValue().toString();
            if ("0".equals(s)) {
                return s;
            }
            final int len = s.length();
            final FastStringBuffer sb = new FastStringBuffer(len+1);
            if (value.signum() < 0) {
                sb.append('-');
            }
            if (scale >= len) {
                sb.append("0.");
                for (int i=len; i<scale; i++) {
                    sb.append('0');
                }
                sb.append(s);
            } else {
                sb.append(s.substring(0, len-scale));
                sb.append('.');
                sb.append(s.substring(len-scale));
            }
            return sb.toString();
        }
        //End of copy
	}
	
	public boolean hasFractionalPart() {
		return (value.scale() > 0);
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
			case Type.UNTYPED_ATOMIC :
				return new UntypedAtomicValue(getStringValue());
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
				throw new XPathException(ErrorCodes.FORG0001,
					"cannot convert  '" 
                    +  Type.getTypeName(this.getType()) 
                    + " ("
                    + value
                    + ")' into "
                    + Type.getTypeName(requiredType));
		}
	}
	
	public boolean isNaN() {
		return false;
	}

	public boolean isInfinite() {
		return false;
	}

	public boolean isZero() {
		return value.signum() == 0;
		//return value.compareTo(ZERO_BIGDECIMAL) == Constants.EQUAL;
	}
    
    public boolean isNegative() {
        return value.signum()<0;
    }

    public boolean isPositive() {
        return value.signum()>0;
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
		if (value.signum() == 0)
			{return this;}
		
		int pre;
		if (precision == null)
			{pre = 0;}
		else 
			{pre = precision.getInt();}
		
		if ( pre >= 0 ) 
			{return new DecimalValue(value.setScale(pre, BigDecimal.ROUND_HALF_EVEN));}
		else 
			{return new DecimalValue(
						value.movePointRight(pre).
						setScale(0, BigDecimal.ROUND_HALF_EVEN).
						movePointLeft(pre));}
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
			//case Type.DECIMAL:
				//return new DecimalValue(value.divide(((DecimalValue) other).value, BigDecimal.ROUND_HALF_UP));
			case Type.INTEGER:
				return div((ComputableValue) other.convertTo(getType()));
			default: 
				if (!(other instanceof DecimalValue)) {
	            	final ComputableValue n = (ComputableValue)this.convertTo(other.getType());
	            	return ((ComputableValue)n).div(other);
				}
				if (((DecimalValue)other).isZero())
					{throw new XPathException(ErrorCodes.FOAR0001, "division by zero");}

				//Copied from Saxon 8.6.1	
		        final int scale = Math.max(DIVIDE_PRECISION, Math.max(value.scale(), ((DecimalValue)other).value.scale()));
				final BigDecimal result = value.divide(((DecimalValue)other).value, scale, BigDecimal.ROUND_HALF_DOWN);
				return new DecimalValue(result);
				//End of copy				
		}		
	}

	public IntegerValue idiv(NumericValue other) throws XPathException {
		if (other.isZero())
			{throw new XPathException(ErrorCodes.FOAR0001, "division by zero");}

		final DecimalValue dv = (DecimalValue)other.convertTo(Type.DECIMAL);
        final BigInteger quot = value.divide(dv.value, 0, BigDecimal.ROUND_DOWN).toBigInteger();
        return new IntegerValue(quot);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			if (other.isZero())
				{throw new XPathException(ErrorCodes.FOAR0001, "division by zero");}

			final BigDecimal quotient = value.divide(((DecimalValue)other).value, 0, BigDecimal.ROUND_DOWN);
            final BigDecimal remainder = value.subtract(quotient.setScale(0, BigDecimal.ROUND_DOWN).multiply(((DecimalValue) other).value));
            return new DecimalValue(remainder);
		} else
			{return ((NumericValue) convertTo(other.getType())).mod(other);}
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
			{return new DecimalValue(
				value.max(((DecimalValue) other.convertTo(Type.DECIMAL)).value));}
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			return new DecimalValue(value.min(((DecimalValue) other).value));
		} else {
			return new DecimalValue(
				value.min(((DecimalValue) other.convertTo(Type.DECIMAL)).value));
		}
	}
	
	public boolean compareTo(Collator collator, int operator, AtomicValue other)
		throws XPathException {
		if (other.isEmpty()) {
			//Never equal, or inequal...
			return false;
		}		
		if(Type.subTypeOf(other.getType(), Type.NUMBER)) {
			if (isNaN()) {
				//NaN does not equal itself.
				if (((NumericValue)other).isNaN()) {
					return operator == Constants.NEQ;
				}
			}			
			if(Type.subTypeOf(other.getType(), Type.DECIMAL)) {
				final DecimalValue otherValue = (DecimalValue)other.convertTo(Type.DECIMAL);
				switch(operator) {
					case Constants.EQ:
						return compareTo(otherValue) == Constants.EQUAL;
					case Constants.NEQ:
						return compareTo(otherValue) != Constants.EQUAL;
					case Constants.GT:
						return compareTo(otherValue) == Constants.SUPERIOR;
					case Constants.GTEQ:
						return compareTo(otherValue) != Constants.INFERIOR;
					case Constants.LT:
						return compareTo(otherValue) == Constants.INFERIOR;
					case Constants.LTEQ:
						return compareTo(otherValue) != Constants.SUPERIOR;
					default:
						throw new XPathException("Type error: cannot apply operator to numeric value");
				}
			}
		}
		//Default to the standard numeric comparison
		return super.compareTo(collator, operator, other);
	}

	
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue)o;
        if(Type.subTypeOf(other.getType(), Type.DECIMAL)) {
        	DecimalValue otherAsDecimal = null;
        	try {
        		otherAsDecimal = (DecimalValue)other.convertTo(Type.DECIMAL);        		
        	} catch (final XPathException e) {
        		//TODO : is this relevant ?
        		return Constants.INFERIOR;
        	}
        	return value.compareTo(otherAsDecimal.value);
        } else
            {return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;}
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
    */
	public int conversionPreference(Class<?> javaClass) {
		if (javaClass.isAssignableFrom(DecimalValue.class))
			{return 0;}
		if (javaClass == BigDecimal.class)
			{return 1;}
		if (javaClass == Long.class || javaClass == long.class)
			{return 4;}
		if (javaClass == Integer.class || javaClass == int.class)
			{return 5;}
		if (javaClass == Short.class || javaClass == short.class)
			{return 6;}
		if (javaClass == Byte.class || javaClass == byte.class)
			{return 7;}
		if (javaClass == Double.class || javaClass == double.class)
			{return 2;}
		if (javaClass == Float.class || javaClass == float.class)
			{return 3;}
		if (javaClass == String.class)
			{return 8;}
		if (javaClass == Boolean.class || javaClass == boolean.class)
			{return 9;}
		if (javaClass == Object.class)
			{return 20;}

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
        @Override
	public <T> T toJavaObject(final Class<T> target) throws XPathException {
		if(target.isAssignableFrom(DecimalValue.class)) {
			return (T)this;
                } else if(target == BigDecimal.class) {
			return (T)value;
                } else if(target == Double.class || target == double.class) {
			return (T)Double.valueOf(value.doubleValue());
                } else if(target == Float.class || target == float.class) {
			return (T)Float.valueOf(value.floatValue());
                } else if(target == Long.class || target == long.class) {
			return (T)Long.valueOf( ((IntegerValue) convertTo(Type.LONG)).getValue() );
		} else if(target == Integer.class || target == int.class) {
			final IntegerValue v = (IntegerValue) convertTo(Type.INT);
			return (T)Integer.valueOf((int) v.getValue());
		} else if(target == Short.class || target == short.class) {
			final IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
			return (T)Short.valueOf((short) v.getValue());
		} else if(target == Byte.class || target == byte.class) {
			final IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
			return (T)Byte.valueOf((byte) v.getValue());
		} else if(target == String.class)
			{return (T)getStringValue();}
		else if(target == Boolean.class)
			{return (T)Boolean.valueOf(effectiveBooleanValue());}

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}
	
	//Copied from Saxon 8.8
    /**
	    * Remove insignificant trailing zeros (the Java BigDecimal class retains trailing zeros,
	    * but the XPath 2.0 xs:decimal type does not). The BigDecimal#stripTrailingZeros() method
	    * was introduced in JDK 1.5: we use it if available, and simulate it if not.
	    */

	    private static BigDecimal stripTrailingZeros(BigDecimal value) {
	        if (stripTrailingZerosMethodUnavailable) {
	            return stripTrailingZerosFallback(value);
	        }

	        try {
	            if (stripTrailingZerosMethod == null) {
	                final Class<?>[] argTypes = {};
	                stripTrailingZerosMethod = BigDecimal.class.getMethod("stripTrailingZeros", argTypes);
	            }
	            final Object result = stripTrailingZerosMethod.invoke(value, EMPTY_OBJECT_ARRAY);
	            return (BigDecimal)result;
	        } catch (final NoSuchMethodException e) {
	            stripTrailingZerosMethodUnavailable = true;
	            return stripTrailingZerosFallback(value);
	        } catch (final IllegalAccessException e) {
	            stripTrailingZerosMethodUnavailable = true;
	            return stripTrailingZerosFallback(value);
	        } catch (final InvocationTargetException e) {
	            stripTrailingZerosMethodUnavailable = true;
	            return stripTrailingZerosFallback(value);
	        }

	    }
	    
	    private static BigDecimal stripTrailingZerosFallback(BigDecimal value) {

	        // The code below differs from JDK 1.5 stripTrailingZeros in that it does not remove trailing zeros
	        // from integers, for example 1000 is not changed to 1E3.

	        int scale = value.scale();
	        if (scale > 0) {
	            BigInteger i = value.unscaledValue();
	            while (true) {
	                final BigInteger[] dr = i.divideAndRemainder(BIG_INTEGER_TEN);
	                if (dr[1].equals(BigInteger.ZERO)) {
	                    i = dr[0];
	                    scale--;
	                    if (scale==0) {
	                        break;
	                    }
	                } else {
	                    break;
	                }
	            }
	            if (scale != value.scale()) {
	                value = new BigDecimal(i, scale);
	            }
	        }
	        return value;
	    }
	   //End of copy
}
