package org.exist.xpath.value;

import org.exist.xpath.XPathException;

public abstract class NumericValue extends AtomicValue {

	public abstract String getStringValue();

	public abstract AtomicValue convertTo(int requiredType) throws XPathException;
	
	public double getDouble() throws XPathException {
		return ((DecimalValue)convertTo(Type.DECIMAL)).getValue();
	}
	
	public long getLong() throws XPathException {
		return ((IntegerValue)convertTo(Type.INTEGER)).getValue();
	}
	
	public int getInt() throws XPathException {
		return (int)((IntegerValue)convertTo(Type.INTEGER)).getValue();
	}

}
