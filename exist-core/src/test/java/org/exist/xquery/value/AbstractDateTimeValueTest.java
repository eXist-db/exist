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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.xml.datatype.XMLGregorianCalendar;

import java.time.Instant;
import java.util.Date;
import java.util.GregorianCalendar;

import static org.junit.jupiter.api.Assertions.*;

class AbstractDateTimeValueTest {
    AbstractDateTimeValue dateValue;

    @BeforeEach
    void prepare() throws XPathException {
        dateValue = new DateValue();
    }

    @Test
    void testConversionPreference() throws XPathException {
        assertEquals(0, dateValue.conversionPreference(DateValue.class));
        assertEquals(1, dateValue.conversionPreference(XMLGregorianCalendar.class));
        assertEquals(2, dateValue.conversionPreference(GregorianCalendar.class));
        assertEquals(3, dateValue.conversionPreference(Date.class));
        assertEquals(4, dateValue.conversionPreference(Instant.class));
    }

    @Test
    void testToJavaObject() throws XPathException {
        assertEquals(dateValue, dateValue.toJavaObject(Object.class));
        assertEquals(dateValue, dateValue.toJavaObject(DateValue.class));
        final XMLGregorianCalendar xmlGregorianCalendar = dateValue.toJavaObject(XMLGregorianCalendar.class);
        final XMLGregorianCalendar expectedCalendar = dateValue.getCanonicalCalendar();
        assertEquals(expectedCalendar, xmlGregorianCalendar);
        assertNotSame(expectedCalendar, xmlGregorianCalendar);
        final Date expectedDate = new Date(dateValue.getTimeInMillis());
        assertEquals(expectedDate, dateValue.toJavaObject(GregorianCalendar.class).getTime());
        assertEquals(expectedDate, dateValue.toJavaObject(Date.class));
        assertEquals(expectedDate.toInstant(), dateValue.toJavaObject(Instant.class));
    }
}
