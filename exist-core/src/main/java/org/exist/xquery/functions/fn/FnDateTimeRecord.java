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
import org.exist.xquery.functions.map.RecordMapType;
import org.exist.xquery.value.*;

import java.util.List;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignature;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

/**
 * Implementation of the XQuery 4.0 fn:dateTime-record named record constructor.
 *
 * <p>The function accepts 0 to 7 optional parameters (year, month, day,
 * hours, minutes, seconds, timezone) and returns a map of type
 * {@code fn:dateTime-record}. Only fields with non-empty values are
 * included in the result map.</p>
 *
 * @see <a href="https://qt4cg.org/specifications/xpath-functions-40/Overview.html#dateTime-record">
 *     XPath and XQuery Functions and Operators 4.0: fn:dateTime-record</a>
 */
public class FnDateTimeRecord extends BasicFunction {

    private static final String FS_NAME = "dateTime-record";

    /** The field names in declaration order. */
    private static final List<String> FIELD_ORDER = List.of(
            "year", "month", "day", "hours", "minutes", "seconds", "timezone"
    );

    private static final FunctionReturnSequenceType FS_RETURN_TYPE = returns(
            Type.DATETIME_RECORD,
            "A record of type fn:dateTime-record containing the specified date/time components.");

    static final FunctionSignature[] FS_DATETIME_RECORD = functionSignatures(
            FS_NAME,
            "Constructs a dateTime-record from optional date/time component values.",
            FS_RETURN_TYPE,
            arities(
                    arity(),
                    arity(
                            optParam("year", Type.INTEGER, "The year component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component"),
                            optParam("day", Type.INTEGER, "The day component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component"),
                            optParam("day", Type.INTEGER, "The day component"),
                            optParam("hours", Type.INTEGER, "The hours component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component"),
                            optParam("day", Type.INTEGER, "The day component"),
                            optParam("hours", Type.INTEGER, "The hours component"),
                            optParam("minutes", Type.INTEGER, "The minutes component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component"),
                            optParam("day", Type.INTEGER, "The day component"),
                            optParam("hours", Type.INTEGER, "The hours component"),
                            optParam("minutes", Type.INTEGER, "The minutes component"),
                            optParam("seconds", Type.DECIMAL, "The seconds component")
                    ),
                    arity(
                            optParam("year", Type.INTEGER, "The year component"),
                            optParam("month", Type.INTEGER, "The month component"),
                            optParam("day", Type.INTEGER, "The day component"),
                            optParam("hours", Type.INTEGER, "The hours component"),
                            optParam("minutes", Type.INTEGER, "The minutes component"),
                            optParam("seconds", Type.DECIMAL, "The seconds component"),
                            optParam("timezone", Type.DAY_TIME_DURATION, "The timezone as xs:dayTimeDuration")
                    )
            )
    );

    public FnDateTimeRecord(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final RecordMapType result = new RecordMapType(this, context, FIELD_ORDER, Type.DATETIME_RECORD);

        for (int i = 0; i < args.length && i < FIELD_ORDER.size(); i++) {
            final Sequence arg = args[i];
            if (arg != null && !arg.isEmpty()) {
                final String fieldName = FIELD_ORDER.get(i);
                if ("timezone".equals(fieldName)) {
                    result.add(new StringValue(this, fieldName), coerceTimezone(arg));
                } else {
                    result.add(new StringValue(this, fieldName), arg);
                }
            }
        }

        return result;
    }

    /**
     * Coerce the timezone argument to xs:dayTimeDuration.
     * Accepts xs:dayTimeDuration directly, or xs:duration (coerced).
     * Rejects xs:string with XPTY0004.
     */
    private Sequence coerceTimezone(final Sequence value) throws XPathException {
        final Item item = value.itemAt(0);
        if (item instanceof DurationValue dv) {
            if (dv.getType() == Type.DAY_TIME_DURATION) {
                return value;
            }
            // Coerce xs:duration or xs:yearMonthDuration to xs:dayTimeDuration
            return dv.convertTo(Type.DAY_TIME_DURATION);
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Expected xs:dayTimeDuration for timezone parameter, got " +
                        Type.getTypeName(item.getType()));
    }
}
