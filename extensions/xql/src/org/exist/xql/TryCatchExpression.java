/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 20010 the eXist-db project
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
 * XQuery try {...} catch{...} expression.
 * 
 * @author Adam Retter <adam@exist-db.org>
 */
public class TryCatchExpression extends AbstractExpression {

    private final Expression tryTargetExpr;

    private final Expression catchExpr;

    public TryCatchExpression(XQueryContext context, Expression tryTargetExpr, Expression catchExpr) {
        super(context);
        this.tryTargetExpr = tryTargetExpr;
        this.catchExpr = catchExpr;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
    @Override
    public int getDependencies() {
            return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
    }

    public Expression getTryTargetExpr() {
        return tryTargetExpr;
    }

    public Expression getCatchExpr() {
        return catchExpr;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
    @Override
    public int getCardinality() {
        return catchExpr.getCardinality();
    }
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setFlags(contextInfo.getFlags() & (~IN_PREDICATE));
        contextInfo.setParent(this);
        tryTargetExpr.analyze(contextInfo);
        catchExpr.analyze(contextInfo);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
    @Override
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {

        context.expressionStart(this);

        //TODO there can be multiple catchExpressions

        try{
            Sequence tryTargetSeq = tryTargetExpr.eval(contextSequence, contextItem);
            return tryTargetSeq;
        } catch(XPathException xpe) {
            Sequence catchSeq = catchExpr.eval(contextSequence, contextItem);
            return catchSeq;
        } finally {
            context.expressionEnd(this);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("try {");
        dumper.startIndent();
        tryTargetExpr.dump(dumper);
        dumper.endIndent();
        dumper.nl().display("} catch (expr) {");    //TODO output the CatchClause
        dumper.startIndent();
        catchExpr.dump(dumper);
        dumper.nl().display("}");
        dumper.endIndent();
    }
    
    @Override
    public String toString() {
    	StringBuilder result = new StringBuilder();
    	result.append("try { ");
    	result.append(tryTargetExpr.toString());
    	result.append(" } catch (expr) { ");        //TODO output the CatchClause
    	result.append(catchExpr.toString());
    	result.append("}");
        return result.toString();
    }    
    
    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    @Override
    public int returnsType() {
        return catchExpr.returnsType();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        tryTargetExpr.resetState(postOptimization);
        catchExpr.resetState(postOptimization);
    }


    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitTryCatch(this);
    }
}
