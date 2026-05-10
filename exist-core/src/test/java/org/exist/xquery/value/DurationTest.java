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

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@RunWith(ParallelRunner.class)
public class DurationTest extends AbstractTimeRelatedTestCase {

    @Test
    public void stringFormat1() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
        assertEquals("P1Y2M3DT1H2M3S", dv.getStringValue());
    }

    @Test
    public void stringFormat2() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y13M1DT25H65M66.5S");
        assertEquals("P2Y1M2DT2H6M6.5S", dv.getStringValue());
    }

    @Test
    public void stringFormat3() throws XPathException {
        final DurationValue dv = new DurationValue("P0Y");
        assertEquals("PT0S", dv.getStringValue());
    }

    @Test
    public void negate() throws XPathException {
        DurationValue dv = new DurationValue("P2D");
        dv = dv.negate();
        assertEquals("-P2D", dv.getStringValue());
        assertEquals(-2, dv.getPart(DurationValue.DAY));
    }

    @Test
    public void convert1() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
        final YearMonthDurationValue ymdv = (YearMonthDurationValue) dv.convertTo(Type.YEAR_MONTH_DURATION);
        assertEquals("P1Y2M", ymdv.getStringValue());
    }

    @Test
    public void convert2() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
        final DayTimeDurationValue dtdv = (DayTimeDurationValue) dv.convertTo(Type.DAY_TIME_DURATION);
        assertEquals("P3DT1H2M3S", dtdv.getStringValue());
    }

    @Test
    public void convert3() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT1H2M3.5S");
        final DayTimeDurationValue dtdv = (DayTimeDurationValue) dv.convertTo(Type.DAY_TIME_DURATION);
        assertEquals("P3DT1H2M3.5S", dtdv.getStringValue());
    }

    @Test
    public void getPart1() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
        assertEquals(1, dv.getPart(DurationValue.YEAR));
        assertEquals(2, dv.getPart(DurationValue.MONTH));
        assertEquals(3, dv.getPart(DurationValue.DAY));
        assertEquals(4, dv.getPart(DurationValue.HOUR));
        assertEquals(5, dv.getPart(DurationValue.MINUTE));
        assertEquals(6, dv.getSeconds(), 0);
    }

    @Test
    public void getPart2() throws XPathException {
        final DurationValue dv = new DurationValue("-P1Y2M3DT4H5M6S");
        assertEquals(-1, dv.getPart(DurationValue.YEAR));
        assertEquals(-2, dv.getPart(DurationValue.MONTH));
        assertEquals(-3, dv.getPart(DurationValue.DAY));
        assertEquals(-4, dv.getPart(DurationValue.HOUR));
        assertEquals(-5, dv.getPart(DurationValue.MINUTE));
        assertEquals(-6, dv.getSeconds(), 0);
    }

    @Test
    public void getType() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
        assertEquals(Type.DURATION, dv.getType());
    }

    @Test
    public void compareSucceeds1() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
        //eq and ne comparison operators are allowed
        dv.compareTo(null, Comparison.EQ, dv);
    }

    @Test
    public void compareSucceeds2() throws XPathException {
        final DurationValue dv1 = new DurationValue("P1Y2M3DT4H5M6S");
        final DurationValue dv2 = new DayTimeDurationValue("P1D");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test
    public void compareSucceeds3() throws XPathException {
        final DurationValue dv1 = new DurationValue("P1Y2M3DT4H5M6S");
        final DurationValue dv2 = new YearMonthDurationValue("P1Y");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test
    public void compareSucceeds4() throws XPathException {
        final DurationValue dv1 = new YearMonthDurationValue("P1Y");
        final DurationValue dv2 = new DayTimeDurationValue("P1D");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test
    public void compareSucceeds5() throws XPathException {
        final DurationValue dv2 = new YearMonthDurationValue("P1Y");
        final DurationValue dv1 = new DayTimeDurationValue("P1D");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test
    public void compareSucceeds6() throws XPathException {
        final DurationValue dv2 = new DurationValue("P1Y2M3DT4H5M6S");
        final DurationValue dv1 = new DayTimeDurationValue("P1D");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test
    public void compareSucceeds7() throws XPathException {
        final DurationValue dv2 = new DurationValue("P1Y2M3DT4H5M6S");
        final DurationValue dv1 = new YearMonthDurationValue("P1Y");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
    }

    @Test(expected = XPathException.class)
    public void compareFail1() throws XPathException {
        final DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
        dv.compareTo(null, Comparison.LT, dv);
    }

    @Test
    public void minMaxFail() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new YearMonthDurationValue("P1Y3M");
        final DurationValue dv3 = new DurationValue("P1Y2M3DT4H5M6S");
        checkMinMaxFails(dv1, dv2);
        checkMinMaxFails(dv2, dv1);
        checkMinMaxFails(dv1, dv3);
        checkMinMaxFails(dv3, dv1);
        checkMinMaxFails(dv2, dv3);
        checkMinMaxFails(dv3, dv2);
    }

    // --- Cross-type canonicalisation (issue #6327) ---
    // op:same-key requires equal hashCodes for equal-value durations across xs:duration
    // family subtypes; the bifurcan map in MapType keys on AtomicValue::hashCode and the
    // map-contains-017 XQTS test exercises this path.

    @Test
    public void hashCodeEqualForDurationAndYearMonthDuration() throws XPathException {
        final DurationValue d = new DurationValue("P1Y");
        final YearMonthDurationValue ymd = new YearMonthDurationValue("P12M");
        assertEquals(d.hashCode(), ymd.hashCode());
        assertEquals(ymd.hashCode(), d.hashCode());
    }

    @Test
    public void hashCodeEqualForDurationAndDayTimeDuration() throws XPathException {
        final DurationValue d = new DurationValue("P1D");
        final DayTimeDurationValue dtd = new DayTimeDurationValue("PT24H");
        assertEquals(d.hashCode(), dtd.hashCode());
    }

    @Test
    public void hashCodeEqualForZeroDurations() throws XPathException {
        final DurationValue d = new DurationValue("P0D");
        final YearMonthDurationValue ymd = new YearMonthDurationValue("P0M");
        final DayTimeDurationValue dtd = new DayTimeDurationValue("PT0S");
        assertEquals(d.hashCode(), ymd.hashCode());
        assertEquals(d.hashCode(), dtd.hashCode());
        assertEquals(ymd.hashCode(), dtd.hashCode());
    }

    @Test
    public void hashCodeEqualForNegativeDurations() throws XPathException {
        final DurationValue d = new DurationValue("-P1Y");
        final YearMonthDurationValue ymd = new YearMonthDurationValue("-P12M");
        assertEquals(d.hashCode(), ymd.hashCode());
    }

    @Test
    public void hashCodeDifferentForUnequalDurations() throws XPathException {
        // sanity: unequal durations should generally hash differently
        final DurationValue p1y = new YearMonthDurationValue("P1Y");
        final DurationValue p2y = new YearMonthDurationValue("P2Y");
        org.junit.Assert.assertNotEquals(p1y.hashCode(), p2y.hashCode());
    }

    @Test
    public void equalsCommutativeAcrossDurationSubtypes() throws XPathException {
        final DurationValue d = new DurationValue("P1Y");
        final YearMonthDurationValue ymd = new YearMonthDurationValue("P12M");
        org.junit.Assert.assertEquals(d, ymd);
        org.junit.Assert.assertEquals(ymd, d);
    }

    @Test
    public void hashCodeDeterministicAcrossInstances() throws XPathException {
        // 100-iter loop exposes any non-determinism (e.g., identity-hash leaks)
        final int expected = new DurationValue("P1Y").hashCode();
        for (int i = 0; i < 100; i++) {
            final DurationValue d = new DurationValue("P1Y");
            final YearMonthDurationValue ymd = new YearMonthDurationValue("P12M");
            assertEquals("iter " + i + ": DurationValue", expected, d.hashCode());
            assertEquals("iter " + i + ": YearMonthDurationValue", expected, ymd.hashCode());
        }
    }
}
