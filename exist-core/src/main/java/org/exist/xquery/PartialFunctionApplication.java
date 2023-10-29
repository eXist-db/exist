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

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.xml.XMLConstants;

public class PartialFunctionApplication extends AbstractExpression {

	public final static String PARTIAL_FUN_PREFIX = "partial";
	
	protected FunctionCall function;
	protected AnalyzeContextInfo cachedContextInfo;

	public PartialFunctionApplication(XQueryContext context, FunctionCall call) {
		super(context);
		this.function = call;
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        this.cachedContextInfo = contextInfo;
	}

	@Override
	public void dump(ExpressionDumper dumper) {
		function.dump(dumper);
	}

	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		final FunctionReference newRef = createPartial(contextSequence, contextItem, function);
		return newRef;
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION;
	}
	
	@Override
	public Cardinality getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	private FunctionReference createPartial(Sequence contextSequence, Item contextItem, FunctionCall staticCall) throws XPathException {
		final FunctionSignature signature = staticCall.getSignature();
		final SequenceType[] paramTypes = signature.getArgumentTypes();
		// the parameters of the newly created inline function:
		// old params except the fixed ones
		final List<SequenceType> newParamTypes = new ArrayList<>();
		// the arguments to pass to the inner call
		final List<Expression> callArgs = new ArrayList<>();
		// parameter variables of the new inline function
		final List<QName> variables = new ArrayList<>();
		// the inline function
		final int argCount = staticCall.getArgumentCount();
		for (int i = 0; i < argCount; i++) {
			final Expression param = staticCall.getArgument(i);
			if (param instanceof Function.Placeholder) {
				// copy parameter sequence types
				// overloaded functions like concat may have an arbitrary number of arguments
				if (i < paramTypes.length)
					{newParamTypes.add(paramTypes[i]);}
				else
					// overloaded function: add last sequence type
					{newParamTypes.add(paramTypes[paramTypes.length - 1]);}
				// create local parameter variables
				final QName varName = new QName("vp" + i, XMLConstants.NULL_NS_URI);
				variables.add(varName);
				// the argument to the inner call is a variable ref
				final VariableReference ref = new VariableReference(context, varName);
				callArgs.add(ref);
			} else {
				// fixed argument: just compute the argument value
				try {
                    param.analyze(cachedContextInfo);
					final Sequence seq = param.eval(contextSequence, contextItem);
					callArgs.add(new PrecomputedValue(context, seq));
				} catch (final XPathException e) {
					if(e.getLine() <= 0) {
						e.setLocation(line, column, getSource());
					}
					// append location of the function call to the exception message:
					e.addFunctionCall(function.functionDef, this);
					throw e;
				}
			}
		}
		final SequenceType[] newParamArray = newParamTypes.toArray(new SequenceType[0]);
		final QName name = new QName(PARTIAL_FUN_PREFIX + hashCode(), XMLConstants.NULL_NS_URI);
		final FunctionSignature newSignature = new FunctionSignature(name, newParamArray, signature.getReturnType());
		final UserDefinedFunction func = new UserDefinedFunction(context, newSignature);
		func.setLocation(staticCall.getLine(), staticCall.getColumn());
		
		// add the created parameter variables to the function
		for (final QName varName: variables) {
			func.addVariable(varName);
		}
		
		final FunctionCall innerCall = new FunctionCall(staticCall);
		innerCall.setArguments(callArgs);
		
		func.setFunctionBody(innerCall);
		
		final FunctionCall newCall = new FunctionCall(context, func);
		newCall.setLocation(staticCall.getLine(), staticCall.getColumn());
		return new FunctionReference(this, newCall);
	}
	
	private static class PrecomputedValue extends AbstractExpression {

		Sequence sequence;
		
		public PrecomputedValue(XQueryContext context, Sequence input) {
			super(context);
			sequence = input;
		}
		
		@Override
		public void analyze(AnalyzeContextInfo contextInfo)
				throws XPathException {	
		}

		@Override
		public void dump(ExpressionDumper dumper) {
		}

		@Override
		public Sequence eval(Sequence contextSequence, Item contextItem)
				throws XPathException {
			return sequence;
		}

		@Override
		public int returnsType() {
			return sequence.getItemType();
		}
		
		@Override
		public int getDependencies() {
			return Dependency.CONTEXT_SET;
		}
	}
}
