package org.exist.xquery;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

public class NamedFunctionReference extends AbstractExpression {

	private QName qname;
	private int arity;

	private FunctionCall resolvedFunction = null;
	
	public NamedFunctionReference(XQueryContext context, QName qname, int arity) {
		super(context);
		this.qname = qname;
		this.arity = arity;
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		resolvedFunction = lookupFunction(this, context, qname, arity);
    	contextInfo.addFlag(SINGLE_STEP_EXECUTION);
    	resolvedFunction.analyze(contextInfo);
	}

	public static FunctionCall lookupFunction(Expression self, XQueryContext context, QName funcName, int arity) throws XPathException {
		final XQueryAST ast = new XQueryAST();
		ast.setLine(self.getLine());
		ast.setColumn(self.getColumn());
		final List<Expression> args = new ArrayList<Expression>(arity);
		for (int i = 0; i < arity; i++) {
			args.add(new Function.Placeholder(context));
		}
		final Expression fun = FunctionFactory.createFunction(context, funcName, ast, null, args, false);
        if (fun == null)
            {throw new XPathException(self, ErrorCodes.XPST0017, "Function not found: " + funcName);}
        if (fun instanceof FunctionCall) {
        	if (((FunctionCall) fun).getFunction() == null) {
				throw new XPathException(self, ErrorCodes.XPST0017, "Function not found: " + funcName);
			}
            // clear line and column as it will be misleading. should be set later to point
            // to the location from where the function is called.
            fun.setLocation(-1, -1);
            return (FunctionCall) fun;
        } else if (fun instanceof Function) {
        	final InternalFunctionCall funcCall;
        	if (fun instanceof InternalFunctionCall) {
				funcCall = (InternalFunctionCall)fun;
			} else {
        		funcCall = new InternalFunctionCall((Function)fun);
			}
            funcCall.setLocation(-1, -1);
	        return FunctionFactory.wrap(context, funcCall);
        } else if (fun instanceof CastExpression) {
            final InternalFunctionCall funcCall = new InternalFunctionCall(((CastExpression)fun).toFunction());
            funcCall.setLocation(-1, -1);
            return FunctionFactory.wrap(context, funcCall);
        } else
        	{throw new XPathException(self, ErrorCodes.XPST0017, "Named function reference should point to a function; found: " +
        			fun.getClass().getName());}
	}
	
	@Override
	public void dump(ExpressionDumper dumper) {
		dumper.display(qname);
		dumper.display('#');
		dumper.display(Integer.toString(arity));
	}

	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		return new FunctionReference(resolvedFunction);
	}

	@Override
	public int returnsType() {
		return Type.FUNCTION_REFERENCE;
	}
	
	@Override
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
    	if (resolvedFunction != null)
    		{resolvedFunction.resetState(postOptimization);}
	}
}
