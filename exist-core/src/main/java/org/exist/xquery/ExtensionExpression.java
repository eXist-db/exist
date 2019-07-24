/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements an XQuery extension expression. An extension expression starts with
 * a list of pragmas, followed by an expression enclosed in curly braces. For evaluation
 * details check {{@link #eval(Sequence, Item)}.
 * 
 * @author wolf
 *
 */
public class ExtensionExpression extends AbstractExpression {

    private Expression innerExpression;
    private List<Pragma> pragmas = new ArrayList<Pragma>(3);

    public ExtensionExpression(XQueryContext context) {
        super(context);
    }

    public void setExpression(Expression inner) {
        this.innerExpression = inner;
    }

    public void addPragma(Pragma pragma) {
        pragmas.add(pragma);
    }

    /**
     * For every pragma in the list, calls {@link Pragma#before(XQueryContext, Expression, Sequence)} before evaluation.
     * The method then tries to call {@link Pragma#eval(Sequence, Item)} on every pragma.
     * If a pragma does not return null for this call, the returned Sequence will become the result
     * of the extension expression. If more than one pragma returns something for eval, an exception
     * will be thrown. If all pragmas return null, we call eval on the original expression and return
     * that.
     */
    public Sequence eval(Sequence contextSequence, Item contextItem)
            throws XPathException {
        callBefore(contextSequence);
        Sequence result = null;
        for (final Pragma pragma : pragmas) {
            Sequence temp = pragma.eval(contextSequence, contextItem);
            if (temp != null) {
                result = temp;
                break;
            }
        }
        if (result == null)
            {result = innerExpression.eval(contextSequence, contextItem);}
        callAfter();
        return result;
    }

    private void callAfter() throws XPathException {
        for (final Pragma pragma : pragmas) {
            pragma.after(context, innerExpression);
        }
    }

    private void callBefore(Sequence contextSequence) throws XPathException {
        for (final Pragma pragma : pragmas) {
            pragma.before(context, innerExpression, contextSequence);
        }
    }

    public int returnsType() {
        return innerExpression.returnsType();
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        final AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
        for (final Pragma pragma : pragmas) {
            pragma.analyze(newContext);
        }
        innerExpression.analyze(newContext);
    }

    public void dump(ExpressionDumper dumper) {
        for (final Pragma pragma : pragmas) {
            dumper.display("(# " + pragma.getQName().getStringValue(), line);
            if (pragma.getContents() != null)
                {dumper.display(' ').display(pragma.getContents());}
            dumper.display("#)").nl();
        }
        dumper.display('{');
        dumper.startIndent();
        innerExpression.dump(dumper);
        dumper.endIndent();
        dumper.nl().display('}').nl();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        return innerExpression.getDependencies();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getCardinality()
     */
    public int getCardinality() {
        return innerExpression.getCardinality();
    }

    public void setContextDocSet(DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        innerExpression.setContextDocSet(contextSet);
    }

    public void setPrimaryAxis(int axis) {
        innerExpression.setPrimaryAxis(axis);
    }

    public int getPrimaryAxis() {
        return innerExpression.getPrimaryAxis();
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.AbstractExpression#resetState()
    */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        innerExpression.resetState(postOptimization);
        for (final Pragma pragma : pragmas) {
            pragma.resetState(postOptimization);
        }
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visit(innerExpression);
    }
}
