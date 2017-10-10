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

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.GregorianCalendar;

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
            if (calendar.getXMLSchemaType() != DatatypeConstants.GYEARMONTH) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
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
            case Type.GYEARMONTH:
            case Type.ATOMIC:
            case Type.ITEM:
                return new GYearMonthValue(this.calendar);
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
                    if (!((DayTimeDurationValue) getTimezone().itemAt(0)).compareTo(null, Comparison.EQ, (DayTimeDurationValue) ((AbstractDateTimeValue) other).getTimezone().itemAt(0))) {
                        return DatatypeConstants.LESSER;
                    } else {
                        // equal? Is this sufficient? /ljo
                        if (this.getCanonicalOrTrimmedCalendar().compare(((AbstractDateTimeValue) other).getCanonicalOrTrimmedCalendar()) == 0) {
                            return DatatypeConstants.EQUAL;
                        }
                        if (!"PT0S".equals(((DayTimeDurationValue) getTimezone().itemAt(0)).getStringValue())) {
                            return DatatypeConstants.LESSER;
                        }
                    }
                } else {
                    if (!((AbstractDateTimeValue) other).getTimezone().isEmpty()) {
                        if (!"PT0S".equals(((DayTimeDurationValue) ((AbstractDateTimeValue) other).getTimezone().itemAt(0)).getStringValue())) {
                            return DatatypeConstants.LESSER;
                        }
                    }
                }
            }
            // filling in missing timezones with local timezone, should be total order as per XPath 2.0 10.4
            final int r = this.getCanonicalOrTrimmedCalendar().compare(((AbstractDateTimeValue) other).getCanonicalOrTrimmedCalendar());
            //getImplicitCalendar().compare(((AbstractDateTimeValue) other).getImplicitCalendar());
            if (r == DatatypeConstants.INDETERMINATE) {
                throw new RuntimeException("indeterminate order between " + this + " and " + other);
            }
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
