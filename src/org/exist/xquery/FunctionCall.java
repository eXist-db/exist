/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery;

import com.sun.xacml.ctx.RequestCtx;

import java.util.List;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.ExistPDP;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Represents a call to a user-defined function 
 * {@link org.exist.xquery.UserDefinedFunction}.
 * 
 * FunctionCall wraps around a user-defined function. It makes sure that all function parameters
 * are checked against the signature of the function. 
 * 
 * @author wolf
 */
public class FunctionCall extends Function {

	private UserDefinedFunction functionDef;
	private Expression expression;
	
	// the name of the function. Used for forward references.
	private QName name = null;
	private List arguments = null;
	
	private boolean isRecursive = false;
	
	private boolean analyzed = false;
	
	public FunctionCall(XQueryContext context, QName name, List arguments) {
		super(context);
		this.name = name;
		this.arguments = arguments;
	}
	
	public FunctionCall(XQueryContext context, UserDefinedFunction functionDef) {
		super(context);
		setFunction(functionDef);
	}
	
	private void setFunction(UserDefinedFunction functionDef) {
		this.functionDef = functionDef;
		this.mySignature = functionDef.getSignature();
		this.expression = functionDef;
		SequenceType returnType = functionDef.getSignature().getReturnType();
		// add return type checks
		if(returnType.getCardinality() != Cardinality.ZERO_OR_MORE)
			expression = new DynamicCardinalityCheck(context, returnType.getCardinality(), expression,
                    new Error(Error.FUNC_RETURN_CARDINALITY));
		if(Type.subTypeOf(returnType.getPrimaryType(), Type.ATOMIC))
			expression = new Atomize(context, expression);
		if(Type.subTypeOf(returnType.getPrimaryType(), Type.NUMBER))
			expression = new UntypedValueCheck(context, returnType.getPrimaryType(), expression, 
                    new Error(Error.FUNC_RETURN_TYPE));
		else if(returnType.getPrimaryType() != Type.ITEM)
			expression = new DynamicTypeCheck(context, returnType.getPrimaryType(), expression);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		contextInfo.setParent(this);
		if (!analyzed) {
			super.analyze(contextInfo);
			if (context.tailRecursiveCall(functionDef.getSignature())) {
				isRecursive = true;
			}
			context.functionStart(functionDef.getSignature());
			try {
				expression.analyze(contextInfo);
				analyzed = true;
			} finally {
				context.functionEnd();
			}
		}
	}
	
    /**
     * Called by {@link XQueryContext} to resolve a call to a function that has not
     * yet been declared. XQueryContext remembers all calls to undeclared functions
     * and tries to resolve them after parsing has completed.
     * 
     * @param functionDef
     * @throws XPathException
     */
	public void resolveForwardReference(UserDefinedFunction functionDef) throws XPathException {
		setFunction(functionDef);
		setArguments(arguments);
		arguments = null;
		name = null;
	} 
	
	public int getArgumentCount() {
		if (arguments == null)
			return super.getArgumentCount();
		else
			return arguments.size();
	}
	
	public QName getQName() {
		return name;
	}
	
	/** 
	 * Evaluates all arguments, then forwards them to the user-defined function.
	 * 
	 * The return value of the user-defined function will be checked against the
	 * provided function signature.
	 * 
	 * @see org.exist.xquery.Expression#eval(Sequence, Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence[] seq = new Sequence[getArgumentCount()];
		for(int i = 0; i < getArgumentCount(); i++) {
			try {
                seq[i] = getArgument(i).eval(contextSequence, contextItem);
//			System.out.println("found " + seq[i].getLength() + " for " + getArgument(i).pprint());
            } catch (XPathException e) {
                if(e.getLine() == 0)
                    e.setASTNode(getASTNode());
                // append location of the function call to the exception message:
                e.addFunctionCall(functionDef, getASTNode());
                throw e;
            }
		}
		return evalFunction(contextSequence, contextItem, seq);
	}

    /**
     * @param contextSequence
     * @param contextItem
     * @param seq
     * @throws XPathException
     */
    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq) throws XPathException {
        if (context.isProfilingEnabled()) {
            context.getProfiler().start(this);     
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }        

		//check access to the method
		try {
			ExistPDP pdp = context.getPDP();
			if(pdp != null) {
				RequestCtx request = pdp.getRequestHelper().createFunctionRequest(context, null, getName());
				//if request is null, this function belongs to a main module and is allowed to be called
				//otherwise, the access must be checked
				if(request != null)
					pdp.evaluate(request);
			}
		} catch (PermissionDeniedException pde) {
			XPathException xe = new XPathException(getASTNode(), "Access to function '" + getName() + "'  denied.", pde);
			xe.addFunctionCall(functionDef, getASTNode());
			throw xe;
		}
		
        functionDef.setArguments(seq);
        
        if (isRecursive) {
//            LOG.warn("Tail recursive function: " + functionDef.getSignature().toString());
            return new DeferredFunctionCallImpl(functionDef.getSignature(), contextSequence, contextItem);
        } else {
            context.functionStart(functionDef.getSignature());
            LocalVariable mark = context.markLocalVariables(true);
            try {
    			Sequence returnSeq = expression.eval(contextSequence, contextItem);
    			while (returnSeq instanceof DeferredFunctionCall && 
    					functionDef.getSignature().equals(((DeferredFunctionCall)returnSeq).getSignature())) {
//    				 LOG.debug("Executing function: " + functionDef.getSignature());
    				returnSeq = ((DeferredFunctionCall) returnSeq).execute();
    			}
                if (context.isProfilingEnabled())
                    context.getProfiler().end(this, "", returnSeq);
    			return returnSeq;
    		} catch(XPathException e) {
    			if(e.getLine() == 0)
    				e.setASTNode(getASTNode());
    			// append location of the function call to the exception message:
    			e.addFunctionCall(functionDef, getASTNode());
    			throw e;
    		} finally {
    			context.popLocalVariables(mark);
                context.functionEnd();
    		}
        }
    }

	 /* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
    public void resetState() {
    	super.resetState();
		functionDef.resetState();
        //TODO : reset expression ?        
	}
    
    private class DeferredFunctionCallImpl extends DeferredFunctionCall {

        private Sequence contextSequence;
        private Item contextItem;

        public DeferredFunctionCallImpl(FunctionSignature signature, Sequence contextSequence, Item contextItem) {
            super(signature);
            this.contextSequence = contextSequence;
            this.contextItem = contextItem;
        }
        
        protected Sequence execute() throws XPathException {
            context.pushDocumentContext();
            context.functionStart(functionDef.getSignature());
            LocalVariable mark = context.markLocalVariables(true);
            try {
                Sequence returnSeq = expression.eval(contextSequence, contextItem);
//                LOG.debug("Returning from execute()");
                return returnSeq;
            } catch(XPathException e) {
                if(e.getLine() == 0)
                    e.setASTNode(getASTNode());
                // append location of the function call to the exception message:
                e.addFunctionCall(functionDef, getASTNode());
                throw e;
            } finally {
                context.popLocalVariables(mark);
                context.functionEnd();
                context.popDocumentContext();
            }
        }
        
    }
}
