/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Runtime-check for the cardinality of a function parameter.
 * 
 * @author wolf
 */
public class DynamicCardinalityCheck extends AbstractExpression {

	private Expression expression;
	private int requiredCardinality;
	
	public DynamicCardinalityCheck(XQueryContext context, int requiredCardinality, Expression expr) {
		super(context);
		this.requiredCardinality = requiredCardinality;
		this.expression = expr;
	}
	
	public DynamicCardinalityCheck(XQueryContext context, int requiredCardinality, Expression expr,
	        XQueryAST ast) {
		super(context);
		this.requiredCardinality = requiredCardinality;
		this.expression = expr;
		setASTNode(ast);
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        expression.analyze(this, flags);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence seq = expression.eval(contextSequence, contextItem);
		int items = seq.getLength();
		if(items > 0 && requiredCardinality == Cardinality.EMPTY)
			throw new XPathException(getASTNode(), "Empty sequence expected for " +
			        ExpressionDumper.dump(expression) + "; got " + items);
		if(items == 0 && (requiredCardinality & Cardinality.ZERO) == 0)
			throw new XPathException(getASTNode(), 
			        "Empty sequence is not allowed here: " + ExpressionDumper.dump(expression));
		else if(items > 1 && (requiredCardinality & Cardinality.MANY) == 0)
			throw new XPathException(getASTNode(), 
			        "Sequence with more than one item is not allowed here: " + 
			        ExpressionDumper.dump(expression) + "; got " + items);
		return seq;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if(dumper.verbosity() > 1) {
	        dumper.display("#cardinality(");
        }
        expression.dump(dumper);
        if(dumper.verbosity() > 1)
	        dumper.display(')');
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
		return expression.getDependencies();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		expression.resetState();
	}
}
