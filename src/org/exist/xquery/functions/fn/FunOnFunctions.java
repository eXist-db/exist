package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.InlineFunction;
import org.exist.xquery.Module;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunOnFunctions extends BasicFunction {

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("function-lookup", Function.BUILTIN_FUNCTION_NS),
            "Returns a reference to the function having a given name and arity, if there is one," +
            " the empty sequence otherwise",
            new SequenceType[] {
                new FunctionParameterSequenceType("name", Type.QNAME, Cardinality.EXACTLY_ONE, "Qualified name of the function"),
                new FunctionParameterSequenceType("arity", Type.INTEGER, Cardinality.EXACTLY_ONE, "The arity (number of arguments) of the function")
            },
            new FunctionReturnSequenceType(Type.FUNCTION_REFERENCE, Cardinality.ZERO_OR_ONE, "The function if found, empty sequence otherwise")),
        new FunctionSignature(
            new QName("function-name", Function.BUILTIN_FUNCTION_NS),
            "Returns the name of the function identified by a function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.QNAME, Cardinality.ZERO_OR_ONE, 
            		"The name of the function or the empty sequence if $function is an anonymous function.")),
		new FunctionSignature(
            new QName("function-arity", Function.BUILTIN_FUNCTION_NS),
            "Returns the arity of the function identified by a function item.",
            new SequenceType[] {
                new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function item")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, 
            		"The arity of the function."))
    };
	
	public FunOnFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		super.analyze(contextInfo);
		if (getContext().getXQueryVersion()<30) {
			throw new XPathException(this, ErrorCodes.EXXQDY0003, "Function '" + 
					getSignature().getName() + "' is only supported for xquery version \"3.0\" and later.");
		}
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (isCalledAs("function-lookup")) {
			QName fname = ((QNameValue) args[0].itemAt(0)).getQName();
			int arity = ((IntegerValue)args[1].itemAt(0)).getInt();
			
	        FunctionCall call = lookupFunction(fname, arity);
	        
	        return call == null ? Sequence.EMPTY_SEQUENCE : new FunctionReference(call);
		} else if (isCalledAs("function-name")) {
			FunctionReference ref = (FunctionReference) args[0].itemAt(0);
			QName qname = ref.getSignature().getName();
			if (qname == null || qname == InlineFunction.INLINE_FUNCTION_QNAME)
				return Sequence.EMPTY_SEQUENCE;
			else
				return new QNameValue(context, qname);
		} else {
			// isCalledAs("function-arity")
			FunctionReference ref = (FunctionReference) args[0].itemAt(0);
			return new IntegerValue(ref.getSignature().getArgumentCount());
		}
	}

	private FunctionCall lookupFunction(QName qname, int arity) throws XPathException {
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
            return null;
	    FunctionCall funcCall = new FunctionCall(context, func);
        funcCall.setLocation(line, column);
	    return funcCall;
	}
}