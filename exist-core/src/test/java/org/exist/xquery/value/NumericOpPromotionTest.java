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
package org.exist.xquery.value;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.util.Arrays;

import org.exist.xquery.XPathException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class NumericOpPromotionTest {

    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"decimal", new DecimalValue(VALUE)},
                {"double", new DoubleValue(VALUE)},
                {"float", new FloatValue((float) VALUE)}
        });
    }
    public String typeName;
    public ComputableValue operand;

	private static final double VALUE = 1.5;
	private static final IntegerValue ZERO = new IntegerValue(0), ONE = new IntegerValue(1);

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void integerDiv(String typeName, ComputableValue operand) throws XPathException {
        initNumericOpPromotionTest(typeName, operand);
		assertDoubleValue(VALUE, operand.div(ONE));
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void integerMult(String typeName, ComputableValue operand) throws XPathException {
        initNumericOpPromotionTest(typeName, operand);
		assertDoubleValue(VALUE, operand.mult(ONE));
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void integerPlus(String typeName, ComputableValue operand) throws XPathException {
        initNumericOpPromotionTest(typeName, operand);
		assertDoubleValue(VALUE, operand.plus(ZERO));
	}

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void integerMinus(String typeName, ComputableValue operand) throws XPathException {
        initNumericOpPromotionTest(typeName, operand);
		assertDoubleValue(VALUE, operand.minus(ZERO));
	}

    private void assertDoubleValue(double target, ComputableValue result) throws XPathException {
        assertEquals(target, (result.toJavaObject(Double.class)).doubleValue(), 0);
    }

    public void initNumericOpPromotionTest(String typeName, ComputableValue operand) {
        this.typeName = typeName;
        this.operand = operand;
    }
}
