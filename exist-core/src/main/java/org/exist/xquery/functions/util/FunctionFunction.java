/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.Messages;
import org.exist.xquery.value.*;

import static org.apache.commons.lang3.ArrayUtils.isEmpty;

/**
 * @author wolf
 */
public class FunctionFunction extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(FunctionFunction.class);
	protected static final FunctionParameterSequenceType functionName = new FunctionParameterSequenceType("name", Type.QNAME, Cardinality.EXACTLY_ONE, "The name of the function");
	protected static final FunctionParameterSequenceType arity = new FunctionParameterSequenceType("arity", Type.INTEGER, Cardinality.EXACTLY_ONE, "The arity of the function");
	protected static final FunctionReturnSequenceType result = new FunctionReturnSequenceType(Type.FUNCTION, Cardinality.EXACTLY_ONE, "the reference to the XQuery function");

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("function", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Creates a reference to an XQuery function which can later be called from util:call. " +
            "This allows for higher-order functions to be implemented in XQuery. A higher-order " +
            "function is a function that takes another function as argument. " +
            "The first argument represents the name of the function, which should be" +
            "a valid QName. The second argument is the arity (number of parameters) of " +
            "the function. If no function can be found that matches the name and arity, " +
            "an error is thrown. " +
            "Please note: the arguments to this function " +
            "have to be literals or need to be resolvable at compile time at least.",
            new SequenceType[] { functionName, arity },
            result
        );
    
    private FunctionCall resolvedFunction = null;

    public FunctionFunction(XQueryContext context) {
        super(context, signature);
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	super.analyze(contextInfo);
    	final String funcName = getArgument(0).eval(null, null).getStringValue();
    	final int arity = ((NumericValue)getArgument(1).eval(null, null).itemAt(0)).getInt();
    	final FunctionCall funcCall = lookupFunction(funcName, arity);
    	contextInfo.addFlag(SINGLE_STEP_EXECUTION);
    	funcCall.analyze(contextInfo);
    }

	@Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
    	
    	final String funcName = args[0].getStringValue();
    	final int arity = ((NumericValue) args[1].itemAt(0)).getInt();
    	this.resolvedFunction = lookupFunction(funcName, arity);
        return new FunctionReference(this, resolvedFunction);
    }

    private FunctionCall lookupFunction(String funcName, int arity) throws XPathException {
		// try to parse the qname
	    QName qname;
	    try {
	        qname = QName.parse(context, funcName, context.getDefaultFunctionNamespace());
	    } catch(final QName.IllegalQNameException e) {
			throw new XPathException(this, ErrorCodes.XPST0081, "No namespace defined for prefix " + funcName);
	    }
	    
	    // check if the function is from a module 
	    final Module[] modules = context.getModules(qname.getNamespaceURI());
	    UserDefinedFunction func = null;
	    if (isEmpty(modules)) {
	        func = context.resolveFunction(qname, arity);
	    } else {
	    	for (final Module module : modules) {
				func = ((ExternalModule)module).getFunction(qname, arity, context);
				if (func != null) {
					if (module.isInternalModule()) {
						logger.error("Cannot create a reference to an internal Java function");
						throw new XPathException(this, "Cannot create a reference to an internal Java function");
					}

					break;
				}
			}
	    }

        if (func == null) {
        	throw new XPathException(this, Messages.getMessage(Error.FUNC_NOT_FOUND, qname, Integer.toString(arity)));
        }

	    final FunctionCall funcCall = new FunctionCall(context, func);
        funcCall.setLocation(line, column);
	    return funcCall;
	}
    
    public void resetState(boolean postOptimization) {
    	super.resetState(postOptimization);
    	if (resolvedFunction != null)
    		{resolvedFunction.resetState(postOptimization);}
        if (!postOptimization)
            {resolvedFunction = null;}
    }
}
