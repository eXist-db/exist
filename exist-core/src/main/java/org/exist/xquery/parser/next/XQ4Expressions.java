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
package org.exist.xquery.parser.next;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.List;

/**
 * Stub XQuery 4.0 expression classes for the recursive descent parser prototype.
 *
 * <p>These stub classes allow the parser to build complete parse trees for XQ4
 * syntax on an eXist build that doesn't yet include the XQ4 expression
 * implementations (e.g., a standalone {@code develop}-based build). They
 * throw {@link XPathException} at evaluation time.</p>
 *
 * <p>When integrating with a build that includes {@code v2/xquery-4.0-parser},
 * replace these stubs with imports from the real {@code org.exist.xquery}
 * package.</p>
 */
public final class XQ4Expressions {

    private XQ4Expressions() {}

    /**
     * XQ4 {@code otherwise} expression: {@code expr1 otherwise expr2}.
     * Returns {@code expr2} when {@code expr1} is the empty sequence.
     */
    public static class OtherwiseExpression extends AbstractExpression {
        private final Expression left;
        private final Expression right;

        public OtherwiseExpression(XQueryContext context, Expression left, Expression right) {
            super(context);
            this.left = left;
            this.right = right;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            final Sequence leftResult = left.eval(contextSequence, contextItem);
            if (!leftResult.isEmpty()) {
                return leftResult;
            }
            return right.eval(contextSequence, contextItem);
        }

        @Override public int returnsType() { return Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("otherwise"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {
            left.analyze(ci);
            right.analyze(ci);
        }
    }

    /**
     * XQ4 {@code while} FLWOR clause: {@code while condition}.
     * Extends {@link WhereClause} since it shares the condition-predicate structure.
     */
    public static class WhileClause extends WhereClause {
        public WhileClause(XQueryContext context, Expression condition) {
            super(context, condition);
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 while clause requires xquery-4.0-parser runtime");
        }

        @Override public void dump(ExpressionDumper d) { d.display("while"); }
    }

    /**
     * XQ4 {@code for member $x in array} binding.
     * Extends {@link ForExpr} since it shares the binding structure.
     */
    public static class ForMemberExpr extends ForExpr {
        public ForMemberExpr(XQueryContext context) {
            super(context, false);
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 for-member clause requires xquery-4.0-parser runtime");
        }

        @Override public void dump(ExpressionDumper d) { d.display("for member"); }
    }

    /**
     * XQ4 focus function: {@code .{ expr }}.
     * Wraps an inline function with a focus parameter.
     */
    public static class FocusFunction extends AbstractExpression {
        public static final String FOCUS_PARAM_NAME = "$$focus";
        private final UserDefinedFunction func;

        public FocusFunction(XQueryContext context, UserDefinedFunction func) {
            super(context);
            this.func = func;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 focus function requires xquery-4.0-parser runtime");
        }

        @Override public int returnsType() { return Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display(".{...}"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {}
    }

    /**
     * XQ4 keyword argument: {@code name: expr}.
     * Represents a named argument in a function call.
     */
    public static class KeywordArgumentExpression extends AbstractExpression {
        private final String keyName;
        private final Expression value;

        public KeywordArgumentExpression(XQueryContext context, String keyName, Expression value) {
            super(context);
            this.keyName = keyName;
            this.value = value;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 keyword argument requires xquery-4.0-parser runtime");
        }

        @Override public int returnsType() { return Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display(keyName + ":"); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {
            value.analyze(ci);
        }
    }

    /**
     * XQ4 mapping arrow operator: {@code expr =!> func()}.
     * Like the regular arrow ({@code =>}) but applies to each item in the sequence.
     */
    public static class MappingArrowOperator extends ArrowOperator {
        public MappingArrowOperator(XQueryContext context, Expression leftExpr) throws XPathException {
            super(context, leftExpr);
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 mapping arrow requires xquery-4.0-parser runtime");
        }

        @Override public void dump(ExpressionDumper d) { d.display("=!>"); }
    }

    /**
     * XQ4 method call operator: {@code expr->method(args)}.
     * Pipeline-style method call on a sequence.
     */
    public static class MethodCallOperator extends AbstractExpression {
        private final Expression source;
        private String methodName;
        private List<Expression> args;

        public MethodCallOperator(XQueryContext context, Expression source) {
            super(context);
            this.source = source;
        }

        public void setMethod(String methodName, List<Expression> args) {
            this.methodName = methodName;
            this.args = args;
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            throw new XPathException(this, "XQ4 method call requires xquery-4.0-parser runtime");
        }

        @Override public int returnsType() { return Type.ITEM; }
        @Override public void dump(ExpressionDumper d) { d.display("->" + methodName); }
        @Override public void analyze(AnalyzeContextInfo ci) throws XPathException {
            source.analyze(ci);
        }
    }
}
