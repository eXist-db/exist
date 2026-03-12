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
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionSignature;
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
 * Compiles an XQuery expression and returns document symbols suitable for
 * Language Server Protocol {@code textDocument/documentSymbol} responses.
 *
 * <p>Returns an array of maps, where each map represents a symbol with the
 * following keys:</p>
 * <ul>
 *   <li>{@code name} — symbol name (e.g., "my:function#2", "$my:variable")</li>
 *   <li>{@code kind} — LSP SymbolKind integer (12=Function, 13=Variable)</li>
 *   <li>{@code line} — 0-based start line</li>
 *   <li>{@code column} — 0-based start column</li>
 *   <li>{@code detail} — additional info (return type, variable type, arity)</li>
 * </ul>
 *
 * <p>Returns an empty array if the expression cannot be compiled.</p>
 *
 * @author eXist-db
 */
public class Symbols extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Symbols.class);

    /** LSP SymbolKind constants */
    private static final long SYMBOL_KIND_FUNCTION = 12;
    private static final long SYMBOL_KIND_VARIABLE = 13;

    private static final String FS_SYMBOLS_NAME = "symbols";
    private static final String FS_SYMBOLS_DESCRIPTION = """
            Compiles the XQuery expression and returns an array of document symbol maps. \
            Each map contains keys: name (xs:string), kind (xs:integer, LSP SymbolKind), \
            line (xs:integer, 0-based), column (xs:integer, 0-based), and \
            detail (xs:string, type or signature info). \
            Returns an empty array if the expression cannot be compiled.""";

    public static final FunctionSignature[] FS_SYMBOLS = functionSignatures(
            LspModule.qname(FS_SYMBOLS_NAME),
            FS_SYMBOLS_DESCRIPTION,
            returns(Type.ARRAY_ITEM, "an array of document symbol maps"),
            arities(
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to analyze.")
                    ),
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to analyze."),
                            optParam("module-load-path", Type.STRING, "The module load path. " +
                                    "Imports will be resolved relative to this. " +
                                    "Use xmldb:exist:///db for database-stored modules.")
                    )
            )
    );

    public Symbols(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        if (expr.trim().isEmpty()) {
            return new ArrayType(this, context, new ArrayList<>());
        }

        final List<Sequence> symbols = new ArrayList<>();

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

            context.pushNamespaceContext();
            try {
                final PathExpr path = compile(pContext, expr);
                if (path != null) {
                    extractFunctions(pContext, symbols);
                    extractVariables(path, symbols);
                }
            } finally {
                context.popNamespaceContext();
                pContext.reset(false);
            }
        } finally {
            pContext.runCleanupTasks();
        }

        return new ArrayType(this, context, symbols);
    }

    /**
     * Compiles the expression and returns the PathExpr, or null on error.
     */
    private PathExpr compile(final XQueryContext pContext, final String expr) {
        try {
            final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(expr));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(pContext);

            parser.xpath();
            if (parser.foundErrors()) {
                return null;
            }

            final AST ast = parser.getAST();
            final PathExpr path = new PathExpr(pContext);
            astParser.xpath(ast, path);
            if (astParser.foundErrors()) {
                return null;
            }

            path.analyze(new AnalyzeContextInfo());
            return path;

        } catch (final Exception e) {
            logger.debug("Error compiling expression for symbol extraction: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts user-defined function declarations from the compilation context.
     */
    private void extractFunctions(final XQueryContext pContext, final List<Sequence> symbols)
            throws XPathException {
        final Iterator<UserDefinedFunction> funcs = pContext.localFunctions();
        while (funcs.hasNext()) {
            final UserDefinedFunction func = funcs.next();
            final FunctionSignature sig = func.getSignature();
            final org.exist.dom.QName name = sig.getName();

            // Build display name: prefix:local#arity
            final String displayName = formatFunctionName(name, sig.getArgumentCount());

            final String detail = sig.toString();

            int line = func.getLine();
            if (line <= 0 && func.getFunctionBody() != null) {
                line = firstPositiveLineIn(func.getFunctionBody());
            }
            final int column = func.getColumn();

            addSymbol(symbols, displayName, SYMBOL_KIND_FUNCTION,
                    Math.max(line - 1, 0), Math.max(column - 1, 0), detail);
        }
    }

    /**
     * Extracts global variable declarations from the compiled PathExpr.
     */
    private void extractVariables(final PathExpr path, final List<Sequence> symbols)
            throws XPathException {
        for (int i = 0; i < path.getSubExpressionCount(); i++) {
            final Expression step = path.getSubExpression(i);
            if (step instanceof final VariableDeclaration varDecl) {
                final org.exist.dom.QName name = varDecl.getName();
                final String displayName = "$" + formatQName(name);

                final SequenceType seqType = varDecl.getSequenceType();
                final String detail = seqType != null
                        ? Type.getTypeName(seqType.getPrimaryType()) + seqType.getCardinality().toXQueryCardinalityString()
                        : "";

                final int line = varDecl.getLine();
                final int column = varDecl.getColumn();

                addSymbol(symbols, displayName, SYMBOL_KIND_VARIABLE,
                        Math.max(line - 1, 0), Math.max(column - 1, 0), detail);
            }
        }
    }

    /**
     * Creates a symbol map and adds it to the list.
     */
    private void addSymbol(final List<Sequence> symbols, final String name, final long kind,
            final int line, final int column, final String detail) throws XPathException {
        final MapType symbol = new MapType(this, context);
        symbol.add(new StringValue(this, "name"), new StringValue(this, name));
        symbol.add(new StringValue(this, "kind"), new IntegerValue(this, kind));
        symbol.add(new StringValue(this, "line"), new IntegerValue(this, line));
        symbol.add(new StringValue(this, "column"), new IntegerValue(this, column));
        symbol.add(new StringValue(this, "detail"), new StringValue(this, detail));
        symbols.add(symbol);
    }

    private static String formatFunctionName(final org.exist.dom.QName name, final int arity) {
        return formatQName(name) + "#" + arity;
    }

    private static String formatQName(final org.exist.dom.QName name) {
        final String prefix = name.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + name.getLocalPart();
        }
        return name.getLocalPart();
    }

    /**
     * Depth-first search for the first positive line number in an expression tree.
     * Used as fallback when the function's own line is not set.
     */
    private static int firstPositiveLineIn(final Expression expr) {
        if (expr == null) {
            return -1;
        }
        final int line = expr.getLine();
        if (line > 0) {
            return line;
        }
        final int count = expr.getSubExpressionCount();
        for (int i = 0; i < count; i++) {
            final int sub = firstPositiveLineIn(expr.getSubExpression(i));
            if (sub > 0) {
                return sub;
            }
        }
        return -1;
    }
}
