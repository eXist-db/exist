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
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.DefaultExpressionVisitor;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.InternalFunctionCall;
import org.exist.xquery.PathExpr;
import org.exist.xquery.UserDefinedFunction;
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
 * Returns hover information for the symbol at a given position in an XQuery
 * expression, suitable for Language Server Protocol {@code textDocument/hover}
 * responses.
 *
 * <p>Returns a map with the following keys:</p>
 * <ul>
 *   <li>{@code contents} — hover text (signature and/or documentation)</li>
 *   <li>{@code kind} — what was found: "function" or "variable"</li>
 * </ul>
 *
 * <p>Returns an empty sequence if nothing is found at the given position.</p>
 *
 * @author eXist-db
 */
public class Hover extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Hover.class);

    private static final String FS_HOVER_NAME = "hover";
    private static final String FS_HOVER_DESCRIPTION = """
            Returns hover information for the symbol at the given position \
            in the XQuery expression. Returns a map with keys: contents \
            (xs:string, signature and documentation) and kind (xs:string, \
            "function" or "variable"). Returns an empty sequence if no \
            symbol is found at the position.""";

    public static final FunctionSignature[] FS_HOVER = functionSignatures(
            LspModule.qname(FS_HOVER_NAME),
            FS_HOVER_DESCRIPTION,
            returns(Type.MAP_ITEM, "a hover info map, or empty sequence"),
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

    public Hover(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        // Convert 0-based LSP position to 1-based parser position
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

                final NodeAtPositionFinder finder = new NodeAtPositionFinder(targetLine, targetColumn);
                path.accept(finder);

                // Also traverse user-defined function bodies (needed for library
                // modules where function bodies aren't part of the main PathExpr)
                final Iterator<UserDefinedFunction> localFuncs = pContext.localFunctions();
                while (localFuncs.hasNext()) {
                    localFuncs.next().getFunctionBody().accept(finder);
                }

                if (finder.foundExpression != null) {
                    return buildHoverResult(finder.foundExpression);
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
            logger.debug("Error compiling expression for hover: {}", e.getMessage());
            return null;
        }
    }

    private Sequence buildHoverResult(final Expression expr) throws XPathException {
        if (expr instanceof final FunctionCall call) {
            return buildFunctionHover(call.getSignature(), call.getFunction());
        } else if (expr instanceof final Function func) {
            // Built-in function (inner function from InternalFunctionCall)
            return buildFunctionHover(func.getSignature(), null);
        } else if (expr instanceof final VariableReference varRef) {
            return buildVariableHover(varRef);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence buildFunctionHover(final FunctionSignature sig,
            final UserDefinedFunction udf) throws XPathException {
        final StringBuilder contents = new StringBuilder();
        contents.append(sig.toString());

        final String description = sig.getDescription();
        if (description != null && !description.isEmpty()) {
            contents.append("\n\n").append(description);
        }

        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "contents"), new StringValue(this, contents.toString()));
        result.add(new StringValue(this, "kind"), new StringValue(this, "function"));
        return result;
    }

    private Sequence buildVariableHover(final VariableReference varRef) throws XPathException {
        final org.exist.dom.QName name = varRef.getName();
        final String prefix = name.getPrefix();
        final String varName = (prefix != null && !prefix.isEmpty())
                ? "$" + prefix + ":" + name.getLocalPart()
                : "$" + name.getLocalPart();

        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "contents"), new StringValue(this, varName));
        result.add(new StringValue(this, "kind"), new StringValue(this, "variable"));
        return result;
    }

    /**
     * Visitor that traverses the full expression tree (including FLWOR clauses)
     * to find the best-matching FunctionCall, InternalFunctionCall, or
     * VariableReference at the given position.
     *
     * <p>Uses {@link DefaultExpressionVisitor} for traversal since it knows
     * how to enter FLWOR expressions, which don't expose children via
     * {@code getSubExpressionCount()}.</p>
     */
    static class NodeAtPositionFinder extends DefaultExpressionVisitor {
        private final int targetLine;
        private final int targetColumn;
        Expression foundExpression;
        private int bestColumn = -1;

        NodeAtPositionFinder(final int targetLine, final int targetColumn) {
            this.targetLine = targetLine;
            this.targetColumn = targetColumn;
        }

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            checkExpression(call);
            super.visitFunctionCall(call);
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            // The InternalFunctionCall wrapper delegates accept() to the inner
            // function, so we receive the inner BasicFunction here. Check position
            // on the inner function (which inherits position from the wrapper
            // via the compilation process).
            checkExpression(function);
            super.visitBuiltinFunction(function);
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            checkExpression(ref);
        }

        @Override
        public void visit(final Expression expression) {
            // Traverse children for generic expressions
            for (int i = 0; i < expression.getSubExpressionCount(); i++) {
                expression.getSubExpression(i).accept(this);
            }
        }

        private void checkExpression(final Expression expr) {
            final int line = expr.getLine();
            final int column = expr.getColumn();

            if (line == targetLine && column <= targetColumn && column > bestColumn) {
                foundExpression = expr;
                bestColumn = column;
            }
        }
    }
}
