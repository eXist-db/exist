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

import org.apache.log4j.Logger;
import org.exist.Namespaces;
import org.exist.xmldb.XmldbURI;
import org.exist.collections.Collection;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.ElementIndex;
import org.exist.storage.QNameRangeIndexSpec;
import org.exist.xquery.functions.ExtFulltext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.Iterator;

public class Optimize extends Pragma {

    public  final static QName OPTIMIZE_PRAGMA = new QName("optimize", Namespaces.EXIST_NS, "exist");

    private final static Logger LOG = Logger.getLogger(TimerPragma.class);

    private boolean enabled = true;
    private XQueryContext context;
    private Optimizable optimizables[];
    private Expression innerExpr;
    private LocationStep contextStep = null;

    public Optimize(XQueryContext context, QName pragmaName, String contents) throws XPathException {
        super(pragmaName, contents);
        this.context = context;
        this.enabled = context.optimizationsEnabled();
        if (contents != null && contents.length() > 0) {
            String param[] = Option.parseKeyValuePair(contents);
            if (param == null)
                throw new XPathException("Invalid content found for pragma exist:optimize: " + contents);
            if ("enable".equals(param[0])) {
                enabled = "yes".equals(param[1]);
            }
        }
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
                contextSequence = contextItem.toSequence();
        // check if all Optimizable expressions signal that they can indeed optimize
        // in the current context
        boolean optimize = false;
        if (optimizables != null && optimizables.length > 0) {
            for (int i = 0; i < optimizables.length; i++) {
                if (optimizables[i].canOptimize(contextSequence))
                    optimize = true;
                else {
                    optimize = false;
                    break;
                }
            }
        }
        if (optimize) {
            NodeSet originalContext = contextSequence.toNodeSet(); // contextSequence will be overwritten
            NodeSet ancestors = null;
            NodeSet result = null;
            for (int current = 0; current < optimizables.length; current++) {
                NodeSet selection = optimizables[current].preSelect(contextSequence, current > 0);
                if (LOG.isTraceEnabled())
                    LOG.trace("exist:optimize: pre-selection: " + selection.getLength());
                if (contextStep == null || current > 0) {
                    ancestors = selection.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.ANCESTOR, true, -1);
                } else {
                    NodeSelector selector;
                    long start = System.currentTimeMillis();
                    selector = new AncestorSelector(selection, -1, true);
                    ElementIndex index = context.getBroker().getElementIndex();
                    QName ancestorQN = contextStep.getTest().getName();
                    if (optimizables[current].optimizeOnSelf()) {
                        ancestors = selection;
                    } else
                        ancestors = index.findElementsByTagName(ancestorQN.getNameType(), selection.getDocumentSet(),
                                ancestorQN, selector);
                    LOG.trace("Ancestor selection took " + (System.currentTimeMillis() - start));
                    LOG.trace("Found: " + ancestors.getLength());
                }
//                if (result == null)
                    result = ancestors;
//                else
//                    result = result.intersection(ancestors);
                contextSequence = result;
            }
            if (contextStep == null) {
                return innerExpr.eval(result);
            } else {
                contextStep.setPreloadNodeSets(true);
                contextStep.setPreloadedData(result.getDocumentSet(), result);
                if (LOG.isTraceEnabled())
                    LOG.trace("exist:optimize: context after optimize: " + result.getLength());
                long start = System.currentTimeMillis();
                contextSequence = filterDocuments(originalContext, result);
                Sequence seq = innerExpr.eval(contextSequence);
                if (LOG.isTraceEnabled())
                    LOG.trace("exist:optimize: inner expr took " + (System.currentTimeMillis() - start));
                return seq;
            }
        } else {
            if (LOG.isTraceEnabled())
                LOG.trace("exist:optimize: Cannot optimize expression.");
            return innerExpr.eval(contextSequence, contextItem);
        }
    }

    private Sequence filterDocuments(NodeSet contextSet, NodeSet ancestors) {
        if (contextSet instanceof VirtualNodeSet)
            return contextSet;
        return contextSet.filterDocuments(ancestors);
    }

    public void before(XQueryContext context, Expression expression) throws XPathException {
        innerExpr = expression;
        if (!enabled)
            return;
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
                if (LOG.isTraceEnabled())
                    LOG.trace("exist:optimize: found optimizable: " + fulltext.getClass().getName());
                addOptimizable(fulltext);
            }

            public void visitGeneralComparison(GeneralComparison comparison) {
                if (LOG.isTraceEnabled())
                    LOG.trace("exist:optimize: found optimizable: " + comparison.getClass().getName());
                addOptimizable(comparison);
            }

            public void visitPredicate(Predicate predicate) {
                predicate.accept(this);
            }

            public void visitBuiltinFunction(Function function) {
                if (function instanceof Optimizable) {
                    if (LOG.isTraceEnabled())
                        LOG.trace("exist:optimize: found optimizable function: " + function.getClass().getName());
                    addOptimizable((Optimizable) function);
                }
            }
        });

        contextStep = BasicExpressionVisitor.findFirstStep(innerExpr);
        if (contextStep != null && contextStep.getTest().isWildcardTest())
            contextStep = null;
        if (LOG.isTraceEnabled())
            LOG.trace("exist:optimize: context step: " + contextStep);
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {
    }

    private void addOptimizable(Optimizable optimizable) {
        if (optimizables == null) {
            optimizables = new Optimizable[1];
            optimizables[0] = optimizable;
        } else {
            Optimizable o[] = new Optimizable[optimizables.length + 1];
            System.arraycopy(optimizables, 0, o, 0, optimizables.length);
            o[optimizables.length] = optimizable;
            optimizables = o;
        }
    }

    /**
     * Check every collection in the context sequence for an existing range index by QName.
     *
     * @param contextSequence
     * @return the type of a usable index or {@link org.exist.xquery.value.Type#ITEM} if there is no common
     *  index.
     */
    public static int getQNameIndexType(XQueryContext context, Sequence contextSequence, QName qname) {
        if (contextSequence == null || qname == null)
            return Type.ITEM;
        int indexType = Type.ITEM;
        for (Iterator i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            Collection collection = (Collection) i.next();
            if (collection.getURI().equals(XmldbURI.SYSTEM_COLLECTION_URI))
                continue;
            QNameRangeIndexSpec config = collection.getIndexByQNameConfiguration(context.getBroker(), qname);
            if (config == null)
                return Type.ITEM;   // found a collection without index
            int type = config.getType();
            if (indexType == Type.ITEM)
                indexType = type;
            else if (indexType != type)
                return Type.ITEM;   // found a collection with a different type
        }
        return indexType;
    }
}