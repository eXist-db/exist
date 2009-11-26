package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

public class DayTimeDurationTest extends AbstractTimeRelatedTestCase {

	public void testCreate1() {
		try {
			new DayTimeDurationValue("P1Y4M");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate2() {
		try {
			new DayTimeDurationValue("P1Y");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCreate3() {
		try {
			new DayTimeDurationValue("P4M");
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testStringFormat1() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("P3DT1H2M3S");
		assertEquals("P3DT1H2M3S", dv.getStringValue());
	}
	public void testStringFormat2() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("P1DT25H65M66.5S");
		assertEquals("P2DT2H6M6.5S", new DurationValue(dv.getCanonicalDuration()).getStringValue());
	}
	public void testStringFormat3() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("P0DT0H");
		assertEquals("PT0S", dv.getStringValue());
	}
	public void testStringFormat4() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("PT5H0M0S");
		assertEquals("PT5H", dv.getStringValue());
	}
	public void testConvert1() throws XPathException {
		DayTimeDurationValue dtdv = new DayTimeDurationValue("P3DT1H2M3S");
		DurationValue dv = (DurationValue) dtdv.convertTo(Type.DURATION);
		assertEquals("P3DT1H2M3S", dv.getStringValue());
	}
	public void testConvert2() throws XPathException {
		DayTimeDurationValue dtdv = new DayTimeDurationValue("P3DT1H2M3S");
		assertEquals("P0M", dtdv.convertTo(Type.YEAR_MONTH_DURATION).getStringValue());
	}
	public void testGetPart1() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("P3DT4H5M6S");
		assertEquals(0, dv.getPart(DurationValue.YEAR));
		assertEquals(0, dv.getPart(DurationValue.MONTH));
		assertEquals(3, dv.getPart(DurationValue.DAY));
		assertEquals(4, dv.getPart(DurationValue.HOUR));
		assertEquals(5, dv.getPart(DurationValue.MINUTE));
		assertEquals(6, dv.getSeconds(), 0);
	}
	public void testGetPart2() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("-P3DT4H5M6S");
		assertEquals(0, dv.getPart(DurationValue.YEAR));
		assertEquals(0, dv.getPart(DurationValue.MONTH));
		assertEquals(-3, dv.getPart(DurationValue.DAY));
		assertEquals(-4, dv.getPart(DurationValue.HOUR));
		assertEquals(-5, dv.getPart(DurationValue.MINUTE));
		assertEquals(-6, dv.getSeconds(), 0);
	}
	public void testGetValue1() throws XPathException {
		DayTimeDurationValue dv = new DayTimeDurationValue("P1DT30S");
		assertEquals(1.0 * 24 * 60 * 60 + 30.0, dv.getValue(), 0.0);
	}
	public void testGetValue2() throws XPathException {
		DayTimeDurationValue dv = new DayTimeDurationValue("P1D");
		assertEquals(1.0 * 24 * 60 * 60, dv.getValue(), 0.0);
	}
	public void testGetType() throws XPathException {
		DurationValue dv = new DayTimeDurationValue("P3DT4H5M6S");
		assertEquals(Type.DAY_TIME_DURATION, dv.getType());
	}
	public void testCompare1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S"), dv2 = new DayTimeDurationValue("P1DT2H3M5S");
		assertEquals(-1, dv1.compareTo(null, dv2));
		assertEquals(+1, dv2.compareTo(null, dv1));
	}
	public void testCompare2() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S"), dv2 = new DayTimeDurationValue("P1DT2H3M4S");
		assertEquals(0, dv1.compareTo(null, dv2));
		assertEquals(0, dv2.compareTo(null, dv1));
	}
	public void testCompare3() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S"), dv2 = new DayTimeDurationValue("P1DT2H3M5S");
		assertFalse(dv1.compareTo(null, Constants.EQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.GT, dv2));
		assertTrue(dv1.compareTo(null, Constants.LT, dv2));
		assertFalse(dv1.compareTo(null, Constants.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.LTEQ, dv2));
	}
	public void testCompare4() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S"), dv2 = new DayTimeDurationValue("P1DT2H3M4S");
		assertTrue(dv1.compareTo(null, Constants.EQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Constants.GT, dv2));
		assertFalse(dv1.compareTo(null, Constants.LT, dv2));
		assertTrue(dv1.compareTo(null, Constants.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Constants.LTEQ, dv2));
	}
	public void testCompare5() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("PT2H"), dv2 = new DayTimeDurationValue("PT2H0M");
		assertEquals(0, dv1.compareTo(null, dv2));
		assertEquals(0, dv2.compareTo(null, dv1));
	}
	public void testMinMax1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S"), dv2 = new DayTimeDurationValue("P1DT2H3M5S");
		assertDurationEquals(dv2, dv1.max(null, dv2));
		assertDurationEquals(dv2, dv2.max(null, dv1));
		assertDurationEquals(dv1, dv1.min(null, dv2));
		assertDurationEquals(dv1, dv2.min(null, dv1));
	}
	public void testPlus1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P2DT12H5M");
		DurationValue dv2 = new DayTimeDurationValue("P5DT12H");
		DurationValue dv3 = new DayTimeDurationValue("P8DT5M");
		assertDurationEquals(dv3, dv1.plus(dv2));
		assertDurationEquals(dv3, dv2.plus(dv1));
	}
	public void testMinus1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P2DT12H");
		DurationValue dv2 = new DayTimeDurationValue("P1DT10H30M");
		DurationValue dv3 = new DayTimeDurationValue("P1DT1H30M");
		assertDurationEquals(dv3, dv1.minus(dv2));
	}
	public void testMult1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("PT2H10M");
		DecimalValue f = new DecimalValue("2.1");
		DurationValue dv2 = new DayTimeDurationValue("PT4H33M");
		assertDurationEquals(dv2, dv1.mult(f));
		assertDurationEquals(dv2, f.mult(dv1));
	}
	public void testDiv1() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H30M10.5S");
		DecimalValue f = new DecimalValue("1.5");
		DurationValue dv2 = new DayTimeDurationValue("PT17H40M7S");
		assertDurationEquals(dv2, dv1.div(f));
	}
	public void testDiv2() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P2DT53M11S");
		DurationValue dv2 = new DayTimeDurationValue("P1DT10H");
		assertEquals(1.4378349, ((Double) dv1.div(dv2).toJavaObject(Double.class)).doubleValue(), 0.0000001);
	}
}
