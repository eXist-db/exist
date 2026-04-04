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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AbstractExpression;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.exist.xquery.functions.map.AbstractMapType;

import javax.xml.XMLConstants;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implements XQuery 4.0 fn:partial-apply.
 *
 * fn:partial-apply($function, $arguments) binds selected arguments to a function,
 * returning a partially applied function with reduced arity.
 */
public class FnPartialApply extends BasicFunction {

    public static final FunctionSignature FN_PARTIAL_APPLY = new FunctionSignature(
            new QName("partial-apply", Function.BUILTIN_FUNCTION_NS),
            "Returns a partially applied function with specified arguments bound.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("function", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The function to partially apply"),
                    new FunctionParameterSequenceType("arguments", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Map from argument positions (xs:positiveInteger) to values")
            },
            new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.EXACTLY_ONE, "the partially applied function"));

    private AnalyzeContextInfo cachedContextInfo;

    public FnPartialApply(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
        final AbstractMapType argMap = (AbstractMapType) args[1].itemAt(0);

        ref.analyze(cachedContextInfo);
        final int originalArity = ref.getSignature().getArgumentCount();

        // Extract bound arguments from the map (1-based positions)
        final Map<Integer, Sequence> boundArgs = new TreeMap<>();
        final Sequence keys = argMap.keys();
        for (final SequenceIterator ki = keys.iterate(); ki.hasNext(); ) {
            final AtomicValue key = ki.nextItem().atomize();
            final int pos = (int) ((IntegerValue) key).getLong();
            if (pos >= 1 && pos <= originalArity) {
                boundArgs.put(pos, argMap.get(key));
            }
        }

        if (boundArgs.isEmpty()) {
            return ref;
        }

        // Build parameter list for the new function (unbound positions only)
        final int newArity = originalArity - boundArgs.size();
        final SequenceType[] newParamTypes = new SequenceType[newArity];
        final List<QName> variables = new ArrayList<>();

        int paramIdx = 0;
        for (int pos = 1; pos <= originalArity; pos++) {
            if (!boundArgs.containsKey(pos)) {
                final QName varName = new QName("pa" + paramIdx, XMLConstants.NULL_NS_URI);
                variables.add(varName);
                newParamTypes[paramIdx] = new FunctionParameterSequenceType(
                        "pa" + paramIdx, Type.ITEM, Cardinality.ZERO_OR_MORE, "unbound parameter");
                paramIdx++;
            }
        }

        final QName name = new QName("partial" + hashCode(), XMLConstants.NULL_NS_URI);
        final FunctionSignature newSignature = new FunctionSignature(name, newParamTypes,
                new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "result"));
        final UserDefinedFunction func = new UserDefinedFunction(context, newSignature);

        for (final QName varName : variables) {
            func.addVariable(varName);
        }

        // Body expression: resolves variables and bound args, calls the original function
        func.setFunctionBody(new PartialCallExpression(context, ref, boundArgs, originalArity, variables));

        final FunctionCall newCall = new FunctionCall(context, func);
        newCall.setLocation(getLine(), getColumn());
        return new FunctionReference(this, newCall);
    }

    /**
     * Expression that invokes the original function with bound + unbound args assembled.
     */
    private static class PartialCallExpression extends AbstractExpression {
        private final FunctionReference originalRef;
        private final Map<Integer, Sequence> boundArgs;
        private final int originalArity;
        private final List<QName> unboundVars;

        PartialCallExpression(final XQueryContext context, final FunctionReference ref,
                              final Map<Integer, Sequence> boundArgs, final int originalArity,
                              final List<QName> unboundVars) {
            super(context);
            this.originalRef = ref;
            this.boundArgs = boundArgs;
            this.originalArity = originalArity;
            this.unboundVars = unboundVars;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence[] fullArgs = new Sequence[originalArity];
            int unboundIdx = 0;
            for (int pos = 1; pos <= originalArity; pos++) {
                if (boundArgs.containsKey(pos)) {
                    fullArgs[pos - 1] = boundArgs.get(pos);
                } else {
                    fullArgs[pos - 1] = context.resolveVariable(unboundVars.get(unboundIdx)).getValue();
                    unboundIdx++;
                }
            }
            return originalRef.evalFunction(null, null, fullArgs);
        }

        @Override
        public int returnsType() {
            return Type.ITEM;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        }

        @Override
        public void dump(final ExpressionDumper dumper) {
            dumper.display("partial-apply(...)");
        }
    }
}
