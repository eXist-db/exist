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
import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.VirtualNodeSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.ExistPDP;
import org.exist.xquery.util.Error;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.*;

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

    private VariableReference varDeps[];

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
	
	/**
	 * For calls to functions in external modules, check that the instance of the function we were
	 * bound to matches the current implementation of the module bound to our context.  If not,
	 * rebind to the correct instance, but don't bother resetting the signature since it's guaranteed
	 * (I hope!) to be the same.
	 */
	private void updateFunction() {
		if (functionDef.getContext() instanceof ModuleContext) {
			ModuleContext modContext = (ModuleContext) functionDef.getContext();
			// util:eval will stuff non-module function declarations into a module context sometimes,
			// so watch out for those and ignore them.
			if (functionDef.getName().getNamespaceURI().equals(modContext.getModuleNamespace()) &&
                    modContext.getRootContext() != context.getRootContext()) {
                ExternalModule rootModule = (ExternalModule) context.getRootModule(functionDef.getName().getNamespaceURI());
                if (rootModule != null) {
                    UserDefinedFunction replacementFunctionDef =
                        rootModule.getFunction(functionDef.getName(), getArgumentCount());
                    if (replacementFunctionDef != null)
                        expression = functionDef = replacementFunctionDef;
                }
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		updateFunction();
		contextInfo.setParent(this);
         AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
         newContextInfo.removeFlag(IN_NODE_CONSTRUCTOR);
         super.analyze(newContextInfo);
		if (context.tailRecursiveCall(functionDef.getSignature())) {
			isRecursive = true;
		}
		context.functionStart(functionDef.getSignature());
		try {
			expression.analyze(newContextInfo);
		} finally {
			context.functionEnd();
		}

         varDeps = new VariableReference[getArgumentCount()];
         for(int i = 0; i < getArgumentCount(); i++) {
             Expression arg = getArgument(i);
             VariableReference varRef = BasicExpressionVisitor.findVariableRef(arg);
             if (varRef != null) {
                 varDeps[i] = varRef;
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
        DocumentSet[] contextDocs = new DocumentSet[getArgumentCount()];
        for(int i = 0; i < getArgumentCount(); i++) {
			try {
                seq[i] = getArgument(i).eval(contextSequence, contextItem);
                if (varDeps != null && varDeps[i] != null) {
                    Variable var = varDeps[i].getVariable();
                    if (var != null)
                        contextDocs[i] = var.getContextDocs();
                }
//			System.out.println("found " + seq[i].getLength() + " for " + getArgument(i).pprint());
            } catch (XPathException e) {
                if(e.getLine() == 0) {
                    e.setLocation(line, column);
                }
                // append location of the function call to the exception message:
                e.addFunctionCall(functionDef, this);
                throw e;
            }
		}
		Sequence result = evalFunction(contextSequence, contextItem, seq, contextDocs);

		try {
			//Don't check deferred calls : it would result in a stack overflow
			//TODO : find a solution or... is it already here ?
			if (!(result instanceof DeferredFunctionCall) &&
				//Don't test on empty sequences since they can have several types
				//TODO : add a prior cardinality check on wether an empty result is allowed or not
				//TODO : should we introduce a deffered type check on VirtualNodeSet 
				// and trigger it when the nodeSet is realized ?
				!(result instanceof VirtualNodeSet) &&
                !result.isEmpty())
				getSignature().getReturnType().checkType(result.getItemType()); 
		} catch (XPathException e) {
			throw new XPathException(this, "err:XPTY0004: return type of function '" + getSignature().getName() + "'. " +
					e.getMessage(), e);
		}

		return result;
	}

    /**
     * @param contextSequence
     * @param contextItem
     * @param seq
     * @throws XPathException
     */
    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq) throws XPathException {
        return evalFunction(contextSequence, contextItem, seq, null);
    }

    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq, DocumentSet[] contextDocs) throws XPathException {
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
			XPathException xe = new XPathException(this, "Access to function '" + getName() + "'  denied.", pde);
			xe.addFunctionCall(functionDef, this);
			throw xe;
		}
		
        functionDef.setArguments(seq, contextDocs);
        
        if (isRecursive) {
//            LOG.warn("Tail recursive function: " + functionDef.getSignature().toString());
            return new DeferredFunctionCallImpl(functionDef.getSignature(), contextSequence, contextItem);
        } else {
            
        	//XXX: should we have it? org.exist.xquery.UserDefinedFunction do a call -shabanovd
        	context.stackEnter(this);
            
        	context.functionStart(functionDef.getSignature());
            LocalVariable mark = context.markLocalVariables(true);
            try {
                if (context.getProfiler().traceFunctions())
                    context.getProfiler().traceFunctionStart(this);
                long start = System.currentTimeMillis();
    			Sequence returnSeq = expression.eval(contextSequence, contextItem);
    			while (returnSeq instanceof DeferredFunctionCall &&
    					functionDef.getSignature().equals(((DeferredFunctionCall)returnSeq).getSignature())) {
//    				 LOG.debug("Executing function: " + functionDef.getSignature());
    				returnSeq = ((DeferredFunctionCall) returnSeq).execute();
    			}
                if (context.getProfiler().traceFunctions())
                    context.getProfiler().traceFunctionEnd(this, (System.currentTimeMillis() - start));
                if (context.isProfilingEnabled())
                    context.getProfiler().end(this, "", returnSeq);
    			return returnSeq;
    		} catch(XPathException e) {
    			if(e.getLine() == 0)
                    e.setLocation(line, column);
    			// append location of the function call to the exception message:
    			e.addFunctionCall(functionDef, this);
    			throw e;
    		} finally {
    			context.popLocalVariables(mark);
                context.functionEnd();
                
                context.stackLeave(this);
    		}
        }
    }

	 /* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
    public void resetState(boolean postOptimization) {
         super.resetState(postOptimization);
         functionDef.resetState(postOptimization);
        //TODO : reset expression ?        
	}

    /* (non-Javadoc)
    * @see org.exist.xquery.Expression#setContextDocSet(org.exist.dom.DocumentSet)
    */
    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        functionDef.setContextDocSet(contextSet);
    }

    public void accept(ExpressionVisitor visitor) {
        // forward to the called function
        for(int i = 0; i < getArgumentCount(); i++) {
        	getArgument(i).accept(visitor);
		}
		functionDef.accept(visitor);
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
            
            context.stackEnter(expression);

            context.functionStart(functionDef.getSignature());
            LocalVariable mark = context.markLocalVariables(true);
            try {
                Sequence returnSeq = expression.eval(contextSequence, contextItem);
//                LOG.debug("Returning from execute()");
                return returnSeq;
            } catch(XPathException e) {
                if(e.getLine() == 0)
                    e.setLocation(line, column);
                // append location of the function call to the exception message:
                e.addFunctionCall(functionDef, FunctionCall.this);
                throw e;
            } finally {
                context.popLocalVariables(mark);
                context.functionEnd();
                
                context.stackLeave(expression);
                
                context.popDocumentContext();
            }
        }
        
    }
}
