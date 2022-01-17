package org.exist.xquery;

import org.exist.EXistException;
import org.exist.storage.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.Constants.ArithmeticOperator;
import org.exist.xquery.value.*;
import org.junit.*;

import java.util.Optional;

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

	@ClassRule
	public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	@BeforeClass
	public static void setUp() throws DatabaseConfigurationException, EXistException, XPathException {
		final BrokerPool pool = existEmbeddedServer.getBrokerPool();

		broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
		context = new XQueryContext(pool);

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
        if(broker != null) {
			broker.close();
		}
        broker = null;
        context = null;
    }
    
	private OpNumeric buildOp(ArithmeticOperator op, AtomicValue a, AtomicValue b) {
		return new OpNumeric(
				context,
				new LiteralValue(context, a),
				new LiteralValue(context, b),
				op);
	}
	
	private void assertOp(String result, ArithmeticOperator op, AtomicValue a, AtomicValue b) throws XPathException {
        Sequence r = buildOp(op, a, b).eval(Sequence.EMPTY_SEQUENCE);
        assertEquals(result, r.itemAt(0).getStringValue());
	}

    @Test
	public void idiv1() throws XPathException {
        assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(3), new DecimalValue("1.5"));
	}

    @Test
	public void idiv2() throws XPathException {
		assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(4), new IntegerValue(2));
	}

    @Test
	public void idiv3() throws XPathException {
		assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(5), new IntegerValue(2));
	}

    @Test
	public void idivReturnType1() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, integer, integer).returnsType());
	}

    @Test
	public void idivReturnType2() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, integer, decimal).returnsType());
	}

    @Test
	public void idivReturnType3() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, decimal, integer).returnsType());
	}

    @Test
	public void divReturnType1() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, integer, integer).returnsType());
	}

    @Test
	public void divReturnType2() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, integer, decimal).returnsType());
	}

    @Test
	public void divReturnType3() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, decimal, integer).returnsType());
	}

    @Test
	public void divReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.DIVISION, dtDuration, integer).returnsType());
	}

    @Test
	public void divReturnType5() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.DIVISION, ymDuration, integer).returnsType());
	}

    @Test
	public void divReturnType6() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void divReturnType7() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void multReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, dtDuration, integer).returnsType());
	}

    @Test
	public void multReturnType2() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, integer, dtDuration).returnsType());
	}

    @Test
	public void multReturnType3() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, ymDuration, integer).returnsType());
	}

    @Test
	public void multReturnType4() {
        assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, integer, ymDuration).returnsType());
    }

    @Test
	public void plusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.ADDITION, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.ADDITION, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType3() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, date, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType4() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dateTime, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType5() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.ADDITION, time, dtDuration).returnsType());
	}

    @Test
	public void plusReturnType6() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, dtDuration, date).returnsType());
	}

    @Test
	public void plusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dtDuration, dateTime).returnsType());
	}

    @Test
	public void plusReturnType8() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.ADDITION, dtDuration, time).returnsType());
	}

    @Test
	public void plusReturnType9() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, date, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType10() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dateTime, ymDuration).returnsType());
	}

    @Test
	public void plusReturnType11() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, ymDuration, date).returnsType());
	}

    @Test
	public void plusReturnType12() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, ymDuration, dateTime).returnsType());
	}

    @Test
	public void minusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, dtDuration, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, ymDuration, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType3() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, dateTime).returnsType());
	}

    @Test
	public void minusReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, date, date).returnsType());
	}

    @Test
	public void minusReturnType5() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, time, time).returnsType());
	}

    @Test
	public void minusReturnType6() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType8() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.SUBTRACTION, date, ymDuration).returnsType());
	}

    @Test
	public void minusReturnType9() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.SUBTRACTION, date, dtDuration).returnsType());
	}

    @Test
	public void minusReturnType10() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.SUBTRACTION, time, dtDuration).returnsType());
	}
}
