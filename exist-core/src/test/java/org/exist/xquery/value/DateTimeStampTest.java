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

import static org.junit.Assert.assertEquals;

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
}
