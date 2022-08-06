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

import javax.xml.XMLConstants;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a value of type xs:dateTimeStamp.
 *
 * @author <a href="mailto:radek@evolvedbinary.com">Radek Hübner</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DateTimeStampValue extends DateTimeValue {

    public static final int MIN_SERIALIZED_SIZE = 8;
    public static final int MAX_SERIALIZED_SIZE = 16;

    private static final QName XML_SCHEMA_TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "dateTimeStamp");

    public DateTimeStampValue(final XMLGregorianCalendar calendar) throws XPathException {
        this(null, calendar);
    }

    public DateTimeStampValue(final Expression expression, final XMLGregorianCalendar calendar) throws XPathException {
        super(expression, calendar);
        checkValidTimezone();
    }

    public DateTimeStampValue(final String dateTime) throws XPathException {
        this(null, dateTime);
    }

    public DateTimeStampValue(final Expression expression, final String dateTime) throws XPathException {
        super(expression, dateTime);
        checkValidTimezone();
    }

    public DateTimeStampValue(final BigInteger year, final int month, final int day, final int hour, final int minute, final int second, final int millisecond, final int timezone) {
        this(null, year, month, day, hour, minute, second, millisecond, timezone);
    }

    public DateTimeStampValue(final Expression expression, final BigInteger year, final int month, final int day, final int hour, final int minute, final int second, final int millisecond, final int timezone) {
        super(expression, TimeUtils.getInstance().newXMLGregorianCalendar(year, month, day, hour, minute, second, millisecond, timezone));
    }

    private void checkValidTimezone() throws XPathException {
        if(calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new XPathException(getExpression(), ErrorCodes.ERROR, "Unable to create xs:dateTimeStamp, timezone missing.");
        }
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.DATE_TIME_STAMP:
                return this;
            case Type.DATE_TIME:
                return new DateTimeValue(getExpression(), calendar);
            default: return
                    super.convertTo(requiredType);
        }
    }

    @Override
    protected AbstractDateTimeValue createSameKind(final XMLGregorianCalendar cal) throws XPathException {
        return new DateTimeStampValue(getExpression(), cal);
    }

    @Override
    public int getType() {
        return Type.DATE_TIME_STAMP;
    }

    @Override
    protected QName getXMLSchemaType() {
        return XML_SCHEMA_TYPE;
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
     * 8 to 16 bytes comprised of 63 to 127 used bits:
     *
     * Eon = 8 to 40 bits (most often 8 bits for dates less than ± One Billion Years). NOTE: Always a multiple of 8 bits.
     * Year = 8 to 40 bits (most often 16 bits for dates less than ± 32,767 Years). NOTE: Always a multiple of 8 bits.
     * - Reserved- = 1 bit
     * Month = 4 bits
     * Day = 5 bits
     * Hour = 5 bits
     * Minute = 6 bits
     * Second = 6 bits
     * Milliseconds = 10 bits
     * Timezone (Hour = 4 bits, Minute = 6 bits, ± = 1 bit) = 11 bits
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        final int tz = calendar.getTimezone();
        final int atz = Math.abs(tz);
        final int tzHour = atz / 60;
        final int tzMinute = atz - (tzHour * 60);

        // TODO(AR) assume max 32 bits for billions of years - not unbounded but very large! We should add an Overflow check to DateTimeStampValue construction!
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(getEonBillions()));                             // 8 to 40 bits Eon
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(calendar.getYear()));                           // 8 to 40 bits Year

        final byte b0 = (byte) (((calendar.getMonth() & 0xF) << 3) | ((calendar.getDay() & 0x1F) >> 2));                                        // 1 bit Reserved, 4 bits Month, 3 bits Day
        final byte b1 = (byte) (((calendar.getDay() & 0x3) << 6)  | ((calendar.getHour() & 0x1F) << 1) | ((calendar.getMinute() & 0x3F) >> 5)); // 2 bits Day, 5 bits Hour, 1 bit Minute
        final byte b2 = (byte) (((calendar.getMinute() & 0x1F) << 3) | ((calendar.getSecond() & 0x3F) >> 3));                                   // 5 bits Minute, 3 bits Second
        final byte b3 = (byte) (((calendar.getSecond() & 0x7) << 5) |  ((calendar.getMillisecond() & 0x3FF) >> 5));                             // 3 bits Second, 5 bits Milliseconds
        final byte b4 = (byte) (((calendar.getMillisecond() & 0x1F) << 3) | ((tzHour & 0xF) >> 1));                                             // 5 bits Milliseconds, 3 bits TZ Hour
        final byte b5 = (byte) (((tzHour & 0x1) << 7) | ((tzMinute & 0x3F) << 1) | (tz > 0 ? 1 : 0));                                           // 1 bit TZ Hour, 6 bits TZ Minute, 1 bit TZ Sign

        buf.put(b0);
        buf.put(b1);
        buf.put(b2);
        buf.put(b3);
        buf.put(b4);
        buf.put(b5);
    }

    /**
     * Deserializes from a ByteBuffer.
     *
     * See {@link #serialize(ByteBuffer)} for format details.
     *
     * @param buf the ByteBuffer to deserialize from.
     *
     * @return the deserialized DateTimeStampValue.
     */
    public static DateTimeStampValue deserialize(final ByteBuffer buf) {
        final int eon = ZigZag.decode(VariableLengthQuantity.readInt(buf));
        final int year = ZigZag.decode(VariableLengthQuantity.readInt(buf));

        final byte b0 = buf.get();
        final byte b1 = buf.get();
        final byte b2 = buf.get();
        final byte b3 = buf.get();
        final byte b4 = buf.get();
        final byte b5 = buf.get();

        final int month = ((b0 >> 3) & 0xF);
        final int day = (((b0 & 0x7) << 2) | ((b1 >> 6) & 0x3));
        final int hour = ((b1 >> 1) & 0x1F);
        final int minute = (((b1 & 0x1) << 5) | ((b2 >> 3) & 0x1F));
        final int seconds = (((b2 & 0x7) << 3) | ((b3 >> 5) & 0x7));
        final int milliseconds = (((b3 & 0x1F) << 5) | ((b4 >> 3) & 0x1F));
        final int tzHour = (((b4 & 0x7) << 1) | ((b5 >> 7) & 0x1));
        final int tzMinute = (b5 >> 1) & 0x3F;
        final int tzSign = ((b5 & 0x1) == 0x1) ? 1 : - 1;
        final int timezone = ((tzHour * 60) + tzMinute) * tzSign;

        return new DateTimeStampValue(calcEonAndYear(eon, year), month, day, hour, minute, seconds, milliseconds, timezone);
    }
}
