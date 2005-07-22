package org.exist.xquery.test;

import org.exist.storage.*;
import org.exist.util.Configuration;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import junit.framework.TestCase;

public class OpNumericTest extends TestCase {
	
	private XQueryContext context;
	private DayTimeDurationValue dtDuration;
	private YearMonthDurationValue ymDuration;
	private DateTimeValue dateTime;
	private DateValue date;
	private TimeValue time;
	private IntegerValue integer;
	private DecimalValue decimal;
	
	protected void setUp() throws Exception {
		super.setUp();
		BrokerPool.configure(1, 1, new Configuration("conf.xml"));
		context = new XQueryContext(BrokerPool.getInstance().get());
		
		dtDuration = new DayTimeDurationValue("P1D");
		ymDuration = new YearMonthDurationValue("P1Y");
		dateTime = new DateTimeValue("2005-06-02T16:28:00Z");
		date = new DateValue("2005-06-02");
		time = new TimeValue("16:28:00Z");
		integer = new IntegerValue(2);
		decimal = new DecimalValue("1.5");
	}
	
	private OpNumeric buildOp(int op, AtomicValue a, AtomicValue b) {
		return new OpNumeric(
				context,
				new LiteralValue(context, a),
				new LiteralValue(context, b),
				op);
	}
	
	private void assertOp(String result, int op, AtomicValue a, AtomicValue b) throws XPathException {
		Sequence r = buildOp(op, a, b).eval(Sequence.EMPTY_SEQUENCE);
		assertEquals(result, r.itemAt(0).getStringValue());
	}

	public void test_idiv1() throws XPathException {
		assertOp("2", Constants.IDIV, new IntegerValue(3), new DecimalValue("1.5"));
	}
	public void test_idiv2() throws XPathException {
		assertOp("2", Constants.IDIV, new IntegerValue(4), new IntegerValue(2));
	}
	public void test_idiv3() throws XPathException {
		assertOp("2", Constants.IDIV, new IntegerValue(5), new IntegerValue(2));
	}
	public void test_idivReturnType1() {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, integer, integer).returnsType());
	}
	public void test_idivReturnType2() throws XPathException {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, integer, decimal).returnsType());
	}
	public void test_idivReturnType3() throws XPathException {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, decimal, integer).returnsType());
	}
	public void test_divReturnType1() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, integer, integer).returnsType());
	}
	public void test_divReturnType2() throws XPathException {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, integer, decimal).returnsType());
	}
	public void test_divReturnType3() throws XPathException {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, decimal, integer).returnsType());
	}
	public void test_divReturnType4() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.DIV, dtDuration, integer).returnsType());
	}
	public void test_divReturnType5() throws XPathException {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.DIV, ymDuration, integer).returnsType());
	}
	public void test_divReturnType6() throws XPathException {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, dtDuration, dtDuration).returnsType());
	}
	public void test_divReturnType7() throws XPathException {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, ymDuration, ymDuration).returnsType());
	}
	public void test_multReturnType1() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MULT, dtDuration, integer).returnsType());
	}
	public void test_multReturnType2() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MULT, integer, dtDuration).returnsType());
	}
	public void test_multReturnType3() throws XPathException {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MULT, ymDuration, integer).returnsType());
	}
	public void test_multReturnType4() throws XPathException {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MULT, integer, ymDuration).returnsType());
	}
	public void test_plusReturnType1() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.PLUS, dtDuration, dtDuration).returnsType());
	}
	public void test_plusReturnType2() throws XPathException {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.PLUS, ymDuration, ymDuration).returnsType());
	}
	public void test_plusReturnType3() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, date, dtDuration).returnsType());
	}
	public void test_plusReturnType4() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dateTime, dtDuration).returnsType());
	}
	public void test_plusReturnType5() throws XPathException {
		assertEquals(Type.TIME, buildOp(Constants.PLUS, time, dtDuration).returnsType());
	}
	public void test_plusReturnType6() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, dtDuration, date).returnsType());
	}
	public void test_plusReturnType7() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dtDuration, dateTime).returnsType());
	}
	public void test_plusReturnType8() throws XPathException {
		assertEquals(Type.TIME, buildOp(Constants.PLUS, dtDuration, time).returnsType());
	}
	public void test_plusReturnType9() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, date, ymDuration).returnsType());
	}
	public void test_plusReturnType10() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dateTime, ymDuration).returnsType());
	}
	public void test_plusReturnType11() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, ymDuration, date).returnsType());
	}
	public void test_plusReturnType12() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, ymDuration, dateTime).returnsType());
	}
	public void test_minusReturnType1() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, dtDuration, dtDuration).returnsType());
	}
	public void test_minusReturnType2() throws XPathException {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MINUS, ymDuration, ymDuration).returnsType());
	}
	public void test_minusReturnType3() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, dateTime, dateTime).returnsType());
	}
	public void test_minusReturnType4() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, date, date).returnsType());
	}
	public void test_minusReturnType5() throws XPathException {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, time, time).returnsType());
	}
	public void test_minusReturnType6() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.MINUS, dateTime, ymDuration).returnsType());
	}
	public void test_minusReturnType7() throws XPathException {
		assertEquals(Type.DATE_TIME, buildOp(Constants.MINUS, dateTime, dtDuration).returnsType());
	}
	public void test_minusReturnType8() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.MINUS, date, ymDuration).returnsType());
	}
	public void test_minusReturnType9() throws XPathException {
		assertEquals(Type.DATE, buildOp(Constants.MINUS, date, dtDuration).returnsType());
	}
	public void test_minusReturnType10() throws XPathException {
		assertEquals(Type.TIME, buildOp(Constants.MINUS, time, dtDuration).returnsType());
	}
}
