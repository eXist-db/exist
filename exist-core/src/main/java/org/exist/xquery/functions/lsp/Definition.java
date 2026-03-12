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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Expression;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.PathExpr;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.VariableDeclaration;
import org.exist.xquery.VariableReference;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.collections.AST;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Returns the definition location of the symbol at a given position in an
 * XQuery expression, suitable for Language Server Protocol
 * {@code textDocument/definition} responses.
 *
 * <p>Returns a map with the following keys:</p>
 * <ul>
 *   <li>{@code line} — 0-based line of the definition</li>
 *   <li>{@code column} — 0-based column of the definition</li>
 *   <li>{@code name} — name of the defined symbol</li>
 *   <li>{@code kind} — what was found: "function" or "variable"</li>
 * </ul>
 *
 * <p>Returns an empty sequence if the symbol at the position is not a
 * user-declared function or variable, or if no symbol is found.</p>
 *
 * @author eXist-db
 */
public class Definition extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Definition.class);

    private static final String FS_DEFINITION_NAME = "definition";
    private static final String FS_DEFINITION_DESCRIPTION = """
            Returns the definition location of the symbol at the given \
            position. Returns a map with keys: line (xs:integer, 0-based), \
            column (xs:integer, 0-based), name (xs:string), and kind \
            (xs:string, "function" or "variable"). Returns an empty \
            sequence if no user-declared definition is found.""";

    public static final FunctionSignature[] FS_DEFINITION = functionSignatures(
            LspModule.qname(FS_DEFINITION_NAME),
            FS_DEFINITION_DESCRIPTION,
            returns(Type.MAP_ITEM, "a definition location map, or empty sequence"),
            arities(
                    arity(
                            param("expression", Type.STRING, "The XQuery expression."),
                            param("line", Type.INTEGER, "0-based line number."),
                            param("column", Type.INTEGER, "0-based column number.")
                    ),
                    arity(
                            param("expression", Type.STRING, "The XQuery expression."),
                            param("line", Type.INTEGER, "0-based line number."),
                            param("column", Type.INTEGER, "0-based column number."),
                            optParam("module-load-path", Type.STRING, "The module load path.")
                    )
            )
    );

    public Definition(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        final int targetLine = ((IntegerValue) args[1].itemAt(0)).getInt() + 1;
        final int targetColumn = ((IntegerValue) args[2].itemAt(0)).getInt() + 1;

        if (expr.trim().isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final XQueryContext pContext = new XQueryContext(context.getBroker().getBrokerPool());
        try {
            if (getArgumentCount() == 4 && args[3].hasOne()) {
                pContext.setModuleLoadPath(args[3].getStringValue());
            }

            context.pushNamespaceContext();
            try {
                final PathExpr path = compile(pContext, expr);
                if (path == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }

                // Find the node at the cursor position
                final Hover.NodeAtPositionFinder finder =
                        new Hover.NodeAtPositionFinder(targetLine, targetColumn);
                path.accept(finder);

                // Also traverse user-defined function bodies (needed for library
                // modules where function bodies aren't part of the main PathExpr)
                final java.util.Iterator<UserDefinedFunction> localFuncs = pContext.localFunctions();
                while (localFuncs.hasNext()) {
                    localFuncs.next().getFunctionBody().accept(finder);
                }

                final Expression found = finder.foundExpression;
                if (found == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }

                // Resolve to definition
                if (found instanceof final FunctionCall call) {
                    return resolveFunctionDefinition(call);
                } else if (found instanceof final VariableReference varRef) {
                    return resolveVariableDefinition(varRef, path);
                }
            } finally {
                context.popNamespaceContext();
                pContext.reset(false);
            }
        } finally {
            pContext.runCleanupTasks();
        }

        return Sequence.EMPTY_SEQUENCE;
    }

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
            logger.debug("Error compiling expression for definition: {}", e.getMessage());
            return null;
        }
    }

    private Sequence resolveFunctionDefinition(final FunctionCall call) throws XPathException {
        final UserDefinedFunction func = call.getFunction();
        if (func == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        int line = func.getLine();
        final int column = func.getColumn();

        if (line <= 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final QName name = func.getSignature().getName();
        final String displayName = formatQName(name) + "#" + func.getSignature().getArgumentCount();

        return buildResult(line - 1, Math.max(column - 1, 0), displayName, "function");
    }

    private Sequence resolveVariableDefinition(final VariableReference varRef,
            final PathExpr path) throws XPathException {
        final QName refName = varRef.getName();

        // Search for matching VariableDeclaration in the prolog
        for (int i = 0; i < path.getSubExpressionCount(); i++) {
            final Expression step = path.getSubExpression(i);
            if (step instanceof final VariableDeclaration varDecl) {
                if (refName.equals(varDecl.getName())) {
                    final int line = varDecl.getLine();
                    if (line <= 0) {
                        continue;
                    }
                    final String displayName = "$" + formatQName(varDecl.getName());
                    return buildResult(line - 1, Math.max(varDecl.getColumn() - 1, 0),
                            displayName, "variable");
                }
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence buildResult(final int line, final int column,
            final String name, final String kind) throws XPathException {
        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "line"), new IntegerValue(this, line));
        result.add(new StringValue(this, "column"), new IntegerValue(this, column));
        result.add(new StringValue(this, "name"), new StringValue(this, name));
        result.add(new StringValue(this, "kind"), new StringValue(this, kind));
        return result;
    }

    private static String formatQName(final QName name) {
        final String prefix = name.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + name.getLocalPart();
        }
        return name.getLocalPart();
    }

}
