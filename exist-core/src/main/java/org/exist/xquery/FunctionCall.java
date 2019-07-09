/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery;

import java.util.Arrays;
import java.util.List;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.VirtualNodeSet;
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

    protected UserDefinedFunction functionDef;
    protected Expression expression;


    // the name of the function. Used for forward references.
    protected QName name = null;
    protected List<Expression> arguments = null;
	
    private boolean recursive = false;

    protected VariableReference varDeps[];

    public FunctionCall(XQueryContext context, QName name, List<Expression> arguments) {
        super(context);
        this.name = name;
        this.arguments = arguments;
    }
	
    public FunctionCall(XQueryContext context, UserDefinedFunction functionDef) {
        super(context);
        setFunction(functionDef);
    }
    
    public FunctionCall(FunctionCall other) {
        super(other.getContext());
        this.name = other.name;
        this.recursive = other.recursive;
        this.functionDef = other.functionDef;
        this.expression = other.expression;
        this.mySignature = other.mySignature;
    }

    private void setFunction(UserDefinedFunction functionDef) {
        this.functionDef = (UserDefinedFunction) functionDef.clone();
        this.mySignature = this.functionDef.getSignature();
        this.expression = this.functionDef;
        this.functionDef.setCaller(this);
        final SequenceType returnType = this.functionDef.getSignature().getReturnType();
        
        // add return type checks
        if(returnType.getCardinality() != Cardinality.ZERO_OR_MORE) {
                expression = new DynamicCardinalityCheck(context, returnType.getCardinality(), expression, new Error(Error.FUNC_RETURN_CARDINALITY));
        }
        
        if(Type.subTypeOf(returnType.getPrimaryType(), Type.ATOMIC)) {
                expression = new Atomize(context, expression);
        }
        
        if(Type.subTypeOf(returnType.getPrimaryType(), Type.NUMBER)) {
                expression = new UntypedValueCheck(context, returnType.getPrimaryType(), expression, new Error(Error.FUNC_RETURN_TYPE));
        } else if(returnType.getPrimaryType() != Type.ITEM) {
                expression = new DynamicTypeCheck(context, returnType.getPrimaryType(), expression);
        }
    }

    public UserDefinedFunction getFunction() {
        return functionDef;
    }

	/**
	 * For calls to functions in external modules, check that the instance of the function we were
	 * bound to matches the current implementation of the module bound to our context.  If not,
	 * rebind to the correct instance, but don't bother resetting the signature since it's guaranteed
	 * (I hope!) to be the same.
	 * @throws XPathException 
	 */
	private void updateFunction() throws XPathException {
		if (functionDef.getContext() instanceof ModuleContext) {
			final ModuleContext modContext = (ModuleContext) functionDef.getContext();
			// util:eval will stuff non-module function declarations into a module context sometimes,
			// so watch out for those and ignore them.
			if (functionDef.getName() != null && 
					functionDef.getName().getNamespaceURI().equals(modContext.getModuleNamespace()) &&
                    modContext.getRootContext() != context.getRootContext()) {
                final ExternalModule rootModule = (ExternalModule) context.getRootModule(functionDef.getName().getNamespaceURI());
                if (rootModule != null) {
                    final UserDefinedFunction replacementFunctionDef =
                        rootModule.getFunction(functionDef.getName(), getArgumentCount(), modContext);
                    if (replacementFunctionDef != null) {
                        expression = functionDef = (UserDefinedFunction) replacementFunctionDef.clone();
                        mySignature = functionDef.getSignature();
                        functionDef.setCaller(this);
                    }
                }
            }
        }
    }
        
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		//updateFunction();
         final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
         newContextInfo.setParent(this);
         newContextInfo.removeFlag(IN_NODE_CONSTRUCTOR);
         super.analyze(newContextInfo);
		if (context.tailRecursiveCall(functionDef.getSignature())) {
			setRecursive(true);
		}
		context.functionStart(functionDef.getSignature());
		try {
			expression.analyze(newContextInfo);
		} finally {
			context.functionEnd();
		}

        varDeps = new VariableReference[getArgumentCount()];
        for(int i = 0; i < getArgumentCount(); i++) {
            final Expression arg = getArgument(i);
            final VariableReference varRef = BasicExpressionVisitor.findVariableRef(arg);
            if(varRef != null) {
                varDeps[i] = varRef;
            }
        }
    }
	
    /**
     * Called by {@link XQueryContext} to resolve a call to a function that has not
     * yet been declared. XQueryContext remembers all calls to undeclared functions
     * and tries to resolve them after parsing has completed.
     * 
     * @param functionDef the function definition to resolve
     * @throws XPathException if an error occurs resolving the forward reference
     */
    public void resolveForwardReference(UserDefinedFunction functionDef) throws XPathException {
        setFunction(functionDef);
        setArguments(arguments);
        arguments = null;
        name = null;
    } 
	
    @Override
    public int getArgumentCount() {
        if(arguments == null) {
            return super.getArgumentCount();
        }
        
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
    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final Sequence[] seq = new Sequence[getArgumentCount()];
        final DocumentSet[] contextDocs = new DocumentSet[getArgumentCount()];
        for(int i = 0; i < getArgumentCount(); i++) {
            try {
                seq[i] = getArgument(i).eval(contextSequence, contextItem);
                if(varDeps != null && varDeps[i] != null) {
                    final Variable var = varDeps[i].getVariable();
                    if(var != null) {
                        contextDocs[i] = var.getContextDocs();
                    }
                }
                //System.out.println("found " + seq[i].getLength() + " for " + getArgument(i).pprint());
            } catch(final XPathException e) {
                if(e.getLine() <= 0) {
                    e.setLocation(line, column, getSource());
                }
                // append location of the function call to the exception message:
                e.addFunctionCall(functionDef, this);
                throw e;
            }
        }
        
        final Sequence result = evalFunction(contextSequence, contextItem, seq, contextDocs);
        try {
            //Don't check deferred calls : it would result in a stack overflow
            //TODO : find a solution or... is it already here ?
            //Don't test on empty sequences since they can have several types
            //TODO : add a prior cardinality check on wether an empty result is allowed or not
            //TODO : should we introduce a deffered type check on VirtualNodeSet 
            // and trigger it when the nodeSet is realized ?
            if(!(result instanceof DeferredFunctionCall) && !(result instanceof VirtualNodeSet) && !result.isEmpty()) {
                getSignature().getReturnType().checkType(result.getItemType());
            }
        } catch(final XPathException e) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "Return type of function '" + getSignature().getName() + "'. " + e.getMessage(), Sequence.EMPTY_SEQUENCE, e);
        }

	
        //Annotation Triggers are bad design, disabled as breaks RESTXQ - Adam.
        /*for (Annotation ann : functionDef.getSignature().getAnnotations()) {
            AnnotationTrigger trigger = ann.getTrigger();
            if (trigger instanceof AnnotationTriggerOnResult) {
                try {
                    ((AnnotationTriggerOnResult) trigger).trigger(result);
                } catch (Throwable e) {
                    throw new XPathException(this, "function '" + getSignature().getName() + "'. " + e.getMessage(), e);
                }
            }
        }*/

        return result;
    }

    /**
     * Evaluate the function.
     *
     * @param contextSequence the context sequence
     * @param contextItem the context item
     * @param seq the sequence
     * @throws XPathException if an error occurs whilst evaluation the function.
     * @return the evaluation result
     */
    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq) throws XPathException {
        return evalFunction(contextSequence, contextItem, seq, null);
    }

    public Sequence evalFunction(Sequence contextSequence, Item contextItem, Sequence[] seq, DocumentSet[] contextDocs) throws XPathException {
        context.proceed(this);
        if(context.isProfilingEnabled()) {
            context.getProfiler().start(this);     
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            
            if(contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            
            if(contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        functionDef.setArguments(seq, contextDocs);
        
        if(isRecursive()) {
            //LOG.warn("Tail recursive function: " + functionDef.getSignature().toString());
            return new DeferredFunctionCallImpl(this, contextSequence, contextItem, seq, contextDocs);
        } else {
            
            //XXX: should we have it? org.exist.xquery.UserDefinedFunction do a call -shabanovd
            context.stackEnter(this);

            long start = System.currentTimeMillis();
            if(context.getProfiler().traceFunctions()) {
                if (context.tailRecursiveCall(getSignature()))
                    {start = -1;}
                context.getProfiler().traceFunctionStart(this);
            }
            context.functionStart(functionDef.getSignature());
            final LocalVariable mark = context.markLocalVariables(true);
            context.pushInScopeNamespaces(false);

            Sequence returnSeq = null;
            try {
                
                returnSeq = expression.eval(contextSequence, contextItem);
                while(returnSeq instanceof DeferredFunctionCall &&
                    functionDef.getSignature().equals(((DeferredFunctionCall)returnSeq).getSignature())) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Executing function: " + functionDef.getSignature());
                    }
                    returnSeq = ((DeferredFunctionCall) returnSeq).execute();
                }
                
                if(context.getProfiler().traceFunctions()) {
                    context.getProfiler().traceFunctionEnd(this, start < 0 ? 0 : System.currentTimeMillis() - start);
                }
                
                if(context.isProfilingEnabled()) {
                    context.getProfiler().end(this, "", returnSeq);
                }
                
                return returnSeq;
    		
            } catch(final XPathException e) {
                // append location of the function call to the exception message:
                if(e.getLine() <= 0) {
                    e.setLocation(expression.getLine(), expression.getColumn());
                }
    			
                e.addFunctionCall(functionDef, this);
                throw e;
            } finally {
                context.popInScopeNamespaces();
                context.popLocalVariables(mark, returnSeq);
                context.functionEnd();

                context.stackLeave(this);
            }
        }
    }

    /**
     * @see org.exist.xquery.PathExpr#resetState(boolean)
     */
    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        if(expression.needsReset() || postOptimization) {
            expression.resetState(postOptimization);
        }
    }

    /**
     * @see org.exist.xquery.Expression#setContextDocSet(org.exist.dom.persistent.DocumentSet)
     */
    @Override
    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        functionDef.setContextDocSet(contextSet);
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitFunctionCall(this);
    }

    private static class DeferredFunctionCallImpl extends DeferredFunctionCall {

        private final FunctionCall call;

        private UserDefinedFunction functionDef;
        private Expression expression;

        private Sequence contextSequence;
        private Item contextItem;
        private final Sequence[] seq;
        private final DocumentSet[] contextDocs;

        private DeferredFunctionCallImpl(FunctionCall call, Sequence contextSequence, Item contextItem, Sequence[] seq, DocumentSet[] contextDocs) {
            super(call.mySignature);
            this.contextSequence = contextSequence;
            this.contextItem = contextItem;
            if (seq != null) {
                // copy the sequence of arguments to make sure it is not overwritten by caller
                this.seq = Arrays.copyOf(seq, seq.length);
            } else {
                this.seq = null;
            }
            this.contextDocs = contextDocs;

            this.call = call;
            setup();
        }

        private void setup() {
            this.functionDef = (UserDefinedFunction) call.functionDef.clone();
            //this.mySignature = this.functionDef.getSignature();
            this.expression = this.functionDef;
            this.functionDef.setCaller(call);
            final SequenceType returnType = this.functionDef.getSignature().getReturnType();

            final XQueryContext context = call.context;

            // add return type checks
            if(returnType.getCardinality() != Cardinality.ZERO_OR_MORE) {
                expression = new DynamicCardinalityCheck(context, returnType.getCardinality(), expression, new Error(Error.FUNC_RETURN_CARDINALITY));
            }

            if(Type.subTypeOf(returnType.getPrimaryType(), Type.ATOMIC)) {
                expression = new Atomize(context, expression);
            }

            if(Type.subTypeOf(returnType.getPrimaryType(), Type.NUMBER)) {
                expression = new UntypedValueCheck(context, returnType.getPrimaryType(), expression, new Error(Error.FUNC_RETURN_TYPE));
            } else if(returnType.getPrimaryType() != Type.ITEM) {
                expression = new DynamicTypeCheck(context, returnType.getPrimaryType(), expression);
            }
        }
        
        @Override
        protected Sequence execute() throws XPathException {
            final XQueryContext context = call.context;

            context.pushDocumentContext();
            //context.stackEnter(expression);
            context.functionStart(functionDef.getSignature());
            final LocalVariable mark = context.markLocalVariables(true);
            Sequence returnSeq = null;
            try {
                
                /*
                  Ensure that the arguments are set for a deferred function
                  as reset may alreay have been called before our deferred execution
                 */
                functionDef.setArguments(seq, contextDocs);
                
                returnSeq = expression.eval(contextSequence, contextItem);
                LOG.trace("Returning from execute()");
                return returnSeq;
            } catch(final XPathException e) {
                // append location of the function call to the exception message:
                if(e.getLine() == 0) {
                    e.setLocation(call.line, call.column);
                }
                e.addFunctionCall(functionDef, call);
                throw e;
            } finally {
                context.popLocalVariables(mark, returnSeq);
                context.functionEnd();
                //context.stackLeave(expression);
                context.popDocumentContext();
            }
        }
        
    }
    
    protected void setRecursive(boolean recursive) {
        this.recursive = recursive;
    }
    
    public boolean isRecursive(){
    	return recursive;
    }
}
