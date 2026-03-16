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
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.DefaultExpressionVisitor;
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
import org.exist.xquery.value.ValueSequence;

import antlr.collections.AST;

import static org.exist.xquery.FunctionDSL.*;

/**
 * Finds all references to the symbol at a given position in an XQuery
 * expression, suitable for Language Server Protocol
 * {@code textDocument/references} responses.
 *
 * <p>Returns an array of maps, each with:</p>
 * <ul>
 *   <li>{@code line} — 0-based line of the reference</li>
 *   <li>{@code column} — 0-based column of the reference</li>
 *   <li>{@code name} — name of the referenced symbol</li>
 *   <li>{@code kind} — "function" or "variable"</li>
 * </ul>
 *
 * <p>Includes the definition itself if {@code includeDeclaration} is true.</p>
 *
 * @author eXist-db
 */
public class References extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(References.class);

    private static final String FS_REFERENCES_NAME = "references";
    private static final String FS_REFERENCES_DESCRIPTION = """
            Finds all references to the symbol at the given position. \
            Returns an array of maps with keys: line (xs:integer, 0-based), \
            column (xs:integer, 0-based), name (xs:string), and kind \
            (xs:string, "function" or "variable"). Returns an empty \
            array if no symbol is found at the position.""";

    public static final FunctionSignature[] FS_REFERENCES = functionSignatures(
            LspModule.qname(FS_REFERENCES_NAME),
            FS_REFERENCES_DESCRIPTION,
            returns(Type.ARRAY_ITEM, "an array of reference location maps"),
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

    public References(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String expr = args[0].getStringValue();
        final int targetLine = ((IntegerValue) args[1].itemAt(0)).getInt() + 1;
        final int targetColumn = ((IntegerValue) args[2].itemAt(0)).getInt() + 1;

        if (expr.trim().isEmpty()) {
            return new ValueSequence();
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
                    return new ValueSequence();
                }

                // Step 1: Find the symbol at the cursor position
                final Hover.NodeAtPositionFinder finder =
                        new Hover.NodeAtPositionFinder(targetLine, targetColumn);
                path.accept(finder);
                final Iterator<UserDefinedFunction> localFuncs = pContext.localFunctions();
                while (localFuncs.hasNext()) {
                    localFuncs.next().getFunctionBody().accept(finder);
                }

                final Expression found = finder.foundExpression;
                if (found == null) {
                    return new ValueSequence();
                }

                // Step 2: Determine what we're looking for
                if (found instanceof final FunctionCall call) {
                    return findFunctionReferences(call, path, pContext);
                } else if (found instanceof final VariableReference varRef) {
                    return findVariableReferences(varRef, path, pContext);
                }
            } finally {
                context.popNamespaceContext();
                pContext.reset(false);
            }
        } finally {
            pContext.runCleanupTasks();
        }

        return new ValueSequence();
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
            logger.debug("Error compiling expression for references: {}", e.getMessage());
            return null;
        }
    }

    private Sequence findFunctionReferences(final FunctionCall call, final PathExpr path,
            final XQueryContext pContext) throws XPathException {
        final UserDefinedFunction targetFunc = call.getFunction();
        if (targetFunc == null) {
            return new ValueSequence();
        }

        final QName targetName = targetFunc.getSignature().getName();
        final int targetArity = targetFunc.getSignature().getArgumentCount();

        // Collect all calls to the same function
        final FunctionReferenceCollector collector =
                new FunctionReferenceCollector(targetName, targetArity);
        path.accept(collector);
        final Iterator<UserDefinedFunction> localFuncs = pContext.localFunctions();
        while (localFuncs.hasNext()) {
            final UserDefinedFunction udf = localFuncs.next();
            udf.getFunctionBody().accept(collector);
            // Include the declaration itself
            if (targetName.equals(udf.getSignature().getName())
                    && targetArity == udf.getSignature().getArgumentCount()) {
                final int line = udf.getLine();
                if (line > 0) {
                    collector.locations.add(new RefLocation(
                            line - 1, Math.max(udf.getColumn() - 1, 0),
                            formatQName(targetName) + "#" + targetArity, "function"));
                }
            }
        }

        return buildResult(collector.locations);
    }

    private Sequence findVariableReferences(final VariableReference varRef, final PathExpr path,
            final XQueryContext pContext) throws XPathException {
        final QName targetName = varRef.getName();

        // Collect all references to the same variable
        final VariableReferenceCollector collector = new VariableReferenceCollector(targetName);
        path.accept(collector);
        final Iterator<UserDefinedFunction> localFuncs = pContext.localFunctions();
        while (localFuncs.hasNext()) {
            localFuncs.next().getFunctionBody().accept(collector);
        }

        // Include the declaration
        for (int i = 0; i < path.getSubExpressionCount(); i++) {
            final Expression step = path.getSubExpression(i);
            if (step instanceof final VariableDeclaration varDecl) {
                if (targetName.equals(varDecl.getName()) && varDecl.getLine() > 0) {
                    collector.locations.add(new RefLocation(
                            varDecl.getLine() - 1, Math.max(varDecl.getColumn() - 1, 0),
                            "$" + formatQName(targetName), "variable"));
                }
            }
        }

        return buildResult(collector.locations);
    }

    private Sequence buildResult(final List<RefLocation> locations) throws XPathException {
        final ValueSequence result = new ValueSequence();
        for (final RefLocation loc : locations) {
            final MapType map = new MapType(this, context);
            map.add(new StringValue(this, "line"), new IntegerValue(this, loc.line));
            map.add(new StringValue(this, "column"), new IntegerValue(this, loc.column));
            map.add(new StringValue(this, "name"), new StringValue(this, loc.name));
            map.add(new StringValue(this, "kind"), new StringValue(this, loc.kind));
            result.add(map);
        }
        return result;
    }

    private static String formatQName(final QName name) {
        final String prefix = name.getPrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + ":" + name.getLocalPart();
        }
        return name.getLocalPart();
    }

    // --- Helper classes ---

    private record RefLocation(int line, int column, String name, String kind) {}

    /**
     * Collects all FunctionCall nodes that call the target function.
     */
    private static class FunctionReferenceCollector extends DefaultExpressionVisitor {
        private final QName targetName;
        private final int targetArity;
        final List<RefLocation> locations = new ArrayList<>();

        FunctionReferenceCollector(final QName targetName, final int targetArity) {
            this.targetName = targetName;
            this.targetArity = targetArity;
        }

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            final UserDefinedFunction func = call.getFunction();
            if (func != null) {
                final QName name = func.getSignature().getName();
                if (targetName.equals(name)
                        && targetArity == func.getSignature().getArgumentCount()
                        && call.getLine() > 0) {
                    locations.add(new RefLocation(
                            call.getLine() - 1, Math.max(call.getColumn() - 1, 0),
                            formatQName(targetName) + "#" + targetArity, "function"));
                }
            }
            super.visitFunctionCall(call);
        }

        @Override
        public void visit(final Expression expression) {
            for (int i = 0; i < expression.getSubExpressionCount(); i++) {
                expression.getSubExpression(i).accept(this);
            }
        }
    }

    /**
     * Collects all VariableReference nodes that refer to the target variable.
     */
    private static class VariableReferenceCollector extends DefaultExpressionVisitor {
        private final QName targetName;
        final List<RefLocation> locations = new ArrayList<>();

        VariableReferenceCollector(final QName targetName) {
            this.targetName = targetName;
        }

        @Override
        public void visitVariableReference(final VariableReference ref) {
            if (targetName.equals(ref.getName()) && ref.getLine() > 0) {
                locations.add(new RefLocation(
                        ref.getLine() - 1, Math.max(ref.getColumn() - 1, 0),
                        "$" + formatQName(targetName), "variable"));
            }
        }

        @Override
        public void visit(final Expression expression) {
            for (int i = 0; i < expression.getSubExpressionCount(); i++) {
                expression.getSubExpression(i).accept(this);
            }
        }
    }
}
