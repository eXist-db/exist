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

import org.exist.util.io.VariableLengthQuantity;
import org.exist.util.io.ZigZag;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Represents a value of type xs:dateTime.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DateTimeValue extends AbstractDateTimeValue {

    public static final int MIN_SERIALIZED_SIZE = 7;
    public static final int MAX_SERIALIZED_SIZE = 16;

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

    public DateTimeValue(final BigInteger year, final int month, final int day, final int hour, final int minute, final int second, final int millisecond, final int timezone) {
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
     * Uses Variable Length Quantities,
     * and so we can only know the possible minimum
     * and maximum length of the output.
     *
     * 7 to 16 bytes comprised of 53 to 128 used bits:
     *
     * Eon = 8 to 40 bits (most often 8 bits for dates less than ± One Billion Years). NOTE: Always a multiple of 8 bits.
     * Year = 8 to 40 bits (most often 16 bits for dates less than ± 32,767 Years). NOTE: Always a multiple of 8 bits.
     * Month = 4 bits
     * Day = 5 bits
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

        // TODO(AR) assume max 32 bits for billions of years - not unbounded but very large! We should add an Overflow check to DateTimeValue construction!
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(getEonBillions()));                                                                  // 8 to 40 bits Eon
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(calendar.getYear()));                                                                // 8 to 40 bits Year

        final byte b0 = (byte) (((calendar.getMonth() & 0xF) << 4) | ((calendar.getDay() & 0x1F) >> 1));                                        // 4 bits Month, 4 bits Day
        final byte b1 = (byte) (((calendar.getDay() & 0x1) << 7)  | ((calendar.getHour() & 0x1F) << 2) | ((calendar.getMinute() & 0x3F) >> 4)); // 1 bit Day, 5 bits Hour, 2 bits Minute
        final byte b2 = (byte) (((calendar.getMinute() & 0xF) << 4) | ((calendar.getSecond() & 0x3F) >> 2));                                    // 4 bits Minute, 4 bits Second
        final byte b3 = (byte) (((calendar.getSecond() & 0x3) << 6) |  ((calendar.getMillisecond() & 0x3FF) >> 4));                             // 2 bits Second, 6 bits Milliseconds

        if (!tzDefined) {
            final byte b4 = (byte) ((calendar.getMillisecond() & 0xF) << 4);                                                                    // 4 bits Milliseconds, 4 bits unused

            buf.put(b0);
            buf.put(b1);
            buf.put(b2);
            buf.put(b3);
            buf.put(b4);

        } else {

            final int atz = Math.abs(tz);
            final int tzHour = atz / 60;
            final int tzMinute = atz - (tzHour * 60);

            final byte b4 = (byte) (((calendar.getMillisecond() & 0xF) << 4) | (1 << 3) | ((tzHour & 0xF) >> 1));                               // 4 bits Milliseconds, 1 bit TZ defined, 3 bits TZ Hour
            final byte b5 = (byte) (((tzHour & 0x1) << 7) | ((tzMinute & 0x3F) << 1) | (tz > 0 ? 1 : 0));                                       // 1 bit TZ Hour, 6 bits TZ Minute, 1 bit TZ Sign

            buf.put(b0);
            buf.put(b1);
            buf.put(b2);
            buf.put(b3);
            buf.put(b4);
            buf.put(b5);
        }
    }

    /**
     * Deserializes from a ByteBuffer.
     *
     * See {@link #serialize(ByteBuffer)} for format details.
     *
     * @param buf the ByteBuffer to deserialize from.
     *
     * @return the deserialized DateTimeValue.
     */
    public static DateTimeValue deserialize(final ByteBuffer buf) {
        final int eon = ZigZag.decode(VariableLengthQuantity.readInt(buf));
        final int year = ZigZag.decode(VariableLengthQuantity.readInt(buf));

        final byte b0 = buf.get();
        final byte b1 = buf.get();
        final byte b2 = buf.get();
        final byte b3 = buf.get();
        final byte b4 = buf.get();

        final int month = ((b0 >> 4) & 0xF);
        final int day = (((b0 & 0xF) << 1) | ((b1 >> 7) & 0x1));
        final int hour = ((b1 >> 2) & 0x1F);
        final int minute = (((b1 & 0x3) << 4) | ((b2 >> 4) & 0xF));
        final int seconds = (((b2 & 0xF) << 2) | ((b3 >> 6) & 0x3));
        final int milliseconds = (((b3 & 0x3F) << 4) | ((b4 >> 4) & 0xF));

        final boolean tzDefined = (b4 & 0x8) == 0x8;
        final int timezone;
        if (!tzDefined) {
            timezone = DatatypeConstants.FIELD_UNDEFINED;

        } else {
            final byte b5 = buf.get();
            final int tzHour = (((b4 & 0x7) << 1) | ((b5 >> 7) & 0x1));
            final int tzMinute = (b5 >> 1) & 0x3F;
            final int tzSign = ((b5 & 0x1) == 0x1) ? 1 : -1;
            timezone = ((tzHour * 60) + tzMinute) * tzSign;
        }

        return new DateTimeValue(calcEonAndYear(eon, year), month, day, hour, minute, seconds, milliseconds, timezone);
    }
}
