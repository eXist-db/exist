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
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Compiles an XQuery expression and returns diagnostic information
 * in a format suitable for Language Server Protocol consumers.
 *
 * <p>Returns an array of maps, where each map represents a diagnostic with
 * the following keys:</p>
 * <ul>
 *   <li>{@code line} — 0-based line number</li>
 *   <li>{@code column} — 0-based column number</li>
 *   <li>{@code severity} — LSP severity: 1 (error), 2 (warning), 3 (info), 4 (hint)</li>
 *   <li>{@code code} — W3C error code (e.g., "XPST0003")</li>
 *   <li>{@code message} — human-readable error description</li>
 * </ul>
 *
 * <p>Returns an empty array if the expression compiles successfully.</p>
 *
 * @author eXist-db
 */
public class Diagnostics extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Diagnostics.class);

    /** LSP DiagnosticSeverity.Error */
    private static final long SEVERITY_ERROR = 1;

    private static final String FS_DIAGNOSTICS_NAME = "diagnostics";
    private static final String FS_DIAGNOSTICS_DESCRIPTION = """
            Compiles the XQuery expression and returns an array of diagnostic maps. \
            Each map contains keys: line (xs:integer, 0-based), column (xs:integer, 0-based), \
            severity (xs:integer, 1=error), code (xs:string, W3C error code), and \
            message (xs:string). Returns an empty array if compilation succeeds.""";

    public static final FunctionSignature[] FS_DIAGNOSTICS = functionSignatures(
            LspModule.qname(FS_DIAGNOSTICS_NAME),
            FS_DIAGNOSTICS_DESCRIPTION,
            returns(Type.ARRAY_ITEM, "an array of diagnostic maps"),
            arities(
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to compile.")
                    ),
                    arity(
                            param("expression", Type.STRING, "The XQuery expression to compile."),
                            optParam("module-load-path", Type.STRING, "The module load path. " +
                                    "Imports will be resolved relative to this. " +
                                    "Use xmldb:exist:///db for database-stored modules.")
                    )
            )
    );

    public Diagnostics(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        if (expr.trim().isEmpty()) {
            return new ArrayType(this, context, new ArrayList<>());
        }

        final List<Sequence> diagnostics = new ArrayList<>();

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        try {
            if (getArgumentCount() == 2 && args[1].hasOne()) {
                pContext.setModuleLoadPath(args[1].getStringValue());
            }

            context.pushNamespaceContext();
            try {
                compile(pContext, expr, diagnostics);
            } finally {
                context.popNamespaceContext();
                pContext.reset(false);
            }
        } finally {
            pContext.runCleanupTasks();
        }

        return new ArrayType(this, context, diagnostics);
    }

    /**
     * Compiles the expression and collects diagnostics.
     *
     * <p>Currently reports the first error encountered. The eXist-db parser
     * does not support error recovery, so compilation stops at the first
     * failure. Future versions may collect multiple diagnostics.</p>
     */
    private void compile(final XQueryContext pContext, final String expr,
            final List<Sequence> diagnostics) throws XPathException {
        try {
            final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(expr));
            final XQueryParser parser = new XQueryParser(lexer);
            final XQueryTreeParser astParser = new XQueryTreeParser(pContext);

            parser.xpath();
            if (parser.foundErrors()) {
                logger.debug(parser.getErrorMessage());
                final Exception lastEx = parser.getLastException();
                if (lastEx instanceof final RecognitionException re) {
                    addDiagnostic(diagnostics, re.getLine(), re.getColumn(), null, parser.getErrorMessage());
                } else if (lastEx instanceof final XPathException xpe) {
                    addDiagnostic(diagnostics, xpe.getLine(), xpe.getColumn(), xpe.getCode(), parser.getErrorMessage());
                } else {
                    addDiagnostic(diagnostics, -1, -1, null, parser.getErrorMessage());
                }
                return;
            }

            final AST ast = parser.getAST();
            final PathExpr path = new PathExpr(pContext);
            astParser.xpath(ast, path);
            if (astParser.foundErrors()) {
                final Exception lastException = astParser.getLastException();
                if (lastException instanceof final XPathException xpe) {
                    addDiagnostic(diagnostics,
                            xpe.getLine(),
                            xpe.getColumn(),
                            xpe.getCode(),
                            xpe.getDetailMessage());
                } else if (lastException != null) {
                    addDiagnostic(diagnostics, -1, -1, null, lastException.getMessage());
                }
                return;
            }

            path.analyze(new AnalyzeContextInfo());

        } catch (final RecognitionException e) {
            addDiagnostic(diagnostics, e.getLine(), e.getColumn(), null, e.getMessage());
        } catch (final TokenStreamException e) {
            addDiagnostic(diagnostics, -1, -1, null, e.getMessage());
        } catch (final XPathException e) {
            addDiagnostic(diagnostics, e.getLine(), e.getColumn(), e.getCode(), e.getDetailMessage());
        }
    }

    /**
     * Creates a diagnostic map and adds it to the list.
     */
    private void addDiagnostic(final List<Sequence> diagnostics, final int line, final int column,
            final ErrorCodes.ErrorCode code, final String message) throws XPathException {
        final MapType diagnostic = new MapType(this, context);
        diagnostic.add(new StringValue(this, "line"), new IntegerValue(this, Math.max(line, 0)));
        diagnostic.add(new StringValue(this, "column"), new IntegerValue(this, Math.max(column, 0)));
        diagnostic.add(new StringValue(this, "severity"), new IntegerValue(this, SEVERITY_ERROR));
        diagnostic.add(new StringValue(this, "code"),
                new StringValue(this, code == null ? "" : code.toString()));
        diagnostic.add(new StringValue(this, "message"),
                new StringValue(this, message == null ? "Unknown error" : message));
        diagnostics.add(diagnostic);
    }
}
