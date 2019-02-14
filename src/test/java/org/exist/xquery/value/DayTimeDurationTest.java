package org.exist.xquery.value;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(ParallelRunner.class)
public class DayTimeDurationTest extends AbstractTimeRelatedTestCase {

    @Test(expected = XPathException.class)
    public void create1() throws XPathException {
        new DayTimeDurationValue("P1Y4M");
    }

    @Test(expected = XPathException.class)
    public void create2() throws XPathException {
        new DayTimeDurationValue("P1Y");
    }

    @Test(expected = XPathException.class)
    public void create3() throws XPathException {
        new DayTimeDurationValue("P4M");
    }

    @Test
    public void stringFormat1() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("P3DT1H2M3S");
        assertEquals("P3DT1H2M3S", dv.getStringValue());
    }

    @Test
    public void stringFormat2() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("P1DT25H65M66.5S");
        assertEquals("P2DT2H6M6.5S", new DurationValue(dv.getCanonicalDuration()).getStringValue());
    }

    @Test
    public void stringFormat3() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("P0DT0H");
        assertEquals("PT0S", dv.getStringValue());
    }

    @Test
    public void stringFormat4() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("PT5H0M0S");
        assertEquals("PT5H", dv.getStringValue());
    }

    @Test
    public void convert1() throws XPathException {
        final DayTimeDurationValue dtdv = new DayTimeDurationValue("P3DT1H2M3S");
        final DurationValue dv = (DurationValue) dtdv.convertTo(Type.DURATION);
        assertEquals("P3DT1H2M3S", dv.getStringValue());
    }

    @Test
    public void convert2() throws XPathException {
        final DayTimeDurationValue dtdv = new DayTimeDurationValue("P3DT1H2M3S");
        assertEquals("P0M", dtdv.convertTo(Type.YEAR_MONTH_DURATION).getStringValue());
    }

    @Test
    public void getPart1() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("P3DT4H5M6S");
        assertEquals(0, dv.getPart(DurationValue.YEAR));
        assertEquals(0, dv.getPart(DurationValue.MONTH));
        assertEquals(3, dv.getPart(DurationValue.DAY));
        assertEquals(4, dv.getPart(DurationValue.HOUR));
        assertEquals(5, dv.getPart(DurationValue.MINUTE));
        assertEquals(6, dv.getSeconds(), 0);
    }

    @Test
    public void getPart2() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("-P3DT4H5M6S");
        assertEquals(0, dv.getPart(DurationValue.YEAR));
        assertEquals(0, dv.getPart(DurationValue.MONTH));
        assertEquals(-3, dv.getPart(DurationValue.DAY));
        assertEquals(-4, dv.getPart(DurationValue.HOUR));
        assertEquals(-5, dv.getPart(DurationValue.MINUTE));
        assertEquals(-6, dv.getSeconds(), 0);
    }

    @Test
    public void getValue1() throws XPathException {
        final DayTimeDurationValue dv = new DayTimeDurationValue("P1DT30S");
        assertEquals(1.0 * 24 * 60 * 60 + 30.0, dv.getValue(), 0.0);
    }

    @Test
    public void getValue2() throws XPathException {
        final DayTimeDurationValue dv = new DayTimeDurationValue("P1D");
        assertEquals(1.0 * 24 * 60 * 60, dv.getValue(), 0.0);
    }

    @Test
    public void getType() throws XPathException {
        final DurationValue dv = new DayTimeDurationValue("P3DT4H5M6S");
        assertEquals(Type.DAY_TIME_DURATION, dv.getType());
    }

    @Test
    public void compare1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT2H3M5S");
        assertEquals(-1, dv1.compareTo(null, dv2));
        assertEquals(+1, dv2.compareTo(null, dv1));
    }

    @Test
    public void compare2() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT2H3M4S");
        assertEquals(0, dv1.compareTo(null, dv2));
        assertEquals(0, dv2.compareTo(null, dv1));
    }

    @Test
    public void compare3() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT2H3M5S");
        assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
        assertTrue(dv1.compareTo(null, Comparison.NEQ, dv2));
        assertFalse(dv1.compareTo(null, Comparison.GT, dv2));
        assertTrue(dv1.compareTo(null, Comparison.LT, dv2));
        assertFalse(dv1.compareTo(null, Comparison.GTEQ, dv2));
        assertTrue(dv1.compareTo(null, Comparison.LTEQ, dv2));
    }

    @Test
    public void compare4() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT2H3M4S");
        assertTrue(dv1.compareTo(null, Comparison.EQ, dv2));
        assertFalse(dv1.compareTo(null, Comparison.NEQ, dv2));
        assertFalse(dv1.compareTo(null, Comparison.GT, dv2));
        assertFalse(dv1.compareTo(null, Comparison.LT, dv2));
        assertTrue(dv1.compareTo(null, Comparison.GTEQ, dv2));
        assertTrue(dv1.compareTo(null, Comparison.LTEQ, dv2));
    }

    @Test
    public void compare5() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("PT2H");
        final DurationValue dv2 = new DayTimeDurationValue("PT2H0M");
        assertEquals(0, dv1.compareTo(null, dv2));
        assertEquals(0, dv2.compareTo(null, dv1));
    }

    @Test
    public void minMax1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT2H3M5S");
        assertDurationEquals(dv2, dv1.max(null, dv2));
        assertDurationEquals(dv2, dv2.max(null, dv1));
        assertDurationEquals(dv1, dv1.min(null, dv2));
        assertDurationEquals(dv1, dv2.min(null, dv1));
    }

    @Test
    public void plus1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P2DT12H5M");
        final DurationValue dv2 = new DayTimeDurationValue("P5DT12H");
        final DurationValue dv3 = new DayTimeDurationValue("P8DT5M");
        assertDurationEquals(dv3, dv1.plus(dv2));
        assertDurationEquals(dv3, dv2.plus(dv1));
    }

    @Test
    public void minus1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P2DT12H");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT10H30M");
        final DurationValue dv3 = new DayTimeDurationValue("P1DT1H30M");
        assertDurationEquals(dv3, dv1.minus(dv2));
    }

    @Test
    public void mult1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("PT2H10M");
        final DecimalValue f = new DecimalValue("2.1");
        final DurationValue dv2 = new DayTimeDurationValue("PT4H33M");
        assertDurationEquals(dv2, dv1.mult(f));
        assertDurationEquals(dv2, f.mult(dv1));
    }

    @Test
    public void div1() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P1DT2H30M10.5S");
        final DecimalValue f = new DecimalValue("1.5");
        final DurationValue dv2 = new DayTimeDurationValue("PT17H40M7S");
        assertDurationEquals(dv2, dv1.div(f));
    }

    @Test
    public void div2() throws XPathException {
        final DurationValue dv1 = new DayTimeDurationValue("P2DT53M11S");
        final DurationValue dv2 = new DayTimeDurationValue("P1DT10H");
        assertEquals(1.4378349, ((Double) dv1.div(dv2).toJavaObject(Double.class)).doubleValue(), 0.0000001);
    }
}
