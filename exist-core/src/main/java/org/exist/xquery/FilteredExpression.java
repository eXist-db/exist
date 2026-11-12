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

        // A filter expression's predicate applies to the sequence that the expression returns, so its positions
        // don't depend on the context nodes. If the expression is evaluated for many context nodes at once, its
        // positions must count within each context node's sequence: when its results are all descendants-or-self of
        // the context nodes, e.g. $nodes/(child)[1], the predicate does that for all of them at once; otherwise
        // PathExpr evaluates it for each context item, see evalForEachContextItem()
        final Sequence outerSequence = contextItem == null && contextSequence != null && contextSequence.hasMany()
                && isDownwardSteps(expression) ? contextSequence : null;
        final Sequence result = filter(outerSequence, expression.eval(contextSequence, contextItem));
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    /**
     * Whether, as E2 of a path E1/E2, this expression must be evaluated for each item of E1, so that the positions
     * of its predicate count within each item's sequence: the predicate may be positional (that is final only at
     * evaluation time, e.g. for [$n]), and the expression's results are not all descendants-or-self of the context
     * node, e.g. (ancestor::entry)[1] or (def, def)[2].
     *
     * @return true if the expression must be evaluated for each context item
     */
    boolean evalForEachContextItem() {
        return !abbreviated
                && predicates.getFirst().getExecutionMode() != Predicate.ExecutionMode.NODE
                && !isDownwardSteps(expression);
    }

    /**
     * Whether the expression is a location step or path of location steps whose results are descendants-or-self of
     * the context node.
     */
    private static boolean isDownwardSteps(final Expression expr) {
        if (expr instanceof LocationStep step) {
            return switch (step.getAxis()) {
                case Constants.CHILD_AXIS, Constants.DESCENDANT_AXIS, Constants.DESCENDANT_SELF_AXIS,
                     Constants.ATTRIBUTE_AXIS, Constants.DESCENDANT_ATTRIBUTE_AXIS, Constants.SELF_AXIS -> true;
                default -> false;
            };
        }
        if (expr instanceof PathExpr path && !(expr instanceof Predicate) && path.getSubExpressionCount() > 0) {
            for (int i = 0; i < path.getSubExpressionCount(); i++) {
                if (!isDownwardSteps(path.getSubExpression(i))) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    /**
     * Evaluate this filtered expression's predicates directly against an
     * already-computed candidate sequence, without re-evaluating
     * {@link #expression}.
     *
     * <p>Used by {@link org.exist.xquery.pragmas.Optimize#eval} when
     * {@link #expression} is a plain {@link VariableReference}: unlike a
     * {@link LocationStep}, {@link VariableReference#eval} ignores whatever
     * contextSequence it is called with and always returns the variable's
     * full bound value. Calling the normal {@link #eval} in that situation
     * would silently discard an index pre-selected {@code NodeSet} and force
     * the predicate to be re-evaluated across the full, unfiltered variable
     * value (GH-873).
     *
     * @param outerContextSequence the outer dynamic context to evaluate the
     *                              predicates against, e.g. for {@code position()}/{@code last()}
     * @param preselected           the pre-selected candidate sequence to filter, in
     *                              place of {@code expression}'s own value
     */
    public Sequence evalOnPreselected(final Sequence outerContextSequence, final Sequence preselected) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", preselected);
        }
        final Sequence result = filter(outerContextSequence, preselected);
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    /**
     * Apply this filtered expression's predicates to {@code seq}, handling the
     * {@code abbreviated}-step grouping-by-parent case the same way for both
     * {@link #eval} and {@link #evalOnPreselected}.
     */
    private Sequence filter(@Nullable final Sequence contextSequence, final Sequence seq) throws XPathException {
        if (seq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final Predicate pred = predicates.getFirst();
        context.setContextSequencePosition(0, seq);
        // If the current step is an // abbreviated step, we have to treat the predicate
        // specially to get the context position right. //a[1] translates
        //to /descendant-or-self::node()/a[1], so we need to return the
        //1st a from any parent of a.
        // If the predicate is known to return a node set, no special treatment is required.
        if (abbreviated && (pred.getExecutionMode() != Predicate.ExecutionMode.NODE ||
                !seq.isPersistentSet())) {
            final Sequence result = new ValueSequence();
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
            return result;
        } else {
            return processPredicate(contextSequence, seq);
        }
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
