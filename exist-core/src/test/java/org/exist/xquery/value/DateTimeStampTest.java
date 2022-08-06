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

import org.exist.xquery.XPathException;
import org.junit.Test;

import javax.xml.datatype.XMLGregorianCalendar;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.GregorianCalendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:radek@evolvedbinary.com">Radek Hübner</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DateTimeStampTest extends AbstractTimeRelatedTestCase {


    @Test(expected = XPathException.class)
    public void constructWithoutTimeZone() throws XPathException {
        new DateTimeStampValue("2005-10-11T10:00:00");
    }

    @Test
    public void convertDateTimeWithTimeZoneToDateTimeStamp() throws XPathException {
        final DateTimeValue dateTimeValue = new DateTimeValue("2005-10-11T10:00:00Z");
        final AtomicValue value = dateTimeValue.convertTo(Type.DATE_TIME_STAMP);
        assertEquals(DateTimeStampValue.class, value.getClass());
    }

    @Test(expected = XPathException.class)
    public void convertDateTimeWithoutTimeZoneToDateTimeStamp() throws XPathException {
        final DateTimeValue dateTimeValue = new DateTimeValue("2005-10-11T10:00:00");
        final AtomicValue value = dateTimeValue.convertTo(Type.DATE_TIME_STAMP);
        assertEquals(DateTimeStampValue.class, value.getClass());
    }

    @Test()
    public void getTimezone() throws XPathException {
        final DateTimeStampValue value = new DateTimeStampValue("2005-10-11T10:00:00+10:00");
        assertEquals(10 * 60, value.calendar.getTimezone());
    }

    @Test
    public void serializeDeserializeNow() throws XPathException {
        final XMLGregorianCalendar now = TimeUtils.getInstance().newXMLGregorianCalendar(new GregorianCalendar());

        serializeDeserialize(now);
    }

    @Test
    public void serializeDeserializeMin() throws XPathException {
        final XMLGregorianCalendar min = TimeUtils.getInstance().newXMLGregorianCalendar();
        min.setYear(Integer.MIN_VALUE + 1);
        min.setMonth(1);
        min.setDay(1);
        min.setHour(0);
        min.setMinute(0);
        min.setSecond(0);
        min.setMillisecond(0);
        min.setTimezone(-12 * 60);

        serializeDeserialize(min);
    }

    @Test
    public void serializeDeserializeMax() throws XPathException {
        final XMLGregorianCalendar max = TimeUtils.getInstance().newXMLGregorianCalendar();
        max.setYear(Integer.MAX_VALUE);
        max.setMonth(12);
        max.setDay(31);
        max.setHour(23);
        max.setMinute(59);
        max.setSecond(59);
        max.setMillisecond(999);
        max.setTimezone(14 * 60);

        serializeDeserialize(max);
    }

    @Test
    public void serializeDeserializeApproxYearsOfUniverse() throws XPathException {
        final XMLGregorianCalendar univ = TimeUtils.getInstance().newXMLGregorianCalendar();
        univ.setYear(new BigInteger("-13787000000"));
        univ.setMonth(1);
        univ.setDay(1);
        univ.setHour(0);
        univ.setMinute(0);
        univ.setSecond(0);
        univ.setMillisecond(0);
        univ.setTimezone(0);

        serializeDeserialize(univ);
    }

    private void serializeDeserialize(final XMLGregorianCalendar calendar) throws XPathException {
        // serialize
        final ByteBuffer buffer = ByteBuffer.allocate(DateTimeStampValue.MAX_SERIALIZED_SIZE);
        final DateTimeStampValue dateTimeStampValue1 = new DateTimeStampValue(calendar);
        dateTimeStampValue1.serialize(buffer);

        assertTrue(buffer.position() >= DateTimeStampValue.MIN_SERIALIZED_SIZE);
        assertTrue(buffer.position() <= DateTimeStampValue.MAX_SERIALIZED_SIZE);
        assertEquals(DateTimeStampValue.MAX_SERIALIZED_SIZE - buffer.position(), buffer.remaining());

        buffer.flip();

        // deserialize
        final DateTimeStampValue dateTimeStampValue2 = DateTimeStampValue.deserialize(buffer);

        assertEquals(dateTimeStampValue1, dateTimeStampValue2);
        assertEquals(calendar.getYear(), dateTimeStampValue2.getTrimmedCalendar().getYear());
        assertEquals(calendar.getMonth(), dateTimeStampValue2.getTrimmedCalendar().getMonth());
        assertEquals(calendar.getDay(), dateTimeStampValue2.getTrimmedCalendar().getDay());
        assertEquals(calendar.getHour(), dateTimeStampValue2.getTrimmedCalendar().getHour());
        assertEquals(calendar.getMinute(), dateTimeStampValue2.getTrimmedCalendar().getMinute());
        assertEquals(calendar.getSecond(), dateTimeStampValue2.getTrimmedCalendar().getSecond());
        assertEquals(calendar.getMillisecond(), dateTimeStampValue2.getTrimmedCalendar().getMillisecond());
        assertEquals(calendar.getTimezone(), dateTimeStampValue2.getTrimmedCalendar().getTimezone());
    }
}
