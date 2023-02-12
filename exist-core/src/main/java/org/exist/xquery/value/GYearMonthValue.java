/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.GregorianCalendar;

public class GYearMonthValue extends AbstractDateTimeValue {

    public GYearMonthValue() throws XPathException {
        super(null, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public GYearMonthValue(final Expression expression) throws XPathException {
        super(expression, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public GYearMonthValue(final XMLGregorianCalendar calendar) throws XPathException {
        this(null, calendar);
    }

    public GYearMonthValue(final Expression expression, XMLGregorianCalendar calendar) throws XPathException {
        super(expression, stripCalendar((XMLGregorianCalendar) calendar.clone()));
    }

    public GYearMonthValue(final String timeValue) throws XPathException {
        this(null, timeValue);
    }

    public GYearMonthValue(final Expression expression, String timeValue) throws XPathException {
        super(expression, timeValue);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.GYEARMONTH) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException(getExpression(), "xs:gYearMonth instance must not have year, month or day fields set");
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

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.G_YEAR_MONTH:
            case Type.ANY_ATOMIC_TYPE:
            case Type.ITEM:
                return new GYearMonthValue(getExpression(), this.calendar);
            case Type.STRING:
                return new StringValue(getExpression(), getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "Type error: cannot cast xs:time to "
                                + Type.getTypeName(requiredType));
        }
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal)
            throws XPathException {
        return new GYearMonthValue(getExpression(), cal);
    }

    public int getType() {
        return Type.G_YEAR_MONTH;
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
        throw new XPathException(getExpression(), 
                "Type error: cannot compare " + Type.getTypeName(getType()) + " to "
                        + Type.getTypeName(other.getType()));
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        throw new XPathException(getExpression(), "Subtraction is not supported on values of type " +
                Type.getTypeName(getType()));
    }
}
