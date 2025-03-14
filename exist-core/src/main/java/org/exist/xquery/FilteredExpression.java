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
package org.exist.xquery;

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.persistent.NodeSet;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

import javax.annotation.Nullable;

/**
 * FilteredExpression represents a primary expression with a predicate. Examples:
 * for $i in (1 to 10)[$i mod 2 = 0], $a[1], (doc("test.xml")//section)[2]. Other predicate
 * expressions are handled by class {@link org.exist.xquery.LocationStep}.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FilteredExpression extends AbstractExpression {

    private final Expression expression;
    private boolean abbreviated = false;
    private final List<Predicate> predicates = new ArrayList<>(2);
    private Expression parent;

    public FilteredExpression(final XQueryContext context, final Expression expr) {
        super(context);
        this.expression = expr.simplify();
    }

    public void addPredicate(final Predicate pred) {
        predicates.add(pred);
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public Expression getExpression() {
        if (expression instanceof final PathExpr pathExpr) {
            return pathExpr.getExpression(0);
        }
        return expression;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        parent = contextInfo.getParent();
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
        if (!predicates.isEmpty()) {
            final AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
            newContext.setParent(this);
            newContext.setContextStep(this);
            newContext.setStaticType(expression.returnsType());
            for (final Predicate pred : predicates) {
                pred.analyze(newContext);
            }
        }
    }

    @Nullable
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        final Sequence result;
        final Sequence seq = expression.eval(contextSequence, contextItem);
        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            final Predicate pred = predicates.getFirst();
            context.setContextSequencePosition(0, seq);
            // If the current step is an // abbreviated step, we have to treat the predicate
            // specially to get the context position right. //a[1] translates 
            //to /descendant-or-self::node()/a[1], so we need to return the
            //1st a from any parent of a.
            // If the predicate is known to return a node set, no special treatment is required.
            if (abbreviated && (pred.getExecutionMode() != Predicate.ExecutionMode.NODE ||
                    !seq.isPersistentSet())) {
                result = new ValueSequence();
                if (seq.isPersistentSet()) {
                    final NodeSet contextSet = seq.toNodeSet();
                    final Sequence outerSequence = contextSet.getParents(getExpressionId());
                    for (final SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
                        final NodeValue node = (NodeValue) i.nextItem();
                        final Sequence newContextSeq =
                            contextSet.selectParentChild((NodeSet) node, NodeSet.DESCENDANT,
                            getExpressionId());
                        final Sequence temp = processPredicate(outerSequence, newContextSeq);
                        result.addAll(temp);
                    }
                } else {
                    final MemoryNodeSet nodes = seq.toMemNodeSet();
                    final Sequence outerSequence = nodes.getParents(new AnyNodeTest());
                    for (final SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
                        final NodeValue node = (NodeValue) i.nextItem();
                        final Sequence newSet = nodes.getChildrenForParent((NodeImpl) node);
                        final Sequence temp = processPredicate(outerSequence, newSet);
                        result.addAll(temp);
                    }
                }
            } else {
                result = processPredicate(contextSequence, seq);
            }
        }
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    private Sequence processPredicate(@Nullable Sequence contextSequence, Sequence seq) throws XPathException {
        int line = -1;
        int column = -1;
        try {  // Keep try-catch out of loop
            for (final Predicate pred : predicates) {
                line = pred.getLine();
                column = pred.getColumn();
                seq = pred.evalPredicate(contextSequence, seq, Constants.DESCENDANT_SELF_AXIS);
                //subsequent predicates operate on the result of the previous one
                contextSequence = null;
            }
        } catch (final XPathException ex) {
            // Add location to exception
            ex.setLocation(line,column);
            throw ex;
        }
        return seq;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        expression.dump(dumper);
        for (final Predicate pred : predicates) {
            pred.dump(dumper);
        }
    }

    @Override
    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(expression.toString());
        for (final Predicate pred : predicates) {
            result.append(pred.toString());
        }
        return result.toString();
    }

    @Override
    public int returnsType() {
        return expression.returnsType();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
        for (final Predicate pred : predicates) {
            pred.resetState(postOptimization);
        }
    }

    @Override
    public void setPrimaryAxis(final int axis) {
        expression.setPrimaryAxis(axis);
    }

    @Override
    public int getPrimaryAxis() {
        return expression.getPrimaryAxis();
    }

    public void setAbbreviated(final boolean abbreviated) {
        this.abbreviated = abbreviated;
    }

    @Override
    public int getDependencies() {
        int deps = Dependency.CONTEXT_SET;
        deps |= expression.getDependencies();
        for (final Predicate pred : predicates) {
            deps |= pred.getDependencies();
        }
        return deps;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitFilteredExpr(this);
    }

    @Override
    public Expression getParent() {
        return parent;
    }
}
