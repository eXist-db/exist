/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.EXistException;
import org.exist.storage.*;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.Constants.ArithmeticOperator;
import org.exist.xquery.value.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import java.util.Optional;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

	@RegisterExtension
	public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @BeforeAll
    static void setUp() throws DatabaseConfigurationException, EXistException, XPathException {
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

    @AfterAll
    static void tearDown() throws EXistException {
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
        Sequence r = buildOp(op, a, b).eval(Sequence.EMPTY_SEQUENCE, null);
        assertEquals(result, r.itemAt(0).getStringValue());
	}

    @org.junit.jupiter.api.Test
    void idiv1() throws XPathException {
        assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(3), new DecimalValue("1.5"));
	}

    @org.junit.jupiter.api.Test
    void idiv2() throws XPathException {
		assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(4), new IntegerValue(2));
	}

    @org.junit.jupiter.api.Test
    void idiv3() throws XPathException {
		assertOp("2", ArithmeticOperator.DIVISION_INTEGER, new IntegerValue(5), new IntegerValue(2));
	}

    @org.junit.jupiter.api.Test
    void idivReturnType1() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, integer, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void idivReturnType2() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, integer, decimal).returnsType());
	}

    @org.junit.jupiter.api.Test
    void idivReturnType3() {
		assertEquals(Type.INTEGER, buildOp(ArithmeticOperator.DIVISION_INTEGER, decimal, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType1() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, integer, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType2() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, integer, decimal).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType3() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, decimal, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.DIVISION, dtDuration, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType5() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.DIVISION, ymDuration, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType6() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, dtDuration, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void divReturnType7() {
		assertEquals(Type.DECIMAL, buildOp(ArithmeticOperator.DIVISION, ymDuration, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void multReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, dtDuration, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void multReturnType2() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, integer, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void multReturnType3() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, ymDuration, integer).returnsType());
	}

    @org.junit.jupiter.api.Test
    void multReturnType4() {
        assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.MULTIPLICATION, integer, ymDuration).returnsType());
    }

    @org.junit.jupiter.api.Test
    void plusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.ADDITION, dtDuration, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.ADDITION, ymDuration, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType3() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, date, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType4() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dateTime, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType5() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.ADDITION, time, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType6() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, dtDuration, date).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dtDuration, dateTime).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType8() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.ADDITION, dtDuration, time).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType9() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, date, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType10() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, dateTime, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType11() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.ADDITION, ymDuration, date).returnsType());
	}

    @org.junit.jupiter.api.Test
    void plusReturnType12() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.ADDITION, ymDuration, dateTime).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType1() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, dtDuration, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType2() {
		assertEquals(Type.YEAR_MONTH_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, ymDuration, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType3() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, dateTime).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType4() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, date, date).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType5() {
		assertEquals(Type.DAY_TIME_DURATION, buildOp(ArithmeticOperator.SUBTRACTION, time, time).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType6() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType7() {
		assertEquals(Type.DATE_TIME, buildOp(ArithmeticOperator.SUBTRACTION, dateTime, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType8() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.SUBTRACTION, date, ymDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType9() {
		assertEquals(Type.DATE, buildOp(ArithmeticOperator.SUBTRACTION, date, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void minusReturnType10() {
		assertEquals(Type.TIME, buildOp(ArithmeticOperator.SUBTRACTION, time, dtDuration).returnsType());
	}

    @org.junit.jupiter.api.Test
    void derivesFrom() {
		// AT is ET
		assertTrue(OpNumeric.derivesFrom(Type.DECIMAL, Type.DECIMAL));
		assertTrue(OpNumeric.derivesFrom(Type.INTEGER, Type.INTEGER));
		assertTrue(OpNumeric.derivesFrom(Type.DOUBLE, Type.DOUBLE));
		assertTrue(OpNumeric.derivesFrom(Type.FLOAT, Type.FLOAT));
		assertTrue(OpNumeric.derivesFrom(Type.NUMERIC, Type.NUMERIC));

		// ET is the base type of AT
		assertTrue(OpNumeric.derivesFrom(Type.INTEGER, Type.DECIMAL));
		assertTrue(OpNumeric.derivesFrom(Type.INTEGER, Type.ANY_ATOMIC_TYPE));
		assertTrue(OpNumeric.derivesFrom(Type.INTEGER, Type.ITEM));
		assertFalse(OpNumeric.derivesFrom(Type.DECIMAL, Type.INTEGER));
		assertFalse(OpNumeric.derivesFrom(Type.ANY_ATOMIC_TYPE, Type.INTEGER));
		assertFalse(OpNumeric.derivesFrom(Type.ITEM, Type.INTEGER));

		// ET is a pure union type of which AT is a member type
		assertTrue(OpNumeric.derivesFrom(Type.DECIMAL, Type.NUMERIC));
		assertTrue(OpNumeric.derivesFrom(Type.INTEGER, Type.NUMERIC));
		assertTrue(OpNumeric.derivesFrom(Type.DOUBLE, Type.NUMERIC));
		assertTrue(OpNumeric.derivesFrom(Type.FLOAT, Type.NUMERIC));
		assertFalse(OpNumeric.derivesFrom(Type.NUMERIC, Type.DECIMAL));
		assertFalse(OpNumeric.derivesFrom(Type.NUMERIC, Type.INTEGER));
		assertFalse(OpNumeric.derivesFrom(Type.NUMERIC, Type.DOUBLE));
		assertFalse(OpNumeric.derivesFrom(Type.NUMERIC, Type.FLOAT));

		// There is a type MT such that derives-from(AT, MT) and derives-from(MT, ET)
//		assertTrue(OpNumeric.derivesFrom(Type.UNSIGNED_BYTE, Type.UNSIGNED_INT));

//		findAtMtMtEt();
	}

//	private void findAtMtMtEt() {
//		for (final int actual : Type.typeNames.keySet()) {
//			for (final int expected : Type.typeNames.keySet()) {
//				if (actual != expected && test(actual, expected)) {
//
//					if (!Type.subTypeOf(actual, expected)) {
//
//						System.out.println("ACTUAL=" + Type.getTypeName(actual) + ", EXPECTED=" + Type.getTypeName(expected));
//					}
//				}
//			}
//		}
//	}
//
//	private boolean test(final int actualType, final int expectedType) {
//		// iterate through AT's super-types
//		for (int t = actualType; t != Type.ITEM && t != Type.ANY_TYPE && t != Type.EMPTY_SEQUENCE; t = Type.getSuperType(t)) {
//			// is the super-type of AT a subtype of ET
//			if (t != expectedType && Type.subTypeOf(t, expectedType)) {
//				return true;
//			}
//		}
//
//		return false;
//	}
}
