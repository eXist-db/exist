/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.value.*;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class CastExpressionTest {

  private static DBBroker broker;
  private static XQueryContext context;

  @ClassRule
  public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

  @BeforeClass
  public static void setUp() throws DatabaseConfigurationException, EXistException, XPathException {
    final BrokerPool pool = existEmbeddedServer.getBrokerPool();

    broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
    context = new XQueryContext(pool);
  }

  @AfterClass
  public static void tearDown() throws EXistException {
    if (broker != null) {
      broker.close();
    }
    broker = null;
    context = null;
  }

  @Test
  public void numericCast() throws XPathException {
    CastExpression numericCastExpr;

    // Test decimal: xs:numeric(1.0)
    numericCastExpr = buildCast(new DecimalValue(1.0), Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);

    // Test decimal: xs:numeric(-1.0)
    numericCastExpr = buildCast(new DecimalValue(-1.0), Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);

    // Test numeric around decimal: xs:numeric(xs:numeric(1.0))
    numericCastExpr = buildCast(new DecimalValue(1.0), Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);

    // Test numeric around decimal: xs:numeric(xs:numeric(-1.0))
    numericCastExpr = buildCast(new DecimalValue(-1.0), Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.DECIMAL, DecimalValue.class, numericCastExpr);

    // Test double: xs:numeric(1.0e2)
    numericCastExpr = buildCast(new DoubleValue("1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test double: xs:numeric(-1.0e2)
    numericCastExpr = buildCast(new DoubleValue("-1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test numeric around double: xs:numeric(1.0e2)
    numericCastExpr = buildCast(new DoubleValue("1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test numeric around double: xs:numeric(-1.0e2)
    numericCastExpr = buildCast(new DoubleValue("-1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test float: xs:numeric(xs:float(1.0))
    CastExpression floatCastExpr;
    floatCastExpr = buildCast(new StringValue("1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);

    // Test float: xs:numeric(xs:float(-1.0))
    floatCastExpr = buildCast(new StringValue("-1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);

    // Test numeric around float: xs:numeric(xs:float(1.0))
    floatCastExpr = buildCast(new StringValue("1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);

    // Test numeric around float: xs:numeric(xs:float(-1.0))
    floatCastExpr = buildCast(new StringValue("-1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);

    // Test integer: xs:numeric(1)
    numericCastExpr = buildCast(new IntegerValue(1), Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);

    // Test integer: xs:numeric(-1)
    numericCastExpr = buildCast(new IntegerValue(-1), Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);

    // Test numeric around integer: xs:numeric(1)
    numericCastExpr = buildCast(new IntegerValue(1), Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);

    // Test numeric around integer: xs:numeric(-1)
    numericCastExpr = buildCast(new IntegerValue(-1), Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.INTEGER, IntegerValue.class, numericCastExpr);

    // Test nonPositiveInteger: xs:numeric(xs:nonPositiveInteger(-2))
    CastExpression nonPositiveIntegerCastExpr;
    nonPositiveIntegerCastExpr = buildCast(new StringValue("-2"), Type.NON_POSITIVE_INTEGER);
    assertCast(Type.NON_POSITIVE_INTEGER, IntegerValue.class, nonPositiveIntegerCastExpr);
    numericCastExpr = buildCast(nonPositiveIntegerCastExpr, Type.NUMERIC);
    assertCast(Type.NON_POSITIVE_INTEGER, IntegerValue.class, numericCastExpr);

    // Test numeric around nonPositiveInteger: xs:numeric(xs:nonPositiveInteger(-2))
    nonPositiveIntegerCastExpr = buildCast(new StringValue("-2"), Type.NON_POSITIVE_INTEGER);
    assertCast(Type.NON_POSITIVE_INTEGER, IntegerValue.class, nonPositiveIntegerCastExpr);
    numericCastExpr = buildCast(nonPositiveIntegerCastExpr, Type.NUMERIC);
    assertCast(Type.NON_POSITIVE_INTEGER, IntegerValue.class, numericCastExpr);
    numericCastExpr = buildCast(numericCastExpr, Type.NUMERIC);
    assertCast(Type.NON_POSITIVE_INTEGER, IntegerValue.class, numericCastExpr);

    // Test string (of decimal): xs:numeric('1.0')
    numericCastExpr = buildCast(new StringValue("1.0"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test string (of decimal): xs:numeric('-1.0')
    numericCastExpr = buildCast(new StringValue("-1.0"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test string (of integer): xs:numeric('1')
    numericCastExpr = buildCast(new StringValue("1"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test string (of integer): xs:numeric('-1')
    numericCastExpr = buildCast(new StringValue("-1"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test string (of double): xs:numeric('1.0e2')
    numericCastExpr = buildCast(new StringValue("1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);

    // Test string (of double): xs:numeric('-1.0e2')
    numericCastExpr = buildCast(new StringValue("-1.0e2"), Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);
  }

  @Test
  public void floatCast() throws XPathException {
    // Test float: xs:float(1.0)
    CastExpression floatCastExpr;
    floatCastExpr = buildCast(new StringValue("1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);

    // Test float: xs:float(-1.0)
    floatCastExpr = buildCast(new StringValue("-1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);

    // Test float: xs:float(xs:numeric(xs:float(1.0)))
    CastExpression numericCastExpr;
    floatCastExpr = buildCast(new StringValue("1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);
    floatCastExpr = buildCast(numericCastExpr, Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);

    // Test float: xs:float(xs:numeric(xs:float(-1.0)))
    floatCastExpr = buildCast(new StringValue("-1.0"), Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
    numericCastExpr = buildCast(floatCastExpr, Type.NUMERIC);
    assertCast(Type.FLOAT, FloatValue.class, numericCastExpr);
    floatCastExpr = buildCast(numericCastExpr, Type.FLOAT);
    assertCast(Type.FLOAT, FloatValue.class, floatCastExpr);
  }

  @Test
  public void doubleCast() throws XPathException {
    // Test double: xs:double(1.0)
    CastExpression doubleCastExpr;
    doubleCastExpr = buildCast(new DoubleValue("1.0e2"), Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);

    // Test double: xs:double(-1.0e2)
    doubleCastExpr = buildCast(new DoubleValue("-1.0e2"), Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);

    // Test double: xs:double(xs:numeric(xs:double(1.0e2)))
    CastExpression numericCastExpr;
    doubleCastExpr = buildCast(new DoubleValue("-1.0e2"), Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);
    numericCastExpr = buildCast(doubleCastExpr, Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);
    doubleCastExpr = buildCast(numericCastExpr, Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);

    // Test float: xs:float(xs:numeric(xs:float(-1.0e2)))
    doubleCastExpr = buildCast(new DoubleValue("-1.0e2"), Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);
    numericCastExpr = buildCast(doubleCastExpr, Type.NUMERIC);
    assertCast(Type.DOUBLE, DoubleValue.class, numericCastExpr);
    doubleCastExpr = buildCast(numericCastExpr, Type.DOUBLE);
    assertCast(Type.DOUBLE, DoubleValue.class, doubleCastExpr);
  }

  private CastExpression buildCast(final AtomicValue inputValue, final int castXdmType) {
    final LiteralValue literalValue = new LiteralValue(context, inputValue);
    final PathExpr pathExpr = new PathExpr(context);
    pathExpr.add(literalValue);
    return new CastExpression(context, pathExpr, castXdmType, Cardinality.EXACTLY_ONE);
  }

  private CastExpression buildCast(final Expression inputExpr, final int castXdmType) {
    return new CastExpression(context, inputExpr, castXdmType, Cardinality.EXACTLY_ONE);
  }

  private void assertCast(final int expectedXdmType, final Class<? extends AtomicValue> expectedInstanceType, final CastExpression actualCastExpression) throws XPathException {
    final Sequence resultSeq = actualCastExpression.eval(null, null);

    assertEquals(1, resultSeq.getItemCount());
    final Item result = resultSeq.itemAt(0);
    assertEquals(expectedXdmType, result.getType());
    assertEquals(expectedInstanceType, result.getClass());
  }
}
