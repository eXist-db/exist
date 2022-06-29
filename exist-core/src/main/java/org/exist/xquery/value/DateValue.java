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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.util.GregorianCalendar;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateValue extends AbstractDateTimeValue {

    public DateValue() throws XPathException {
        super(null, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public DateValue(final Expression expression) throws XPathException {
        super(expression, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public DateValue(final String dateString) throws XPathException {
        this(null, dateString);
    }

    public DateValue(final Expression expression, String dateString) throws XPathException {
        super(expression, dateString);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.DATE) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException(getExpression(), "xs:date must not have hour, minute or second fields set");
        }
    }

    public DateValue(final XMLGregorianCalendar calendar) throws XPathException {
        this(null, calendar);
    }

    public DateValue(final Expression expression, XMLGregorianCalendar calendar) throws XPathException {
        super(expression, stripCalendar(cloneXMLGregorianCalendar(calendar)));
    }

    private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
        calendar.setHour(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMinute(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setSecond(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMillisecond(DatatypeConstants.FIELD_UNDEFINED);
        return calendar;
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException {
        return new DateValue(getExpression(), cal);
    }

    protected QName getXMLSchemaType() {
        return DatatypeConstants.DATE;
    }

    public int getType() {
        return Type.DATE;
    }

    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.DATE:
            case Type.ATOMIC:
            case Type.ITEM:
                return this;
            case Type.DATE_TIME:
                return new DateTimeValue(getExpression(), calendar);
            case Type.GYEAR:
                return new GYearValue(getExpression(), this.calendar);
            case Type.GYEARMONTH:
                return new GYearMonthValue(getExpression(), calendar);
            case Type.GMONTHDAY:
                return new GMonthDayValue(getExpression(), calendar);
            case Type.GDAY:
                return new GDayValue(getExpression(), calendar);
            case Type.GMONTH:
                return new GMonthValue(getExpression(), calendar);
            case Type.UNTYPED_ATOMIC: {
                final DateValue dv = new DateValue(getExpression(), getStringValue());
                return new UntypedAtomicValue(getExpression(), dv.getStringValue());
            }
            case Type.STRING: {
                final DateValue dv = new DateValue(getExpression(), calendar);
                return new StringValue(getExpression(), dv.getStringValue());
            }
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001, "can not convert " +
                        Type.getTypeName(getType()) + "('" + getStringValue() + "') to " +
                        Type.getTypeName(requiredType));
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DATE:
                return new DayTimeDurationValue(getExpression(), getTimeInMillis() - ((DateValue) other).getTimeInMillis());
            case Type.YEAR_MONTH_DURATION:
                return ((YearMonthDurationValue) other).negate().plus(this);
            case Type.DAY_TIME_DURATION:
                return ((DayTimeDurationValue) other).negate().plus(this);
            default:
                throw new XPathException(getExpression(), 
                        "Operand to minus should be of type xdt:yearMonthDuration or xdt:dayTimeDuration; got: "
                                + Type.getTypeName(other.getType()));
        }
    }
}