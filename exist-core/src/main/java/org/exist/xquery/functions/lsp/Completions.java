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
package org.exist.xquery.functions.lsp;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.PathExpr;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.VariableDeclaration;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.collections.AST;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Returns completion items available in the context of an XQuery expression,
 * suitable for Language Server Protocol {@code textDocument/completion} responses.
 *
 * <p>Returns an array of maps, where each map represents a completion item with
 * the following keys:</p>
 * <ul>
 *   <li>{@code label} — display text (e.g., "fn:count")</li>
 *   <li>{@code kind} — LSP CompletionItemKind integer (3=Function, 6=Variable)</li>
 *   <li>{@code detail} — signature or type info</li>
 *   <li>{@code documentation} — description from function signature</li>
 *   <li>{@code insertText} — text to insert (e.g., "fn:count()")</li>
 * </ul>
 *
 * <p>Built-in module functions are always returned. If the expression compiles
 * successfully, user-declared functions and variables are included as well.</p>
 *
 * @author eXist-db
 */
public class Completions extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Completions.class);

    /** LSP CompletionItemKind constants */
    private static final long COMPLETION_KIND_FUNCTION = 3;
    private static final long COMPLETION_KIND_VARIABLE = 6;
    private static final long COMPLETION_KIND_MODULE = 9;
    private static final long COMPLETION_KIND_KEYWORD = 14;

    private static final String FS_COMPLETIONS_NAME = "completions";
    private static final String FS_COMPLETIONS_DESCRIPTION = """
            Returns an array of completion item maps available in the context of \
            the given XQuery expression. Each map contains keys: label (xs:string), \
            kind (xs:integer, LSP CompletionItemKind), detail (xs:string, signature), \
            documentation (xs:string), and insertText (xs:string). \
            Built-in functions are always included; user-declared symbols are \
            included if the expression compiles successfully.""";

    public static final FunctionSignature[] FS_COMPLETIONS = functionSignatures(
            LspModule.qname(FS_COMPLETIONS_NAME),
            FS_COMPLETIONS_DESCRIPTION,
            returns(Type.ARRAY_ITEM, "an array of completion item maps"),
            arities(
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to analyze for available completions.")
                    ),
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to analyze for available completions."),
                            optParam("module-load-path", Type.STRING, "The module load path. " +
                                    "Imports will be resolved relative to this. " +
                                    "Use xmldb:exist:///db or /db for database-stored modules.")
                    )
            )
    );

    /** XQuery keywords for completion */
    private static final String[] XQUERY_KEYWORDS = {
            "declare", "function", "variable", "namespace", "module",
            "import", "at", "as", "instance", "of", "cast", "castable", "treat",
            "let", "for", "in", "where", "order", "by", "ascending", "descending",
            "group", "count", "return", "if", "then", "else",
            "some", "every", "satisfies",
            "typeswitch", "switch", "case", "default",
            "try", "catch",
            "element", "attribute", "text", "comment", "document",
            "processing-instruction", "node",
            "empty-sequence", "item",
            "or", "and", "not",
            "div", "idiv", "mod",
            "union", "intersect", "except",
            "to", "eq", "ne", "lt", "le", "gt", "ge",
            "is", "preceding", "following"
    };

    public Completions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        final List<Sequence> completions = new ArrayList<>();

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

            // Built-in module functions are always available
            addBuiltinFunctions(pContext, completions);

            // Keywords
            addKeywords(completions);

            // Try to compile to discover user-declared symbols
            if (!expr.trim().isEmpty()) {
                context.pushNamespaceContext();
                try {
                    addUserDeclaredSymbols(pContext, expr, completions);
                } finally {
                    context.popNamespaceContext();
                    pContext.reset(false);
                }
            }
        } finally {
            pContext.runCleanupTasks();
        }

        return new ArrayType(this, context, completions);
    }

    /**
     * Adds completion items for all functions in all loaded built-in modules.
     */
    private void addBuiltinFunctions(final XQueryContext pContext, final List<Sequence> completions)
            throws XPathException {
        final Set<String> seen = new HashSet<>();
        final Iterator<Module> modules = pContext.getAllModules();

        while (modules.hasNext()) {
            final Module module = modules.next();
            if (!module.isInternalModule()) {
                continue;
            }

            String prefix = module.getDefaultPrefix();
            if (prefix == null || prefix.isEmpty()) {
                // Some built-in modules (e.g., XPath functions) have empty default prefix
                // but are bound to a conventional prefix (e.g., "fn") in the context
                prefix = pContext.getPrefixForURI(module.getNamespaceURI());
                if (prefix == null) {
                    prefix = "";
                }
            }
            final FunctionSignature[] signatures = module.listFunctions();

            for (final FunctionSignature sig : signatures) {
                if (sig.isPrivate()) {
                    continue;
                }

                final QName name = sig.getName();
                final String label = formatLabel(prefix, name.getLocalPart(), sig.getArgumentCount());

                // Deduplicate overloaded functions
                if (!seen.add(label)) {
                    continue;
                }

                final String detail = sig.toString();
                final String documentation = sig.getDescription() != null ? sig.getDescription() : "";
                final String insertText = formatInsertText(prefix, name.getLocalPart(), sig.getArgumentCount());

                addCompletion(completions, label, COMPLETION_KIND_FUNCTION, detail, documentation, insertText);
            }
        }
    }

    /**
     * Adds XQuery keywords as completion items.
     */
    private void addKeywords(final List<Sequence> completions) throws XPathException {
        for (final String keyword : XQUERY_KEYWORDS) {
            addCompletion(completions, keyword, COMPLETION_KIND_KEYWORD, "keyword", "", keyword);
        }
    }

    /**
     * Tries to compile the expression and adds user-declared functions and variables.
     */
    private void addUserDeclaredSymbols(final XQueryContext pContext, final String expr,
            final List<Sequence> completions) throws XPathException {
        try {
            final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(expr));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(pContext);

            parser.xpath();
            if (parser.foundErrors()) {
                return;
            }

            final AST ast = parser.getAST();
            final PathExpr path = new PathExpr(pContext);
            astParser.xpath(ast, path);
            if (astParser.foundErrors()) {
                return;
            }

            path.analyze(new AnalyzeContextInfo());

            // User-declared functions
            final Iterator<UserDefinedFunction> funcs = pContext.localFunctions();
            while (funcs.hasNext()) {
                final UserDefinedFunction func = funcs.next();
                final FunctionSignature sig = func.getSignature();
                final QName name = sig.getName();
                final String prefix = name.getPrefix();
                final String label = formatLabel(
                        prefix != null ? prefix : "", name.getLocalPart(), sig.getArgumentCount());
                final String detail = sig.toString();
                final String insertText = formatInsertText(
                        prefix != null ? prefix : "", name.getLocalPart(), sig.getArgumentCount());

                addCompletion(completions, label, COMPLETION_KIND_FUNCTION, detail, "", insertText);
            }

            // User-declared global variables
            for (int i = 0; i < path.getSubExpressionCount(); i++) {
                final Expression step = path.getSubExpression(i);
                if (step instanceof final VariableDeclaration varDecl) {
                    final QName name = varDecl.getName();
                    final String varName = "$" + formatQName(name);
                    final SequenceType seqType = varDecl.getSequenceType();
                    final String detail = seqType != null
                            ? Type.getTypeName(seqType.getPrimaryType()) + seqType.getCardinality().toXQueryCardinalityString()
                            : "";

                    addCompletion(completions, varName, COMPLETION_KIND_VARIABLE, detail, "", varName);
                }
            }

        } catch (final Exception e) {
            logger.debug("Error compiling expression for completions: {}", e.getMessage());
        }
    }

    /**
     * Creates a completion item map and adds it to the list.
     */
    private void addCompletion(final List<Sequence> completions, final String label,
            final long kind, final String detail, final String documentation,
            final String insertText) throws XPathException {
        final MapType item = new MapType(this, context);
        item.add(new StringValue(this, "label"), new StringValue(this, label));
        item.add(new StringValue(this, "kind"), new IntegerValue(this, kind));
        item.add(new StringValue(this, "detail"), new StringValue(this, detail));
        item.add(new StringValue(this, "documentation"), new StringValue(this, documentation));
        item.add(new StringValue(this, "insertText"), new StringValue(this, insertText));
        completions.add(item);
    }

    private static String formatLabel(final String prefix, final String localPart, final int arity) {
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + localPart + "#" + arity;
        }
        return localPart + "#" + arity;
    }

    private static String formatInsertText(final String prefix, final String localPart, final int arity) {
        final StringBuilder sb = new StringBuilder();
        if (prefix != null && !prefix.isEmpty()) {
            sb.append(prefix).append(':');
        }
        sb.append(localPart).append('(');
        if (arity > 0) {
            // Leave cursor inside parens for user to fill in args
        }
        sb.append(')');
        return sb.toString();
    }

    private static String formatQName(final QName name) {
        final String prefix = name.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + name.getLocalPart();
        }
        return name.getLocalPart();
    }

}
