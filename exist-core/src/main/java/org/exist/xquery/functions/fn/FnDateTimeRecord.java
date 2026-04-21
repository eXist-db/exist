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
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

/**
 * Implements fn:dateTime-record (XQuery 4.0).
 *
 * A named record type constructor that creates a map with keys:
 * year, month, day, hours, minutes, seconds, timezone.
 * Each field is optional (can be empty sequence).
 *
 * Supports 0-7 positional arguments.
 */
public class FnDateTimeRecord extends BasicFunction {

    private static final String[] FIELD_NAMES = {
        "year", "month", "day", "hours", "minutes", "seconds", "timezone"
    };

    public static final FunctionSignature[] SIGNATURES;

    static {
        SIGNATURES = new FunctionSignature[8]; // 0..7 args
        for (int arity = 0; arity <= 7; arity++) {
            final SequenceType[] params = new SequenceType[arity];
            for (int i = 0; i < arity; i++) {
                params[i] = new FunctionParameterSequenceType(
                    FIELD_NAMES[i], Type.ITEM, Cardinality.ZERO_OR_ONE,
                    "The " + FIELD_NAMES[i] + " component"
                );
            }
            SIGNATURES[arity] = new FunctionSignature(
                new QName("dateTime-record", Function.BUILTIN_FUNCTION_NS),
                "Creates a dateTime record (map) with the specified components.",
                params,
                new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                    "A map with year, month, day, hours, minutes, seconds, timezone entries")
            );
        }
    }

    public FnDateTimeRecord(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final MapType result = new MapType(context, null);

        for (int i = 0; i < args.length; i++) {
            if (!args[i].isEmpty()) {
                result.add(new StringValue(this, FIELD_NAMES[i]), args[i]);
            }
        }

        return result;
    }
}
