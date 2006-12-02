/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  \$Id\$
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.dom.NodeSet;
import org.exist.Namespaces;
import org.exist.storage.ElementIndex;
import org.exist.storage.ElementValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.functions.ExtFulltext;
import org.apache.log4j.Logger;

public class Optimize extends Pragma {

    public  final static QName OPTIMIZE_PRAGMA = new QName("optimize", Namespaces.EXIST_NS, "exist");

    private final static Logger LOG = Logger.getLogger(TimerPragma.class);

    private XQueryContext context;
    private Optimizable optimizable;
    private Expression innerExpr;
    private LocationStep contextStep = null;

    public Optimize(XQueryContext context, QName pragmaName, String contents) throws XPathException {
        super(pragmaName, contents);
        this.context = context;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        long start = System.currentTimeMillis();
        NodeSet contextSet = contextSequence.toNodeSet();
        NodeSet selection = optimizable.preSelect(contextSequence, contextItem);
        LOG.trace("pre-selection: " + selection.getLength());
        NodeSet ancestors;
        if (contextStep == null) {
            ancestors = selection.selectAncestorDescendant(contextSet, NodeSet.ANCESTOR, true, -1);
            return innerExpr.eval(ancestors);
        } else {
            start = System.currentTimeMillis();
            NodeSelector selector;
            selector = new AncestorSelector(selection, -1, true);
            ElementIndex index = context.getBroker().getElementIndex();
            ancestors = index.findElementsByTagName(ElementValue.ELEMENT, selection.getDocumentSet(),
                    contextStep.getTest().getName(), selector);
            contextStep.setPreloadNodeSets(true);
            contextStep.setPreloadedData(ancestors.getDocumentSet(), ancestors);
            if (LOG.isTraceEnabled())
                LOG.trace("context after optimize: " + ancestors.getLength());
            LOG.trace("optimize took " + (System.currentTimeMillis() - start));
            start = System.currentTimeMillis();
            Sequence result = innerExpr.eval(contextSequence);
            LOG.trace("inner expr took " + (System.currentTimeMillis() - start));
            return result;
        }
    }

    public void before(XQueryContext context, Expression expression) throws XPathException {
        innerExpr = expression;
        innerExpr.accept(new BasicExpressionVisitor() {

            public void visitPathExpr(PathExpr expression) {
                for (int i = 0; i < expression.getLength(); i++) {
                    Expression next = expression.getExpression(i);
			        next.accept(this);
                }
            }

            public void visit(Expression expression) {
                super.visit(expression);
            }

            public void visitFtExpression(ExtFulltext fulltext) {
                LOG.trace("Found optimizable: " + fulltext.getClass().getName());
//                optimizable = fulltext;
            }

            public void visitPredicate(Predicate predicate) {
                predicate.accept(this);
            }
        });
        this.contextStep = BasicExpressionVisitor.findFirstStep(innerExpr);
        LOG.trace("context step: " + this.contextStep);
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {

    }
}