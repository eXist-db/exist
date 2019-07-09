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
import org.exist.xquery.value.Type;

import java.util.Optional;

/**
 * A global variable declaration (with: declare variable). Variable bindings within
 * for and let expressions are handled by {@link org.exist.xquery.ForExpr} and
 * {@link org.exist.xquery.LetExpr}.
 * 
 * @author wolf
 */
public class VariableDeclaration extends AbstractExpression implements RewritableExpression {

	final QName qname;
    Optional<Expression> expression;
    SequenceType sequenceType = null;
    boolean analyzeDone = false;

	public VariableDeclaration(XQueryContext context, QName qname, Expression expr) {
		super(context);
		this.qname = qname;
		this.expression = Optional.ofNullable(expr);
	}

    public QName getName() {
        return qname;
    }

	/**
	 * Set the sequence type of the variable.
	 * 
	 * @param type the sequence type
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}

    public SequenceType getSequenceType() {
        return sequenceType;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        final Variable var = new VariableImpl(qname);
        var.setIsInitialized(false);
        if (!analyzeDone) {
            final Module myModule = context.getModule(qname.getNamespaceURI());
            if(myModule != null) {
// WM: duplicate var declaration is now caught in the XQuery tree parser
//                if (myModule.isVarDeclared(qn))
//                    throw new XPathException(this, ErrorCodes.XQST0049, "It is a static error if more than one " +
//                            "variable declared or imported by a module has the same expanded QName. Variable: " + qn);
                myModule.declareVariable(var);
            } else {
// WM: duplicate var declaration is now caught in the XQuery tree parser
//                if(context.isVarDeclared(qn)) {
//                    throw new XPathException(this, ErrorCodes.XQST0049, "It is a static error if more than one " +
//                            "variable declared or imported by a module has the same expanded QName. Variable: " + qn);
//                }
                context.declareGlobalVariable(var);
            }
            analyzeDone = true;
        }
        analyzeExpression(contextInfo);
        var.setIsInitialized(true);
    }

    /**
     * Analyze just the expression. For dynamically imported modules this needs to be done one time
     * after import.
     *
     * @param contextInfo context information
     * @throws XPathException in case of static error
     */
    public void analyzeExpression(AnalyzeContextInfo contextInfo) throws XPathException {
        if (expression.isPresent()) {
            expression.get().analyze(contextInfo);
        }
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }

        context.pushInScopeNamespaces(false);
        try {
            final Module myModule = context.getRootModule(qname.getNamespaceURI());

            context.pushDocumentContext();
            try {
                context.prologEnter(this);
                if (expression.isPresent()) {
                    // normal variable declaration or external var with default value
                    final Sequence seq = expression.get().eval(contextSequence, null);
                    final Variable var;
                    if (myModule != null) {
                        var = myModule.declareVariable(qname, seq);
                        var.setSequenceType(sequenceType);
                        var.checkType();
                    } else {
                        var = new VariableImpl(qname);
                        var.setValue(seq);
                        var.setSequenceType(sequenceType);
                        var.checkType();
                        context.declareGlobalVariable(var);
                    }

                    if (context.getProfiler().isEnabled()) {
                        //Note : that we use seq but we return Sequence.EMPTY_SEQUENCE
                        context.getProfiler().end(this, "", seq);
                    }
                } else {
                    // external variable without default
                    final Variable external = context.resolveGlobalVariable(qname);
                    if (external == null) {
                        // If no value is provided for the variable by the external environment, and VarDefaultValue
                        // is not specified, then a dynamic error is raised [err:XPDY0002]
                        throw new XPathException(ErrorCodes.XPDY0002, "no value specified for external variable " +
                                qname);
                    }
                    external.setSequenceType(sequenceType);

                    if (myModule != null) {
                        // declare on module
                        myModule.declareVariable(external);
                    }
                }
            } finally {
                context.popDocumentContext();
            }
        } finally {
            context.popInScopeNamespaces();
        }

        return null;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.nl().display("declare variable $").display(qname.toString(), line);
        if(sequenceType != null) {
            dumper.display(" as ").display(sequenceType.toString());
        }
        dumper.display("{");
        dumper.startIndent();
        expression.ifPresent(e -> e.dump(dumper));
        dumper.endIndent();
        dumper.nl().display("}").nl();
    }
    
    public String toString() {
    	final StringBuilder result = new StringBuilder();
    	result.append("declare variable $").append(qname);
        if(sequenceType != null) {
        	result.append(" as ").append(sequenceType.toString());
        }
        if (expression.isPresent()) {
            result.append("{");
            result.append(expression.toString());
            result.append("}");
        }
        return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return expression.isPresent() ? expression.get().returnsType() : Type.ITEM;
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
		return expression.isPresent() ? expression.get().getCardinality() : Cardinality.ONE_OR_MORE;
	}

	public Optional<Expression> getExpression() {
		return expression;
	}
	
	/* RewritableExpression API */
    
	public void replace(Expression oldExpr, Expression newExpr) {
	    if (expression.isPresent() && expression.get() == oldExpr) {
            this.expression = Optional.ofNullable(newExpr);
        }
	}

	@Override
	public Expression getPrevious(Expression current) {
		return null;
	}
	
	@Override
	public Expression getFirst() {
		return null;
	}
	
	@Override
	public void remove(Expression oldExpr) throws XPathException {
	}

	/* END RewritableExpression API */

    @Override
    public boolean allowMixedNodesInReturn() {
        return true;
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitVariableDeclaration(this);
    }
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.ifPresent(e -> e.resetState(postOptimization));
        if (!postOptimization)
            {analyzeDone = false;}
    }
}
