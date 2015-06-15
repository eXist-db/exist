package org.exist.xquery;

import org.exist.EXistException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.*;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.*;
import org.junit.*;

import static org.junit.Assert.assertEquals;

public class OpNumericTest {

    private static DBBroker broker;
	private static XQueryContext context;
	private static DayTimeDurationValue dtDuration;
	private static YearMonthDurationValue ymDuration;
	private static DateTimeValue dateTime;
	private static DateValue date;
	private static TimeValue time;
	private static IntegerValue integer;
	private static DecimalValue decimal;

	@BeforeClass
	public static void setUp() throws DatabaseConfigurationException, EXistException, XPathException {
		Configuration config = new Configuration();
		BrokerPool.configure(1, 5, config);

		BrokerPool pool = BrokerPool.getInstance();

		broker = pool.get(pool.getSecurityManager().getSystemSubject());
		context = new XQueryContext(broker.getBrokerPool(), AccessContext.TEST);

		dtDuration = new DayTimeDurationValue("P1D");
		ymDuration = new YearMonthDurationValue("P1Y");
		dateTime = new DateTimeValue("2005-06-02T16:28:00Z");
		date = new DateValue("2005-06-02");
		time = new TimeValue("16:28:00Z");
		integer = new IntegerValue(2);
		decimal = new DecimalValue("1.5");
	}

    @AfterClass
    public static void tearDown() throws EXistException {
        BrokerPool.getInstance().release(broker);
        BrokerPool.stopAll(false);
        broker = null;
        context = null;
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

    @Test
	public void idiv1() throws XPathException {
        assertOp("2", Constants.IDIV, new IntegerValue(3), new DecimalValue("1.5"));
	}

    @Test
	public void idiv2() throws XPathException {
		assertOp("2", Constants.IDIV, new IntegerValue(4), new IntegerValue(2));
	}

    @Test
	public void idiv3() throws XPathException {
		assertOp("2", Constants.IDIV, new IntegerValue(5), new IntegerValue(2));
	}

    @Test
	public void idivReturnType1() {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, integer, integer).returnsType());
	}

    @Test
	public void idivReturnType2() {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, integer, decimal).returnsType());
	}

    @Test
	public void idivReturnType3() {
		assertEquals(Type.INTEGER, buildOp(Constants.IDIV, decimal, integer).returnsType());
	}

    @Test
	public void divReturnType1() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, integer, integer).returnsType());
	}

    @Test
	public void divReturnType2() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, integer, decimal).returnsType());
	}

    @Test
	public void divReturnType3() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, decimal, integer).returnsType());
	}

    @Test
	public void divReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.DIV, dtDuration, integer).returnsType());
	}

    @Test
	public void divReturnType5() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.DIV, ymDuration, integer).returnsType());
	}

    @Test
	public void divReturnType6() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void divReturnType7() {
		assertEquals(Type.DECIMAL, buildOp(Constants.DIV, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void multReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MULT, dtDuration, integer).returnsType());
	}

    @Test
	public void multReturnType2() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MULT, integer, dtDuration).returnsType());
	}

    @Test
	public void multReturnType3() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MULT, ymDuration, integer).returnsType());
	}

    @Test
	public void multReturnType4() {
        assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MULT, integer, ymDuration).returnsType());
    }

    @Test
	public void plusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.PLUS, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.PLUS, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType3() {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, date, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType4() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dateTime, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType5() {
		assertEquals(Type.TIME, buildOp(Constants.PLUS, time, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType6() {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, dtDuration, date).returnsType());
	}

    @Test
	public void plusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dtDuration, dateTime).returnsType());
	}

    @Test
	public void plusReturnType8() {
		assertEquals(Type.TIME, buildOp(Constants.PLUS, dtDuration, time).returnsType());
	}

    @Test
	public void plusReturnType9() {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, date, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType10() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, dateTime, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType11() {
		assertEquals(Type.DATE, buildOp(Constants.PLUS, ymDuration, date).returnsType());
	}

    @Test
	public void plusReturnType12() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.PLUS, ymDuration, dateTime).returnsType());
	}

    @Test
	public void minusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(Constants.MINUS, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType3() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, dateTime, dateTime).returnsType());
	}

    @Test
	public void minusReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, date, date).returnsType());
	}

    @Test
	public void minusReturnType5() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(Constants.MINUS, time, time).returnsType());
	}

    @Test
	public void minusReturnType6() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.MINUS, dateTime, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(Constants.MINUS, dateTime, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType8() {
		assertEquals(Type.DATE, buildOp(Constants.MINUS, date, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType9() {
		assertEquals(Type.DATE, buildOp(Constants.MINUS, date, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType10() {
		assertEquals(Type.TIME, buildOp(Constants.MINUS, time, dtDuration).returnsType());
	}
}
