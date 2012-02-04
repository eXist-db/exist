package org.exist.xquery;

import org.exist.dom.QName;
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
		resolvedFunction = lookupFunction(qname, arity);
    	contextInfo.addFlag(SINGLE_STEP_EXECUTION);
    	resolvedFunction.analyze(contextInfo);
	}

	private FunctionCall lookupFunction(QName funcName, int arity) throws XPathException {
	    // check if the function is from a module 
	    Module module = context.getModule(qname.getNamespaceURI());
	    UserDefinedFunction func;
	    if(module == null) {
	        func = context.resolveFunction(qname, arity);
	    } else {
	        if(module.isInternalModule()) {
                throw new XPathException(this, ErrorCodes.XPST0017, "Cannot create a reference to an internal Java function");
            }
	        func = ((ExternalModule)module).getFunction(qname, arity);
	    }
        if (func == null)
            throw new XPathException(this, ErrorCodes.XPST0017, "Function not found: " + qname);
	    FunctionCall funcCall = new FunctionCall(context, func);
        funcCall.setLocation(line, column);
	    return funcCall;
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
    		resolvedFunction.resetState(postOptimization);
	}
}
