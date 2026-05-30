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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for the date/time-family subtype comparison fix.
 *
 * <p>xs:dateTimeStamp is a restriction of xs:dateTime (XSD 1.1 §3.4.28). Per
 * XPath/XQuery 3.1, comparing the two should succeed — the
 * {@code AbstractDateTimeValue.compareTo} guard previously used strict
 * primitive-type equality and rejected the cross-type case with XPTY0004.</p>
 */
public class DateTimeSubtypeCompareTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer embedded =
            new ExistXmldbEmbeddedServer(false, true, true);

    /** xs:dateTimeStamp `le` xs:dateTime — the issue #5478 reproducer. */
    @Test
    public void dateTimeStampLeDateTime() throws XMLDBException {
        final String query =
                "declare function local:less($a as xs:string, $b as xs:dateTime) as xs:boolean { "
                + "  if ($a='') then true() else xs:dateTime($a) <= $b "
                + "}; "
                + "local:less('2024-01-01T00:00:00Z', xs:dateTimeStamp('2024-02-01T00:00:00.000Z'))";
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("true", rs.getResource(0).getContent());
    }

    /** Reverse direction: xs:dateTime vs xs:dateTimeStamp. */
    @Test
    public void dateTimeGtDateTimeStamp() throws XMLDBException {
        final String query =
                "xs:dateTime('2024-02-01T00:00:00Z') gt xs:dateTimeStamp('2024-01-01T00:00:00.000Z')";
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("true", rs.getResource(0).getContent());
    }

    /** General-comparison (`=`) flavour. */
    @Test
    public void dateTimeStampGeneralEqDateTime() throws XMLDBException {
        final String query =
                "xs:dateTimeStamp('2024-01-01T00:00:00Z') = xs:dateTime('2024-01-01T00:00:00Z')";
        final ResourceSet rs = embedded.executeQuery(query);
        assertEquals("true", rs.getResource(0).getContent());
    }

    /** Sister types must still be rejected. xs:date vs xs:time → XPTY0004. */
    @Test
    public void sisterTypesStillRejected() throws XMLDBException {
        final String query =
                "xs:date('2024-01-01') eq xs:time('12:00:00')";
        try {
            final ResourceSet rs = embedded.executeQuery(query);
            org.junit.Assert.fail("Expected XPTY0004 for cross-sister-type comparison, got: "
                    + (rs.getSize() > 0 ? rs.getResource(0).getContent() : "<empty>"));
        } catch (final XMLDBException e) {
            org.junit.Assert.assertTrue("Expected XPTY0004 in: " + e.getMessage(),
                    e.getMessage().contains("XPTY0004"));
        }
    }
}
