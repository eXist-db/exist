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

	private Expression testExpr;
	private Expression thenExpr;
	private Expression elseExpr;
	
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
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return thenExpr.getCardinality() | elseExpr.getCardinality();
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        flags &= (~IN_PREDICATE);
        testExpr.analyze(this, flags);
        thenExpr.analyze(this, flags);
        elseExpr.analyze(this, flags);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence testSeq = testExpr.eval(contextSequence, contextItem);
		if(testSeq.effectiveBooleanValue()) {
			return thenExpr.eval(contextSequence, contextItem);
		} else {
			return elseExpr.eval(contextSequence, contextItem);
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
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		if(thenExpr.returnsType() == elseExpr.returnsType())
			return thenExpr.returnsType();
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		testExpr.resetState();
		thenExpr.resetState();
		elseExpr.resetState();
	}

}
