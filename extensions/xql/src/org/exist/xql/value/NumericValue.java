package org.exist.xquery.value;

import java.text.Collator;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public abstract class NumericValue extends ComputableValue {

	public abstract String getStringValue() throws XPathException;

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;
	
	public double getDouble() throws XPathException {
		return ((DoubleValue)convertTo(Type.DOUBLE)).getValue();
	}
	
	public long getLong() throws XPathException {
		return ((IntegerValue)convertTo(Type.INTEGER)).getValue();
	}
	
	public int getInt() throws XPathException {
		return (int)((IntegerValue)convertTo(Type.INTEGER)).getValue();
	}
	
	public abstract boolean hasFractionalPart();
	
	public abstract boolean isNaN();

	public abstract boolean isInfinite();

	public abstract boolean isZero();
    
    public abstract boolean isNegative();
    
    public abstract boolean isPositive();
	
	public boolean effectiveBooleanValue() throws XPathException {
		//If its operand is a singleton value of any numeric type or derived from a numeric type, 
		//fn:boolean returns false if the operand value is NaN or is numerically equal to zero; 
		//otherwise it returns true.		
		return !(isNaN() || isZero());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(int, org.exist.xquery.value.AtomicValue)
	 */
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
			double otherVal = ((NumericValue)other).getDouble();
			double val = getDouble();
			switch(operator) {
				case Constants.EQ:
					return val == otherVal;
				case Constants.NEQ:
					return val != otherVal;
				case Constants.GT:
					return val > otherVal;
				case Constants.GTEQ:
					return val >= otherVal;
				case Constants.LT:
					return val < otherVal;
				case Constants.LTEQ:
					return val <= otherVal;
				default:
					throw new XPathException("Type error: cannot apply operator to numeric value");
			}
		}
		throw new XPathException("Type error: cannot compare operands: " + 
			Type.getTypeName(getType()) + " and " + 
			Type.getTypeName(other.getType()));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#compareTo(org.exist.xquery.value.AtomicValue)
	 */
	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if(Type.subTypeOf(other.getType(), Type.NUMBER)) {
			if (isNaN()) {
				//NaN does not equal itself.
				if (((NumericValue)other).isNaN())
				return Constants.INFERIOR;
			}
			double otherVal = ((NumericValue)other).getDouble();
			double val = getDouble();
			if(val == otherVal)
				return Constants.EQUAL;
			else if(val > otherVal)
				return Constants.SUPERIOR;
			else
				return Constants.INFERIOR;
		} else {
			throw new XPathException("cannot compare numeric value to non-numeric value");
		}
	}
	
	public abstract NumericValue negate() throws XPathException;
	public abstract NumericValue ceiling() throws XPathException;
	public abstract NumericValue floor() throws XPathException;
	public abstract NumericValue round() throws XPathException;
	public abstract NumericValue round(IntegerValue precision) throws XPathException;
	public abstract NumericValue mod(NumericValue other) throws XPathException;
	//TODO : implement here ?
	public abstract	IntegerValue idiv(NumericValue other) throws XPathException;
	public abstract NumericValue abs() throws XPathException;
	public abstract AtomicValue max(Collator collator, AtomicValue other) throws XPathException;
	public abstract AtomicValue min(Collator collator, AtomicValue other) throws XPathException;

	
}
