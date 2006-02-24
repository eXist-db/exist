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

import java.math.*;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.Duration;

import org.exist.util.FastStringBuffer;
import org.exist.xquery.XPathException;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DayTimeDurationValue extends OrderedDurationValue {

	private static final Duration CANONICAL_ZERO_DURATION =
		TimeUtils.getInstance().newDuration(true, null, null, null, null, null, ZERO_DECIMAL);
	
	DayTimeDurationValue(Duration duration) throws XPathException {
		super(duration);
		if (duration.isSet(DatatypeConstants.YEARS) || duration.isSet(DatatypeConstants.MONTHS))
			throw new XPathException("the value '" + duration + "' is not an xdt:dayTimeDuration since it specifies year or month values");
	}

	public DayTimeDurationValue(long millis) throws XPathException {
		this(TimeUtils.getInstance().newDurationDayTime(millis));
	}
	
	public DayTimeDurationValue(String str) throws XPathException {
		this(TimeUtils.getInstance().newDurationDayTime(str));
	}

	public int getType() {
		return Type.DAY_TIME_DURATION;
	}
	
	public double getValue() {
		double value = duration.getDays();
		value = value * 24 + duration.getHours();
		value = value * 60 + duration.getMinutes();
		Number n = duration.getField(DatatypeConstants.SECONDS);
		value = value * 60 + (n == null ? 0 : n.doubleValue());
		return value * duration.getSign();
	}
	
	public long getValueInMilliseconds() {
		return (long) (getValue() * 1000);
	}
	
	protected Duration canonicalZeroDuration() {
		return CANONICAL_ZERO_DURATION;
	}
	
	public String getStringValue() {
		//canonicalize();
		Duration canonical = getCanonicalDuration();
		if (canonical != null)
			return canonical.toString();
		int d = duration.getDays();
		int h = duration.getHours();
		int m = duration.getMinutes();
		int s = duration.getSeconds();
		//TODO
		long micros = 0;
		
		//Copied from Saxon 8.6.1		
        FastStringBuffer sb = new FastStringBuffer(32);
        if (duration.getSign() < 0) {
            sb.append('-');
        }
        sb.append('P');        
        if (d != 0) {
            sb.append(d + "D");
        }
        if ( d==0 || h!=0 || m!=0 || s!=0 || micros!=0) {
            sb.append('T');
        }
        if (h != 0) {
            sb.append(h + "H");
        }
        if (m != 0) {
            sb.append(m + "M");
        }
        if (s != 0 || micros != 0 || (d==0 && m==0 && h==0)) {
            if (micros == 0) {
                sb.append(s + "S");
            } else {
                long ms = (s * 1000000) + micros;
                String mss = ms + "";
                if (s == 0) {
                    mss = "0000000" + mss;
                    mss = mss.substring(mss.length()-7);
                }
                sb.append(mss.substring(0, mss.length()-6));
                sb.append('.');
                int lastSigDigit = mss.length()-1;
                while (mss.charAt(lastSigDigit) == '0') {
                    lastSigDigit--;
                }
                sb.append(mss.substring(mss.length()-6, lastSigDigit+1));
                sb.append('S');
            }
        }
        //End of copy        
        return sb.toString();
        
	}
	
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ITEM:
			case Type.ATOMIC:
			case Type.DAY_TIME_DURATION:
				return this;
			case Type.STRING:				
				return new StringValue(getStringValue());
			case Type.DURATION:
				return new DurationValue(TimeUtils.getInstance().newDuration(
						duration.getSign() >= 0, null, null,
						(BigInteger) duration.getField(DatatypeConstants.DAYS),
						(BigInteger) duration.getField(DatatypeConstants.HOURS),
						(BigInteger) duration.getField(DatatypeConstants.MINUTES),
						(BigDecimal) duration.getField(DatatypeConstants.SECONDS)));
			case Type.DOUBLE:
				//return new DoubleValue(monthsValueSigned().doubleValue());
				return new DoubleValue(Double.NaN);
			case Type.DECIMAL:
				return new DecimalValue(monthsValueSigned().doubleValue());				
			default:
				throw new XPathException(
					"Type error: cannot cast xs:dayTimeDuration to "
					+ Type.getTypeName(requiredType));
		}
	}
	
	protected DurationValue createSameKind(Duration dur) throws XPathException {
		return new DayTimeDurationValue(dur);
	}
	
	public ComputableValue plus(ComputableValue other) throws XPathException {
		try {
			return super.plus(other);
		} catch (IllegalArgumentException e) {
				throw new XPathException("Operand to plus should be of type xdt:dayTimeDuration, xs:time, " +
					"xs:date or xs:dateTime; got: " +
					Type.getTypeName(other.getType()));
		}
	}
	
	public ComputableValue mult(ComputableValue other) throws XPathException {
		BigDecimal factor = numberToBigDecimal(other, "Operand to mult should be of numeric type; got: ");
		boolean isFactorNegative = factor.signum() < 0;
		DayTimeDurationValue product = new DayTimeDurationValue(duration.multiply(factor.abs()));
		if (isFactorNegative)
			return product.negate();
		return product;
		
	}

	public ComputableValue div(ComputableValue other) throws XPathException {		
		if (other.getType() == Type.DAY_TIME_DURATION) {		
			DecimalValue a = new DecimalValue(secondsValueSigned());
			DecimalValue b = new DecimalValue(((DayTimeDurationValue)other).secondsValueSigned());
			return new DecimalValue(a.value.divide(b.value, 20, BigDecimal.ROUND_HALF_UP));
		}		
		BigDecimal divisor = numberToBigDecimal(other, "Operand to div should be of xdt:dayTimeDuration or numeric type; got: ");
		boolean isDivisorNegative = divisor.signum() < 0;
		BigDecimal secondsValueSigned = secondsValueSigned();
		DayTimeDurationValue quotient = fromDecimalSeconds(secondsValueSigned.divide(divisor.abs(), Math.max(Math.max(3, secondsValueSigned.scale()), divisor.scale()), BigDecimal.ROUND_HALF_UP));
		if (isDivisorNegative)
			return quotient.negate();
		return quotient;		
	}

	private DayTimeDurationValue fromDecimalSeconds(BigDecimal x) throws XPathException {
		return new DayTimeDurationValue(TimeUtils.getInstance().newDuration(
				x.signum() >= 0, null, null, null, null, null, x));
	}

}
