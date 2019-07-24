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
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * @author wolf
 */
public class AtomicToString extends AbstractExpression {

    Expression expression;

    public AtomicToString(XQueryContext context, Expression expr) {
        super(context);
        this.expression = expr;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet,
     * org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final Sequence seq = expression.eval(contextSequence, contextItem);
        if (seq.isEmpty())
            {return Sequence.EMPTY_SEQUENCE;}
        Item next;
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator i = seq.iterate(); i.hasNext();) {
            next = i.nextItem();
            result.add(new StringValue(next.getStringValue()));
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("xs:string(");
        expression.dump(dumper);
        dumper.display(")");
    }

    public String toString() {
        return "xs:string(" + expression.toString() + ")";
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        return Type.STRING;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#preselect(org.exist.dom.persistent.DocumentSet)
     */
    public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
        return in_docs;
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

}
