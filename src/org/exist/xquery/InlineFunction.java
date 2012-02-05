package org.exist.xquery;

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class InlineFunction extends AbstractExpression {

	private UserDefinedFunction function;
	
	public InlineFunction(XQueryContext context, UserDefinedFunction function) {
		super(context);
		this.function = function;
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		AnalyzeContextInfo info = new AnalyzeContextInfo(contextInfo);
		info.addFlag(SINGLE_STEP_EXECUTION);
		function.analyze(info);
	}

	@Override
	public void dump(ExpressionDumper dumper) {
		dumper.display("function");
		function.dump(dumper);
	}

	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		FunctionCall call = new FunctionCall(context, function);
		function.setCaller(call);
		return new FunctionReference(call);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION_REFERENCE;
	}

}
