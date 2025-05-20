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

import org.exist.util.ByteConversion;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Represents a value of type xs:dateTime.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateTimeValue extends AbstractDateTimeValue {

    public static final int SERIALIZED_SIZE = 13;

    public DateTimeValue() throws XPathException {
        super(null, TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar()));
        normalize();
    }

    public DateTimeValue(final Expression expression) throws XPathException {
        super(expression, TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar()));
        normalize();
    }

    public DateTimeValue(final XMLGregorianCalendar calendar) {
        this(null, calendar);
    }

    public DateTimeValue(final Expression expression, XMLGregorianCalendar calendar) {
        super(expression, fillCalendar(cloneXMLGregorianCalendar(calendar)));
        normalize();
    }

    public DateTimeValue(final Date date) {
        this(null, date);
    }

    public DateTimeValue(final Expression expression, Date date) {
        super(expression, dateToXMLGregorianCalendar(date));
        normalize();
    }

    public DateTimeValue(final int year, final int month, final int day, final int hour, final int minute, final int second, final int millisecond, final int timezone) {
        super(TimeUtils.getInstance().newXMLGregorianCalendar(year, month, day, hour, minute, second, millisecond, timezone));
    }

    public DateTimeValue(String dateTime) throws XPathException {
        this(null, dateTime);
    }

    public DateTimeValue(final Expression expression, String dateTime) throws XPathException {
        super(expression, dateTime);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.DATETIME) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException(getExpression(), "xs:dateTime instance must have all fields set");
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
        return new DateTimeValue(getExpression(), cal);
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
            case Type.DATE_TIME_STAMP:
                return new DateTimeStampValue(getExpression(), calendar);
            case Type.DATE:
                return new DateValue(getExpression(), calendar);
            case Type.TIME:
                return new TimeValue(getExpression(), calendar);
            case Type.GYEAR:
                return new GYearValue(getExpression(), calendar);
            case Type.GYEARMONTH:
                return new GYearMonthValue(getExpression(), calendar);
            case Type.GMONTHDAY:
                return new GMonthDayValue(getExpression(), calendar);
            case Type.GDAY:
                return new GDayValue(getExpression(), calendar);
            case Type.GMONTH:
                return new GMonthValue(getExpression(), calendar);
            case Type.STRING:
                return new StringValue(getExpression(), getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "Type error: cannot cast xs:dateTime to "
                                + Type.getTypeName(requiredType));
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DATE_TIME_STAMP:
            case Type.DATE_TIME:
                return new DayTimeDurationValue(getExpression(), getTimeInMillis() - ((DateTimeValue) other).getTimeInMillis());
            case Type.YEAR_MONTH_DURATION:
                return ((YearMonthDurationValue) other).negate().plus(this);
            case Type.DAY_TIME_DURATION:
                return ((DayTimeDurationValue) other).negate().plus(this);
            default:
                throw new XPathException(getExpression(), 
                        "Operand to minus should be of type xs:dateTime, xdt:dayTimeDuration or xdt:yearMonthDuration; got: "
                                + Type.getTypeName(other.getType()));
        }
    }

    public Date getDate() {
        return calendar.toGregorianCalendar().getTime();
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target == byte[].class) {
            final ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf.array();
        } else if (target == ByteBuffer.class) {
            final ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf;
        } else {
            return super.toJavaObject(target);
        }
    }

    /**
     * Serializes to a ByteBuffer.
     *
     * 13 bytes where: [0-3 (Year), 4 (Month), 5 (Day), 6 (Hour), 7 (Minute), 8 (Second), 9-10 (Milliseconds), 11-12 (Timezone)]
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        ByteConversion.intToByteH(calendar.getYear(), buf);
        buf.put((byte) calendar.getMonth());
        buf.put((byte) calendar.getDay());
        buf.put((byte) calendar.getHour());
        buf.put((byte) calendar.getMinute());
        buf.put((byte) calendar.getSecond());

        final int ms = calendar.getMillisecond();
        if (ms == DatatypeConstants.FIELD_UNDEFINED) {
            buf.putShort((short) 0);
        } else {
            ByteConversion.shortToByteH((short) ms, buf);
        }

        // values for timezone range from -14*60 to 14*60, so we can use a short, but
        // need to choose a different value for FIELD_UNDEFINED, which is not the same as 0 (= UTC)
        final int timezone = calendar.getTimezone();
        ByteConversion.shortToByteH((short) (timezone == DatatypeConstants.FIELD_UNDEFINED ? Short.MAX_VALUE : timezone), buf);
    }

    public static AtomicValue deserialize(final ByteBuffer buf) {
        final int year = ByteConversion.byteToIntH(buf);
        final int month = buf.get();
        final int day = buf.get();
        final int hour = buf.get();
        final int minute = buf.get();
        final int second = buf.get();

        final int ms = ByteConversion.byteToShortH(buf);

        int timezone = ByteConversion.byteToShortH(buf);
        if (timezone == Short.MAX_VALUE) {
            timezone = DatatypeConstants.FIELD_UNDEFINED;
        }

        return new DateTimeValue(year, month, day, hour, minute, second, ms, timezone);
    }
}
