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
}
