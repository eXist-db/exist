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

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.persistent.NodeSet;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * FilteredExpression represents a primary expression with a predicate. Examples:
 * for $i in (1 to 10)[$i mod 2 = 0], $a[1], (doc("test.xml")//section)[2]. Other predicate
 * expressions are handled by class {@link org.exist.xquery.LocationStep}.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FilteredExpression extends AbstractExpression {

    final protected Expression expression;
    protected boolean abbreviated = false;
    final protected List<Predicate> predicates = new ArrayList<Predicate>(2);
    private Expression parent;

    public FilteredExpression(XQueryContext context, Expression expr) {
        super(context);
        this.expression = expr.simplify();
    }

    public void addPredicate(Predicate pred) {
        predicates.add(pred);
    }

    public List<Predicate> getPredicates() {
        return predicates;
    }

    public Expression getExpression() {
        if (expression instanceof PathExpr)
            {return ((PathExpr)expression).getExpression(0);}
        return expression;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.AnalyzeContextInfo)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        parent = contextInfo.getParent();
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
        if (predicates.size() > 0) {
            final AnalyzeContextInfo newContext = new AnalyzeContextInfo(contextInfo);
            newContext.setParent(this);
            newContext.setContextStep(this);
            newContext.setStaticType(expression.returnsType());
            for (final Predicate pred : predicates) {
                pred.analyze(newContext);
            }
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        if (contextItem != null)
            {contextSequence = contextItem.toSequence();}
        Sequence result;
        final Sequence seq = expression.eval(contextSequence, contextItem);
        if (seq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
            final Predicate pred = predicates.get(0);
            context.setContextSequencePosition(0, seq);
            // If the current step is an // abbreviated step, we have to treat the predicate
            // specially to get the context position right. //a[1] translates 
            //to /descendant-or-self::node()/a[1], so we need to return the
            //1st a from any parent of a.
            // If the predicate is known to return a node set, no special treatment is required.
            if (abbreviated && (pred.getExecutionMode() != Predicate.NODE ||
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
        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);} 
        return result;
    }

    private Sequence processPredicate(Sequence contextSequence, Sequence seq) throws XPathException {
        for (final Predicate pred : predicates) {
            seq = pred.evalPredicate(contextSequence, seq, Constants.DESCENDANT_SELF_AXIS);
            //subsequent predicates operate on the result of the previous one
            contextSequence = null;
        }
        return seq;
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
    */
    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
        for (final Predicate pred : predicates) {
            pred.dump(dumper);
        }
    }

    public String toString() {
        final StringBuilder result = new StringBuilder();
        result.append(expression.toString());
        for (final Predicate pred : predicates) {
            result.append(pred.toString());
        }
        return result.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#returnsType()
     */
    public int returnsType() {
        return expression.returnsType();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#resetState()
     */
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
        for (final Predicate pred : predicates) {
            pred.resetState(postOptimization);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
     */
    public void setPrimaryAxis(int axis) {
        expression.setPrimaryAxis(axis);
    }

    public int getPrimaryAxis() {
        return expression.getPrimaryAxis();
    }

    public void setAbbreviated(boolean abbrev) {
        abbreviated = abbrev;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.AbstractExpression#getDependencies()
     */
    public int getDependencies() {
        int deps = Dependency.CONTEXT_SET;
        deps |= expression.getDependencies();
        for (final Predicate pred : predicates) {
            deps |= pred.getDependencies();
        }
        return deps;
    }

    public void accept(ExpressionVisitor visitor) {
        visitor.visitFilteredExpr(this);
    }

    public Expression getParent() {
        return parent;
    }
}
