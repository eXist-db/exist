package org.exist.xquery.value.test;

import junit.framework.TestCase;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;

/**
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class DurationOpTest extends TestCase {
	public void testDateDiff1() throws XPathException {
		DateValue d1 = new DateValue("2005-10-11"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 2*24*60*60, r.getValue(), 0);
		assertEquals(2, r.getPart(DurationValue.DAY));
	}
	public void testDateDiff2() throws XPathException {
		DateValue d1 = new DateValue("2005-10-10"), d2 = new DateValue("2005-10-09");
		DayTimeDurationValue r = (DayTimeDurationValue) d1.minus(d2);
		assertEquals((double) 1*24*60*60, r.getValue(), 0);
		assertEquals(1, r.getPart(DurationValue.DAY));
	}
}
