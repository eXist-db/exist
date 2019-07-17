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
import java.util.GregorianCalendar;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class TimeValue extends AbstractDateTimeValue {

    public TimeValue() throws XPathException {
        super(stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public TimeValue(XMLGregorianCalendar calendar) throws XPathException {
        super(stripCalendar((XMLGregorianCalendar) calendar.clone()));
    }

    public TimeValue(String timeValue) throws XPathException {
        super(timeValue);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.TIME) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException("xs:time instance must not have year, month or day fields set");
        }
    }

    private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
        calendar = (XMLGregorianCalendar) calendar.clone();
        calendar.setYear(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMonth(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setDay(DatatypeConstants.FIELD_UNDEFINED);
        return calendar;
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException {
        return new TimeValue(cal);
    }

    protected QName getXMLSchemaType() {
        return DatatypeConstants.TIME;
    }

    public int getType() {
        return Type.TIME;
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.TIME:
            case Type.ATOMIC:
            case Type.ITEM:
                return this;
//		case Type.DATE_TIME :
//			xs:time -> xs:dateTime conversion not defined in Funcs&Ops 17.1.5
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            default:
                throw new XPathException(
                        "Type error: cannot cast xs:time to "
                                + Type.getTypeName(requiredType));
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.TIME:
                return new DayTimeDurationValue(getTimeInMillis() - ((TimeValue) other).getTimeInMillis());
            case Type.DAY_TIME_DURATION:
                return ((DayTimeDurationValue) other).negate().plus(this);
            default:
                throw new XPathException(
                        "Operand to minus should be of type xs:time or xdt:dayTimeDuration; got: "
                                + Type.getTypeName(other.getType()));
        }
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        if (other.getType() == Type.DAY_TIME_DURATION) {
            return other.plus(this);
        }
        throw new XPathException(
                "Operand to plus should be of type xdt:dayTimeDuration; got: "
                        + Type.getTypeName(other.getType()));
    }

}
