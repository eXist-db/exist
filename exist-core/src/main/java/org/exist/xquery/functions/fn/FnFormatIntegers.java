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

import org.exist.xquery.*;
import org.exist.xquery.functions.integer.IntegerPicture;
import org.exist.xquery.value.*;

import java.math.BigInteger;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.FunctionDSL.arity;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

/**
 * Implements fn:format-integer as per W3C XPath and XQuery Functions and Operators 3.1
 *
 * fn:format-number($value as integer?, $picture as xs:string) as xs:string
 * fn:format-number($value as integer?, $picture as xs:string, $lang as xs:string) as xs:string
 *
 * @author <a href="mailto:alan@evolvedbinary.com">Alan Paxton</a>
 */
public class FnFormatIntegers extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_VALUE = optParam("value", Type.NUMBER, "The number to format");
    private static final FunctionParameterSequenceType FS_PARAM_PICTURE = param("picture", Type.STRING, "The picture string to use for formatting. To understand the picture string syntax, see: https://www.w3.org/TR/xpath-functions-31/#func-format-number");

    private static final String FS_FORMAT_INTEGER_NAME = "format-integer";
    static final FunctionSignature[] FS_FORMAT_INTEGER = functionSignatures(
            FS_FORMAT_INTEGER_NAME,
            "Returns a string containing an integer formatted according to a given picture string.",
            returns(Type.STRING, "The formatted string representation of the supplied integer"),
            arities(
                    arity(
                            FS_PARAM_VALUE,
                            FS_PARAM_PICTURE
                    ),
                    arity(
                            FS_PARAM_VALUE,
                            FS_PARAM_PICTURE,
                            optParam("lang", Type.LANGUAGE, "The language in which to format the integers.")
                    )
            )
    );

    public FnFormatIntegers(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence)
            throws XPathException {
        // If $value is an empty sequence, the function returns a zero-length string
        // https://www.w3.org/TR/xpath-functions-31/#func-format-integer
        if (args[0].isEmpty()) {
            return AtomicValue.EMPTY_SEQUENCE;
        }

        // If the value of $value is negative, the rules below are applied to the absolute value of $value,
        // and a minus sign is prepended to the result.
        final IntegerValue integerValue = (IntegerValue) args[0].itemAt(0);
        final BigInteger bigInteger = integerValue.toJavaObject(BigInteger.class);

        final IntegerPicture picture = IntegerPicture.fromString(args[1].getStringValue());

        String language;
        if (args.length == 3 && !args[2].isEmpty()) {
            language = args[2].getStringValue();
        } else {
            language = context.getDefaultLanguage();
        }

        return new StringValue(picture.formatInteger(bigInteger, language));
    }
}
