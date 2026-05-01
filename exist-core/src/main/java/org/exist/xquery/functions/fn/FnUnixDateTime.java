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
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Implements XQuery 4.0 fn:unix-dateTime.
 *
 * fn:unix-dateTime($value as xs:nonNegativeInteger?) as xs:dateTimeStamp
 * Converts milliseconds since Unix epoch to xs:dateTime with UTC timezone.
 */
public class FnUnixDateTime extends BasicFunction {

    public static final FunctionSignature[] FN_UNIX_DATETIME = {
            new FunctionSignature(
                    new QName("unix-dateTime", Function.BUILTIN_FUNCTION_NS),
                    "Converts Unix time in milliseconds to xs:dateTime in UTC.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.NON_NEGATIVE_INTEGER, Cardinality.ZERO_OR_ONE, "Unix time in milliseconds since epoch")
                    },
                    new FunctionReturnSequenceType(Type.DATE_TIME_STAMP, Cardinality.EXACTLY_ONE, "the corresponding dateTime in UTC")),
            new FunctionSignature(
                    new QName("unix-dateTime", Function.BUILTIN_FUNCTION_NS),
                    "Returns the Unix epoch (1970-01-01T00:00:00Z).",
                    new SequenceType[] {
                    },
                    new FunctionReturnSequenceType(Type.DATE_TIME_STAMP, Cardinality.EXACTLY_ONE, "the Unix epoch"))
    };

    public FnUnixDateTime(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        long millis = 0;
        if (args.length > 0 && !args[0].isEmpty()) {
            millis = ((IntegerValue) args[0].itemAt(0)).getLong();
        }

        final ZonedDateTime zdt = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC);
        // Format with explicit seconds and optional milliseconds
        final long ms = millis % 1000;
        final String pattern = (ms != 0) ? "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" : "yyyy-MM-dd'T'HH:mm:ss'Z'";
        final String isoStr = DateTimeFormatter.ofPattern(pattern).format(zdt);
        return new DateTimeValue(this, isoStr);
    }
}
