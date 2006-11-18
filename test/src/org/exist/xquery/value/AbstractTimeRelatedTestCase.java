package org.exist.xquery.value;

import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

import junit.framework.TestCase;

public abstract class AbstractTimeRelatedTestCase extends TestCase {
	
	protected void setUp() throws Exception {
		super.setUp();
		TimeUtils.getInstance().overrideLocalTimezoneOffset(-5 * 60 * 60 * 1000);
	}
	
	protected void tearDown() throws Exception {
		super.tearDown();
		TimeUtils.getInstance().resetLocalTimezoneOffset();
	}

	protected void checkMinMaxFails(DurationValue a, DurationValue b) {
		try {
			a.max(null, b);
			fail(a + " max " + b + " succeeded");
		} catch (XPathException e) { /*expected*/ }
		try {
			a.min(null, b);
			fail(a + " min " + b + " succeeded");
		} catch (XPathException e) { /*expected*/ }
	}

	// type explicitly included in method name to avoid accidental (and confusing!) use of overloaded assertEquals methods
	protected void assertDurationEquals(DurationValue dv1, AtomicValue dv2) throws XPathException {
		if (!dv1.compareTo(null, Constants.EQ, dv2)) fail(dv1 + " != " + dv2);
	}
	
	// type explicitly included in method name to avoid accidental (and confusing!) use of overloaded assertEquals methods
	protected void assertDateEquals(AbstractDateTimeValue v1, AtomicValue v2) throws XPathException {
		if (!v1.compareTo(null, Constants.EQ, v2)) fail(v1 + " != " + v2);
	}
	
	protected DayTimeDurationValue getLocalTimezoneDuration() throws XPathException {
		return new DayTimeDurationValue(TimeUtils.getInstance().getLocalTimezoneOffsetMillis());
	}
	
	protected String getLocalTimezoneOffset() throws XPathException {
		int offset = (int) (TimeUtils.getInstance().getLocalTimezoneOffsetMillis() / 60000L);
		int hours = offset / 60, minutes = offset % 60;
		StringBuffer buf = new StringBuffer();
		buf.append(offset < 0 ? '-' : '+');
		if (hours < 10) buf.append('0');
		buf.append(hours);
		buf.append(':');
		if (minutes < 10) buf.append('0');
		buf.append(minutes);
		return buf.toString();
	}

}
