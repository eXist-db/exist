package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DurationTest extends AbstractTimeRelatedTestCase {
	public void testStringFormat1() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
		assertEquals("P1Y2M3DT1H2M3S", dv.getStringValue());
	}
	public void testStringFormat2() throws XPathException {
		DurationValue dv = new DurationValue("P1Y13M1DT25H65M66.5S");
		assertEquals("P2Y1M2DT2H6M6.5S", dv.getStringValue());
	}
	public void testStringFormat3() throws XPathException {
		DurationValue dv = new DurationValue("P0Y");
		assertEquals("PT0S", dv.getStringValue());
	}
	public void testNegate() throws XPathException {
		DurationValue dv = new DurationValue("P2D");
		dv = dv.negate();
		assertEquals("-P2D", dv.getStringValue());
		assertEquals(-2, dv.getPart(DurationValue.DAY));
	}
	public void testConvert1() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
		YearMonthDurationValue ymdv = (YearMonthDurationValue) dv.convertTo(Type.YEAR_MONTH_DURATION);
		assertEquals("P1Y2M", ymdv.getStringValue());
	}
	public void testConvert2() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT1H2M3S");
		DayTimeDurationValue dtdv = (DayTimeDurationValue) dv.convertTo(Type.DAY_TIME_DURATION);
		assertEquals("P3DT1H2M3S", dtdv.getStringValue());
	}
	public void testConvert3() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT1H2M3.5S");
		DayTimeDurationValue dtdv = (DayTimeDurationValue) dv.convertTo(Type.DAY_TIME_DURATION);
		assertEquals("P3DT1H2M3.5S", dtdv.getStringValue());
	}
	public void testGetPart1() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
		assertEquals(1, dv.getPart(DurationValue.YEAR));
		assertEquals(2, dv.getPart(DurationValue.MONTH));
		assertEquals(3, dv.getPart(DurationValue.DAY));
		assertEquals(4, dv.getPart(DurationValue.HOUR));
		assertEquals(5, dv.getPart(DurationValue.MINUTE));
		assertEquals(6, dv.getSeconds(), 0);
	}
	public void testGetPart2() throws XPathException {
		DurationValue dv = new DurationValue("-P1Y2M3DT4H5M6S");
		assertEquals(-1, dv.getPart(DurationValue.YEAR));
		assertEquals(-2, dv.getPart(DurationValue.MONTH));
		assertEquals(-3, dv.getPart(DurationValue.DAY));
		assertEquals(-4, dv.getPart(DurationValue.HOUR));
		assertEquals(-5, dv.getPart(DurationValue.MINUTE));
		assertEquals(-6, dv.getSeconds(), 0);
	}
	public void testGetType() throws XPathException {
		DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
		assertEquals(Type.DURATION, dv.getType());
	}
	
	public void testCompareSucceeds1() throws XPathException {
		try {
			DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
			//eq and ne comparison operators are allowed
			dv.compareTo(null, Constants.EQ, dv);			
		} catch (XPathException e) {
			fail();
		}
	}
	
	public void testCompareSucceeds2() throws XPathException {
		try {
			DurationValue dv1 = new DurationValue("P1Y2M3DT4H5M6S"), dv2 = new DayTimeDurationValue("P1D");
			assertFalse(dv1.compareTo(null, Constants.EQ, dv2));
			//Saxon returns false for :
			//xs:duration("P1Y2M3DT4H5M6S") eq xs:dayTimeDuration("P1D")
			//fail();
		} catch (XPathException e) {
			fail();
		}
	}
	public void testCompareSucceeds3() throws XPathException {
		try {
			DurationValue dv1 = new DurationValue("P1Y2M3DT4H5M6S"), dv2 = new YearMonthDurationValue("P1Y");
			assertFalse(dv1.compareTo(null, Constants.EQ, dv2));
			//Saxon returns true for :
			//xs:duration("P1Y2M3DT4H5M6S") ne xs:dayTimeDuration("P1D")
			//fail();
		} catch (XPathException e) {
			fail();
		}
	}

	public void testCompareFail1() throws XPathException {
		try {
			DurationValue dv = new DurationValue("P1Y2M3DT4H5M6S");
			dv.compareTo(null, Constants.LT, dv);
			fail();
		} catch (XPathException e) {
			// expected
		}
	}	

	public void testCompareFail5() throws XPathException {
		try {
			DurationValue dv1 = new YearMonthDurationValue("P1Y"), dv2 = new DayTimeDurationValue("P1D");
			dv1.compareTo(null, Constants.EQ, dv2);
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCompareFail6() throws XPathException {
		try {
			DurationValue dv2 = new YearMonthDurationValue("P1Y"), dv1 = new DayTimeDurationValue("P1D");
			dv1.compareTo(null, Constants.EQ, dv2);
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCompareFail7() throws XPathException {
		try {
			DurationValue dv2 = new DurationValue("P1Y2M3DT4H5M6S"), dv1 = new DayTimeDurationValue("P1D");
			dv1.compareTo(null, Constants.EQ, dv2);
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testCompareFail8() throws XPathException {
		try {
			DurationValue dv2 = new DurationValue("P1Y2M3DT4H5M6S"), dv1 = new YearMonthDurationValue("P1Y");
			dv1.compareTo(null, Constants.EQ, dv2);
			fail();
		} catch (XPathException e) {
			// expected
		}
	}
	public void testMinMaxFail() throws XPathException {
		DurationValue dv1 = new DayTimeDurationValue("P1DT2H3M4S");
		DurationValue dv2 = new YearMonthDurationValue("P1Y3M");
		DurationValue dv3 = new DurationValue("P1Y2M3DT4H5M6S");
		checkMinMaxFails(dv1, dv2);  checkMinMaxFails(dv2, dv1);
		checkMinMaxFails(dv1, dv3);  checkMinMaxFails(dv3, dv1);
		checkMinMaxFails(dv2, dv3);  checkMinMaxFails(dv3, dv2);
	}
}
