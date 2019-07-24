/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.pragmas;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.QName;
import org.exist.indexing.StructuralIndex;
import org.exist.storage.QNameRangeIndexSpec;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.Iterator;
import java.util.List;

public class Optimize extends Pragma {

    public  final static QName OPTIMIZE_PRAGMA = new QName("optimize", Namespaces.EXIST_NS, "exist");

    private final static Logger LOG = LogManager.getLogger(Optimize.class);

    private boolean enabled = true;
    private XQueryContext context;
    private Optimizable optimizables[];
    private Expression innerExpr = null;
    private LocationStep contextStep = null;
    private VariableReference contextVar = null;
    private int contextId = Expression.NO_CONTEXT_ID;

    private NodeSet cachedContext = null;
    private int cachedTimestamp;
    private boolean cachedOptimize;
    
    public Optimize(XQueryContext context, QName pragmaName, String contents, boolean explicit) throws XPathException {
        super(pragmaName, contents);
        this.context = context;
        this.enabled = explicit || context.optimizationsEnabled();
        if (contents != null && contents.length() > 0) {
            final String param[] = Option.parseKeyValuePair(contents);
            if (param == null)
                {throw new XPathException("Invalid content found for pragma exist:optimize: " + contents);}
            if ("enable".equals(param[0])) {
                enabled = "yes".equals(param[1]);
            }
        }
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.contextId = contextInfo.getContextId();
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (contextItem != null)
                {contextSequence = contextItem.toSequence();}

        boolean useCached = false;
        boolean optimize = false;
        NodeSet originalContext = null;

        if (contextSequence == null || contextSequence.isPersistentSet()) {    // don't try to optimize in-memory node sets!
            // contextSequence will be overwritten
            originalContext = contextSequence == null ? null : contextSequence.toNodeSet();
            if (cachedContext != null && cachedContext == originalContext)
                {useCached = !originalContext.hasChanged(cachedTimestamp);}
            if (contextVar != null) {
                contextSequence = contextVar.eval(contextSequence);
            }
            // check if all Optimizable expressions signal that they can indeed optimize
            // in the current context
            if (useCached)
                {optimize = cachedOptimize;}
            else {
                if (optimizables != null && optimizables.length > 0) {
                    for (int i = 0; i < optimizables.length; i++) {
                        if (optimizables[i].canOptimize(contextSequence))
                            {optimize = true;}
                        else {
                            optimize = false;
                            break;
                        }
                    }
                }
            }
        }
        if (optimize) {
            cachedContext = originalContext;
            cachedTimestamp = originalContext == null ? 0 : originalContext.getState();
            cachedOptimize = true;
            NodeSet ancestors;
            NodeSet result = null;
            for (int current = 0; current < optimizables.length; current++) {
                NodeSet selection = optimizables[current].preSelect(contextSequence, current > 0);
                if (LOG.isTraceEnabled())
                    {LOG.trace("exist:optimize: pre-selection: " + selection.getLength());}
                // determine the set of potential ancestors for which the predicate has to
                // be re-evaluated to filter out wrong matches
                if (selection.isEmpty())
                	{ancestors = selection;}
                else if (contextStep == null || current > 0) {
                		ancestors = selection.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.ANCESTOR,
                				true, contextId, true);
                } else {
//                    NodeSelector selector;
                    final long start = System.currentTimeMillis();
//                    selector = new AncestorSelector(selection, contextId, true, false);
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    final QName ancestorQN = contextStep.getTest().getName();
                    if (optimizables[current].optimizeOnSelf()) {
                        ancestors = index.findAncestorsByTagName(ancestorQN.getNameType(), ancestorQN, Constants.SELF_AXIS,
                            selection.getDocumentSet(), selection, contextId);
                    } else {
                        ancestors = index.findAncestorsByTagName(ancestorQN.getNameType(), ancestorQN,
                            optimizables[current].optimizeOnChild() ? Constants.PARENT_AXIS : Constants.ANCESTOR_SELF_AXIS,
                            selection.getDocumentSet(), selection, contextId);
                    }
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Ancestor selection took " + (System.currentTimeMillis() - start));
                        LOG.trace("Found: " + ancestors.getLength());
                    }
                }
                result = ancestors;
                contextSequence = result;
            }
            if (contextStep == null) {
                return innerExpr.eval(result);
            } else {
                contextStep.setPreloadedData(result.getDocumentSet(), result);
                if (LOG.isTraceEnabled())
                    {LOG.trace("exist:optimize: context after optimize: " + result.getLength());}
                final long start = System.currentTimeMillis();
                if (originalContext != null)
                    {contextSequence = originalContext.filterDocuments(result);}
                else
                    {contextSequence = null;}
                final Sequence seq = innerExpr.eval(contextSequence);
                if (LOG.isTraceEnabled())
                    {LOG.trace("exist:optimize: inner expr took " + (System.currentTimeMillis() - start) +
                        "; found: "+ seq.getItemCount());}
                return seq;
            }
        } else {
            if (LOG.isTraceEnabled())
                {LOG.trace("exist:optimize: Cannot optimize expression.");}
            if (originalContext != null)
                {contextSequence = originalContext;}
            return innerExpr.eval(contextSequence, contextItem);
        }
    }

    public void before(XQueryContext context, Expression expression, Sequence contextSequence) throws XPathException {
        if (innerExpr != null)
            {return;}
        innerExpr = expression;
        if (!enabled)
            {return;}
        innerExpr.accept(new BasicExpressionVisitor() {

            public void visitPathExpr(PathExpr expression) {
                for (int i = 0; i < expression.getLength(); i++) {
                    final Expression next = expression.getExpression(i);
			        next.accept(this);
                }
            }

            public void visitLocationStep(LocationStep locationStep) {
                final List<Predicate> predicates = locationStep.getPredicates();
                for (final Predicate pred : predicates) {
                    pred.accept(this);
                }
            }

            public void visitFilteredExpr(FilteredExpression filtered) {
                final Expression filteredExpr = filtered.getExpression();
                if (filteredExpr instanceof VariableReference)
                    {contextVar = (VariableReference) filteredExpr;}

                final List<Predicate> predicates = filtered.getPredicates();
                for (final Predicate pred : predicates) {
                    pred.accept(this);
                }
            }

            public void visit(Expression expression) {
                super.visit(expression);
            }

            public void visitGeneralComparison(GeneralComparison comparison) {
                if (LOG.isTraceEnabled())
                    {LOG.trace("exist:optimize: found optimizable: " + comparison.getClass().getName());}
                addOptimizable(comparison);
            }

            public void visitPredicate(Predicate predicate) {
                predicate.getExpression(0).accept(this);
            }

            public void visitBuiltinFunction(Function function) {
                if (function instanceof Optimizable) {
                    if (LOG.isTraceEnabled())
                        {LOG.trace("exist:optimize: found optimizable function: " + function.getClass().getName());}
                    addOptimizable((Optimizable) function);
                }
            }
        });

        contextStep = BasicExpressionVisitor.findFirstStep(innerExpr);
        if (contextStep != null && contextStep.getTest().isWildcardTest())
            {contextStep = null;}
        if (LOG.isTraceEnabled()) {
            LOG.trace("exist:optimize: context step: " + contextStep);
            LOG.trace("exist:optimize: context var: " + contextVar);
        }
    }

    public void after(XQueryContext context, Expression expression) throws XPathException {
    }

    private void addOptimizable(Optimizable optimizable) {final int axis = optimizable.getOptimizeAxis();
        if (!(axis == Constants.CHILD_AXIS || axis == Constants.SELF_AXIS || axis == Constants.DESCENDANT_AXIS ||
                axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
            // reverse axes cannot be optimized
            return;
        }
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

    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        cachedContext = null;
    }

    /**
     * Check every collection in the context sequence for an existing range index by QName.
     *
     * @param context current context
     * @param contextSequence context sequence
     * @param qname QName indicating the index to check
     * @return the type of a usable index or {@link org.exist.xquery.value.Type#ITEM} if there is no common
     *  index.
     */
    public static int getQNameIndexType(XQueryContext context, Sequence contextSequence, QName qname) {
        if (contextSequence == null || qname == null)
            {return Type.ITEM;}
        
        final String enforceIndexUse = 
        		(String) context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_ENFORCE_INDEX_USE);
        int indexType = Type.ITEM;
        for (final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            final Collection collection = i.next();
            if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI))
                {continue;}
            final QNameRangeIndexSpec config = collection.getIndexByQNameConfiguration(context.getBroker(), qname);
            if (config == null) {
                // no index found for this collection
                if (LOG.isTraceEnabled())
                    {LOG.trace("Cannot optimize: collection " + collection.getURI() + " does not define an index " +
                        "on " + qname);}
                // if enfoceIndexUse == "always", continue to check other collections
                // for indexes. It is sufficient if one collection defines an index
                if (enforceIndexUse == null || !"always".equals(enforceIndexUse))
                    {return Type.ITEM;}   // found a collection without index
            } else {
            int type = config.getType();
                if (indexType == Type.ITEM) {
                    indexType = type;
                    // if enforceIndexUse == "always", it is sufficient if one collection
                    // defines an index. Just return it.
                    if (enforceIndexUse != null && "always".equals(enforceIndexUse))
                        {return indexType;}
                } else if (indexType != type) {
                    // found an index with a bad type. cannot optimize.
                    // TODO: should this continue checking other collections?
                    if (LOG.isTraceEnabled())
                        {LOG.trace("Cannot optimize: collection " + collection.getURI() + " does not define an index " +
                            "with the required type " + Type.getTypeName(type) + " on " + qname);}
                    return Type.ITEM;   // found a collection with a different type
                }
            }
        }
        return indexType;
    }
}