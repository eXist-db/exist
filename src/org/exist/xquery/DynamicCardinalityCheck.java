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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.Error;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Runtime-check for the cardinality of a function parameter.
 * 
 * @author wolf
 */
public class DynamicCardinalityCheck extends AbstractExpression {

    final private Expression expression;
    final private int requiredCardinality;
    private Error error;

    public DynamicCardinalityCheck(XQueryContext context, int requiredCardinality,
            Expression expr, Error error) {
        super(context);
        this.requiredCardinality = requiredCardinality;
        this.expression = expr;
        this.error = error;
        setLocation(expression.getLine(), expression.getColumn());
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence seq = expression.eval(contextSequence, contextItem);
        int actualCardinality;
        if (seq.isEmpty())
            {actualCardinality = Cardinality.EMPTY;}
        else if (seq.hasMany())
            {actualCardinality = Cardinality.MANY;}
        else
            {actualCardinality = Cardinality.ONE;}
        if (!Cardinality.checkCardinality(requiredCardinality, actualCardinality)) {
            error.addArgs(ExpressionDumper.dump(expression),
                Cardinality.getDescription(requiredCardinality),
                Integer.valueOf(seq.getItemCount()));
            throw new XPathException(this, error.toString());
        }
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", seq);}
        return seq;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        if(dumper.verbosity() > 1) {
            dumper.display("dynamic-cardinality-check"); 
            dumper.display("("); 
            dumper.display("\"" + Cardinality.getDescription(requiredCardinality) + "\"");
            dumper.display(", ");
        }
        expression.dump(dumper);
        if(dumper.verbosity() > 1)
            {dumper.display(")");}
    }

    public String toString() {
        return expression.toString();
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

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }

    public void accept(ExpressionVisitor visitor) {
        expression.accept(visitor);
    }

    public int getSubExpressionCount() {
        return 1;
    }
    
    public Expression getSubExpression(int index) {
        if (index == 0)
            {return expression;}
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: "+getSubExpressionCount());
    }
}
