package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

public class PartialFunctionReference extends FunctionReference {

	Sequence[] argValues;
	List<Expression> unfixedArgs = null;
	
	public PartialFunctionReference(FunctionCall fcall, Sequence[] arguments) {
		super(fcall);
		this.argValues = arguments;
	}

	@Override
	public Sequence eval(Sequence contextSequence) throws XPathException {
		Sequence[] arguments = new Sequence[argValues.length];
		Iterator<Expression> iter = unfixedArgs.iterator();
		for (int i = 0; i < argValues.length; i++) {
			if (argValues[i] == null) {
				if (!iter.hasNext())
					throw new XPathException("Number of argument to partially applied function does not match");
				Expression nextArg = iter.next();
				arguments[i] = nextArg.eval(contextSequence);
			} else
				arguments[i] = argValues[i];
		}
		return functionCall.evalFunction(contextSequence, null, arguments);
	}
	
	@Override
	public Sequence evalFunction(Sequence contextSequence, Item contextItem,
			Sequence[] seq) throws XPathException {
		// merge argument sequence with fixed arguments
		Sequence[] arguments = new Sequence[argValues.length];
		int j = 0;
		for (int i = 0; i < arguments.length; i++) {
			if (argValues[i] == null) {
				arguments[i] = seq[j++];
			} else {
				arguments[i] = argValues[i];
			}
		}
		return super.evalFunction(contextSequence, contextItem, arguments);
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if (unfixedArgs != null) {
			// merge arguments before calling analyze on the function
			List<Expression> updatedArgs = new ArrayList<Expression>(functionCall.getArgumentCount());
			int j = 0;
			for (int i = 0; i < functionCall.getArgumentCount(); i++) {
				Expression arg = functionCall.getArgument(i);
				if (arg instanceof Function.Placeholder)
					arg = unfixedArgs.get(j++);
				updatedArgs.add(arg);
			}
			functionCall.setArguments(updatedArgs);
		}
		functionCall.analyze(contextInfo);
	}
	
	@Override
	public void setArguments(List<Expression> arguments) throws XPathException {
		unfixedArgs = arguments;
	}
}
