package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.*;
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
		try {
			if (isCalledAs("function-lookup")) {
				final QName fname = ((QNameValue) args[0].itemAt(0)).getQName();
				final int arity = ((IntegerValue)args[1].itemAt(0)).getInt();
				
			    FunctionCall call;
                try {
                    call = NamedFunctionReference.lookupFunction(this, context, fname, arity);
                } catch (final XPathException e) {
                    if (e.getErrorCode() == ErrorCodes.XPST0017) {
                        // return empty sequence for all "function not found" related errors
                        return Sequence.EMPTY_SEQUENCE;
                    }
                    throw e;
                }
			    return call == null ? Sequence.EMPTY_SEQUENCE : new FunctionReference(call);
			} else if (isCalledAs("function-name")) {
				final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
				final QName qname = ref.getSignature().getName();
				if (qname == null || qname == InlineFunction.INLINE_FUNCTION_QNAME)
					{return Sequence.EMPTY_SEQUENCE;}
				else
					{return new QNameValue(context, qname);}
			} else {
				// isCalledAs("function-arity")
				final FunctionReference ref = (FunctionReference) args[0].itemAt(0);
				return new IntegerValue(ref.getSignature().getArgumentCount());
			}
		} catch (final Exception e) {
			if (e instanceof XPathException)
				{throw (XPathException)e;}
			else
				{throw new XPathException(this, ErrorCodes.XPST0017, e.getMessage());}
		}
	}

	public static FunctionCall lookupFunction(Expression parent, QName qname, int arity) throws XPathException {
	    // check if the function is from a module 
	    final Module module = parent.getContext().getModule(qname.getNamespaceURI());
	    try {
			UserDefinedFunction func;
			if(module == null) {
			    func = parent.getContext().resolveFunction(qname, arity);
			} else {
			    if(module.isInternalModule()) {
			        throw new XPathException(parent, ErrorCodes.XPST0017, "Cannot create a reference to an internal Java function");
			    }
			    func = ((ExternalModule)module).getFunction(qname, arity, parent.getContext());
			}
			if (func == null)
			    {return null;}
			final FunctionCall funcCall = new FunctionCall(parent.getContext(), func);
			funcCall.setLocation(parent.getLine(), parent.getColumn());
			return funcCall;
		} catch (final XPathException e) {
			// function not found: return empty sequence
			return null;
		}
	}
}