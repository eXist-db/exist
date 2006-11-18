package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public class YearMonthDurationTest extends AbstractTimeRelatedTestCase {

	public void testCreate1() {
		try {
			new YearMonthDurationValue("P1D");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate2() {
		try {
			new YearMonthDurationValue("PT1H");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate3() {
		try {
			new YearMonthDurationValue("PT1M");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate4() {
		try {
			new YearMonthDurationValue("PT1S");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testStringFormat1() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals("P1Y2M", dv.getStringValue());
	}
	public void testStringFormat2() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("P1Y25M");
		assertEquals("P3Y1M", dv.getStringValue());
	}
	public void testStringFormat3() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("P0Y");
		assertEquals("P0M", dv.getStringValue());
	}
	public void testConvert1() throws XPathException {
		YearMonthDurationValue ymdv = new YearMonthDurationValue("P1Y2M");
		DurationValue dv = (DurationValue) ymdv.convertTo(Type.DURATION);
		assertEquals("P1Y2M", dv.getStringValue());
	}
	public void testConvert2() throws XPathException {
		YearMonthDurationValue ymdv = new YearMonthDurationValue("P1Y2M");
		assertEquals("PT0S", ymdv.convertTo(Type.DAY_TIME_DURATION).getStringValue());
	}
	public void testGetPart1() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals(1, dv.getPart(DurationValue.YEAR));
		assertEquals(2, dv.getPart(DurationValue.MONTH));
		assertEquals(0, dv.getPart(DurationValue.DAY));
		assertEquals(0, dv.getPart(DurationValue.HOUR));
		assertEquals(0, dv.getPart(DurationValue.MINUTE));
		assertEquals(0, dv.getSeconds(), 0);
	}
	public void testGetPart2() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("-P1Y2M");
		assertEquals(-1, dv.getPart(DurationValue.YEAR));
		assertEquals(-2, dv.getPart(DurationValue.MONTH));
		assertEquals(0, dv.getPart(DurationValue.DAY));
		assertEquals(0, dv.getPart(DurationValue.HOUR));
		assertEquals(0, dv.getPart(DurationValue.MINUTE));
		assertEquals(0, dv.getSeconds(), 0);
	}
	public void testGetType() throws XPathException {
		DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals(Type.YEAR_MONTH_DURATION, dv.getType());
	}
	public void testCompare1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P1Y2M"), dv2 = new YearMonthDurationValue("P1Y3M");
		assertEquals(-1, dv1.compareTo(null, dv2));
		assertEquals(+1, dv2.compareTo(null, dv1));
	}
	public void testCompare2() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P1Y2M"), dv2 = new YearMonthDurationValue("P1Y2M");
		assertEquals(0, dv1.compareTo(null, dv2));
		assertEquals(0, dv2.compareTo(null, dv1));
	}
	public void testCompare3() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P1Y2M"), dv2 = new YearMonthDurationValue("P1Y3M");
		assertFalse(dv1.compareTo(null, Constants.EQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.GT, dv2));
		assertTrue(dv1.compareTo(null, Constants.LT, dv2));
		assertFalse(dv1.compareTo(null, Constants.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.LTEQ, dv2));
	}
	public void testCompare4() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P1Y2M"), dv2 = new YearMonthDurationValue("P1Y2M");
		assertTrue(dv1.compareTo(null, Constants.EQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.GT, dv2));
		assertFalse(dv1.compareTo(null, Constants.LT, dv2));
		assertTrue(dv1.compareTo(null, Constants.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.LTEQ, dv2));
	}
	public void testMinMax1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P1Y2M"), dv2 = new YearMonthDurationValue("P1Y3M");
		assertDurationEquals(dv2, dv1.max(null, dv2));
		assertDurationEquals(dv2, dv2.max(null, dv1));
		assertDurationEquals(dv1, dv1.min(null, dv2));
		assertDurationEquals(dv1, dv2.min(null, dv1));
	}
	public void testPlus1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		DurationValue dv2 = new YearMonthDurationValue("P3Y3M");
		DurationValue dv3 = new YearMonthDurationValue("P6Y2M");
		assertDurationEquals(dv3, dv1.plus(dv2));
		assertDurationEquals(dv3, dv2.plus(dv1));
	}
	public void testMinus1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		DurationValue dv2 = new YearMonthDurationValue("P3Y3M");
		DurationValue dv3 = new YearMonthDurationValue("-P4M");
		assertDurationEquals(dv3, dv1.minus(dv2));
	}
	public void testMult1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		DecimalValue f = new DecimalValue("2.3");
		DurationValue dv2 = new YearMonthDurationValue("P6Y9M");
		assertDurationEquals(dv2, dv1.mult(f));
		assertDurationEquals(dv2, f.mult(dv1));
	}
	public void testDiv1() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		DecimalValue f = new DecimalValue("1.5");
		DurationValue dv2 = new YearMonthDurationValue("P1Y11M");
		assertDurationEquals(dv2, dv1.div(f));
	}
	public void testDiv2() throws XPathException {
		DurationValue dv1 = new YearMonthDurationValue("P3Y4M");
		DurationValue dv2 = new YearMonthDurationValue("-P1Y4M");
		assertEquals(-2.5, ((Double) dv1.div(dv2).toJavaObject(Double.class)).doubleValue(), 0);
	}
}
