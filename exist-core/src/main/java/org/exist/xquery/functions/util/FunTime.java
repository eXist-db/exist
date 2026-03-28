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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * Pass-through profiling function that measures execution time of an expression.
 * Returns the expression result unchanged, logging the elapsed time.
 *
 * <p>Inspired by BaseX's prof:time().</p>
 *
 * <pre>
 * util:time(collection("/db/data")//title)
 * util:time(collection("/db/data")//title, "title lookup")
 * </pre>
 */
public class FunTime extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(FunTime.class);

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("time", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures the execution time of the given expression and logs it. Returns the result unchanged.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("expr", Type.ITEM, Cardinality.ZERO_OR_MORE,
                                    "The expression to measure")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "The result of the expression, unchanged")
            ),
            new FunctionSignature(
                    new QName("time", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures the execution time of the given expression and logs it with a label. Returns the result unchanged.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("expr", Type.ITEM, Cardinality.ZERO_OR_MORE,
                                    "The expression to measure"),
                            new FunctionParameterSequenceType("label", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "A label for the log message")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "The result of the expression, unchanged")
            )
    };

    public FunTime(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final long startNanos = System.nanoTime();

        // The expression has already been evaluated by the function call mechanism —
        // args[0] contains the result
        final Sequence result = args[0];

        final long elapsedNanos = System.nanoTime() - startNanos;
        final double elapsedMs = elapsedNanos / 1_000_000.0;

        final String label = getArgumentCount() == 2
                ? args[1].getStringValue()
                : "util:time()";

        LOG.info("{} \u2014 {}", label, formatDuration(elapsedMs));

        return result;
    }

    static String formatDuration(final double ms) {
        if (ms < 1.0) {
            return String.format("%.1f\u00B5s", ms * 1000);
        } else if (ms < 1000.0) {
            return String.format("%.1fms", ms);
        } else {
            return String.format("%.2fs", ms / 1000);
        }
    }
}
