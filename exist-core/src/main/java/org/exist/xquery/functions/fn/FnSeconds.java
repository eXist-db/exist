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
import org.exist.xquery.value.*;

/**
 * Implements XQuery 4.0 fn:seconds.
 *
 * Converts a number of seconds (as xs:double) to an xs:dayTimeDuration.
 */
public class FnSeconds extends BasicFunction {

    public static final FunctionSignature FN_SECONDS = new FunctionSignature(
            new QName("seconds", Function.BUILTIN_FUNCTION_NS),
            "Returns a dayTimeDuration representing the given number of seconds.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("value", Type.DECIMAL, Cardinality.ZERO_OR_ONE, "The number of seconds")
            },
            new FunctionReturnSequenceType(Type.DAY_TIME_DURATION, Cardinality.ZERO_OR_ONE, "The duration"));

    public FnSeconds(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final double seconds = ((DoubleValue) args[0].itemAt(0).convertTo(Type.DOUBLE)).getDouble();

        if (Double.isNaN(seconds) || Double.isInfinite(seconds)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Cannot create duration from " + (Double.isNaN(seconds) ? "NaN" : "Infinity"));
        }

        // Convert seconds to dayTimeDuration (constructor takes milliseconds)
        final long millis = Math.round(seconds * 1000.0);
        return new DayTimeDurationValue(this, millis);
    }
}
