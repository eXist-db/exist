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
import java.util.GregorianCalendar;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DateValue extends AbstractDateTimeValue {

    /**
     * <p>BigInteger constant; representing one billion.</p>
     */
    private static final BigInteger BILLION_B = BigInteger.valueOf(1000000000);

    public static final int MIN_SERIALIZED_SIZE = 4;
    public static final int MAX_SERIALIZED_SIZE = 13;

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

    public DateValue(final BigInteger year, final int month, final int day, final int timezone) {
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

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target == byte[].class) {
            final ByteBuffer buf = ByteBuffer.allocate(MAX_SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf.array();
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
     * 4 to 13 bytes comprised of 26 to 101 used bits:
     *
     * Eon = 8 to 40 bits (most often 8 bits for dates less than ± One Billion Years). NOTE: Always a multiple of 8 bits.
     * Year = 8 to 40 bits (most often 16 bits for dates less than ± 32,767 Years). NOTE: Always a multiple of 8 bits.
     * - Reserved- = 3 bits
     * Month = 4 bits
     * Day = 5 bits
     * Timezone defined = 1 bit
     * (if Timezone defined) Timezone (Hour = 4 bits, Minute = 6 bits, ± = 1 bit) = 11 bits
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        final int eon;
        if (calendar.getEon() != null) {
            final BigInteger temp = calendar.getEon().divide(BILLION_B);
            final long templ = temp.longValue();
            if (templ > Integer.MAX_VALUE || templ < Integer.MIN_VALUE) {
                throw new IllegalArgumentException("Calendar EON is out of Integer range: " + temp);
            }
            eon = temp.intValue();
        } else {
            eon = 0;
        }

        final int tz = calendar.getTimezone();
        final boolean tzDefined = tz != DatatypeConstants.FIELD_UNDEFINED;

        // TODO(AR) assume max 32 bits for billions of years - not unbounded but very large! We should add an Overflow check to DateValue construction!
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(eon));                                               // 8 to 40 bits Eon
        VariableLengthQuantity.writeInt(buf, ZigZag.encode(calendar.getYear()));                                // 8 to 40 bits Year

        final byte b0 = (byte) (((calendar.getMonth() & 0xF) << 1) | (calendar.getDay() >> 4));                 // 3 bits Reserved, 4 bits Month, 1 bit Day

        if (!tzDefined) {

            final byte b1 = (byte) ((calendar.getDay() & 0xF) << 4);                                            // 4 bits Day, 4 bits unused

            buf.put(b0);
            buf.put(b1);

        } else {
            final int atz = Math.abs(tz);
            final int tzHour = atz / 60;
            final int tzMinute = atz - (tzHour * 60);

            final byte b1 = (byte) (((calendar.getDay() & 0xF) << 4) | (1 << 3) | ((tzHour & 0xF) >> 1));       // 4 bits Day, 1 bit TZ defined, 3 bits TZ Hour
            final byte b2 = (byte) (((tzHour & 0x1) << 7) | ((tzMinute & 0x3F) << 1) | (tz > 0 ? 1 : 0));       // 1 bit TZ Hour, 6 bits TZ Minute, 1 bit TZ Sign

            buf.put(b0);
            buf.put(b1);
            buf.put(b2);
        }
    }

    /**
     * Deserializes from a ByteBuffer.
     *
     * See {@link #serialize(ByteBuffer)} for format details.
     *
     * @param buf the ByteBuffer to deserialize from.
     *
     * @return the deserialized DateValue.
     */
    public static DateValue deserialize(final ByteBuffer buf) {
        final int eon = ZigZag.decode(VariableLengthQuantity.readInt(buf));
        final int year = ZigZag.decode(VariableLengthQuantity.readInt(buf));

        final byte b0 = buf.get();
        final byte b1 = buf.get();

        final int month = (b0 >> 1) & 0xF;
        final int day = ((b0 & 1) << 4) | ((b1 >> 4) & 0xF);

        final boolean tzDefined = (b1 & 0x8) == 0x8;
        final int timezone;
        if (!tzDefined) {
            timezone = DatatypeConstants.FIELD_UNDEFINED;

        } else {
            final byte b2 = buf.get();
            final int tzHour = (((b1 & 0x7) << 1) | ((b2 >> 7) & 0x1));
            final int tzMinute = (b2 >> 1) & 0x3F;
            final int tzSign = ((b2 & 0x1) == 0x1) ? 1 : -1;
            timezone = ((tzHour * 60) + tzMinute) * tzSign;
        }

        BigInteger eonAndYear = BigInteger.ZERO;
        if (eon != 0) {
            eonAndYear = BILLION_B.multiply(BigInteger.valueOf(eon));
        }
        eonAndYear = eonAndYear.add(BigInteger.valueOf(year));

        return new DateValue(eonAndYear, month, day, timezone);
    }
}
