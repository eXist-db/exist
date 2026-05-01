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
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * XPath/XQuery 4.0 context-item function expression: {@code fn { body }}.
 * Produces a single-arity function whose argument is bound as the body's
 * context item, so the dot ({@code .}) inside {@code body} resolves to the
 * call's argument. Equivalent to {@code function($_) { body-with-.-bound }}
 * but without rewriting the body AST.
 *
 * Spec: XQuery 4.0 PR1499 (ContextItemFunctionExpr).
 */
public class ContextItemFunctionExpr extends AbstractExpression {

    private static final QName ARG_QNAME = new QName("_", null, null);

    private final Expression body;
    private InlineFunction inlineFunction;
    private AnalyzeContextInfo cachedContextInfo;

    public ContextItemFunctionExpr(final XQueryContext context, final Expression body) {
        super(context);
        this.body = body;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        cachedContextInfo.setParent(this);
        // Build a one-arg user-defined function whose body is the supplied
        // expression. The function sets the context item to its argument
        // before evaluating the body, so SELF (`.`) inside the body resolves
        // to the call's argument.
        final FunctionSignature sig = new FunctionSignature(
                InlineFunction.INLINE_FUNCTION_QNAME,
                new SequenceType[]{
                        new FunctionParameterSequenceType("_", Type.ITEM,
                                Cardinality.ZERO_OR_MORE,
                                "context-item function argument")
                },
                new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
        final ContextItemFunctionDef function = new ContextItemFunctionDef(context, sig, body);
        function.addVariable(ARG_QNAME);
        function.setFunctionBody(body);
        function.setLocation(getLine(), getColumn());
        inlineFunction = new InlineFunction(context, function);
        inlineFunction.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        return inlineFunction.eval(contextSequence, contextItem);
    }

    @Override
    public int returnsType() {
        return Type.FUNCTION;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("fn { ");
        body.dump(dumper);
        dumper.display(" }");
    }

    @Override
    public String toString() {
        return "fn { " + body.toString() + " }";
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        body.resetState(postOptimization);
        if (inlineFunction != null) {
            inlineFunction.resetState(postOptimization);
        }
    }

    /**
     * UserDefinedFunction subclass that, on each call, binds the first argument
     * as the body's context item. The body sees the arg through SELF (`.`)
     * rather than through a {@code $_} variable, matching XQ4 PR1499 semantics.
     */
    private static final class ContextItemFunctionDef extends UserDefinedFunction {

        private final Expression userBody;

        ContextItemFunctionDef(final XQueryContext context, final FunctionSignature signature,
                final Expression body) {
            super(context, signature);
            this.userBody = body;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence[] args = getCurrentArguments();
            final Sequence argSequence = (args != null && args.length > 0)
                    ? args[0]
                    : Sequence.EMPTY_SEQUENCE;
            final Item argItem = argSequence.isEmpty() ? null : argSequence.itemAt(0);
            context.stackEnter(this);
            final LocalVariable mark = context.markLocalVariables(true);
            final java.util.List<ClosureVariable> closure = getClosureVariables();
            if (closure != null) {
                context.restoreStack(closure);
            }
            Sequence result = null;
            try {
                result = userBody.eval(argSequence, argItem);
                return result;
            } finally {
                context.popLocalVariables(mark, result);
                context.stackLeave(this);
            }
        }
    }
}
