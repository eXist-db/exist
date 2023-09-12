/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
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

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.List;

import static java.lang.System.arraycopy;

public class Optimize extends Pragma {
    public static final String OPTIMIZE_PRAGMA_LOCAL_NAME = "optimize";
    public static final QName OPTIMIZE_PRAGMA = new QName(OPTIMIZE_PRAGMA_LOCAL_NAME, Namespaces.EXIST_NS, "exist");

    private static final Logger LOG = LogManager.getLogger(Optimize.class);

    private boolean enabled = true;
    private final XQueryContext context;
    private Optimizable[] optimizables;
    private Expression innerExpr = null;
    private LocationStep contextStep = null;
    private VariableReference contextVar = null;
    private int contextId = Expression.NO_CONTEXT_ID;

    private NodeSet cachedContext = null;
    private int cachedTimestamp;
    private boolean cachedOptimize;

    public Optimize(final XQueryContext context, final QName pragmaName, @Nullable final String contents, final boolean explicit) throws XPathException {
        this(null, context, pragmaName, contents, explicit);
    }

    public Optimize(@Nullable final Expression expression, final XQueryContext context, final QName pragmaName, @Nullable final String contents, boolean explicit) throws XPathException {
        super(expression, pragmaName, contents);
        this.context = context;
        this.enabled = explicit || context.optimizationsEnabled();
        if (contents == null || contents.isEmpty()) {
            return;
        }
        final String[] param = Option.parseKeyValuePair(contents);
        if (param == null) {
            throw new XPathException((Expression) null,
                    "Invalid content found for pragma exist:optimize: " + contents);
        }
        if ("enable".equals(param[0])) {
            enabled = "yes".equals(param[1]);
        }
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        this.contextId = contextInfo.getContextId();
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        boolean useCached = false;
        boolean optimize = false;
        NodeSet originalContext = null;

        if (contextSequence == null || contextSequence.isPersistentSet()) {    // don't try to optimize in-memory node sets!
            // contextSequence will be overwritten
            originalContext = contextSequence == null ? null : contextSequence.toNodeSet();
            if (cachedContext != null && cachedContext == originalContext) {
                useCached = !originalContext.hasChanged(cachedTimestamp);
            }
            if (contextVar != null) {
                contextSequence = contextVar.eval(contextSequence);
            }
            // check if all Optimizable expressions signal that they can indeed optimize
            // in the current context
            if (useCached) {
                optimize = cachedOptimize;
            } else {
                if (optimizables != null) {
                    for (final Optimizable optimizable : optimizables) {
                        final Sequence canBeOptimized = optimizable.canOptimizeSequence(contextSequence);
                        if (canBeOptimized == null) {
                            optimize = false;
                            break;  // exit for-each loop
                        }
                        if (canBeOptimized.getItemCount() == contextSequence.getItemCount()) {
                            // everything in sequence can be optimized
                            optimize = true;  // so far so good, head to next for-loop of `optimizable`
                        } else {
                            // nothing or only some bits can be optimized
                            optimize = false;
                            break;  // exit for-each loop
                        }
                    }
                }
            }
        }
        if (optimize) {
            cachedContext = originalContext;
            cachedTimestamp = originalContext == null ? 0 : originalContext.getState();
            cachedOptimize = true;
            NodeSet result = null;
            for (int current = 0; current < optimizables.length; current++) {
                final NodeSet selection = optimizables[current].preSelect(contextSequence, current > 0);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("exist:optimize: pre-selection: {}", selection.getLength());
                }
                // determine the set of potential ancestors for which the predicate has to
                // be re-evaluated to filter out wrong matches
                final NodeSet ancestors;
                if (selection.isEmpty()) {
                    ancestors = selection;
                } else if (contextStep == null || current > 0) {
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
                        LOG.trace("Ancestor selection took {}", System.currentTimeMillis() - start);
                        LOG.trace("Found: {}", ancestors.getLength());
                    }
                }
                result = ancestors;
                contextSequence = result;
            }
            if (contextStep == null) {
                return innerExpr.eval(result);
            } else {
                contextStep.setPreloadedData(result.getDocumentSet(), result);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("exist:optimize: context after optimize: {}", result.getLength());
                }
                final long start = System.currentTimeMillis();
                if (originalContext != null) {
                    contextSequence = originalContext.filterDocuments(result);
                } else {
                    contextSequence = null;
                }
                final Sequence seq = innerExpr.eval(contextSequence);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("exist:optimize: inner expr took {}; found: {}", System.currentTimeMillis() - start, seq.getItemCount());
                }
                return seq;
            }
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("exist:optimize: Cannot optimize expression.");
            }
            if (originalContext != null) {
                contextSequence = originalContext;
            }
            return innerExpr.eval(contextSequence, contextItem);
        }
    }

    @Override
    public void before(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        before(context, null, contextSequence);
    }

    @Override
    public void before(final XQueryContext context, final Expression expression, final Sequence contextSequence) throws XPathException {
        if (innerExpr != null) {
            return;
        }
        innerExpr = expression;
        if (!enabled) {
            return;
        }
        innerExpr.accept(new BasicExpressionVisitor() {

            @Override
            public void visitPathExpr(final PathExpr expression) {
                for (int i = 0; i < expression.getSubExpressionCount(); i++) {
                    final Expression next = expression.getSubExpression(i);
                    next.accept(this);
                }
            }

            @Override
            public void visitLocationStep(final LocationStep locationStep) {
                @Nullable final Predicate[] predicates = locationStep.getPredicates();
                if (predicates != null) {
                    for (final Predicate pred : predicates) {
                        pred.accept(this);
                    }
                }
            }

            @Override
            public void visitFilteredExpr(final FilteredExpression filtered) {
                final Expression filteredExpr = filtered.getExpression();
                if (filteredExpr instanceof VariableReference) {
                    contextVar = (VariableReference) filteredExpr;
                }

                final List<Predicate> predicates = filtered.getPredicates();
                for (final Predicate pred : predicates) {
                    pred.accept(this);
                }
            }

            @Override
            public void visit(final Expression expression) {
                super.visit(expression);
            }

            @Override
            public void visitGeneralComparison(final GeneralComparison comparison) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("exist:optimize: found optimizable: {}", comparison.getClass().getName());
                }
                addOptimizable(comparison);
            }

            @Override
            public void visitPredicate(final Predicate predicate) {
                predicate.getExpression(0).accept(this);
            }

            @Override
            public void visitBuiltinFunction(final Function function) {
                if (function instanceof Optimizable) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("exist:optimize: found optimizable function: {}", function.getClass().getName());
                    }
                    addOptimizable((Optimizable) function);
                }
            }
        });

        contextStep = BasicExpressionVisitor.findFirstStep(innerExpr);
        if (contextStep != null && contextStep.getTest().isWildcardTest()) {
            contextStep = null;
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("exist:optimize: context step: {}", contextStep);
            LOG.trace("exist:optimize: context var: {}", contextVar);
        }
    }

    @Override
    public void after(final XQueryContext context) throws XPathException {
        after(context, null);
    }

    @Override
    public void after(final XQueryContext context, @Nullable final Expression expression) throws XPathException {
    }

    private void addOptimizable(final Optimizable optimizable) {
        final int axis = optimizable.getOptimizeAxis();

        if (!(axis == Constants.CHILD_AXIS || axis == Constants.SELF_AXIS || axis == Constants.DESCENDANT_AXIS ||
                axis == Constants.DESCENDANT_SELF_AXIS || axis == Constants.ATTRIBUTE_AXIS ||
                axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)) {
            // reverse axes cannot be optimized
            return;
        }

        if (optimizables == null) {
            optimizables = new Optimizable[1];
            optimizables[0] = optimizable;
            return;
        }

        Optimizable[] o = new Optimizable[optimizables.length + 1];
        arraycopy(optimizables, 0, o, 0, optimizables.length);
        o[optimizables.length] = optimizable;
        optimizables = o;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        cachedContext = null;
    }

    /**
     * Check every collection in the context sequence for an existing range index by QName.
     *
     * @param context         current context
     * @param contextSequence context sequence
     * @param qname           QName indicating the index to check
     * @return the type of a usable index or {@link org.exist.xquery.value.Type#ITEM} if there is no common
     * index.
     */
    public static int getQNameIndexType(final XQueryContext context, @Nullable final Sequence contextSequence, @Nullable final QName qname) {
        if (contextSequence == null || qname == null) {
            return Type.ITEM;
        }

        final String enforceIndexUseValue =
                (String) context.getBroker().getConfiguration().getProperty(XQueryContext.PROPERTY_ENFORCE_INDEX_USE);
        final boolean enforceIndexUse = enforceIndexUseValue != null;
        final boolean alwaysEnforceIndexUse = enforceIndexUse && "always".equals(enforceIndexUseValue);

        int indexType = Type.ITEM;

        for (final Iterator<Collection> i = contextSequence.getCollectionIterator(); i.hasNext(); ) {
            try (final Collection collection = i.next()) {
                // always skip system collection
                if (collection.getURI().startsWith(XmldbURI.SYSTEM_COLLECTION_URI)) {
                    continue;
                }
                // load index configuration for current collection
                final QNameRangeIndexSpec config = collection.getIndexByQNameConfiguration(context.getBroker(), qname);
                if (config == null) {
                    // no index found for this collection
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("Cannot optimize: collection {} does not define an index on {}",
                                collection.getURI(), qname);
                    }
                    // If enforceIndexUse is set to "always" we have to continue to check other collections
                    // for indexes. Otherwise, it is sufficient if one collection defines an index.
                    if (!enforceIndexUse || !alwaysEnforceIndexUse) {
                        return Type.ITEM;
                    }   // found a collection without index
                } else {
                    int type = config.getType();
                    if (indexType == Type.ITEM) {
                        indexType = type;
                        // If enforceIndexUse is set to "always", it is sufficient if only one collection
                        // defines an index. Just return it.
                        if (alwaysEnforceIndexUse) {
                            return indexType;
                        }
                    } else if (indexType != type) {
                        // Found an index with a bad type. cannot optimize.
                        // TODO: should this continue checking other collections?
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("Cannot optimize: collection {} does not define an index with the required type {} on {}",
                                    collection.getURI(), Type.getTypeName(type), qname);
                        }
                        return Type.ITEM;   // found a collection with a different type
                    }
                }
            }
        }
        return indexType;
    }
}