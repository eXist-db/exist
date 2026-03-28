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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;


/**
 * Profiling function that measures execution time and memory, returning
 * a map with the measurements and the expression result.
 *
 * <p>Inspired by BaseX's prof:track().</p>
 *
 * <pre>
 * let $r := util:track(collection("/db/data")//title)
 * return (
 *   "Time: " || $r?time,
 *   "Memory: " || $r?memory || " bytes",
 *   "Items: " || count($r?value)
 * )
 * </pre>
 */
public class FunTrack extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("track", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures execution time and memory of the given expression. " +
                            "Returns map { 'time': xs:dayTimeDuration, 'memory': xs:integer, 'value': item()* }.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("expr", Type.ITEM, Cardinality.ZERO_OR_MORE,
                                    "The expression to measure")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                            "A map with keys 'time' (xs:dayTimeDuration), 'memory' (xs:integer bytes), 'value' (the result)")
            ),
            new FunctionSignature(
                    new QName("track", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Measures execution time and memory of the given expression with a label. " +
                            "Returns map { 'time': xs:dayTimeDuration, 'memory': xs:integer, 'value': item()*, 'label': xs:string }.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("expr", Type.ITEM, Cardinality.ZERO_OR_MORE,
                                    "The expression to measure"),
                            new FunctionParameterSequenceType("label", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "A label for identifying this measurement")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                            "A map with keys 'time', 'memory', 'value', and 'label'")
            )
    };

    public FunTrack(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Runtime runtime = Runtime.getRuntime();
        final long memBefore = runtime.totalMemory() - runtime.freeMemory();
        final long startNanos = System.nanoTime();

        // The expression has already been evaluated — args[0] contains the result
        final Sequence result = args[0];

        final long elapsedNanos = System.nanoTime() - startNanos;
        final long memAfter = runtime.totalMemory() - runtime.freeMemory();
        final long memDelta = memAfter - memBefore;

        // Build the result map
        final MapType map = new MapType(this, context);

        // time as xs:dayTimeDuration (millisecond precision)
        try {
            map.add(new StringValue("time"), new DayTimeDurationValue(this, elapsedNanos / 1_000_000));
        } catch (final XPathException e) {
            // Fallback: store milliseconds as integer
            map.add(new StringValue("time"), new IntegerValue(this, elapsedNanos / 1_000_000));
        }

        // memory as xs:integer (bytes)
        map.add(new StringValue("memory"), new IntegerValue(this, memDelta));

        // value: the expression result
        map.add(new StringValue("value"), result);

        // optional label
        if (getArgumentCount() == 2) {
            map.add(new StringValue("label"), new StringValue(args[1].getStringValue()));
        }

        return map;
    }
}
