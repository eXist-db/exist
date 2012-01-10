package org.exist.xquery;

import org.exist.xquery.util.Error;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class ConcatExpr extends PathExpr {

	public ConcatExpr(XQueryContext context) {
		super(context);
	}

	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if(getContext().getXQueryVersion() < 30){
            throw new XPathException(this, ErrorCodes.EXXQDY0003, "switch expression is not available before XQuery 3.0");
        }
		super.analyze(contextInfo);
	}
	
	@Override
	public void add(Expression expr) {
		expr = new DynamicCardinalityCheck(context, Cardinality.ZERO_OR_ONE, expr,
            new Error(Error.FUNC_PARAM_CARDINALITY));
        if (!Type.subTypeOf(expr.returnsType(), Type.ATOMIC))
            expr = new Atomize(context, expr);
		super.add(expr);
	}
	
	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
		
		StringBuilder concat = new StringBuilder();
		for(Expression step : steps) {
			concat.append(step.eval(contextSequence, contextItem).getStringValue());
		}
		StringValue result = new StringValue(concat.toString());
		
		if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
		
		return result;
	}
	
	@Override
	public int returnsType() {
		return Type.STRING;
	}
	
	@Override
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
}
