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
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.*;

import antlr.collections.AST;

import java.io.StringReader;

/**
 * Execute a query with profiling and report which indexes were used.
 * Returns an XML report showing index type, usage count, and elapsed time.
 *
 * <pre>
 * util:index-report('collection("/db/data")//book[@year > 2020]')
 * </pre>
 *
 * Returns:
 * <pre>
 * &lt;index-report xmlns="http://exist-db.org/xquery/profiling"&gt;
 *   &lt;index type="range" source="..." elapsed="0.5" calls="42" optimization-level="BASIC"/&gt;
 *   &lt;optimization type="RANGE_IDX" source="..." line="1" column="15"/&gt;
 * &lt;/index-report&gt;
 * </pre>
 */
public class FunIndexReport extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("index-report", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Executes the given query with profiling enabled and returns an XML report " +
                            "showing which indexes were used and which optimizations were applied.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The XQuery expression to analyze for index usage")
                    },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                            "An XML report of index usage and optimizations")
            )
    };

    public FunIndexReport(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String query = args[0].getStringValue();
        if (query.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Query expression is empty");
        }

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        context.pushNamespaceContext();

        try {
            // Enable profiling with full verbosity
            final Profiler profiler = pContext.getProfiler();
            profiler.configure(new Option(
                    this,
                    new QName("profiling", "http://exist-db.org/xquery/util", "exist"),
                    "enabled=yes verbosity=10"
            ));

            // Compile
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

            path.analyze(new AnalyzeContextInfo());

            // Execute to trigger index usage
            path.eval(null, null);

            // Serialize the per-query profiler stats as the index report
            final PerformanceStats stats = profiler.getPerformanceStats();
            context.pushDocumentContext();
            try {
                final MemTreeBuilder builder = context.getDocumentBuilder();
                if (stats instanceof final PerformanceStatsImpl statsImpl) {
                    statsImpl.serialize(builder);
                } else {
                    builder.startElement("", "index-report", "index-report", null);
                    builder.endElement();
                }
                final DocumentImpl doc = (DocumentImpl) builder.getDocument();
                return (org.exist.dom.memtree.ElementImpl) doc.getDocumentElement();
            } finally {
                context.popDocumentContext();
            }

        } catch (final XPathException e) {
            throw e;
        } catch (final Exception e) {
            throw new XPathException(this, ErrorCodes.FOER0000, "Error profiling query: " + e.getMessage());
        } finally {
            context.popNamespaceContext();
            pContext.reset(false);
        }
    }
}
