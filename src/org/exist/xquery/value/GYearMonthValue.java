/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
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
import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public class GYearMonthValue extends AbstractDateTimeValue {

    public GYearMonthValue() throws XPathException {
        super(stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public GYearMonthValue(XMLGregorianCalendar calendar) throws XPathException {
        super(stripCalendar((XMLGregorianCalendar) calendar.clone()));
    }

    public GYearMonthValue(String timeValue) throws XPathException {
        super(timeValue);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.GYEARMONTH) throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw new XPathException("xs:gYearMonth instance must not have year, month or day fields set");
        }
    }

    private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
        calendar = (XMLGregorianCalendar) calendar.clone();
        calendar.setDay(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setHour(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMinute(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setSecond(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
        return calendar;
    }
    
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.GYEARMONTH :
            case Type.ATOMIC :
            case Type.ITEM :
            	GYearMonthValue gymv = new GYearMonthValue(this.calendar); 
        		//For still unknown reasons, negative dates are one year too early
        		if (getStringValue().startsWith("-")) {
        			gymv.calendar.setYear(gymv.calendar.getYear() + 1);
        			return new GYearMonthValue(gymv.calendar);
        		}
        		return gymv;
            case Type.STRING :
                return new StringValue(getStringValue());
            case Type.UNTYPED_ATOMIC :
                return new UntypedAtomicValue(getStringValue());
            default :
                throw new XPathException(
                    "Type error: cannot cast xs:time to "
                        + Type.getTypeName(requiredType));
        }
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal)
            throws XPathException {
        return new GYearMonthValue(cal);
    }

    public int getType() {
        return Type.GYEARMONTH;
    }
    
    protected QName getXMLSchemaType() {
        return DatatypeConstants.GYEARMONTH;
    }

	public int compareTo(Collator collator, AtomicValue other) throws XPathException {
		if (other.getType() == getType()) {
			if (!getTimezone().isEmpty()) {
				if (!((AbstractDateTimeValue) other).getTimezone().isEmpty()) {
					if (!((DayTimeDurationValue)getTimezone().itemAt(0)).compareTo(null, Constants.EQ, (DayTimeDurationValue)((AbstractDateTimeValue)other).getTimezone().itemAt(0))) {
						return DatatypeConstants.LESSER;
	    			} else {
	    				if (!((DayTimeDurationValue)getTimezone().itemAt(0)).getStringValue().equals("PT0S"))
	    					return DatatypeConstants.LESSER;
	    			}
	    		} else {
	    			if (!((AbstractDateTimeValue)other).getTimezone().isEmpty()) {
	    				if (!((DayTimeDurationValue)((AbstractDateTimeValue)other).getTimezone().itemAt(0)).getStringValue().equals("PT0S"))
	    					return DatatypeConstants.LESSER;
	    			}
				}
			}
			// filling in missing timezones with local timezone, should be total order as per XPath 2.0 10.4
			int r =	this.getTrimmedCalendar().compare(((AbstractDateTimeValue) other).getTrimmedCalendar());
				//getImplicitCalendar().compare(((AbstractDateTimeValue) other).getImplicitCalendar());
			if (r == DatatypeConstants.INDETERMINATE) throw new RuntimeException("indeterminate order between " + this + " and " + other);
			return r;
		}
		throw new XPathException(
			"Type error: cannot compare " + Type.getTypeName(getType()) + " to "
				+ Type.getTypeName(other.getType()));
	}
    
    public ComputableValue minus(ComputableValue other) throws XPathException {
        throw new XPathException("Subtraction is not supported on values of type " +
                Type.getTypeName(getType()));
    }
}
