package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 *	note: some of these tests rely on local timezone override to -05:00, done in super.setUp()
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DateTest extends AbstractTimeRelatedTestCase {
	
	public void testCreate1() {
		try {
			new DateValue("2005-10-11T10:00:00Z");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate2() {
		try {
			new DateValue("10:00:00Z");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testStringFormat1() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11Z");
		assertEquals("2005-10-11Z", v.getStringValue());
	}
	public void testStringFormat2() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11-01:00");
		assertEquals("2005-10-11-01:00", v.getStringValue());
		assertEquals("2005-10-11-01:00", v.getTrimmedCalendar().toXMLFormat());
	}
	public void testStringFormat3() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11-00:00");
		assertEquals("2005-10-11Z", v.getStringValue());
	}
	public void testStringFormat4() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11");
		assertEquals("2005-10-11", v.getStringValue());
	}
	public void testGetPart1() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11Z");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
	}
	public void testGetPart2() throws XPathException {
		AbstractDateTimeValue v = new DateValue("2005-10-11");
		assertEquals(2005, v.getPart(AbstractDateTimeValue.YEAR));
		assertEquals(10, v.getPart(AbstractDateTimeValue.MONTH));
		assertEquals(11, v.getPart(AbstractDateTimeValue.DAY));
	}
	public void testConvert1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(v1, v2);
	}
	public void testConvert2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		AtomicValue v2 = v1.convertTo(Type.DATE);
		assertDateEquals(v1, v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}
	public void testConvert3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:00");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(new DateTimeValue("2005-10-11T00:00:00+05:00"), v2);
	}
	public void testConvert4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		AtomicValue v2 = v1.convertTo(Type.DATE_TIME);
		assertDateEquals(new DateTimeValue("2005-10-11T00:00:00"), v2);
		assertEquals(Sequence.EMPTY_SEQUENCE, ((AbstractDateTimeValue) v2).getTimezone());
	}
	public void testGetType() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		assertEquals(Type.DATE, v1.getType());
	}
	public void testGetTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11");
		assertEquals(Sequence.EMPTY_SEQUENCE, v1.getTimezone());
	}
	public void testGetTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11+05:30");
		assertDurationEquals(new DayTimeDurationValue("PT5H30M"), (AtomicValue) v1.getTimezone());
	}
	public void testGetTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11-05:30");
		assertDurationEquals(new DayTimeDurationValue("-PT5H30M"), (AtomicValue) v1.getTimezone());
	}
	public void testGetTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2005-10-11Z");
		assertDurationEquals(new DayTimeDurationValue("P0D"), (AtomicValue) v1.getTimezone());
	}
	public void testWithoutTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		assertEquals("2002-03-07", v1.withoutTimezone().toString());
	}
	public void testWithoutTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		assertEquals("2002-03-07", v1.withoutTimezone().toString());
	}
	public void testAdjustedToTimezone1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07-05:00", v2.getStringValue());
		assertEquals("2002-03-07-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(null);
		assertEquals("2002-03-07-05:00", v2.getStringValue());
		assertEquals("2002-03-07-05:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-07-10:00", v2.getStringValue());
		assertEquals("2002-03-07-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07-07:00");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("-PT10H"));
		assertEquals("2002-03-06-10:00", v2.getStringValue());
		assertEquals("2002-03-06-10:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testAdjustedToTimezone5() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("-PT15H"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone6() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("PT14H01M"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone7() throws XPathException {
		try {
			AbstractDateTimeValue v1 = new DateValue("2002-03-07+01:00");
			v1.adjustedToTimezone(new DayTimeDurationValue("PT8H4S"));
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testAdjustedToTimezone8() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2002-03-07");
		AbstractDateTimeValue v2 = v1.adjustedToTimezone(new DayTimeDurationValue("PT14H"));
		assertEquals("2002-03-07+14:00", v2.getStringValue());
		assertEquals("2002-03-07+14:00", v2.getTrimmedCalendar().toXMLFormat());
	}
	public void testCompare1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25+07:00");
		assertEquals(1, v1.compareTo(null, v2));
		assertEquals(-1, v2.compareTo(null, v1));
	}
	public void testCompare2() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25-12:00"), v2 = new DateValue("2004-12-26+12:00");
		assertEquals(0, v1.compareTo(null, v2));
		assertEquals(0, v2.compareTo(null, v1));
	}
	public void testCompare3() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertEquals(-1, v1.compareTo(null, v2));
		assertEquals(+1, v2.compareTo(null, v1));
	}
	public void testCompare4() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25+07:00");
		assertEquals(1, v1.compareTo(null, v2));
		assertEquals(-1, v2.compareTo(null, v1));
	}
	public void testCompare5() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25-12:00"), v2 = new DateValue("2004-12-26+12:00");
		assertTrue(v1.compareTo(null, Constants.EQ, v2));
		assertFalse(v1.compareTo(null, Constants.NEQ, v2));
		assertFalse(v1.compareTo(null, Constants.GT, v2));
		assertFalse(v1.compareTo(null, Constants.LT, v2));
		assertTrue(v1.compareTo(null, Constants.GTEQ, v2));
		assertTrue(v1.compareTo(null, Constants.LTEQ, v2));
	}
	public void testCompare7() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertFalse(v1.compareTo(null, Constants.EQ, v2));
		assertTrue(v1.compareTo(null, Constants.NEQ, v2));
		assertFalse(v1.compareTo(null, Constants.GT, v2));
		assertTrue(v1.compareTo(null, Constants.LT, v2));
		assertFalse(v1.compareTo(null, Constants.GTEQ, v2));
		assertTrue(v1.compareTo(null, Constants.LTEQ, v2));
	}
	public void testMinMax1() throws XPathException {
		AbstractDateTimeValue v1 = new DateValue("2004-12-25Z"), v2 = new DateValue("2004-12-25-05:00");
		assertDateEquals(v2, v1.max(null, v2));
		assertDateEquals(v2, v2.max(null, v1));
		assertDateEquals(v1, v1.min(null, v2));
		assertDateEquals(v1, v2.min(null, v1));
	}
	public void testPlus1() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("2001-12-30");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus2() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30Z");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("2001-12-30Z");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus3() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2004-10-30");
		DurationValue d = new DayTimeDurationValue("P2DT2H30M0S");
		AbstractDateTimeValue r = new DateValue("2004-11-01");
		assertDateEquals(r, t.plus(d));
	}
	public void testPlus4() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2004-10-30Z");
		DurationValue d = new DayTimeDurationValue("P2DT2H30M0S");
		AbstractDateTimeValue r = new DateValue("2004-11-01Z");
		assertDateEquals(r, t.plus(d));
	}
	public void testMinus1() throws XPathException {
		AbstractDateTimeValue t1 = new DateValue("2000-10-30");
		AbstractDateTimeValue t2 = new DateValue("1999-11-28");
		DurationValue d = new DayTimeDurationValue("P337D");
		assertDurationEquals(d, t1.minus(t2));
	}
	public void testMinus2() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new YearMonthDurationValue("P1Y2M");
		AbstractDateTimeValue r = new DateValue("1999-08-30");
		assertDateEquals(r, t.minus(d));
	}
	public void testMinus3() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-02-29Z");
		DurationValue d = new YearMonthDurationValue("P1Y");
		AbstractDateTimeValue r = new DateValue("1999-02-28Z");
		assertDateEquals(r, t.minus(d));
	}
	public void testMinus4() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-31-05:00");
		DurationValue d = new YearMonthDurationValue("P1Y1M");
		AbstractDateTimeValue r = new DateValue("1999-09-30-05:00");
		assertDateEquals(r, t.minus(d));
	}
	public void testMinus5() throws XPathException {
		AbstractDateTimeValue t = new DateValue("2000-10-30");
		DurationValue d = new DayTimeDurationValue("P3DT1H15M");
		AbstractDateTimeValue r = new DateValue("2000-10-26");
		assertDateEquals(r, t.minus(d));
	}
	public void testMinus6() throws XPathException {
		DateValue d1 = new DateValue("2005-10-11"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 2*24*60*60, r.getValue(), 0);
		assertEquals(2, r.getPart(DurationValue.DAY));
	}
	public void testMinus7() throws XPathException {
		DateValue d1 = new DateValue("2005-10-10"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 1*24*60*60, r.getValue(), 0);
		assertEquals(1, r.getPart(DurationValue.DAY));
	}
	public void testEqual() {
		try {
			assertEquals(new DateValue("2010-06-01+05:00"), new DateValue("2010-06-01+05:00"));
		} catch (XPathException e) {
			fail();
		}
	}
}
