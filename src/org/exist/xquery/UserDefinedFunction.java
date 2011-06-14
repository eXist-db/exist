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

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * @author wolf
 */
public class UserDefinedFunction extends Function implements Cloneable {

	private Expression body;
	
	private List<QName> parameters = new ArrayList<QName>(5);
	
	private Sequence[] currentArguments = null;

    private DocumentSet[] contextDocs = null;
    
    private boolean bodyAnalyzed = false;
    
    private FunctionCall call;
    
	public UserDefinedFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public void setFunctionBody(Expression body) {
		this.body = body.simplify();
	}

    public Expression getFunctionBody() {
        return body;
    }
    
    public void addVariable(String varName) throws XPathException {
		QName qname = QName.parse(context, varName, null);
		if (parameters.contains(qname))
			throw new XPathException("XQST0039: function " + getName() + " is already have parameter with the name "+varName);

		parameters.add(qname);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#setArguments(java.util.List)
	 */
	public void setArguments(Sequence[] args, DocumentSet[] contextDocs) throws XPathException {
		this.currentArguments = args;
        this.contextDocs = contextDocs;
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Function#analyze(org.exist.xquery.AnalyzeContextInfo)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		if(!call.isRecursive()) {
			// Save the local variable stack
			LocalVariable mark = context.markLocalVariables(true);
			try {
				LocalVariable var;
				for(QName varName : parameters) {
					var = new LocalVariable(varName);
					context.declareVariableBinding(var);
				}
				
				contextInfo.setParent(this);
				if (!bodyAnalyzed) {
					body.analyze(contextInfo);
					bodyAnalyzed = true;
				}
			} finally {
				// restore the local variable stack
				context.popLocalVariables(mark);
			}
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
//        context.expressionStart(this);
        context.stackEnter(this);
        // Save the local variable stack
        LocalVariable mark = context.markLocalVariables(true);
		try {
			QName varName;
			LocalVariable var;
			int j = 0;
			for (int i = 0; i < parameters.size(); i++, j++) {
				varName = parameters.get(i);
				var = new LocalVariable(varName);
				var.setValue(currentArguments[j]);
				if (contextDocs != null)
					var.setContextDocs(contextDocs[i]);
				context.declareVariableBinding(var);
				
				int actualCardinality;
				if (currentArguments[j].isEmpty()) actualCardinality = Cardinality.EMPTY;
				else if (currentArguments[j].hasMany()) actualCardinality = Cardinality.MANY;
				else actualCardinality = Cardinality.ONE;
				
				if (!Cardinality.checkCardinality(getSignature().getArgumentTypes()[j].getCardinality(), actualCardinality))
					throw new XPathException(this, ErrorCodes.XPTY0004, "Invalid cardinality for parameter $" + varName +  
 						". Expected " + Cardinality.getDescription(getSignature().getArgumentTypes()[j].getCardinality()) + 
 						", got " + currentArguments[j].getItemCount());
			}
			Sequence result = body.eval(contextSequence, contextItem);
			return result;
		} finally {
			// restore the local variable stack
            context.popLocalVariables(mark);
            context.stackLeave(this);
//            context.expressionEnd(this);
        }
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Function#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        FunctionSignature signature = getSignature();
        dumper.display(signature.getName());
        dumper.display('(');
        for(int i = 0; i < signature.getArgumentTypes().length; i++) {
			if(i > 0)
				dumper.display(", ");
			dumper.display(signature.getArgumentTypes()[i]);
		}
		dumper.display(") ");
        dumper.display(signature.getReturnType().toString());
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString() {
        FunctionSignature signature = getSignature();
        StringBuilder buf = new StringBuilder();
        buf.append(signature.getName());
        buf.append('(');
        for(int i = 0; i < signature.getArgumentTypes().length; i++) {
			if(i > 0)
				buf.append(", ");
			buf.append(signature.getArgumentTypes()[i]);
		}
        buf.append(')');
        return buf.toString();
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM
			+ Dependency.CONTEXT_POSITION;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
        // Question: understand this test. Why not reset even is not in recursion ?
		// Answer: would lead to an infinite loop if the function is recursive.
		if(!call.isRecursive()) {
            bodyAnalyzed = false;
			body.resetState(postOptimization);
		}
        if (!postOptimization) {
            currentArguments = null;
            contextDocs = null;
        }
    }

	public boolean needsReset() {
		return currentArguments != null;
	}
	
    public void accept(ExpressionVisitor visitor) {
        visitor.visitUserFunction(this);
    }
    
    /**
     * Return the functions parameters list
     * 
     * @return List of function parameters
     */
    public List<QName> getParameters()
    {
    	return parameters;
    }

    public synchronized Object clone() {
    	try {
    		UserDefinedFunction clone = (UserDefinedFunction) super.clone();
    		
    		clone.currentArguments = null;
    		clone.contextDocs = null;
    		
    		clone.body = this.body; // so body will be analyzed and optimized for all calls of such functions in recursion.  
    	    
    	    return clone;
    	} catch (CloneNotSupportedException e) {
    	    // this shouldn't happen, since we are Cloneable
    	    throw new InternalError();
    	}
    }
    
    public FunctionCall getCaller(){
    	return call;
    }
    
    public void setCaller(FunctionCall call){
    	this.call = call;
    }
}
