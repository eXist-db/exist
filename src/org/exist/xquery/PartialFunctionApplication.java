package org.exist.xquery;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class PartialFunctionApplication extends AbstractExpression {

	public final static String PARTIAL_FUN_PREFIX = "partial";
	
	protected FunctionCall function;
	
	public PartialFunctionApplication(XQueryContext context, FunctionCall call) {
		super(context);
		this.function = call;
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
	}

	@Override
	public void dump(ExpressionDumper dumper) {
		function.dump(dumper);
	}

	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		FunctionReference newRef = createPartial(contextSequence, contextItem, function);
		return newRef;
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION_REFERENCE;
	}
	
	@Override
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	private FunctionReference createPartial(Sequence contextSequence, Item contextItem, FunctionCall staticCall) throws XPathException {
		FunctionSignature signature = staticCall.getSignature();
		SequenceType[] paramTypes = signature.getArgumentTypes();
		// the parameters of the newly created inline function:
		// old params except the fixed ones
		List<SequenceType> newParamTypes = new ArrayList<SequenceType>();
		// the arguments to pass to the inner call
		List<Expression> callArgs = new ArrayList<Expression>();
		// parameter variables of the new inline function
		List<QName> variables = new ArrayList<QName>();
		// the inline function
		int argCount = staticCall.getArgumentCount();
		for (int i = 0; i < argCount; i++) {
			Expression param = staticCall.getArgument(i);
			if (param instanceof Function.Placeholder) {
				// copy parameter sequence types
				// overloaded functions like concat may have an arbitrary number of arguments
				if (i < paramTypes.length)
					newParamTypes.add(paramTypes[i]);
				else
					// overloaded function: add last sequence type
					newParamTypes.add(paramTypes[paramTypes.length - 1]);
				// create local parameter variables
				QName varName = new QName("vp" + i);
				variables.add(varName);
				// the argument to the inner call is a variable ref
				VariableReference ref = new VariableReference(context, varName.toString());
				callArgs.add(ref);
			} else {
				// fixed argument: just compute the argument value
				try {
					Sequence seq = param.eval(contextSequence, contextItem);
					callArgs.add(new PrecomputedValue(context, seq));
				} catch (XPathException e) {
					if(e.getLine() <= 0) {
						e.setLocation(line, column, getSource());
					}
					// append location of the function call to the exception message:
					e.addFunctionCall(function.functionDef, this);
					throw e;
				}
			}
		}
		SequenceType[] newParamArray = newParamTypes.toArray(new SequenceType[newParamTypes.size()]);
		QName name = new QName(PARTIAL_FUN_PREFIX + hashCode());
		FunctionSignature newSignature = new FunctionSignature(name, newParamArray, signature.getReturnType());
		UserDefinedFunction func = new UserDefinedFunction(context, newSignature);
		// add the created parameter variables to the function
		for (QName varName: variables) {
			func.addVariable(varName);
		}
		
		FunctionCall innerCall = new FunctionCall(staticCall);
		innerCall.setArguments(callArgs);
		
		func.setFunctionBody(innerCall);
		
		FunctionCall newCall = new FunctionCall(context, func);
		return new FunctionReference(newCall);
	}
	
	private class PrecomputedValue extends AbstractExpression {

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
