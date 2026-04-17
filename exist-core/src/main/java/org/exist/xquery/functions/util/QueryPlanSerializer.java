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

import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.*;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;

/**
 * Serializes a compiled XQuery expression tree as XML.
 * Extends DefaultExpressionVisitor to walk the tree and emit XML elements
 * for each expression type.
 *
 * <p>Output namespace: http://exist-db.org/xquery/profiling</p>
 */
public class QueryPlanSerializer extends DefaultExpressionVisitor {

    public static final String NAMESPACE = "http://exist-db.org/xquery/profiling";

    private final MemTreeBuilder builder;

    public QueryPlanSerializer(final MemTreeBuilder builder) {
        this.builder = builder;
    }

    // === Helper methods ===

    private void startElement(final String name) {
        builder.startElement("", name, name, null);
    }

    private void startElement(final String name, final String[][] attrs) {
        final org.xml.sax.helpers.AttributesImpl atts = new org.xml.sax.helpers.AttributesImpl();
        for (final String[] attr : attrs) {
            if (attr[1] != null) {
                atts.addAttribute("", attr[0], attr[0], "CDATA", attr[1]);
            }
        }
        builder.startElement("", name, name, atts);
    }

    private void endElement() {
        builder.endElement();
    }

    private String[][] locationAttrs(final Expression expr) {
        if (expr.getLine() > 0) {
            return new String[][]{
                    {"line", String.valueOf(expr.getLine())},
                    {"column", String.valueOf(expr.getColumn())}
            };
        }
        return new String[0][];
    }

    private String[][] mergeAttrs(final String[][]... attrSets) {
        int total = 0;
        for (final String[][] set : attrSets) total += set.length;
        final String[][] result = new String[total][];
        int idx = 0;
        for (final String[][] set : attrSets) {
            System.arraycopy(set, 0, result, idx, set.length);
            idx += set.length;
        }
        return result;
    }

    // === Visitor methods ===

    @Override
    public void visit(final Expression expression) {
        final String className = expression.getClass().getSimpleName();
        if (className.isEmpty()) {
            startElement("expression", mergeAttrs(
                    new String[][]{{"class", expression.getClass().getName()}},
                    locationAttrs(expression)));
        } else {
            startElement("expression", mergeAttrs(
                    new String[][]{{"type", className}},
                    locationAttrs(expression)));
        }
        endElement();
    }

    @Override
    public void visitPathExpr(final PathExpr expression) {
        if (expression.getLength() == 1) {
            // Unwrap single-step path expressions
            expression.getExpression(0).accept(this);
            return;
        }
        startElement("path", locationAttrs(expression));
        for (int i = 0; i < expression.getLength(); i++) {
            expression.getExpression(i).accept(this);
        }
        endElement();
    }

    @Override
    public void visitLocationStep(final LocationStep locationStep) {
        startElement("step", mergeAttrs(
                new String[][]{
                        {"axis", org.exist.xquery.Constants.AXISSPECIFIERS[locationStep.getAxis()]},
                        {"test", locationStep.getTest().toString()}
                },
                locationAttrs(locationStep)));
        @Nullable final Predicate[] predicates = locationStep.getPredicates();
        if (predicates != null) {
            for (final Predicate pred : predicates) {
                pred.accept(this);
            }
        }
        endElement();
    }

    @Override
    public void visitFilteredExpr(final FilteredExpression filtered) {
        startElement("filter", locationAttrs(filtered));
        filtered.getExpression().accept(this);
        for (final Predicate pred : filtered.getPredicates()) {
            pred.accept(this);
        }
        endElement();
    }

    @Override
    public void visitPredicate(final Predicate predicate) {
        startElement("predicate", locationAttrs(predicate));
        predicate.getExpression(0).accept(this);
        endElement();
    }

    @Override
    public void visitFunctionCall(final FunctionCall call) {
        final String name = call.getFunction().getName().getStringValue();
        startElement("function-call", mergeAttrs(
                new String[][]{
                        {"name", name},
                        {"arity", String.valueOf(call.getArgumentCount())}
                },
                locationAttrs(call)));
        for (int i = 0; i < call.getArgumentCount(); i++) {
            call.getArgument(i).accept(this);
        }
        endElement();
    }

    @Override
    public void visitBuiltinFunction(final Function function) {
        startElement("builtin-function", mergeAttrs(
                new String[][]{
                        {"name", function.getName().getStringValue()},
                        {"arity", String.valueOf(function.getArgumentCount())}
                },
                locationAttrs(function)));
        for (int i = 0; i < function.getArgumentCount(); i++) {
            function.getArgument(i).accept(this);
        }
        endElement();
    }

    @Override
    public void visitUserFunction(final UserDefinedFunction function) {
        startElement("user-function", new String[][]{
                {"name", function.getName().getStringValue()},
                {"arity", String.valueOf(function.getSignature().getArgumentCount())}
        });
        function.getFunctionBody().accept(this);
        endElement();
    }

    @Override
    public void visitForExpression(final ForExpr forExpr) {
        startElement("for", mergeAttrs(
                new String[][]{{"variable", "$" + forExpr.getVariable().getStringValue()}},
                locationAttrs(forExpr)));
        startElement("in");
        forExpr.getInputSequence().accept(this);
        endElement();
        forExpr.getReturnExpression().accept(this);
        endElement();
    }

    @Override
    public void visitLetExpression(final LetExpr letExpr) {
        startElement("let", mergeAttrs(
                new String[][]{{"variable", "$" + letExpr.getVariable().getStringValue()}},
                locationAttrs(letExpr)));
        startElement("value");
        letExpr.getInputSequence().accept(this);
        endElement();
        letExpr.getReturnExpression().accept(this);
        endElement();
    }

    @Override
    public void visitWhereClause(final WhereClause where) {
        startElement("where", locationAttrs(where));
        where.getWhereExpr().accept(this);
        endElement();
        where.getReturnExpression().accept(this);
    }

    @Override
    public void visitOrderByClause(final OrderByClause orderBy) {
        startElement("order-by", locationAttrs(orderBy));
        for (final OrderSpec spec : orderBy.getOrderSpecs()) {
            startElement("order-spec");
            spec.getSortExpression().accept(this);
            endElement();
        }
        endElement();
        orderBy.getReturnExpression().accept(this);
    }

    @Override
    public void visitGroupByClause(final GroupByClause groupBy) {
        startElement("group-by", locationAttrs(groupBy));
        for (final GroupSpec spec : groupBy.getGroupSpecs()) {
            startElement("group-spec");
            spec.getGroupExpression().accept(this);
            endElement();
        }
        endElement();
        groupBy.getReturnExpression().accept(this);
    }

    @Override
    public void visitGeneralComparison(final GeneralComparison comparison) {
        startElement("comparison", mergeAttrs(
                new String[][]{{"operator", comparison.toString().contains("=") ? "eq" : "cmp"}},
                locationAttrs(comparison)));
        comparison.getLeft().accept(this);
        comparison.getRight().accept(this);
        endElement();
    }

    @Override
    public void visitAndExpr(final OpAnd and) {
        startElement("and", locationAttrs(and));
        and.getLeft().accept(this);
        and.getRight().accept(this);
        endElement();
    }

    @Override
    public void visitOrExpr(final OpOr or) {
        startElement("or", locationAttrs(or));
        or.getLeft().accept(this);
        or.getRight().accept(this);
        endElement();
    }

    @Override
    public void visitConditional(final ConditionalExpression conditional) {
        startElement("if", locationAttrs(conditional));
        startElement("test");
        conditional.getTestExpr().accept(this);
        endElement();
        startElement("then");
        conditional.getThenExpr().accept(this);
        endElement();
        startElement("else");
        conditional.getElseExpr().accept(this);
        endElement();
        endElement();
    }

    @Override
    public void visitCastExpr(final CastExpression expression) {
        startElement("cast", mergeAttrs(
                new String[][]{{"cardinality", expression.getCardinality().getHumanDescription()}},
                locationAttrs(expression)));
        endElement();
    }

    @Override
    public void visitUnionExpr(final Union union) {
        startElement("union", locationAttrs(union));
        union.getLeft().accept(this);
        union.getRight().accept(this);
        endElement();
    }

    @Override
    public void visitIntersectionExpr(final Intersect intersect) {
        startElement("intersect", locationAttrs(intersect));
        intersect.getLeft().accept(this);
        intersect.getRight().accept(this);
        endElement();
    }

    @Override
    public void visitVariableReference(final VariableReference ref) {
        startElement("variable", mergeAttrs(
                new String[][]{{"name", "$" + ref.getName().toString()}},
                locationAttrs(ref)));
        endElement();
    }

    @Override
    public void visitVariableDeclaration(final VariableDeclaration decl) {
        startElement("variable-declaration", mergeAttrs(
                new String[][]{{"name", "$" + decl.getName().toString()}},
                locationAttrs(decl)));
        decl.getExpression().ifPresent(e -> e.accept(this));
        endElement();
    }

    @Override
    public void visitDocumentConstructor(final DocumentConstructor constructor) {
        startElement("document-constructor", locationAttrs(constructor));
        constructor.getContent().accept(this);
        endElement();
    }

    @Override
    public void visitElementConstructor(final ElementConstructor constructor) {
        startElement("element-constructor", locationAttrs(constructor));
        constructor.getNameExpr().accept(this);
        if (constructor.getContent() != null) {
            constructor.getContent().accept(this);
        }
        endElement();
    }

    @Override
    public void visitTextConstructor(final DynamicTextConstructor constructor) {
        startElement("text-constructor", locationAttrs(constructor));
        constructor.getContent().accept(this);
        endElement();
    }

    @Override
    public void visitTryCatch(final TryCatchExpression tryCatch) {
        startElement("try-catch", locationAttrs(tryCatch));
        startElement("try");
        tryCatch.getTryTargetExpr().accept(this);
        endElement();
        for (final TryCatchExpression.CatchClause clause : tryCatch.getCatchClauses()) {
            startElement("catch");
            clause.getCatchExpr().accept(this);
            endElement();
        }
        endElement();
    }

    @Override
    public void visitSimpleMapOperator(final OpSimpleMap simpleMap) {
        startElement("simple-map", locationAttrs(simpleMap));
        simpleMap.getLeft().accept(this);
        simpleMap.getRight().accept(this);
        endElement();
    }
}
