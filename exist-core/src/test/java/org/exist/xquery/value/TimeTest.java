package org.exist.xquery.value;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * note: some of these tests rely on local timezone override to -05:00, done in super.setUp()
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@RunWith(ParallelRunner.class)
public class TimeTest extends AbstractTimeRelatedTestCase {

    @Test(expected = XPathException.class)
    public void create1() throws XPathException {
        new TimeValue("2005-10-11T10:00:00Z");
    }

    @Test(expected = XPathException.class)
    public void create2() throws XPathException {
        new TimeValue("2005-10-11");
    }

    @Test
    public void stringFormat1() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00Z");
        assertEquals("10:00:00Z", v.getStringValue());
    }

    @Test
    public void stringFormat2() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00-01:00");
        assertEquals("10:00:00-01:00", v.getStringValue());
        assertEquals("10:00:00-01:00", v.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void stringFormat3() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("24:00:00");
        assertEquals("00:00:00", v.getStringValue());
    }

    @Test
    public void stringFormat4() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00-00:00");
        assertEquals("10:00:00Z", v.getStringValue());
    }

    @Test
    public void stringFormat5() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00.5");
        assertEquals("10:00:00.5", v.getStringValue());
    }

    @Test
    public void stringFormat6() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00.50");
        assertEquals("10:00:00.5", v.getStringValue());
    }

    @Test
    public void stringFormat7() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00.0");
        assertEquals("10:00:00", v.getStringValue());
    }

    @Test
    public void stringFormat8() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:00:00");
        assertEquals("10:00:00", v.getStringValue());
    }

    @Test
    public void getPart1() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:05:02.6Z");
        assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
        assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
        assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
        assertEquals(600, v.getPart(AbstractDateTimeValue.MILLISECOND));
    }

    @Test
    public void getPart2() throws XPathException {
        final AbstractDateTimeValue v = new TimeValue("10:05:02");
        assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
        assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
        assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
    }

    @Test
    public void convert1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02+05:00");
        final AtomicValue v2 = v1.convertTo(Type.TIME);
        assertDateEquals(v1, v2);
    }

    @Test
    public void convert2() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02");
        final AtomicValue v2 = v1.convertTo(Type.TIME);
        assertDateEquals(v1, v2);
        assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
    }

    @Test
    public void getType() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02");
        assertEquals(Type.TIME, v1.getType());
    }

    @Test
    public void getTimezone1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02");
        assertEquals(Sequence.EMPTY_SEQUENCE, v1.getTimezone());
    }

    @Test
    public void getTimezone2() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02+05:30");
        assertDurationEquals(new DayTimeDurationValue("PT5H30M"), (AtomicValue) v1.getTimezone());
    }

    @Test
    public void getTimezone3() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02-05:30");
        assertDurationEquals(new DayTimeDurationValue("-PT5H30M"), (AtomicValue) v1.getTimezone());
    }

    @Test
    public void getTimezone4() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:05:02Z");
        assertDurationEquals(new DayTimeDurationValue("P0D"), (AtomicValue) v1.getTimezone());
    }

    @Test
    public void withoutTimezone1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00");
        assertEquals("10:00:00", v1.withoutTimezone().toString());
    }

    @Test
    public void withoutTimezone2() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00-07:00");
        assertEquals("10:00:00", v1.withoutTimezone().toString());
    }

    @Test
    public void adjustedToTimezone1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
        assertEquals("10:00:00-05:00", v2.getStringValue());
        assertEquals("10:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void adjustedToTimezone2() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00-07:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
        assertEquals("12:00:00-05:00", v2.getStringValue());
        assertEquals("12:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void adjustedToTimezone3() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
        assertEquals("10:00:00-10:00", v2.getStringValue());
        assertEquals("10:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void adjustedToTimezone4() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00-07:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
        assertEquals("07:00:00-10:00", v2.getStringValue());
        assertEquals("07:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void adjustedToTimezone5() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("10:00:00-07:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT10H"));
        assertEquals("03:00:00+10:00", v2.getStringValue());
        assertEquals("03:00:00+10:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test(expected = XPathException.class)
    public void adjustedToTimezone6() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("-PT15H"));
    }

    @Test(expected = XPathException.class)
    public void adjustedToTimezone7() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT14H01M"));
    }

    @Test(expected = XPathException.class)
    public void adjustedToTimezone8() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT8H4S"));
    }

    @Test
    public void adjustedToTimezone9() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("00:00:00");
        final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT14H"));
        assertEquals("00:00:00+14:00", v2.getStringValue());
        assertEquals("00:00:00+14:00", v2.getTrimmedCalendar().toXMLFormat());
    }

    @Test
    public void compare1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("08:00:00+09:00");
        final AbstractDateTimeValue v2 = new TimeValue("17:00:00-06:00");
        assertEquals(-1, v1.compareTo(null, v2));
        assertEquals(+1, v2.compareTo(null, v1));
    }

    @Test
    public void compare2() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("21:30:00+10:30");
        final AbstractDateTimeValue v2 = new TimeValue("06:00:00-05:00");
        assertEquals(0, v1.compareTo(null, v2));
        assertEquals(0, v2.compareTo(null, v1));
    }

    @Test
    public void compare3() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("12:00:00");
        final AbstractDateTimeValue v2 = new TimeValue("23:00:00+06:00");
        assertEquals(0, v1.compareTo(null, v2));
        assertEquals(0, v2.compareTo(null, v1));
    }

    @Test
    public void compare4() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("11:00:00");
        final AbstractDateTimeValue v2 = new TimeValue("17:00:00Z");
        assertEquals(-1, v1.compareTo(null, v2));
        assertEquals(+1, v2.compareTo(null, v1));
    }

    @Test
    public void compare6() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("21:30:00+10:30");
        final AbstractDateTimeValue v2 = new TimeValue("06:00:00-05:00");
        assertTrue(v1.compareTo(null, Comparison.EQ, v2));
        assertFalse(v1.compareTo(null, Comparison.NEQ, v2));
        assertFalse(v1.compareTo(null, Comparison.GT, v2));
        assertFalse(v1.compareTo(null, Comparison.LT, v2));
        assertTrue(v1.compareTo(null, Comparison.GTEQ, v2));
        assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
    }

    @Test
    public void compare7() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("11:00:00");
        final AbstractDateTimeValue v2 = new TimeValue("17:00:00Z");
        assertFalse(v1.compareTo(null, Comparison.EQ, v2));
        assertTrue(v1.compareTo(null, Comparison.NEQ, v2));
        assertFalse(v1.compareTo(null, Comparison.GT, v2));
        assertTrue(v1.compareTo(null, Comparison.LT, v2));
        assertFalse(v1.compareTo(null, Comparison.GTEQ, v2));
        assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
    }

    @Test
    public void minMax1() throws XPathException {
        final AbstractDateTimeValue v1 = new TimeValue("11:00:00");
        final AbstractDateTimeValue v2 = new TimeValue("17:00:00Z");
        assertDateEquals(v2, v1.max(null, v2));
        assertDateEquals(v2, v2.max(null, v1));
        assertDateEquals(v1, v1.min(null, v2));
        assertDateEquals(v1, v2.min(null, v1));
    }

    @Test
    public void plus1() throws XPathException {
        final AbstractDateTimeValue t = new TimeValue("11:12:00");
        final DurationValue d = new DayTimeDurationValue("P3DT1H15M");
        final AbstractDateTimeValue r = new TimeValue("12:27:00");
        assertDateEquals(r, t.plus(d));
    }

    @Test
    public void plus2() throws XPathException {
        final AbstractDateTimeValue t = new TimeValue("23:12:00+03:00");
        final DurationValue d = new DayTimeDurationValue("P1DT3H15M");
        final AbstractDateTimeValue r = new TimeValue("02:27:00+03:00");
        assertDateEquals(r, t.plus(d));
    }

    @Test
    public void minus1() throws XPathException {
        final AbstractDateTimeValue t1 = new TimeValue("11:12:00Z");
        final AbstractDateTimeValue t2 = new TimeValue("04:00:00");
        final DurationValue d = new DayTimeDurationValue("PT2H12M");
        assertDurationEquals(d, t1.minus(t2));
    }

    @Test
    public void minus2() throws XPathException {
        final AbstractDateTimeValue t1 = new TimeValue("11:00:00-05:00");
        final AbstractDateTimeValue t2 = new TimeValue("21:30:00+05:30");
        final DurationValue d = new DayTimeDurationValue("PT0S");
        assertDurationEquals(d, t1.minus(t2));
    }

    @Test
    public void minus3() throws XPathException {
        final AbstractDateTimeValue t1 = new TimeValue("17:00:00-06:00");
        final AbstractDateTimeValue t2 = new TimeValue("08:00:00+09:00");
        final DurationValue d = new DayTimeDurationValue("P1D");
        assertDurationEquals(d, t1.minus(t2));
    }

    @Test
    public void minus4() throws XPathException {
        final AbstractDateTimeValue t = new TimeValue("11:12:00");
        final DurationValue d = new DayTimeDurationValue("P3DT1H15M");
        final AbstractDateTimeValue r = new TimeValue("09:57:00");
        assertDateEquals(r, t.minus(d));
    }

    @Test
    public void minus5() throws XPathException {
        final AbstractDateTimeValue t = new TimeValue("08:20:00-05:00");
        final DurationValue d = new DayTimeDurationValue("P23DT10H10M");
        final AbstractDateTimeValue r = new TimeValue("22:10:00-05:00");
        assertDateEquals(r, t.minus(d));
    }
}
