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

import java.util.List;

import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class DynamicFunctionCall extends AbstractExpression {

    private final Expression functionExpr;
    private final List<Expression> arguments;
    private final boolean isPartial;
    
    private AnalyzeContextInfo cachedContextInfo;

    public DynamicFunctionCall(final XQueryContext context, final Expression fun, final List<Expression> args, final boolean partial) {
        super(context);
        setLocation(fun.getLine(), fun.getColumn());
        this.functionExpr = fun;
        this.arguments = args;
        this.isPartial = partial;
    }

    @Override
    public int getDependencies() {
        int dependencies = functionExpr.getDependencies();
        if (arguments != null) {
            for (final Expression argument : arguments) {
                dependencies |= argument.getDependencies();
            }
        }
        return dependencies;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        functionExpr.analyze(contextInfo);
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        functionExpr.dump(dumper);
        dumper.display('(');
        for (final Expression arg : arguments) {
            arg.dump(dumper);
        }
        dumper.display(')');
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        context.proceed(this);
        final Sequence funcSeq = functionExpr.eval(contextSequence, contextItem);
        if (funcSeq.getCardinality() != Cardinality.EXACTLY_ONE)
            {throw new XPathException(this, ErrorCodes.XPTY0004,
                "Expected exactly one item for the function to be called, got " + funcSeq.getItemCount() +
                ". Expression: " + ExpressionDumper.dump(functionExpr));}
        final Item item0 = funcSeq.itemAt(0);
        if (!Type.subTypeOf(item0.getType(), Type.FUNCTION))
            {throw new XPathException(this, ErrorCodes.XPTY0004,
                "Type error: expected function, got " + Type.getTypeName(item0.getType()));}
        final FunctionReference ref = (FunctionReference)item0;
        // if the call is a partial application, create a new function
        if (isPartial) {
        	try {
                if (ref instanceof ArrayType) {
                    ref.setArguments(arguments);
                    return ref;
                } else {
                    final FunctionCall call = ref.getCall();
                    call.setArguments(arguments);
                    final PartialFunctionApplication partialApp = new PartialFunctionApplication(context, call);
                    partialApp.analyze(new AnalyzeContextInfo(cachedContextInfo));
                    return partialApp.eval(contextSequence, contextItem);
                }
        	} catch (final XPathException e) {
				e.setLocation(line, column, getSource());
				throw e;
        	} catch (final Exception e) {
                throw new XPathException(this, e);
            }
        } else {
	        ref.setArguments(arguments);
            // need to create a new AnalyzeContextInfo to avoid memory leak
            // cachedContextInfo will stay in memory
	        ref.analyze(new AnalyzeContextInfo(cachedContextInfo));
	        // Evaluate the function
            try {
                return ref.eval(contextSequence, contextItem);
            } catch (XPathException e) {
                if (e.getLine() <= 0) {
                    e.setLocation(getLine(), getColumn(), getSource());
                }
                throw e;
            } finally {
                ref.close();
            }
        }
    }

    @Override
    public int returnsType() {
        return Type.ITEM; // Unknown until the reference is resolved
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        functionExpr.resetState(postOptimization);
        arguments.forEach(arg -> arg.resetState(postOptimization));
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        if (functionExpr != null) {
            builder.append(functionExpr);
        } else {
            builder.append("DynamicFunctionCall{arguments=");
        }

        builder.append('(');
        if (arguments != null) {
            for (int i = 0; i < arguments.size(); i++) {
                if (i > 0) {
                    builder.append(", ");
                }
                builder.append(arguments.get(i).toString());
            }
        }
        builder.append(')');

        if (functionExpr == null) {
            builder.append('}');
        }

        return builder.toString();
    }
}
