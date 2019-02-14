package org.exist.xquery.value;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.xquery.Constants;
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
public class DateTimeTest extends AbstractTimeRelatedTestCase {

	@Test(expected = XPathException.class)
	public void create1() throws XPathException {
        new DateTimeValue("2005-10-11");
	}

    @Test(expected = XPathException.class)
	public void create2() throws XPathException {
        new DateTimeValue("10:00:00Z");
	}

    @Test
	public void stringFormat1() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00Z");
		assertEquals("2005-10-11T10:00:00Z", v.getStringValue());
	}

    @Test
	public void stringFormat2() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00-01:00");
		assertEquals("2005-10-11T10:00:00-01:00", v.getStringValue());
		assertEquals("2005-10-11T10:00:00-01:00", v.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void stringFormat3() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T24:00:00");
		assertEquals("2005-10-12T00:00:00", v.getStringValue());
	}

    @Test
	public void stringFormat4() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00-00:00");
		assertEquals("2005-10-11T10:00:00Z", v.getStringValue());
	}

    @Test
	public void stringFormat5() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.5");
		assertEquals("2005-10-11T10:00:00.5", v.getStringValue());
	}

    @Test
	public void stringFormat6() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.50");
		assertEquals("2005-10-11T10:00:00.5", v.getStringValue());
	}

    @Test
	public void stringFormat7() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.0");
		assertEquals("2005-10-11T10:00:00", v.getStringValue());
	}

    @Test
	public void stringFormat8() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00");
		assertEquals("2005-10-11T10:00:00", v.getStringValue());
	}

    @Test
	public void getPart1() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:05:02.6Z");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
		assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
		assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
		assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
		assertEquals(600, v.getPart(AbstractDateTimeValue.MILLISECOND));
	}

    @Test
	public void getPart2() throws XPathException {
		final AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
		assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
		assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
		assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
	}

    @Test
	public void convert1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(v1, v2);
	}

    @Test
	public void convert2() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		final AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(v1, v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}

    @Test
	public void convert3() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		final AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(new DateValue("2005-10-11+05:00"), v2);
	}

    @Test
	public void convert4() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		final AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(new DateValue("2005-10-11"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}

    @Test
	public void convert5() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		final AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02+05:00"), v2);
	}

    @Test
	public void convert6() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		final AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}

    @Test
	public void convert7() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02.123456");
		final AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02.123456"), v2);
	}

    @Test
	public void getType() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(Type.DATE_TIME, v1.getType());
	}

    @Test
	public void getTimezone1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(Sequence.EMPTY_SEQUENCE, v1.getTimezone());
	}

    @Test
	public void getTimezone2() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:30");
		assertDurationEquals(new DayTimeDurationValue("PT5H30M"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void getTimezone3() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02-05:30");
		assertDurationEquals(new DayTimeDurationValue("-PT5H30M"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void getTimezone4() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02Z");
		assertDurationEquals(new DayTimeDurationValue("P0D"), (AtomicValue) v1.getTimezone());
	}

    @Test
	public void withoutTimezone1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		assertEquals("2002-03-07T10:00:00", v1.withoutTimezone().toString());
	}

    @Test
	public void withoutTimezone2() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		assertEquals("2002-03-07T10:00:00", v1.withoutTimezone().toString());
	}

    @Test
	public void adjustedToTimezone1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07T10:00:00-05:00", v2.getStringValue());
		assertEquals("2002-03-07T10:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone2() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07T12:00:00-05:00", v2.getStringValue());
		assertEquals("2002-03-07T12:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone3() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07T10:00:00-10:00", v2.getStringValue());
		assertEquals("2002-03-07T10:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone4() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07T07:00:00-10:00", v2.getStringValue());
		assertEquals("2002-03-07T07:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone5() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT10H"));
		assertEquals("2002-03-08T03:00:00+10:00", v2.getStringValue());
		assertEquals("2002-03-08T03:00:00+10:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void adjustedToTimezone6() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT8H"));
		assertEquals("2002-03-06T15:00:00-08:00", v2.getStringValue());
		assertEquals("2002-03-06T15:00:00-08:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone7() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("-PT15H"));
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone8() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT14H01M"));
	}

    @Test(expected = XPathException.class)
	public void adjustedToTimezone9() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
        v1.adjustedToTimezone(new DayTimeDurationValue("PT8H4S"));
	}

    @Test
	public void adjustedToTimezone10() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00");
		final AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT14H"));
		assertEquals("2002-03-07T00:00:00+14:00", v2.getStringValue());
		assertEquals("2002-03-07T00:00:00+14:00", v2.getTrimmedCalendar().toXMLFormat());
	}

    @Test
	public void compare1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00-01:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T17:00:00+04:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}

    @Test
	public void compare2() throws XPathException {
		final AbstractDateTimeValue  v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T23:00:00+06:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}

    @Test
	public void compare3() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertEquals(-1, v1.compareTo(null, v2));
		assertEquals(+1, v2.compareTo(null, v1));
	}

    @Test
	public void compare4() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T12:00:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}

    @Test
	public void compare5() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T23:00:00-04:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-03T02:00:00-01:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}

    @Test
	public void compare6() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T12:00:00");
		assertTrue(v1.compareTo(null, Comparison.EQ, v2));
		assertFalse(v1.compareTo(null, Comparison.NEQ, v2));
		assertFalse(v1.compareTo(null, Comparison.GT, v2));
		assertFalse(v1.compareTo(null, Comparison.LT, v2));
		assertTrue(v1.compareTo(null, Comparison.GTEQ, v2));
		assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
	}

    @Test
	public void compare7() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertFalse(v1.compareTo(null, Comparison.EQ, v2));
		assertTrue(v1.compareTo(null, Comparison.NEQ, v2));
		assertFalse(v1.compareTo(null, Comparison.GT, v2));
		assertTrue(v1.compareTo(null, Comparison.LT, v2));
		assertFalse(v1.compareTo(null, Comparison.GTEQ, v2));
		assertTrue(v1.compareTo(null, Comparison.LTEQ, v2));
	}

    @Test
	public void compare8() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2006-09-14T04:15:16.559+04:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2006-10-14T04:15:16.559+04:00");
		assertEquals(Constants.INFERIOR, v1.compareTo(v2));
	}

    @Test
	public void minMax1() throws XPathException {
		final AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00");
		final AbstractDateTimeValue v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertDateEquals(v2, v1.max(null, v2));
		assertDateEquals(v2, v2.max(null, v1));
		assertDateEquals(v1, v1.min(null, v2));
		assertDateEquals(v1, v2.min(null, v1));
	}

    @Test
	public void plus1() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		final DurationValue d = new YearMonthDurationValue("P1Y2M");
		final AbstractDateTimeValue r = new DateTimeValue("2001-12-30T11:12:00");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus2() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00Z");
		final DurationValue d = new YearMonthDurationValue("P1Y2M");
		final AbstractDateTimeValue r = new DateTimeValue("2001-12-30T11:12:00Z");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus3() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		final DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		final AbstractDateTimeValue r = new DateTimeValue("2000-11-02T12:27:00");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void plus4() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00Z");
		final DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		final AbstractDateTimeValue r = new DateTimeValue("2000-11-02T12:27:00Z");
		assertDateEquals(r, t.plus(d));
	}

    @Test
	public void minus1() throws XPathException {
		final AbstractDateTimeValue t1 = new DateTimeValue("2000-10-30T06:12:00");
		final AbstractDateTimeValue t2 = new DateTimeValue("1999-11-28T09:00:00Z");
		final DurationValue d = new DayTimeDurationValue("P337DT2H12M");
		assertDurationEquals(d, t1.minus(t2));
	}

    @Test
	public void minus2() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		final DurationValue d = new YearMonthDurationValue("P1Y2M");
		final AbstractDateTimeValue r = new DateTimeValue("1999-08-30T11:12:00");
		assertDateEquals(r, t.minus(d));
	}

    @Test
	public void minus3() throws XPathException {
		final AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		final DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		final AbstractDateTimeValue r = new DateTimeValue("2000-10-27T09:57:00");
		assertDateEquals(r, t.minus(d));
	}
}
