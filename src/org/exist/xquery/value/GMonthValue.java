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

import java.util.GregorianCalendar;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

import org.exist.xquery.XPathException;

public class GMonthValue extends AbstractDateTimeValue {

    public GMonthValue() throws XPathException {
        super(stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public GMonthValue(XMLGregorianCalendar calendar) throws XPathException {
        super(stripCalendar((XMLGregorianCalendar) calendar.clone()));
    }

    public GMonthValue(String timeValue) throws XPathException {
        super(fixTimezone(timeValue));
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.GMONTH) throw new IllegalStateException();
        } catch (IllegalStateException e) {
            throw new XPathException("xs:gMonth instance must not have year, month or day fields set");
        }
    }

    private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
        calendar = (XMLGregorianCalendar) calendar.clone();
        calendar.setYear(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setDay(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setHour(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMinute(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setSecond(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
        return calendar;
    }
    
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.TIME :
            case Type.ATOMIC :
            case Type.ITEM :
                return this;
            case Type.STRING :
                return new StringValue(getStringValue());
            case Type.UNTYPED_ATOMIC :
                return new UntypedAtomicValue(getStringValue());
            default :
                throw new XPathException(
                    "Type error: cannot cast xs:gMonth to "
                        + Type.getTypeName(requiredType));
        }
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal)
            throws XPathException {
        return new GMonthValue(cal);
    }

    public int getType() {
        return Type.GYEAR;
    }
    
    protected QName getXMLSchemaType() {
        return DatatypeConstants.GMONTH;
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        throw new XPathException("Subtraction is not supported on values of type " +
                Type.getTypeName(getType()));
    }
    
    private static String fixTimezone(String value) {
        int p = value.indexOf('Z');
        return p == -1 ? value : value.substring(0, p);
    }
}
