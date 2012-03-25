package org.exist.xquery;

import org.exist.dom.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class PartialFunctionApplication extends AbstractExpression {

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
		Sequence[] seq = new Sequence[function.getArgumentCount()];
        for (int i = 0; i < function.getArgumentCount(); i++) {
        	Expression arg = function.getArgument(i);
        	if (arg instanceof Function.Placeholder)
        		continue;
			try {
                seq[i] = arg.eval(contextSequence, contextItem);
//			System.out.println("found " + seq[i].getLength() + " for " + getArgument(i).pprint());
            } catch (XPathException e) {
                if(e.getLine() <= 0) {
                    e.setLocation(line, column, getSource());
                }
                // append location of the function call to the exception message:
                e.addFunctionCall(function.functionDef, this);
                throw e;
            }
		}
		return new PartialFunctionReference(function, seq);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION_REFERENCE;
	}
	
	@Override
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
}
