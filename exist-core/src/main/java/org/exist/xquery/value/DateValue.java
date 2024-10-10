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
import java.util.GregorianCalendar;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateValue extends AbstractDateTimeValue {

    public static final int SERIALIZED_SIZE = 8;

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

    public DateValue(final int year, final int month, final int day, final int timezone) {
        super(TimeUtils.getInstance().newXMLGregorianCalendarDate(year, month, day, timezone));
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

    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.DATE:
            case Type.ANY_ATOMIC_TYPE:
            case Type.ITEM:
                return this;
            case Type.DATE_TIME:
                return new DateTimeValue(getExpression(), calendar);
            case Type.G_YEAR:
                return new GYearValue(getExpression(), this.calendar);
            case Type.G_YEAR_MONTH:
                return new GYearMonthValue(getExpression(), calendar);
            case Type.G_MONTH_DAY:
                return new GMonthDayValue(getExpression(), calendar);
            case Type.G_DAY:
                return new GDayValue(getExpression(), calendar);
            case Type.G_MONTH:
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
        return switch (other.getType()) {
            case Type.DATE ->
                    new DayTimeDurationValue(getExpression(), getTimeInMillis() - ((DateValue) other).getTimeInMillis());
            case Type.YEAR_MONTH_DURATION -> ((YearMonthDurationValue) other).negate().plus(this);
            case Type.DAY_TIME_DURATION -> ((DayTimeDurationValue) other).negate().plus(this);
            default -> throw new XPathException(getExpression(),
                    "Operand to minus should be of type xdt:yearMonthDuration or xdt:dayTimeDuration; got: "
                            + Type.getTypeName(other.getType()));
        };
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
     * 8 bytes where: [0-3 (Year), 4 (Month), 5 (Day), 6-7 (Timezone)]
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        ByteConversion.intToByteH(calendar.getYear(), buf);
        buf.put((byte) calendar.getMonth());
        buf.put((byte) calendar.getDay());

        // values for timezone range from -14*60 to 14*60, so we can use a short, but
        // need to choose a different value for FIELD_UNDEFINED, which is not the same as 0 (= UTC)
        final int timezone = calendar.getTimezone();
        ByteConversion.shortToByteH((short) (timezone == DatatypeConstants.FIELD_UNDEFINED ? Short.MAX_VALUE : timezone), buf);
    }

    public static AtomicValue deserialize(final ByteBuffer buf) {
        final int year = ByteConversion.byteToIntH(buf);
        final int month = buf.get();
        final int day = buf.get();

        int timezone = ByteConversion.byteToShortH(buf);
        if (timezone == Short.MAX_VALUE) {
            timezone = DatatypeConstants.FIELD_UNDEFINED;
        }

        return new DateValue(year, month, day, timezone);
    }
}
