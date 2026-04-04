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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the XQuery 4.0 mapping arrow operator (=!>).
 *
 * Unlike the fat arrow (=>), which passes the entire left-hand sequence
 * as the first argument, the mapping arrow iterates over each item in
 * the sequence and passes each one individually, concatenating the results.
 *
 * {@code (1, 2, 3) =!> string()} is equivalent to {@code (1, 2, 3) ! string(.)}.
 */
public class MappingArrowOperator extends AbstractExpression {

    private QName qname = null;
    private Expression leftExpr;
    private FunctionCall fcall = null;
    private Expression funcSpec = null;
    private List<Expression> parameters;
    private AnalyzeContextInfo cachedContextInfo;

    public MappingArrowOperator(final XQueryContext context, final Expression leftExpr) throws XPathException {
        super(context);
        this.leftExpr = leftExpr;
    }

    public void setArrowFunction(final String fname, final List<Expression> params) throws XPathException {
        try {
            this.qname = QName.parse(context, fname, context.getDefaultFunctionNamespace());
            this.parameters = params;
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
        if (qname != null) {
            fcall = NamedFunctionReference.lookupFunction(this, context, qname, parameters.size() + 1);
        }
        this.cachedContextInfo = contextInfo;
        leftExpr.analyze(contextInfo);
        if (fcall != null) {
            fcall.analyze(contextInfo);
        }
        if (funcSpec != null) {
            funcSpec.analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        final Sequence inputSeq = leftExpr.eval(contextSequence, null);

        if (inputSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        for (int i = 0; i < inputSeq.getItemCount(); i++) {
            final Item item = inputSeq.itemAt(i);
            final Sequence itemSeq = item.toSequence();

            final FunctionReference fref;
            if (fcall != null) {
                fref = new FunctionReference(this, fcall);
            } else {
                final Sequence funcSeq = funcSpec.eval(itemSeq, null);
                if (funcSeq.getCardinality() != Cardinality.EXACTLY_ONE) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Expected exactly one item for the function to be called, got " + funcSeq.getItemCount() +
                                    ". Expression: " + ExpressionDumper.dump(funcSpec));
                }
                final Item item0 = funcSeq.itemAt(0);
                if (!Type.subTypeOf(item0.getType(), Type.FUNCTION)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Type error: expected function, got " + Type.getTypeName(item0.getType()));
                }
                fref = (FunctionReference) item0;
            }
            try {
                final List<Expression> fparams = new ArrayList<>(parameters.size() + 1);
                fparams.add(new ContextParam(context, itemSeq));
                fparams.addAll(parameters);

                fref.setArguments(fparams);
                fref.analyze(new AnalyzeContextInfo(cachedContextInfo));
                result.addAll(fref.eval(null));
            } finally {
                fref.close();
            }
        }
        return result;
    }

    @Override
    public int returnsType() {
        return fcall == null ? Type.ITEM : fcall.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        leftExpr.dump(dumper);
        dumper.display(" =!> ");
        if (fcall != null) {
            dumper.display(fcall.getFunction().getName()).display('(');
        } else {
            funcSpec.dump(dumper);
        }
        for (int i = 0; i < parameters.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
                parameters.get(i).dump(dumper);
            }
        }
        dumper.display(')');
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        leftExpr.resetState(postOptimization);
        if (fcall != null) {
            fcall.resetState(postOptimization);
        }
        if (funcSpec != null) {
            funcSpec.resetState(postOptimization);
        }
        for (Expression param : parameters) {
            param.resetState(postOptimization);
        }
    }

    private class ContextParam extends Function.Placeholder {
        private final Sequence sequence;

        ContextParam(XQueryContext context, Sequence sequence) {
            super(context);
            this.sequence = sequence;
        }

        @Override
        public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
            // no-op: context param is pre-evaluated
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
            // no-op: context param has no source representation
        }
    }
}
