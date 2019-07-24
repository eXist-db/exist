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

import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Represents a value of type xs:dateTime.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateTimeValue extends AbstractDateTimeValue {

    public DateTimeValue() throws XPathException {
        super(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar()));
        normalize();
    }

    public DateTimeValue(XMLGregorianCalendar calendar) {
        super(fillCalendar(cloneXMLGregorianCalendar(calendar)));
        normalize();
    }

    public DateTimeValue(Date date) {
        super(dateToXMLGregorianCalendar(date));
        normalize();
    }

    public DateTimeValue(String dateTime) throws XPathException {
        super(dateTime);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.DATETIME) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException("xs:dateTime instance must have all fields set");
        }
        normalize();
    }

    private static XMLGregorianCalendar dateToXMLGregorianCalendar(Date date) {
        final GregorianCalendar gc = new GregorianCalendar();
        gc.setTime(date);
        final XMLGregorianCalendar xgc = TimeUtils.getInstance().newXMLGregorianCalendar(gc);
        xgc.normalize();
        return xgc;
    }

    private static XMLGregorianCalendar fillCalendar(XMLGregorianCalendar calendar) {
        if (calendar.getHour() == DatatypeConstants.FIELD_UNDEFINED) {
            calendar.setHour(0);
        }
        if (calendar.getMinute() == DatatypeConstants.FIELD_UNDEFINED) {
            calendar.setMinute(0);
        }
        if (calendar.getSecond() == DatatypeConstants.FIELD_UNDEFINED) {
            calendar.setSecond(0);
        }
        if (calendar.getMillisecond() == DatatypeConstants.FIELD_UNDEFINED) {
            calendar.setMillisecond(0);
        }
        return calendar;
    }

    protected void normalize() {
        if (calendar.getHour() == 24 && calendar.getMinute() == 0 && calendar.getSecond() == 0) {
            calendar.setHour(0);
            calendar.add(TimeUtils.ONE_DAY);
        }

    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException {
        return new DateTimeValue(cal);
    }

    protected QName getXMLSchemaType() {
        return DatatypeConstants.DATETIME;
    }

    public int getType() {
        return Type.DATE_TIME;
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.DATE_TIME:
            case Type.ATOMIC:
            case Type.ITEM:
                return this;
            case Type.DATE:
                return new DateValue(calendar);
            case Type.TIME:
                return new TimeValue(calendar);
            case Type.GYEAR:
                return new GYearValue(calendar);
            case Type.GYEARMONTH:
                return new GYearMonthValue(calendar);
            case Type.GMONTHDAY:
                return new GMonthDayValue(calendar);
            case Type.GDAY:
                return new GDayValue(calendar);
            case Type.GMONTH:
                return new GMonthValue(calendar);
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            default:
                throw new XPathException(
                        "Type error: cannot cast xs:dateTime to "
                                + Type.getTypeName(requiredType));
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DATE_TIME:
                return new DayTimeDurationValue(getTimeInMillis() - ((DateTimeValue) other).getTimeInMillis());
            case Type.YEAR_MONTH_DURATION:
                return ((YearMonthDurationValue) other).negate().plus(this);
            case Type.DAY_TIME_DURATION:
                return ((DayTimeDurationValue) other).negate().plus(this);
            default:
                throw new XPathException(
                        "Operand to minus should be of type xs:dateTime, xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
                                + Type.getTypeName(other.getType()));
        }
    }

    public Date getDate() {
        return calendar.toGregorianCalendar().getTime();
    }

}
