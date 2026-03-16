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

import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements the XQuery 4.0 method call operator (=?>).
 *
 * {@code $map =?> method(args)} looks up the key "method" in the map,
 * retrieves the function stored there, and calls it with the map as
 * the first argument followed by any additional arguments.
 *
 * For each item in the left-hand sequence:
 * <ol>
 *   <li>The item must be a map (XPTY0004 otherwise)</li>
 *   <li>The method name is looked up as a key in the map</li>
 *   <li>The value must be exactly one function (XPTY0004 otherwise)</li>
 *   <li>The function is called with the map as first argument + additional args</li>
 * </ol>
 *
 * Like the mapping arrow (=!>), it processes each item individually
 * and concatenates results.
 */
public class MethodCallOperator extends AbstractExpression {

    private Expression leftExpr;
    private String methodName;
    private List<Expression> parameters;
    private AnalyzeContextInfo cachedContextInfo;

    public MethodCallOperator(final XQueryContext context, final Expression leftExpr) throws XPathException {
        super(context);
        this.leftExpr = leftExpr;
    }

    public void setMethod(final String methodName, final List<Expression> params) {
        this.methodName = methodName;
        this.parameters = params;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        this.cachedContextInfo = contextInfo;
        leftExpr.analyze(contextInfo);
        if (parameters != null) {
            for (final Expression param : parameters) {
                param.analyze(contextInfo);
            }
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

            // The item must be a map
            if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Method call operator (=?>) requires a map, got " +
                                Type.getTypeName(item.getType()));
            }

            final AbstractMapType map = (AbstractMapType) item;

            // Look up the method name as a key in the map
            final Sequence methodValue = map.get(new StringValue(this, methodName));
            if (methodValue == null || methodValue.isEmpty()) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Method '" + methodName + "' not found in map");
            }

            if (methodValue.getItemCount() != 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Method '" + methodName + "' must be a single function, got " +
                                methodValue.getItemCount() + " items");
            }

            final Item methodItem = methodValue.itemAt(0);
            if (!Type.subTypeOf(methodItem.getType(), Type.FUNCTION)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Method '" + methodName + "' is not a function, got " +
                                Type.getTypeName(methodItem.getType()));
            }

            final FunctionReference fref = (FunctionReference) methodItem;

            // Check arity: function must accept at least 1 argument (the map itself)
            final int expectedArity = (parameters != null ? parameters.size() : 0) + 1;
            if (fref.getSignature().getArgumentCount() == 0) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Method '" + methodName + "' has arity 0 and cannot accept the map as first argument");
            }

            try {
                final List<Expression> fparams = new ArrayList<>(expectedArity);
                fparams.add(new ContextParam(context, item.toSequence()));
                if (parameters != null) {
                    fparams.addAll(parameters);
                }

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
        return Type.ITEM;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        leftExpr.dump(dumper);
        dumper.display(" =?> ").display(methodName).display('(');
        if (parameters != null) {
            for (int i = 0; i < parameters.size(); i++) {
                if (i > 0) {
                    dumper.display(", ");
                }
                parameters.get(i).dump(dumper);
            }
        }
        dumper.display(')');
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        leftExpr.resetState(postOptimization);
        if (parameters != null) {
            for (Expression param : parameters) {
                param.resetState(postOptimization);
            }
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
        }
    }
}
