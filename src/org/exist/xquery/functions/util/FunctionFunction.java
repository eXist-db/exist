/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.*;

/**
 * @author wolf
 */
public class FunctionFunction extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates a reference to an XQuery function which can later be called from util:call. " +
            "This allows for higher-order functions to be implemented in XQuery. A higher-order " +
            "function is a function that takes another function as argument. " +
            "The first argument represents the name of the function, which should be" +
            "a valid QName. The second argument is the arity of the function. If no" +
            "function can be found that matches the name and arity, an error is thrown. " +
            "Please note: due to the special character of util:function, the arguments to this function " +
            "have to be literals or need to be resolvable at compile time at least.",
            new SequenceType[] {
                new SequenceType(Type.QNAME, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
            },
            new SequenceType(Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE)
        );
    
    private FunctionCall resolvedFunction = null;
    
    /**
     * @param context
     */
    public FunctionFunction(XQueryContext context) {
        super(context, signature);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	super.analyze(contextInfo);
    	String funcName = getArgument(0).eval(null).getStringValue();
    	int arity = ((NumericValue)getArgument(1).eval(null).itemAt(0)).getInt();
    	FunctionCall funcCall = lookupFunction(funcName, arity);
    	contextInfo.addFlag(SINGLE_STEP_EXECUTION);
    	funcCall.analyze(contextInfo);
    }

	/* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
    	String funcName = args[0].getStringValue();
    	int arity = ((NumericValue) args[1].itemAt(0)).getInt();
    	this.resolvedFunction = lookupFunction(funcName, arity);
        return new FunctionReference(resolvedFunction);
    }

    private FunctionCall lookupFunction(String funcName, int arity) throws XPathException {
		// try to parse the qname
	    QName qname;
	    try {
	        qname = QName.parse(context, funcName, context.getDefaultFunctionNamespace());
	    } catch(XPathException e) {
	        e.setASTNode(getASTNode());
	        throw e;
	    }
	    
	    // check if the function is from a module 
	    Module module = context.getModule(qname.getNamespaceURI());
	    UserDefinedFunction func;
	    if(module == null) {
	        func = context.resolveFunction(qname, arity);
			if (func == null)
				throw new XPathException(getASTNode(), Messages.getMessage(Error.FUNC_NOT_FOUND, qname, Integer.toString(arity)));
	    } else {
	        if(module.isInternalModule())
	            throw new XPathException(getASTNode(), "Cannot create a reference to an internal Java function");
	        func = ((ExternalModule)module).getFunction(qname, arity);
	    }
	    FunctionCall funcCall = new FunctionCall(context, func);
	    funcCall.setASTNode(getASTNode());
	    return funcCall;
	}
    
    public void resetState(boolean postOptimization) {
    	super.resetState(postOptimization);
    	if (resolvedFunction != null)
    		resolvedFunction.resetState(postOptimization);
        if (!postOptimization)
            resolvedFunction = null;
    }
}
