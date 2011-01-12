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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * XQuery if ... then ... else expression.
 * 
 * @author wolf
 */
public class ConditionalExpression extends AbstractExpression {

    private final Expression testExpr;

    private final Expression thenExpr;

    private final Expression elseExpr;

    public ConditionalExpression(XQueryContext context, Expression testExpr, Expression thenExpr,
		Expression elseExpr) {
		super(context);
		this.testExpr = testExpr;
		this.thenExpr = thenExpr;
		this.elseExpr = elseExpr;
	}

    /* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}

    public Expression getTestExpr() {
        return testExpr;
    }

    public Expression getThenExpr() {
        return thenExpr;
    }

    public Expression getElseExpr() {
        return elseExpr;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return thenExpr.getCardinality() | elseExpr.getCardinality();
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
        contextInfo.setParent(this);
        testExpr.analyze(contextInfo);
        thenExpr.analyze(contextInfo);
        elseExpr.analyze(contextInfo);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
        context.expressionStart(this);
		Sequence testSeq = testExpr.eval(contextSequence, contextItem);
        try {
    		if (testSeq.effectiveBooleanValue()) {
    			return thenExpr.eval(contextSequence, contextItem);
    		} else {
    			return elseExpr.eval(contextSequence, contextItem);
    		}
        } catch (XPathException e) {
            if (e.getLine() == 0)
                e.setLocation(line, column);
            throw e;
        } finally {
            context.expressionEnd(this);
        }
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#preselect(org.exist.dom.DocumentSet)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("if (");
        dumper.startIndent();
        testExpr.dump(dumper);
        dumper.endIndent();
        dumper.nl().display(") then");
        dumper.startIndent();
        thenExpr.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("else");
        dumper.startIndent();
        elseExpr.dump(dumper);
        dumper.endIndent();
    }
    
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("if ( ");
    	result.append(testExpr.toString());       
    	result.append(" ) then ");        
    	result.append(thenExpr.toString());        
    	result.append(" else ");       
    	result.append(elseExpr.toString());
        return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.getCommonSuperType(thenExpr.returnsType(), elseExpr.returnsType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		testExpr.resetState(postOptimization);
		thenExpr.resetState(postOptimization);
		elseExpr.resetState(postOptimization);
	}


    public void accept(ExpressionVisitor visitor) {
        visitor.visitConditional(this);
    }
}
