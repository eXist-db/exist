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

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateValue extends AbstractDateTimeValue {

	public DateValue() throws XPathException {
		super(stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
	}

	public DateValue(String dateString) throws XPathException {
		super(dateString);
		try {
			if (calendar.getXMLSchemaType() != DatatypeConstants.DATE) {throw new IllegalStateException();}
		} catch (final IllegalStateException e) {
			throw new XPathException("xs:date must not have hour, minute or second fields set");
		}
	}
	
	public DateValue(XMLGregorianCalendar calendar) throws XPathException {
		super(stripCalendar(cloneXMLGregorianCalendar(calendar)));
	}
	
	private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
		calendar.setHour(DatatypeConstants.FIELD_UNDEFINED);
		calendar.setMinute(DatatypeConstants.FIELD_UNDEFINED);
		calendar.setSecond(DatatypeConstants.FIELD_UNDEFINED);
		calendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
		return calendar;
	}

	protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException {
		return new DateValue(cal);
	}

	protected QName getXMLSchemaType() {
		return DatatypeConstants.DATE;
	}

	public int getType() {
		return Type.DATE;
	}

	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.DATE :
			case Type.ATOMIC :
			case Type.ITEM :
                return this;
			case Type.DATE_TIME :
				return new DateTimeValue(calendar);
            case Type.GYEAR :
            	return new GYearValue(this.calendar); 
            case Type.GYEARMONTH :
                return new GYearMonthValue(calendar);
            case Type.GMONTHDAY :
                return new GMonthDayValue(calendar);
            case Type.GDAY :
                return new GDayValue(calendar);
            case Type.GMONTH :
                return new GMonthValue(calendar);
			case Type.UNTYPED_ATOMIC: {			
	            final DateValue dv = new DateValue(getStringValue()); 
				return new UntypedAtomicValue(dv.getStringValue());		
			}
			case Type.STRING : {
            	final DateValue dv = new DateValue(calendar); 
				return new StringValue(dv.getStringValue());
			}
			default :
				throw new XPathException(ErrorCodes.FORG0001, "can not convert " + 
						Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
						Type.getTypeName(requiredType));
		}
	}

	public ComputableValue minus(ComputableValue other) throws XPathException {
		switch (other.getType()) {
			case Type.DATE :
				return new DayTimeDurationValue(getTimeInMillis() - ((DateValue) other).getTimeInMillis());
			case Type.YEAR_MONTH_DURATION :
				return ((YearMonthDurationValue) other).negate().plus(this);
			case Type.DAY_TIME_DURATION :
				return ((DayTimeDurationValue) other).negate().plus(this);
			default :
				throw new XPathException(
					"Operand to minus should be of type xdt:yearMonthDuration or xdt:dayTimeDuration; got: "
						+ Type.getTypeName(other.getType()));
		}
	}
}