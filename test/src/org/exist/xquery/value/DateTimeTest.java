package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.junit.Ignore;

/**
 *	note: some of these tests rely on local timezone override to -05:00, done in super.setUp()
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateTimeTest extends AbstractTimeRelatedTestCase {
	
	public void testCreate1() {
		try {
			new DateTimeValue("2005-10-11");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate2() {
		try {
			new DateTimeValue("10:00:00Z");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testStringFormat1() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00Z");
		assertEquals("2005-10-11T10:00:00Z", v.getStringValue());
	}
	public void testStringFormat2() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00-01:00");
		assertEquals("2005-10-11T10:00:00-01:00", v.getStringValue());
		assertEquals("2005-10-11T10:00:00-01:00", v.getTrimmedCalendar().toXMLFormat());
	}
	// TODO: reinstate when Java's parsing is fixed to handle 24:00:00
	// see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6238849
	public void bugtestStringFormat3() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T24:00:00");
		assertEquals("2005-10-12T00:00:00", v.getStringValue());
	}
	public void testStringFormat4() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00-00:00");
		assertEquals("2005-10-11T10:00:00Z", v.getStringValue());
	}
	public void testStringFormat5() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.5");
		assertEquals("2005-10-11T10:00:00.5", v.getStringValue());
	}
	public void testStringFormat6() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.50");
		assertEquals("2005-10-11T10:00:00.5", v.getStringValue());
	}
	public void testStringFormat7() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00.0");
		assertEquals("2005-10-11T10:00:00", v.getStringValue());
	}
	public void testStringFormat8() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:00:00");
		assertEquals("2005-10-11T10:00:00", v.getStringValue());
	}
	public void testGetPart1() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:05:02.6Z");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
		assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
		assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
		assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
		assertEquals(600, v.getPart(AbstractDateTimeValue.MILLISECOND));
	}
	public void testGetPart2() throws XPathException {
		AbstractDateTimeValue v = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
		assertEquals(10, v.getPart(AbstractDateTimeValue.HOUR));
		assertEquals(5, v.getPart(AbstractDateTimeValue.MINUTE));
		assertEquals(2, v.getPart(AbstractDateTimeValue.SECOND));
	}
	public void testConvert1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(v1, v2);
	}
	public void testConvert2() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(v1, v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}
	public void testConvert3() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(new DateValue("2005-10-11+05:00"), v2);
	}
	public void testConvert4() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(new DateValue("2005-10-11"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}
	public void testConvert5() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:00");
		AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02+05:00"), v2);
	}
	public void testConvert6() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}
	public void testConvert7() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02.123456");
		AtomicValue v2 = v1.convertTo(Type.TIME);
		assertDateEquals(new TimeValue("10:05:02.123456"), v2);
	}
	public void testGetType() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(Type.DATE_TIME, v1.getType());
	}
	public void testGetTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02");
		assertEquals(Sequence.EMPTY_SEQUENCE, v1.getTimezone());
	}
	public void testGetTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02+05:30");
		assertDurationEquals(new DayTimeDurationValue("PT5H30M"), (AtomicValue) v1.getTimezone());
	}
	public void testGetTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02-05:30");
		assertDurationEquals(new DayTimeDurationValue("-PT5H30M"), (AtomicValue) v1.getTimezone());
	}
	public void testGetTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2005-10-11T10:05:02Z");
		assertDurationEquals(new DayTimeDurationValue("P0D"), (AtomicValue) v1.getTimezone());
	}
	public void testWithoutTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		assertEquals("2002-03-07T10:00:00", v1.withoutTimezone().toString());
	}
	public void testWithoutTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		assertEquals("2002-03-07T10:00:00", v1.withoutTimezone().toString());
	}
	public void testAdjustedToTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07T10:00:00-05:00", v2.getStringValue());
		assertEquals("2002-03-07T10:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07T12:00:00-05:00", v2.getStringValue());
		assertEquals("2002-03-07T12:00:00-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07T10:00:00-10:00", v2.getStringValue());
		assertEquals("2002-03-07T10:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07T07:00:00-10:00", v2.getStringValue());
		assertEquals("2002-03-07T07:00:00-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone5() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T10:00:00-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT10H"));
		assertEquals("2002-03-08T03:00:00+10:00", v2.getStringValue());
		assertEquals("2002-03-08T03:00:00+10:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone6() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT8H"));
		assertEquals("2002-03-06T15:00:00-08:00", v2.getStringValue());
		assertEquals("2002-03-06T15:00:00-08:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone7() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("-PT15H"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone8() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("PT14H01M"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone9() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("PT8H4S"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone10() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-03-07T00:00:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT14H"));
		assertEquals("2002-03-07T00:00:00+14:00", v2.getStringValue());
		assertEquals("2002-03-07T00:00:00+14:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testCompare1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00-01:00"), v2 = new DateTimeValue("2002-04-02T17:00:00+04:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}
	public void testCompare2() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T23:00:00+06:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}
	public void testCompare3() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertEquals(-1, v1.compareTo(null, v2));
		assertEquals(+1, v2.compareTo(null, v1));
	}
	public void testCompare4() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T12:00:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}
	public void testCompare5() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T23:00:00-04:00"), v2 = new DateTimeValue("2002-04-03T02:00:00-01:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}
	public void testCompare6() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T12:00:00");
		assertTrue(v1.compareTo(null, Constants.EQ, v2));
		assertFalse(v1.compareTo(null, Constants.NEQ, v2));
		assertFalse(v1.compareTo(null, Constants.GT, v2));
		assertFalse(v1.compareTo(null, Constants.LT, v2));
		assertTrue(v1.compareTo(null, Constants.GTEQ, v2));
		assertTrue(v1.compareTo(null, Constants.LTEQ, v2));
	}
	public void testCompare7() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertFalse(v1.compareTo(null, Constants.EQ, v2));
		assertTrue(v1.compareTo(null, Constants.NEQ, v2));
		assertFalse(v1.compareTo(null, Constants.GT, v2));
		assertTrue(v1.compareTo(null, Constants.LT, v2));
		assertFalse(v1.compareTo(null, Constants.GTEQ, v2));
		assertTrue(v1.compareTo(null, Constants.LTEQ, v2));
	}
	
	public void testCompare8() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2006-09-14T04:15:16.559+04:00"), v2 = new DateTimeValue("2006-10-14T04:15:16.559+04:00");
		assertEquals(Constants.INFERIOR, v1.compareTo(v2));
	}
	
	public void testMinMax1() throws XPathException {
		AbstractDateTimeValue v1 = new DateTimeValue("2002-04-02T12:00:00"), v2 = new DateTimeValue("2002-04-02T17:00:00");
		assertDateEquals(v2, v1.max(null, v2));
		assertDateEquals(v2, v2.max(null, v1));
		assertDateEquals(v1, v1.min(null, v2));
		assertDateEquals(v1, v2.min(null, v1));
	}
	public void testPlus1() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateTimeValue("2001-12-30T11:12:00");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus2() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00Z");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateTimeValue("2001-12-30T11:12:00Z");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus3() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		AbstractDateTimeValue r = new DateTimeValue("2000-11-02T12:27:00");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus4() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00Z");
		DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		AbstractDateTimeValue r = new DateTimeValue("2000-11-02T12:27:00Z");
		assertDateEquals(r, t.plus(d));
	}
	public void testMinus1() throws XPathException {
		AbstractDateTimeValue t1 = new DateTimeValue("2000-10-30T06:12:00");
		AbstractDateTimeValue t2 = new DateTimeValue("1999-11-28T09:00:00Z");
		DurationValue d = new DayTimeDurationValue("P337DT2H12M");
		assertDurationEquals(d, t1.minus(t2));
	}
	public void testMinus2() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateTimeValue("1999-08-30T11:12:00");
		assertDateEquals(r, t.minus(d));
	}
	public void testMinus3() throws XPathException {
		AbstractDateTimeValue t = new DateTimeValue("2000-10-30T11:12:00");
		DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		AbstractDateTimeValue r = new DateTimeValue("2000-10-27T09:57:00");
		assertDateEquals(r, t.minus(d));
	}
}
