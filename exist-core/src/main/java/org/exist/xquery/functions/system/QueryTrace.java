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
package org.exist.xquery.functions.system;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * The query-scoped counterpart of {@link FunctionTrace}: statistics of the running query only.
 * <p>
 * {@code system:enable-tracing}, {@code system:trace} and {@code system:clear-trace} work on the statistics of the whole
 * database instance: while tracing is on, every query records into them, and a query that reads or clears them
 * reads or clears what all the others recorded. That is what a monitor wants, but it makes a measurement of one
 * query unreliable whenever anything else runs at the same time. These functions use the statistics that every
 * query keeps for itself, which imported modules and evaluated queries share with the query that loaded them.
 */
public class QueryTrace extends BasicFunction {

    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("query-trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Returns the function call, index use and optimization statistics recorded by the running query since "
                + "query tracing was enabled or cleared. Other queries are not part of it, whatever they do.",
            null,
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE, "the statistics of this query")
        ),
        new FunctionSignature(
            new QName("enable-query-tracing", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Enable or disable the recording of statistics for the running query only. The database-wide tracing, "
                + "which system:enable-tracing switches, is not changed and no other query starts recording.",
            new SequenceType[] {
                new FunctionParameterSequenceType("enable", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                    "true to record statistics for this query, false to stop")
            },
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        ),
        new FunctionSignature(
            new QName("clear-query-trace", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
            "Clear the statistics recorded by the running query. The database-wide statistics are not changed.",
            null,
            new SequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE)
        )
    };

    public QueryTrace(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("enable-query-tracing")) {
            context.getProfiler().setStatsEnabled(args[0].effectiveBooleanValue());
            return Sequence.EMPTY_SEQUENCE;

        } else if (isCalledAs("clear-query-trace")) {
            context.getProfiler().clearStats();
            return Sequence.EMPTY_SEQUENCE;
        }

        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            context.getProfiler().serializeStats(builder);
            builder.endDocument();
            return (NodeValue) builder.getDocument().getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }
}
