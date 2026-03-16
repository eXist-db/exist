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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.ArrayDeque;
import java.util.List;

/**
 * Implements XQuery 4.0 focus functions: {@code fn { expr }} and {@code function { expr }}.
 *
 * <p>A focus function is an inline function with an implicit single parameter
 * of type {@code item()*}. When called, the argument is bound as the context
 * item for the body expression.</p>
 *
 * <p>Formally: {@code fn { EXPR }} is equivalent to
 * {@code function($dot as item()*) as item()* { EXPR }} where EXPR is
 * evaluated with the context value set to {@code $dot}.</p>
 */
public class FocusFunction extends AbstractExpression {

    public static final String FOCUS_PARAM_NAME = ".focus";

    private final UserDefinedFunction function;
    private final ArrayDeque<FunctionCall> calls = new ArrayDeque<>();
    private AnalyzeContextInfo cachedContextInfo;

    public FocusFunction(final XQueryContext context, final UserDefinedFunction function) {
        super(context);
        this.function = function;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        cachedContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        cachedContextInfo.setParent(this);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("fn ");
        function.dump(dumper);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem)
            throws XPathException {
        final List<ClosureVariable> closureVars = context.getLocalStack();

        final FunctionCall call = new FocusFunctionCall(context, function);
        call.getFunction().setClosureVariables(closureVars);
        call.setLocation(function.getLine(), function.getColumn());
        call.analyze(new AnalyzeContextInfo(cachedContextInfo));

        calls.push(call);

        return new FunctionReference(this, call);
    }

    @Override
    public int returnsType() {
        return Type.FUNCTION;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        calls.clear();
        function.resetState(postOptimization);
    }

    /**
     * A specialized FunctionCall that sets the argument as context item
     * before evaluating the function body.
     */
    public static class FocusFunctionCall extends FunctionCall {

        public FocusFunctionCall(final XQueryContext context, final UserDefinedFunction function) {
            super(context, function);
        }

        @Override
        public Sequence evalFunction(final Sequence contextSequence, final Item contextItem,
                final Sequence[] seq, final DocumentSet[] contextDocs) throws XPathException {
            // The focus function's single argument becomes the context item
            // for the body evaluation.
            final Sequence focusArg = (seq != null && seq.length > 0) ? seq[0] : Sequence.EMPTY_SEQUENCE;

            context.stackEnter(this);
            final LocalVariable mark = context.markLocalVariables(true);
            if (getFunction().getClosureVariables() != null) {
                context.restoreStack(getFunction().getClosureVariables());
            }
            try {
                // Bind the implicit parameter
                final UserDefinedFunction func = getFunction();
                if (!func.getParameters().isEmpty()) {
                    final LocalVariable var = new LocalVariable(
                            func.getParameters().get(0));
                    var.setValue(focusArg);
                    context.declareVariableBinding(var);
                }

                // Evaluate the body with the argument as context
                final Expression body = func.getFunctionBody();
                if (focusArg.getItemCount() == 1) {
                    return body.eval(focusArg, focusArg.itemAt(0));
                } else {
                    return body.eval(focusArg, null);
                }
            } finally {
                context.popLocalVariables(mark);
                context.stackLeave(this);
            }
        }
    }
}
