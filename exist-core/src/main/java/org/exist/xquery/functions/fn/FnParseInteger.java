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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.math.BigInteger;

/**
 * Implements XQuery 4.0 fn:parse-integer.
 *
 * fn:parse-integer($value, $radix?) parses a string as an integer in the given radix (2-36).
 */
public class FnParseInteger extends BasicFunction {

    private static final ErrorCodes.ErrorCode FORG0011 = new ErrorCodes.ErrorCode("FORG0011",
            "Radix is out of range (must be 2-36)");
    private static final ErrorCodes.ErrorCode FORG0012 = new ErrorCodes.ErrorCode("FORG0012",
            "Invalid integer string for the given radix");

    public static final FunctionSignature[] FN_PARSE_INTEGER = {
            new FunctionSignature(
                    new QName("parse-integer", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as an integer in the given radix.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to parse"),
                            new FunctionParameterSequenceType("radix", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The radix (2-36), default 10")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the parsed integer")),
            new FunctionSignature(
                    new QName("parse-integer", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as a decimal integer.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The string to parse")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the parsed integer"))
    };

    public FnParseInteger(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String value = args[0].getStringValue();

        int radix = 10;
        if (args.length > 1 && !args[1].isEmpty()) {
            radix = (int) ((IntegerValue) args[1].itemAt(0)).getLong();
        }

        if (radix < 2 || radix > 36) {
            throw new XPathException(this, FORG0011, "Radix must be between 2 and 36, got: " + radix);
        }

        // Preprocess: strip whitespace and underscores
        String stripped = value.replaceAll("[\\s_]", "");

        if (stripped.isEmpty()) {
            throw new XPathException(this, FORG0012, "Empty string after stripping whitespace and underscores");
        }

        // Handle optional sign
        boolean negative = false;
        if (stripped.charAt(0) == '-') {
            negative = true;
            stripped = stripped.substring(1);
        } else if (stripped.charAt(0) == '+') {
            stripped = stripped.substring(1);
        }

        if (stripped.isEmpty()) {
            throw new XPathException(this, FORG0012, "No digits found after sign");
        }

        // Validate digits for the given radix
        final String lowerStripped = stripped.toLowerCase();
        for (int i = 0; i < lowerStripped.length(); i++) {
            final char c = lowerStripped.charAt(i);
            final int digit;
            if (c >= '0' && c <= '9') {
                digit = c - '0';
            } else if (c >= 'a' && c <= 'z') {
                digit = c - 'a' + 10;
            } else {
                throw new XPathException(this, FORG0012,
                        "Invalid character '" + c + "' for radix " + radix);
            }
            if (digit >= radix) {
                throw new XPathException(this, FORG0012,
                        "Invalid character '" + c + "' for radix " + radix);
            }
        }

        try {
            BigInteger result = new BigInteger(lowerStripped, radix);
            if (negative) {
                result = result.negate();
            }
            return new IntegerValue(this, result);
        } catch (final NumberFormatException e) {
            throw new XPathException(this, FORG0012,
                    "Cannot parse '" + value + "' as integer with radix " + radix);
        }
    }
}
