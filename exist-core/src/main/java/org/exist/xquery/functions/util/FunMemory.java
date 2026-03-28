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
 * Pass-through profiling function that measures memory usage of an expression.
 * Returns the expression result unchanged, logging the memory delta.
 *
 * <p>Inspired by BaseX's prof:memory().</p>
 *
 * <pre>
 * util:memory(parse-json(unparsed-text("/db/large.json")))
 * util:memory(parse-json(unparsed-text("/db/large.json")), "JSON parse")
 * </pre>
 */
public class FunMemory extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(FunMemory.class);

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("memory", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures the memory usage of the given expression and logs it. Returns the result unchanged.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("expr", Type.ITEM, Cardinality.ZERO_OR_MORE,
                                    "The expression to measure")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE,
                            "The result of the expression, unchanged")
            ),
            new FunctionSignature(
                    new QName("memory", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures the memory usage of the given expression and logs it with a label. Returns the result unchanged.",
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

    public FunMemory(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Runtime runtime = Runtime.getRuntime();
        final long memBefore = runtime.totalMemory() - runtime.freeMemory();

        // The expression has already been evaluated — args[0] contains the result
        final Sequence result = args[0];

        final long memAfter = runtime.totalMemory() - runtime.freeMemory();
        final long memDelta = memAfter - memBefore;

        final String label = getArgumentCount() == 2
                ? args[1].getStringValue()
                : "util:memory()";

        LOG.info("{} \u2014 {}", label, formatBytes(memDelta));

        return result;
    }

    static String formatBytes(final long bytes) {
        final long abs = Math.abs(bytes);
        if (abs < 1024) {
            return bytes + " B";
        } else if (abs < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (abs < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
        }
    }
}
