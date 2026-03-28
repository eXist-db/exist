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
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.*;

import antlr.collections.AST;

import java.io.StringReader;

/**
 * Execute a query with profiling enabled and return structured profiling data.
 * Combines timing, memory measurement, expression tree, and index/optimization
 * statistics into a single map result.
 *
 * <pre>
 * let $p := util:profile('collection("/db/data")//book[year > 2020]')
 * return (
 *   $p?result,          (: the query result :)
 *   $p?time,            (: xs:dayTimeDuration :)
 *   $p?memory,          (: xs:integer bytes :)
 *   $p?plan,            (: element(explain) — expression tree :)
 *   $p?stats            (: element() — profiler statistics XML :)
 * )
 * </pre>
 */
public class FunProfile extends BasicFunction {

    private static final Logger LOG = LogManager.getLogger(FunProfile.class);

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("profile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Executes the given query with profiling enabled and returns a map with " +
                            "the result, timing, memory, expression tree, and profiler statistics.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The XQuery expression to profile")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                            "A map with keys: result, time, memory, plan, stats")
            ),
            new FunctionSignature(
                    new QName("profile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Executes the given query with profiling enabled and returns a map with " +
                            "the result, timing, memory, expression tree, and profiler statistics.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The XQuery expression to profile"),
                            new FunctionParameterSequenceType("module-load-path", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The module load path for resolving imports")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.EXACTLY_ONE,
                            "A map with keys: result, time, memory, plan, stats")
            )
    };

    public FunProfile(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String query = args[0].getStringValue();
        if (query.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Query expression is empty");
        }

        final DBBroker broker = context.getBroker();
        final XQueryContext pContext = new XQueryContext(broker.getBrokerPool());
        context.pushNamespaceContext();

        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

            // Enable profiling on the context
            final Profiler profiler = pContext.getProfiler();
            profiler.configure(new Option(
                    this,
                    new QName("profiling", "http://exist-db.org/xquery/util", "exist"),
                    "enabled=yes verbosity=10"
            ));

            // Compile the query
            final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(query));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(pContext);

            parser.xpath();
            if (parser.foundErrors()) {
                throw new XPathException(this, ErrorCodes.XPST0003,
                        "Parse error in query: " + parser.getErrorMessage());
            }

            final AST ast = parser.getAST();
            final PathExpr path = new PathExpr(pContext);
            astParser.xpath(ast, path);
            if (astParser.foundErrors()) {
                throw astParser.getLastException();
            }

            // Analyze (optimize)
            path.analyze(new AnalyzeContextInfo());

            // Generate the expression plan BEFORE execution
            final Sequence plan = serializeExpressionTree(path);

            // Execute with timing and memory measurement
            final Runtime runtime = Runtime.getRuntime();
            final long memBefore = runtime.totalMemory() - runtime.freeMemory();
            final long startNanos = System.nanoTime();

            final Sequence result = path.eval(null, null);

            final long elapsedNanos = System.nanoTime() - startNanos;
            final long memAfter = runtime.totalMemory() - runtime.freeMemory();
            final long memDelta = memAfter - memBefore;

            // Capture profiler stats as XML
            final Sequence statsXml = serializeProfilerStats(pContext);

            // Build the result map
            final MapType resultMap = new MapType(this, context);

            resultMap.add(new StringValue("result"), result);

            try {
                resultMap.add(new StringValue("time"),
                        new DayTimeDurationValue(this, elapsedNanos / 1_000_000));
            } catch (final XPathException e) {
                resultMap.add(new StringValue("time"),
                        new IntegerValue(this, elapsedNanos / 1_000_000));
            }

            resultMap.add(new StringValue("memory"), new IntegerValue(this, memDelta));
            resultMap.add(new StringValue("plan"), plan);
            resultMap.add(new StringValue("stats"), statsXml);

            return resultMap;

        } catch (final Exception e) {
            if (e instanceof XPathException) {
                throw (XPathException) e;
            }
            throw new XPathException(this, ErrorCodes.XPST0003, "Error profiling query: " + e.getMessage());
        } finally {
            context.popNamespaceContext();
            pContext.reset(false);
        }
    }

    private Sequence serializeExpressionTree(final Expression expression) throws XPathException {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startElement("", "explain", "explain", null);
            final QueryPlanSerializer visitor = new QueryPlanSerializer(builder);
            expression.accept(visitor);
            builder.endElement();
            final DocumentImpl doc = (DocumentImpl) builder.getDocument();
            return (org.exist.dom.memtree.ElementImpl) doc.getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }

    private Sequence serializeProfilerStats(final XQueryContext pContext) throws XPathException {
        final PerformanceStats stats = pContext.getBroker().getBrokerPool().getPerformanceStats();
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            if (stats instanceof PerformanceStatsImpl) {
                ((PerformanceStatsImpl) stats).serialize(builder);
            } else {
                builder.startElement("", "stats", "stats", null);
                builder.endElement();
            }
            final DocumentImpl doc = (DocumentImpl) builder.getDocument();
            return (org.exist.dom.memtree.ElementImpl) doc.getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }
}
