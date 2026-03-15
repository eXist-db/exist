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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DecimalValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import org.exist.xquery.functions.map.MapType;

import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implements XQuery 4.0 fn:divide-decimals.
 *
 * fn:divide-decimals($value, $divisor, $precision?) returns a record with
 * quotient and remainder fields.
 */
public class FnDivideDecimals extends BasicFunction {

    public static final FunctionSignature[] FN_DIVIDE_DECIMALS = {
            new FunctionSignature(
                    new QName("divide-decimals", Function.BUILTIN_FUNCTION_NS),
                    "Divides one decimal by another to specified precision, returning quotient and remainder.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DECIMAL, Cardinality.EXACTLY_ONE, "The dividend"),
                            new FunctionParameterSequenceType("divisor", Type.DECIMAL, Cardinality.EXACTLY_ONE, "The divisor"),
                            new FunctionParameterSequenceType("precision", Type.INTEGER, Cardinality.ZERO_OR_ONE, "Decimal precision (default: 0)")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "record with quotient and remainder")),
            new FunctionSignature(
                    new QName("divide-decimals", Function.BUILTIN_FUNCTION_NS),
                    "Divides one decimal by another returning integer quotient and remainder.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.DECIMAL, Cardinality.EXACTLY_ONE, "The dividend"),
                            new FunctionParameterSequenceType("divisor", Type.DECIMAL, Cardinality.EXACTLY_ONE, "The divisor")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "record with quotient and remainder"))
    };

    public FnDivideDecimals(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final BigDecimal value = toBigDecimal(args[0].itemAt(0));
        final BigDecimal divisor = toBigDecimal(args[1].itemAt(0));

        if (divisor.compareTo(BigDecimal.ZERO) == 0) {
            throw new XPathException(this, ErrorCodes.FOAR0001, "Division by zero");
        }

        int precision = 0;
        if (args.length > 2 && !args[2].isEmpty()) {
            precision = (int) ((IntegerValue) args[2].itemAt(0)).getLong();
        }

        // Quotient: truncate toward zero to given precision
        final BigDecimal quotient = value.divide(divisor, precision, RoundingMode.DOWN);
        final BigDecimal remainder = value.subtract(quotient.multiply(divisor));

        // Build result record (map)
        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "quotient"), new DecimalValue(this, quotient));
        result.add(new StringValue(this, "remainder"), new DecimalValue(this, remainder));

        return result;
    }

    private BigDecimal toBigDecimal(final Item item) throws XPathException {
        final AtomicValue av = item.atomize();
        if (av instanceof DecimalValue) {
            return ((DecimalValue) av).getValue();
        }
        // xs:integer is a subtype of xs:decimal
        if (av instanceof IntegerValue) {
            return new BigDecimal(((IntegerValue) av).getLong());
        }
        // Fallback: convert to decimal
        return ((DecimalValue) av.convertTo(Type.DECIMAL)).getValue();
    }
}
