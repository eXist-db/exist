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
 * Returns the compiled expression tree of an XQuery expression as XML.
 * This is the core query visibility function — shows what the optimizer produces.
 *
 * <pre>
 * util:explain('for $x in 1 to 10 where $x > 5 return $x * 2')
 * </pre>
 *
 * Returns an XML representation of the expression tree showing FLWOR clauses,
 * path expressions, function calls, comparisons, etc.
 */
public class FunExplain extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("explain", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Compiles the given XQuery expression and returns its expression tree as XML. " +
                            "Shows the post-optimization query plan.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The XQuery expression to explain")
                    },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                            "An XML representation of the compiled expression tree")
            ),
            new FunctionSignature(
                    new QName("explain", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Compiles the given XQuery expression and returns its expression tree as XML. " +
                            "The module-load-path controls where imports are resolved.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The XQuery expression to explain"),
                            new FunctionParameterSequenceType("module-load-path", Type.STRING, Cardinality.EXACTLY_ONE,
                                    "The module load path for resolving imports")
                    },
                    new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE,
                            "An XML representation of the compiled expression tree")
            )
    };

    public FunExplain(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String query = args[0].getStringValue();
        if (query.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Query expression is empty");
        }

        // Compile the query using the same pattern as util:compile()
        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        context.pushNamespaceContext();
        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

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

            // Analyze (optimize) the expression tree
            path.analyze(new AnalyzeContextInfo());

            // Serialize the expression tree as XML
            return serializeExpressionTree(path);

        } catch (final Exception e) {
            throw new XPathException(this, ErrorCodes.XPST0003, "Parse error: " + e.getMessage());
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
            // Return the root element, not the document node
            return (org.exist.dom.memtree.ElementImpl) doc.getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }
}
