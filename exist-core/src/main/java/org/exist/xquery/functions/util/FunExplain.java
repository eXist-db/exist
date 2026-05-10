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

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.source.Source;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.*;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import org.xml.sax.helpers.AttributesImpl;

import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static javax.xml.XMLConstants.XML_NS_PREFIX;

/**
 * Returns the compiled expression tree of an XQuery expression as XML.
 * This is the core query visibility function — shows what the optimizer produces.
 *
 * <pre>
 * util:explain('for $x in 1 to 10 where $x > 5 return $x * 2')
 * </pre>
 *
 * Returns an XML representation of the expression tree showing prolog
 * declarations (namespace declarations, module imports) followed by the
 * post-optimization expression body (FLWOR clauses, path expressions,
 * function calls, comparisons, etc.).
 */
public class FunExplain extends BasicFunction {

    /** Prefixes pre-registered by every fresh XQueryContext via loadDefaultNS. */
    private static final Set<String> BUILTIN_PREFIXES = Set.of(
            XML_NS_PREFIX,
            "xs",
            "xsi",
            "xdt",
            "fn",
            "local",
            Namespaces.W3C_XQUERY_XPATH_ERROR_PREFIX,
            Namespaces.EXIST_NS_PREFIX,
            Namespaces.EXIST_JAVA_BINDING_NS_PREFIX,
            Namespaces.EXIST_XQUERY_XPATH_ERROR_PREFIX,
            "dbgp"
    );

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("explain", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Compiles the given XQuery expression and returns its expression tree as XML. " +
                            "Shows the post-optimization query plan, including a prolog element " +
                            "with declared namespaces and module imports.",
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

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        context.pushNamespaceContext();
        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

            final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(query));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(pContext);

            try {
                parser.xpath();
            } catch (final RecognitionException | TokenStreamException e) {
                throw parseError(e.getMessage());
            }
            if (parser.foundErrors()) {
                throw parseError(parser.getErrorMessage());
            }

            final AST ast = parser.getAST();
            final PathExpr path = new PathExpr(pContext);
            try {
                astParser.xpath(ast, path);
            } catch (final RecognitionException e) {
                throw parseError(e.getMessage());
            }
            if (astParser.foundErrors()) {
                final Exception last = astParser.getLastException();
                throw parseError(last != null ? last.getMessage() : "unknown AST construction error");
            }

            path.analyze(new AnalyzeContextInfo());

            return serializeExpressionTree(pContext, path);

        } finally {
            context.popNamespaceContext();
            pContext.reset(false);
        }
    }

    private XPathException parseError(final String message) {
        return new XPathException(this, ErrorCodes.XPST0003,
                "Failed to parse query supplied to util:explain: " + message);
    }

    private Sequence serializeExpressionTree(final XQueryContext pContext, final Expression expression)
            throws XPathException {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();

            builder.startElement("", "explain", "explain", null);

            writeProlog(builder, pContext);

            builder.startElement("", "body", "body", null);
            final QueryPlanSerializer visitor = new QueryPlanSerializer(builder);
            expression.accept(visitor);
            builder.endElement(); // body

            builder.endElement(); // explain

            final DocumentImpl doc = (DocumentImpl) builder.getDocument();
            return (org.exist.dom.memtree.ElementImpl) doc.getDocumentElement();
        } finally {
            context.popDocumentContext();
        }
    }

    private void writeProlog(final MemTreeBuilder builder, final XQueryContext pContext) {
        builder.startElement("", "prolog", "prolog", null);

        for (final Map.Entry<String, String> entry : pContext.getStaticNamespaces().entrySet()) {
            final String prefix = entry.getKey();
            if (BUILTIN_PREFIXES.contains(prefix)) {
                continue;
            }
            final AttributesImpl atts = new AttributesImpl();
            atts.addAttribute("", "prefix", "prefix", "CDATA", prefix);
            atts.addAttribute("", "uri", "uri", "CDATA", entry.getValue());
            builder.startElement("", "namespace", "namespace", atts);
            builder.endElement();
        }

        for (final Iterator<Module> it = pContext.getRootModules(); it.hasNext(); ) {
            final Module module = it.next();
            if (module == null || module.isInternalModule()) {
                continue;
            }
            final AttributesImpl atts = new AttributesImpl();
            atts.addAttribute("", "uri", "uri", "CDATA", module.getNamespaceURI());
            final String prefix = pContext.getInScopePrefix(module.getNamespaceURI());
            if (prefix != null && !prefix.isEmpty()) {
                atts.addAttribute("", "prefix", "prefix", "CDATA", prefix);
            }
            if (module instanceof final ExternalModule external) {
                final Source source = external.getSource();
                if (source != null) {
                    final String location = source.pathOrShortIdentifier();
                    if (location != null) {
                        atts.addAttribute("", "location", "location", "CDATA", location);
                    }
                }
            }
            builder.startElement("", "module-import", "module-import", atts);
            builder.endElement();
        }

        builder.endElement(); // prolog
    }
}
