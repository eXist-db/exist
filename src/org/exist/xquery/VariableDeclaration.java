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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;

/**
 * A global variable declaration (with: declare variable). Variable bindings within
 * for and let expressions are handled by {@link org.exist.xquery.ForExpr} and
 * {@link org.exist.xquery.LetExpr}.
 * 
 * @author wolf
 */
public class VariableDeclaration extends AbstractExpression {

	String qname;
	SequenceType sequenceType = null;
	Expression expression;
    boolean analyzeDone = false;

    /**
	 * @param context
	 */
	public VariableDeclaration(XQueryContext context, String qname, Expression expr) {
		super(context);
		this.qname = qname;
		this.expression = expr;
	}

	/**
	 * Set the sequence type of the variable.
	 * 
	 * @param type
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        QName qn = QName.parse(context, qname, null);
        Variable var = new Variable(qn);
        var.setIsInitialized(false);
        if (!analyzeDone) {
            Module myModule = context.getModule(qn.getNamespaceURI());
            if(myModule != null) {
// WM: duplicate var declaration is now caught in the XQuery tree parser
                if (myModule.isVarDeclared(qn))
                    throw new XPathException(this, "err:XQST0049: It is a static error if more than one " +
                            "variable declared or imported by a module has the same expanded QName. Variable: " + qn);
                myModule.declareVariable(var);
            } else {
// WM: duplicate var declaration is now caught in the XQuery tree parser
                if(context.isVarDeclared(qn)) {
                    throw new XPathException(this, "err:XQST0049: It is a static error if more than one " +
                            "variable declared or imported by a module has the same expanded QName. Variable: " + qn);
                }
                context.declareGlobalVariable(var);
            }
            analyzeDone = true;
        }
        expression.analyze(contextInfo);
        var.setIsInitialized(true);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        context.pushInScopeNamespaces(false);
		QName qn = QName.parse(context, qname, null);
        
		Module myModule = context.getRootModule(qn.getNamespaceURI());		
        context.pushDocumentContext();
		// declare the variable
		Sequence seq = expression.eval(null, null);
        Variable var;
		if(myModule != null) {
			var = myModule.declareVariable(qn, seq);
            var.setSequenceType(sequenceType);
            var.checkType();
        } else {
			var = new Variable(qn);
			var.setValue(seq);
            var.setSequenceType(sequenceType);
            var.checkType();
			context.declareGlobalVariable(var);
		}
        
        if (context.getProfiler().isEnabled())
            //Note : that we use seq but we return Sequence.EMPTY_SEQUENCE
            context.getProfiler().end(this, "", seq);
        context.popInScopeNamespaces();
        context.popDocumentContext();
		return Sequence.EMPTY_SEQUENCE;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.nl().display("declare variable $").display(qname, line);
        if(sequenceType != null) {
            dumper.display(" as ").display(sequenceType.toString());
        }
        dumper.display("{");
        dumper.startIndent();
        expression.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("}").nl();
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("declare variable $").append(qname);
        if(sequenceType != null) {
        	result.append(" as ").append(sequenceType.toString());
        }
        result.append("{");
        result.append(expression.toString());        
        result.append("}");
        return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return expression.returnsType();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return expression.getCardinality();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
        if (!postOptimization)
            analyzeDone = false;
    }
}
