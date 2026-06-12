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
import org.exist.dom.QName.IllegalQNameException;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the XQuery 3.1 arrow operator.
 *
 * @author wolf
 */
public class ArrowOperator extends AbstractExpression {

    private QName qname = null;
    private Expression leftExpr;
    private Expression namedCall = null;
    private Expression funcSpec = null;
    private List<Expression> parameters;
    private AnalyzeContextInfo cachedContextInfo;

    public ArrowOperator(final XQueryContext context, final Expression leftExpr) throws
            XPathException {
        super(context);
        this.leftExpr = leftExpr;
    }

    public void setArrowFunction(final String fname, final List<Expression> params) throws XPathException {
        try {
            this.qname = QName.parse(context, fname, context.getDefaultFunctionNamespace());
            this.parameters = params;
            // defer resolving the function to analyze to make sure all functions are known
        } catch (final IllegalQNameException e) {
            throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + fname);
        }
    }

    public void setArrowFunction(final PathExpr funcSpec, final List<Expression> params) {
        this.funcSpec = funcSpec.simplify();
        this.parameters = params;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if(getContext().getXQueryVersion() < 31) {
            throw new XPathException(this,
                ErrorCodes.EXXQDY0003,
                "arrow operator is not available before XQuery 3.1");
        }
        this.cachedContextInfo = contextInfo;
        if (qname != null) {
            // Statically-named function: compile the arrow to the equivalent call
            // f(leftExpr, parameters...), exactly as the parser builds a normal function call (the
            // functionCall rule in XQueryTree.g). The left-hand side becomes a real argument
            // expression — so it keeps its static context (fixing the lost-variable-scope bug, e.g.
            // EXPR => util:eval()) — and a '?' placeholder yields a partial function application
            // (fixing the placeholder arity bug). This replaces the previous dynamic FunctionReference
            // dispatch, which pre-evaluated the left-hand side and modelled it as a placeholder.
            final XQueryAST ast = new XQueryAST();
            ast.setLine(getLine());
            ast.setColumn(getColumn());
            final List<Expression> callArgs = new ArrayList<>(parameters.size() + 1);
            callArgs.add(toArgument(leftExpr));
            boolean partial = false;
            for (final Expression param : parameters) {
                if (param instanceof Function.Placeholder) {
                    partial = true;
                    callArgs.add(param);
                } else {
                    callArgs.add(toArgument(param));
                }
            }
            Expression call = FunctionFactory.createFunction(context, qname, ast, null, callArgs);
            if (partial) {
                // mirror the functionCall rule: a '?' placeholder turns the call into a partial
                // function application yielding a function item of the remaining arity.
                if (!(call instanceof FunctionCall)) {
                    if (call instanceof CastExpression) {
                        call = ((CastExpression) call).toFunction();
                    }
                    call = FunctionFactory.wrap(context, (Function) call);
                }
                call = new PartialFunctionApplication(context, (FunctionCall) call);
            }
            namedCall = call;
            namedCall.analyze(contextInfo);
        } else {
            leftExpr.analyze(contextInfo);
            funcSpec.analyze(contextInfo);
        }
    }

    /**
     * Wraps an argument expression in a {@link PathExpr} when it is not already one, matching how the
     * parser supplies function-call arguments (so {@link FunctionFactory#createFunction} optimizations
     * that expect {@code PathExpr} arguments behave identically to a normal call). Placeholders are
     * passed through unchanged by the caller.
     */
    private Expression toArgument(final Expression expr) {
        if (expr instanceof PathExpr) {
            return expr;
        }
        final PathExpr wrapped = new PathExpr(context);
        wrapped.add(expr);
        return wrapped;
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (namedCall != null) {
            // Statically-named arrow, compiled to f(leftExpr, parameters...): evaluate it directly,
            // so the left-hand side is evaluated as an ordinary argument in this call's context.
            return namedCall.eval(contextSequence, contextItem);
        }

        // Dynamic (higher-order) right-hand side: the function to call is obtained by evaluating
        // funcSpec, so the left-hand side value is captured and supplied as the first argument.
        final Sequence focus = contextItem != null ? contextItem.toSequence() : contextSequence;
        final Sequence leftValue = leftExpr.eval(focus, null);

        final Sequence funcSeq = funcSpec.eval(leftValue, contextItem);
        if (funcSeq.getCardinality() != Cardinality.EXACTLY_ONE)
        {throw new XPathException(this, ErrorCodes.XPTY0004,
                "Expected exactly one item for the function to be called, got " + funcSeq.getItemCount() +
                        ". Expression: " + ExpressionDumper.dump(funcSpec));}
        final Item item0 = funcSeq.itemAt(0);
        if (!Type.subTypeOf(item0.getType(), Type.FUNCTION)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                "Type error: expected function, got " + Type.getTypeName(item0.getType()));
        }
        final FunctionReference fref = (FunctionReference) item0;
        try {
            final List<Expression> fparams = new ArrayList<>(parameters.size() + 1);
            fparams.add(new ContextParam(context, leftValue));
            fparams.addAll(parameters);

            fref.setArguments(fparams);
            // need to create a new AnalyzeContextInfo to avoid memory leak
            // cachedContextInfo will stay in memory
            fref.analyze(new AnalyzeContextInfo(cachedContextInfo));
            // Evaluate the function
            return fref.eval(null);
        } finally {
            fref.close();
        }
    }

    @Override
    public int returnsType() {
        return namedCall == null ? Type.ITEM : namedCall.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return namedCall == null ? super.getCardinality() : namedCall.getCardinality();
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        leftExpr.dump(dumper);
        dumper.display(" => ");
        if (qname != null) {
            dumper.display(qname.getStringValue());
        } else {
            funcSpec.dump(dumper);
        }
        dumper.display('(');
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            parameters.get(i).dump(dumper);
        }
        dumper.display(')');
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if (namedCall != null) {
            // namedCall owns leftExpr and parameters as its arguments.
            namedCall.resetState(postOptimization);
        } else {
            leftExpr.resetState(postOptimization);
            if (funcSpec != null) {
                funcSpec.resetState(postOptimization);
            }
            for (Expression param : parameters) {
                param.resetState(postOptimization);
            }
        }
    }

    private class ContextParam extends Function.Placeholder {

        private Sequence sequence;

        ContextParam(XQueryContext context, Sequence sequence) {
            super(context);
            this.sequence = sequence;
        }

        @Override
        public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
            // nothing to analyze: the captured left-hand-side value is already evaluated
        }

        @Override
        public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
            return sequence;
        }

        @Override
        public int returnsType() {
            return sequence.getItemType();
        }

        @Override
        public void dump(ExpressionDumper dumper) {
            // the captured value has no source representation to display
        }
    }
}