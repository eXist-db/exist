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
 *	note: some of these tests rely on local timezone override to -05:00, done in super.setUp()
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@RunWith(ParallelRunner.class)
public class DateTest extends AbstractTimeRelatedTestCase {

	@Test(expected = XPathException.class)
	public void create1() throws XPathException {
		new DateValue("2005-10-11T10:00:00Z");
	}

	@Test(expected = XPathException.class)
	public void create2() throws XPathException {
		new DateValue("10:00:00Z");
	}

	@Test
	public void stringFormat1() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11Z");
		assertEquals("2005-10-11Z", v.getStringValue());
	}

	@Test
	public void stringFormat2() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11-01:00");
		assertEquals("2005-10-11-01:00", v.getStringValue());
		assertEquals("2005-10-11-01:00", v.getTrimmedCalendar().toXMLFormat());
	}

	@Test
	public void stringFormat3() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11-00:00");
		assertEquals("2005-10-11Z", v.getStringValue());
	}

	@Test
	public void stringFormat4() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11");
		assertEquals("2005-10-11", v.getStringValue());
	}

    @Test
	public void getPart1() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11Z");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
	}

    @Test
	public void getPart2() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
	}

    @Test
	public void convert1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(v1, v2);
	}

    @Test
	public void convert2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(v1, v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}

    @Test
	public void convert3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(new DateTimeValue("2005-10-11T00:00:00+05:00"), v2);
	}

    @Test
	public void convert4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(new DateTimeValue("2005-10-11T00:00:00"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}

    @Test
	public void getType() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		assertEquals(Type.DATE, v1.getType());
	}

    @Test
	public void getTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		assertEquals(Sequence.EMPTY_SEQUENCE, v1.getTimezone());
	}

    @Test
	public void getTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:30");
		assertDurationEquals(new DayTimeDurationValue("PT5H30M"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void getTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11-05:30");
		assertDurationEquals(new DayTimeDurationValue("-PT5H30M"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void getTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11Z");
		assertDurationEquals(new DayTimeDurationValue("P0D"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void withoutTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		assertEquals("2002-03-07", v1.withoutTimezone().toString());
	}

    @Test
	public void withoutTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		assertEquals("2002-03-07", v1.withoutTimezone().toString());
	}

    @Test
	public void adjustedToTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07-05:00", v2.getStringValue());
		assertEquals("2002-03-07-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07-05:00", v2.getStringValue());
		assertEquals("2002-03-07-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07-10:00", v2.getStringValue());
		assertEquals("2002-03-07-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-06-10:00", v2.getStringValue());
		assertEquals("2002-03-06-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone5() throws XPathException {
        AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("-PT15H"));
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone6() throws XPathException {
        AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT14H01M"));
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone7() throws XPathException {
        AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT8H4S"));
	}

    @Test
	public void adjustedToTimezone8() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT14H"));
		assertEquals("2002-03-07+14:00", v2.getStringValue());
		assertEquals("2002-03-07+14:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void compare1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25+07:00");
		assertEquals(1, v1.compareTo(null, v2));
		assertEquals(-1, v2.compareTo(null, v1));
	}

    @Test
	public void compare2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25-12:00"), v2 = new DateValue("2004-12-26+12:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}

    @Test
	public void compare3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertEquals(-1, v1.compareTo(null, v2));
		assertEquals(+1, v2.compareTo(null, v1));
	}

    @Test
	public void compare4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25+07:00");
		assertEquals(1, v1.compareTo(null, v2));
		assertEquals(-1, v2.compareTo(null, v1));
	}

    @Test
	public void compare5() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25-12:00"), v2 = new DateValue("2004-12-26+12:00");
		assertTrue(v1.compareTo(null, Comparison.EQ, v2));
		assertFalse(v1.compareTo(null, Comparison.NEQ, v2));
		assertFalse(v1.compareTo(null, Comparison.GT, v2));
		assertFalse(v1.compareTo(null, Comparison.LT, v2));
		assertTrue(v1.compareTo(null, Comparison.GTEQ, v2));
		assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
	}

    @Test
	public void compare7() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertFalse(v1.compareTo(null, Comparison.EQ, v2));
		assertTrue(v1.compareTo(null, Comparison.NEQ, v2));
		assertFalse(v1.compareTo(null, Comparison.GT, v2));
		assertTrue(v1.compareTo(null, Comparison.LT, v2));
		assertFalse(v1.compareTo(null, Comparison.GTEQ, v2));
		assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
	}

    @Test
	public void minMax1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertDateEquals(v2, v1.max(null, v2));
		assertDateEquals(v2, v2.max(null, v1));
		assertDateEquals(v1, v1.min(null, v2));
		assertDateEquals(v1, v2.min(null, v1));
	}

    @Test
	public void plus1() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("2001-12-30");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus2() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30Z");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("2001-12-30Z");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus3() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2004-10-30");
		DurationValue d = new DayTimeDurationValue("P2DT2H30M0S");
		AbstractDateTimeValue r = new DateValue("2004-11-01");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus4() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2004-10-30Z");
		DurationValue d = new DayTimeDurationValue("P2DT2H30M0S");
		AbstractDateTimeValue r = new DateValue("2004-11-01Z");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void minus1() throws XPathException {
		AbstractDateTimeValue t1 = new DateValue("2000-10-30");
		AbstractDateTimeValue t2 = new DateValue("1999-11-28");
		DurationValue d = new DayTimeDurationValue("P337D");
		assertDurationEquals(d, t1.minus(t2));
	}

    @Test
	public void minus2() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("1999-08-30");
		assertDateEquals(r, t.minus(d));
	}

    @Test
	public void minus3() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-02-29Z");
		DurationValue d = new YearMonthDurationValue("P1Y");
		AbstractDateTimeValue r = new DateValue("1999-02-28Z");
		assertDateEquals(r, t.minus(d));
	}

    @Test
	public void minus4() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-31-05:00");
		DurationValue d = new YearMonthDurationValue("P1Y1M");
		AbstractDateTimeValue r = new DateValue("1999-09-30-05:00");
		assertDateEquals(r, t.minus(d));
	}

    @Test
	public void minus5() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		AbstractDateTimeValue r = new DateValue("2000-10-26");
		assertDateEquals(r, t.minus(d));
	}

    @Test
	public void minus6() throws XPathException {
		DateValue d1 = new DateValue("2005-10-11"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 2*24*60*60, r.getValue(), 0);
		assertEquals(2, r.getPart(DurationValue.DAY));
	}

    @Test
	public void minus7() throws XPathException {
		DateValue d1 = new DateValue("2005-10-10"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 1*24*60*60, r.getValue(), 0);
		assertEquals(1, r.getPart(DurationValue.DAY));
	}

    @Test
	public void equal() throws XPathException {
        assertEquals(new DateValue("2010-06-01+05:00"), new DateValue("2010-06-01+05:00"));
	}
}
