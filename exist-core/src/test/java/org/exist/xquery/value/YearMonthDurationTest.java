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
public class YearMonthDurationTest extends AbstractTimeRelatedTestCase {

	@Test(expected = XPathException.class)
	public void create1() throws XPathException {
		new YearMonthDurationValue("P1D");
	}

	@Test(expected = XPathException.class)
	public void create2() throws XPathException {
		new YearMonthDurationValue("PT1H");
	}

	@Test(expected = XPathException.class)
	public void create3() throws XPathException {
		new YearMonthDurationValue("PT1M");
	}

	@Test(expected = XPathException.class)
	public void create4() throws XPathException {
		new YearMonthDurationValue("PT1S");
	}

	@Test
	public void stringFormat1() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals("P1Y2M", dv.getStringValue());
	}

	@Test
	public void stringFormat2() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("P1Y25M");
		assertEquals("P3Y1M", dv.getStringValue());
	}

	@Test
	public void stringFormat3() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("P0Y");
		assertEquals("P0M", dv.getStringValue());
	}

	@Test
	public void convert1() throws XPathException {
		final YearMonthDurationValue ymdv = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv = (DurationValue) ymdv.convertTo(Type.DURATION);
		assertEquals("P1Y2M", dv.getStringValue());
	}

	@Test
	public void convert2() throws XPathException {
		final YearMonthDurationValue ymdv = new YearMonthDurationValue("P1Y2M");
		assertEquals("PT0S", ymdv.convertTo(Type.DAY_TIME_DURATION).getStringValue());
	}

	@Test
	public void getPart1() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals(1, dv.getPart(DurationValue.YEAR));
		assertEquals(2, dv.getPart(DurationValue.MONTH));
		assertEquals(0, dv.getPart(DurationValue.DAY));
		assertEquals(0, dv.getPart(DurationValue.HOUR));
		assertEquals(0, dv.getPart(DurationValue.MINUTE));
		assertEquals(0, dv.getSeconds(), 0);
	}

	@Test
	public void getPart2() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("-P1Y2M");
		assertEquals(-1, dv.getPart(DurationValue.YEAR));
		assertEquals(-2, dv.getPart(DurationValue.MONTH));
		assertEquals(0, dv.getPart(DurationValue.DAY));
		assertEquals(0, dv.getPart(DurationValue.HOUR));
		assertEquals(0, dv.getPart(DurationValue.MINUTE));
		assertEquals(0, dv.getSeconds(), 0);
	}

	@Test
	public void getType() throws XPathException {
		final DurationValue dv = new YearMonthDurationValue("P1Y2M");
		assertEquals(Type.YEAR_MONTH_DURATION, dv.getType());
	}

	@Test
	public void compare1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y3M");
		assertEquals(-1, dv1.compareTo(null, dv2));
		assertEquals(+1, dv2.compareTo(null, dv1));
	}

	@Test
	public void compare2() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y2M");
		assertEquals(0, dv1.compareTo(null, dv2));
		assertEquals(0, dv2.compareTo(null, dv1));
	}

	@Test
	public void compare3() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y3M");
		assertFalse(dv1.compareTo(null, Comparison.EQ, dv2));
		assertTrue(dv1.compareTo(null, Comparison.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Comparison.GT, dv2));
		assertTrue(dv1.compareTo(null, Comparison.LT, dv2));
		assertFalse(dv1.compareTo(null, Comparison.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Comparison.LTEQ, dv2));
	}

	@Test
	public void compare4() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y2M");
		assertTrue(dv1.compareTo(null, Comparison.EQ, dv2));
		assertFalse(dv1.compareTo(null, Comparison.NEQ, dv2));
		assertFalse(dv1.compareTo(null, Comparison.GT, dv2));
		assertFalse(dv1.compareTo(null, Comparison.LT, dv2));
		assertTrue(dv1.compareTo(null, Comparison.GTEQ, dv2));
		assertTrue(dv1.compareTo(null, Comparison.LTEQ, dv2));
	}

	@Test
	public void minMax1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P1Y2M");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y3M");
		assertDurationEquals(dv2, dv1.max(null, dv2));
		assertDurationEquals(dv2, dv2.max(null, dv1));
		assertDurationEquals(dv1, dv1.min(null, dv2));
		assertDurationEquals(dv1, dv2.min(null, dv1));
	}

	@Test
	public void plus1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		final DurationValue dv2 = new YearMonthDurationValue("P3Y3M");
		final DurationValue dv3 = new YearMonthDurationValue("P6Y2M");
		assertDurationEquals(dv3, dv1.plus(dv2));
		assertDurationEquals(dv3, dv2.plus(dv1));
	}

	@Test
	public void minus1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		final DurationValue dv2 = new YearMonthDurationValue("P3Y3M");
		final DurationValue dv3 = new YearMonthDurationValue("-P4M");
		assertDurationEquals(dv3, dv1.minus(dv2));
	}

	@Test
	public void mult1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		final DecimalValue f = new DecimalValue("2.3");
		final DurationValue dv2 = new YearMonthDurationValue("P6Y9M");
		assertDurationEquals(dv2, dv1.mult(f));
		assertDurationEquals(dv2, f.mult(dv1));
	}

	@Test
	public void div1() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P2Y11M");
		final DecimalValue f = new DecimalValue("1.5");
		final DurationValue dv2 = new YearMonthDurationValue("P1Y11M");
		assertDurationEquals(dv2, dv1.div(f));
	}

	@Test
	public void div2() throws XPathException {
		final DurationValue dv1 = new YearMonthDurationValue("P3Y4M");
		final DurationValue dv2 = new YearMonthDurationValue("-P1Y4M");
		assertEquals(-2.5, ((Double) dv1.div(dv2).toJavaObject(Double.class)).doubleValue(), 0);
	}
}
