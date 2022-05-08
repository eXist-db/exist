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
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.GregorianCalendar;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TimeValue extends AbstractDateTimeValue {

    public static final int MIN_SERIALIZED_SIZE = 4;
    public static final int MAX_SERIALIZED_SIZE = 5;

    public TimeValue() throws XPathException {
        super(null, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public TimeValue(final Expression expression) throws XPathException {
        super(expression, stripCalendar(TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar())));
    }

    public TimeValue(final XMLGregorianCalendar calendar) throws XPathException {
        this(null, calendar);
    }

    public TimeValue(final Expression expression, XMLGregorianCalendar calendar) throws XPathException {
        super(expression, stripCalendar((XMLGregorianCalendar) calendar.clone()));
    }

    public TimeValue(final String timeValue) throws XPathException {
        this(null, timeValue);
    }

    public TimeValue(final Expression expression, String timeValue) throws XPathException {
        super(expression, timeValue);
        try {
            if (calendar.getXMLSchemaType() != DatatypeConstants.TIME) {
                throw new IllegalStateException();
            }
        } catch (final IllegalStateException e) {
            throw new XPathException(expression, "xs:time instance must not have year, month or day fields set");
        }
    }

    public TimeValue(final int hour, final int minute, final int second, final int millisecond, final int timezone) {
        super(TimeUtils.getInstance().newXMLGregorianCalendarTime(hour, minute, second, millisecond, timezone));
    }
    private static XMLGregorianCalendar stripCalendar(XMLGregorianCalendar calendar) {
        calendar = (XMLGregorianCalendar) calendar.clone();
        calendar.setYear(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setMonth(DatatypeConstants.FIELD_UNDEFINED);
        calendar.setDay(DatatypeConstants.FIELD_UNDEFINED);
        return calendar;
    }

    protected AbstractDateTimeValue createSameKind(XMLGregorianCalendar cal) throws XPathException {
        return new TimeValue(getExpression(), cal);
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
                return new StringValue(getExpression(), getStringValue());
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
                        "Type error: cannot cast xs:time to "
                                + Type.getTypeName(requiredType));
        }
    }

    public ComputableValue minus(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.TIME:
                return new DayTimeDurationValue(getExpression(), getTimeInMillis() - ((TimeValue) other).getTimeInMillis());
            case Type.DAY_TIME_DURATION:
                return ((DayTimeDurationValue) other).negate().plus(this);
            default:
                throw new XPathException(getExpression(),
                        "Operand to minus should be of type xs:time or xdt:dayTimeDuration; got: "
                                + Type.getTypeName(other.getType()));
        }
    }

    public ComputableValue plus(ComputableValue other) throws XPathException {
        if (other.getType() == Type.DAY_TIME_DURATION) {
            return other.plus(this);
        }
        throw new XPathException(getExpression(),
                "Operand to plus should be of type xdt:dayTimeDuration; got: "
                        + Type.getTypeName(other.getType()));
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target == byte[].class) {
            final ByteBuffer buf = ByteBuffer.allocate(MAX_SERIALIZED_SIZE);
            serialize(buf);
            return (T) Arrays.copyOf(buf.array(), buf.position());
        } else if (target == ByteBuffer.class) {
            final ByteBuffer buf = ByteBuffer.allocate(MAX_SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf;
        } else {
            return super.toJavaObject(target);
        }
    }

    /**
     * Serializes to a ByteBuffer.
     *
     * 4 to 5 bytes comprised of 28 to 39 used bits:
     *
     * -Reserved- = 1 bits
     * Hour = 5 bits
     * Minute = 6 bits
     * Second = 6 bits
     * Milliseconds = 10 bits
     * Timezone defined = 1 bit
     * (if Timezone defined) Timezone (Hour = 4 bits, Minute = 6 bits, ± = 1 bit) = 11 bits
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        final int tz = calendar.getTimezone();
        final boolean tzDefined = tz != DatatypeConstants.FIELD_UNDEFINED;

        final byte b0 = (byte) (((calendar.getHour() & 0x1F) << 2) | ((calendar.getMinute() & 0x3F) >> 4));                             // 1 bit Reserved, 5 bits Hour, 2 bit Minute
        final byte b1 = (byte) (((calendar.getMinute() & 0xF) << 4) | ((calendar.getSecond() & 0x3F) >> 2));                            // 4 bits Minute, 4 bits Second
        final byte b2 = (byte) (((calendar.getSecond() & 0x3) << 6) | ((calendar.getMillisecond() & 0x3FF) >> 4));                      // 2 bits Second, 6 bits Millisecond

        if (!tzDefined) {
            final byte b3 = (byte) ((calendar.getMillisecond() & 0xF) << 4);                                                            // 4 bits Millisecond, 4 bits unused

            buf.put(b0);
            buf.put(b1);
            buf.put(b2);
            buf.put(b3);

        } else {
            final int atz = Math.abs(tz);
            final int tzHour = atz / 60;
            final int tzMinute = atz - (tzHour * 60);

            final byte b3 = (byte) (((calendar.getMillisecond() & 0xF) << 4) | (1 << 3) | ((tzHour & 0xF) >> 1));                       // 4 bits Millisecond, 1 bit TZ defined, 3 bits TZ Hour
            final byte b4 = (byte) (((tzHour & 0x1) << 7) | ((tzMinute & 0x3F) << 1) | (tz > 0 ? 1 : 0));                               // 1 bit TZ Hour, 6 bits TZ Minute, 1 bit TZ Sign

            buf.put(b0);
            buf.put(b1);
            buf.put(b2);
            buf.put(b3);
            buf.put(b4);
        }
    }

    /**
     * Deserializes from a ByteBuffer.
     *
     * See {@link #serialize(ByteBuffer)} for format details.
     *
     * @param buf the ByteBuffer to deserialize from.
     *
     * @return the deserialized TimeValue.
     */
    public static TimeValue deserialize(final ByteBuffer buf) {
        final byte b0 = buf.get();
        final byte b1 = buf.get();
        final byte b2 = buf.get();
        final byte b3 = buf.get();

        final int hour = (b0 >> 2) & 0x1F;
        final int minute = (((b0 & 0x3) << 4) | ((b1 >> 4) & 0xF));
        final int second = (((b1 & 0xF) << 2) | ((b2 >> 6) & 0x3));
        final int ms = (((b2 & 0x3F) << 4) | ((b3 >> 4) & 0xF));

        final boolean tzDefined = (b3 & 0x8) == 0x8;

        final int timezone;
        if (!tzDefined) {
            timezone = DatatypeConstants.FIELD_UNDEFINED;

        } else {
            final byte b4 = buf.get();
            final int tzHour = (((b3 & 0x7) << 1) | ((b4 >> 7) & 0x1));
            final int tzMinute = (b4 >> 1) & 0x3F;
            final int tzSign = ((b4 & 0x1) == 0x1) ? 1 : -1;
            timezone = ((tzHour * 60) + tzMinute) * tzSign;
        }

        return new TimeValue(hour, minute, second, ms, timezone);
    }
}
