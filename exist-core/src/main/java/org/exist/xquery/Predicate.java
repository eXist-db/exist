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

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NewArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.TreeSet;

import static org.exist.xquery.Predicate.ExecutionMode.*;
import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * Handles predicate expressions.
 *
 * @author Wolfgang Meier
 */
public class Predicate extends PathExpr {

    public enum ExecutionMode {
        UNKNOWN,
        NODE,
        BOOLEAN,
        POSITIONAL
    }

    private CachedResult cached = null;

    private ExecutionMode executionMode = UNKNOWN;

    private int outerContextId;

    private Expression parent;

    public Predicate(final XQueryContext context) {
        super(context);
    }

    @Override
    public void addPath(final PathExpr path) {
        if (path.getSubExpressionCount() == 1) {
            add(path.getSubExpression(0));
        } else {
            super.addPath(path);
        }
    }

    @Override
    public int getDependencies() {
        final int deps;
        if (getSubExpressionCount() == 1) {
            deps = getSubExpression(0).getDependencies();
        } else {
            deps = super.getDependencies();
        }
        return deps;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        parent = contextInfo.getParent();
        AnalyzeContextInfo newContextInfo = createContext(contextInfo);
        super.analyze(newContextInfo);
        final Expression inner = getSubExpression(0);
        final int staticReturnType = newContextInfo.getStaticReturnType();
        final int innerType = staticReturnType != Type.ITEM ?
                staticReturnType : inner.returnsType();
        // Case 1: predicate expression returns a node set.
        // Check the returned node set against the context set
        // and return all nodes from the context, for which the
        // predicate expression returns a non-empty sequence.
        if (Type.subTypeOf(innerType, Type.NODE)
                && !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM)) {
            executionMode = NODE;
            // Case 2: predicate expression returns a unique number and has no
            // dependency with the context item.
        } else if (Type.subTypeOfUnion(innerType, Type.NUMERIC) &&
                !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM) &&
                    inner.getCardinality().isSuperCardinalityOrEqualOf(Cardinality.EXACTLY_ONE)) {
            executionMode = POSITIONAL;
        }
        // Case 3: all other cases, boolean evaluation (that can be "promoted" later)
        else {
            executionMode = BOOLEAN;
        }
        if (executionMode == BOOLEAN) {
            // need to re-analyze:
            newContextInfo = createContext(contextInfo);
            newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
            super.analyze(newContextInfo);
        }
    }

    private AnalyzeContextInfo createContext(final AnalyzeContextInfo contextInfo) {
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        // set flag to signal subexpression that we are in a predicate
        newContextInfo.addFlag(IN_PREDICATE);
        newContextInfo.removeFlag(IN_WHERE_CLAUSE); // remove where clause flag
        newContextInfo.removeFlag(DOT_TEST);
        outerContextId = newContextInfo.getContextId();
        newContextInfo.setContextId(getExpressionId());
        newContextInfo.setStaticType(contextInfo.getStaticType());
        newContextInfo.setParent(this);
        return newContextInfo;
    }

    public Sequence preprocess() throws XPathException {
        final Expression inner = steps.size() == 1 ? getSubExpression(0) : this;
        return inner.eval(null, null);
    }

    public Sequence evalPredicate(final Sequence outerSequence,
            final Sequence contextSequence, final int mode) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
        }
        Sequence result;
        final Expression inner = steps.size() == 1 ? getSubExpression(0) : this;
        if (inner == null) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            if (executionMode == UNKNOWN) {
                executionMode = BOOLEAN;
            }

            final Tuple2<ExecutionMode, Sequence> recomputed = recomputeExecutionMode(contextSequence, inner);
            final ExecutionMode recomputedExecutionMode = recomputed._1;
            @Nullable Sequence innerSeq = recomputed._2;

            switch (recomputedExecutionMode) {
                case NODE:
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(this,
                                Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "Node selection");
                    }
                    result = selectByNodeSet(contextSequence);
                    break;
                case BOOLEAN:
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(this,
                                Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "Boolean evaluation");
                    }
                    result = evalBoolean(contextSequence, inner, mode);
                    break;
                case POSITIONAL:
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(this,
                                Profiler.OPTIMIZATION_FLAGS, "OPTIMIZATION CHOICE", "Positional evaluation");
                    }
                    // In case it hasn't been evaluated above
                    if (innerSeq == null) {
                        // for a positional predicate, check if it depends on the context item
                        // if not, do not pass the context sequence to avoid cardinality errors
                        context.setContextSequencePosition(0, contextSequence);
                        innerSeq = inner.eval(Dependency.dependsOn(inner.getDependencies(), Dependency.CONTEXT_ITEM)
                                ? contextSequence : null, null);
                    }

                    // We must check for empty sequences here to avoid an NPE
                    if (innerSeq.isEmpty()) {
                        result = Sequence.EMPTY_SEQUENCE;
                    } else if (innerSeq.getCardinality().isSubCardinalityOrEqualOf(Cardinality.EXACTLY_ONE)) {
                        result = selectByPosition(outerSequence, contextSequence, mode, innerSeq);
                    } else {
                        throw new XPathException(this, ErrorCodes.FORG0006, "Effective boolean value is not defined for a sequence of two or more items starting with a " + Type.getTypeName(innerSeq.itemAt(0).getType()) + " value");
                    }
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unsupported execution mode: '" + recomputedExecutionMode + "'");
            }
        }
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;
    }

    private Tuple2<ExecutionMode, Sequence> recomputeExecutionMode(final Sequence contextSequence, final Expression inner) throws XPathException {
        ExecutionMode recomputedExecutionMode = executionMode;
        Sequence innerSeq = null;

        // Atomic context sequences :
        if (Type.subTypeOf(contextSequence.getItemType(), Type.ANY_ATOMIC_TYPE)) {
            // We can't have a node set operation : reconsider depending of
            // the inner sequence
            if (executionMode == NODE && !(contextSequence instanceof VirtualNodeSet)) {
                // (1,2,2,4)[.]
                if (Type.subTypeOfUnion(contextSequence.getItemType(), Type.NUMERIC)) {
                    recomputedExecutionMode = POSITIONAL;
                } else {
                    recomputedExecutionMode = BOOLEAN;
                }
            }
            // If there is no dependency on the context item, try a positional promotion
            if (executionMode == BOOLEAN && !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM) &&
                    // Hack : GeneralComparison lies on its dependencies
                    // TODO : try to remove this since our dependency
                    // computation should now be better
                    !((inner instanceof GeneralComparison) &&
                            ((GeneralComparison) inner).invalidNodeEvaluation)) {
                innerSeq = inner.eval(contextSequence, null);
                // Only if we have an actual *singleton* of numeric items
                if (innerSeq.hasOne()
                        && Type.subTypeOfUnion(innerSeq.getItemType(), Type.NUMERIC)) {
                    recomputedExecutionMode = POSITIONAL;
                }
            }
        } else if (executionMode == NODE && !contextSequence.isPersistentSet()) {
            recomputedExecutionMode = BOOLEAN;
        } else {
            if (executionMode == BOOLEAN && !Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM)) {
                /*
                 *
                 * WARNING : this sequence will be evaluated with
                 * preloadable nodesets !
                 */
                innerSeq = inner.eval(contextSequence, null);
                // Try to promote a boolean evaluation to a nodeset one
                // We are now sure of the inner sequence return type
                if (Type.subTypeOf(innerSeq.getItemType(), Type.NODE)
                        && innerSeq.isPersistentSet()) {
                    recomputedExecutionMode = NODE;
                    // Try to promote a boolean evaluation to a positional one
                    // Only if we have an actual *singleton* of numeric items
                } else if (innerSeq.hasOne()
                        && Type.subTypeOfUnion(innerSeq.getItemType(), Type.NUMERIC)) {
                    recomputedExecutionMode = POSITIONAL;
                }
            }
        }

        return Tuple(recomputedExecutionMode, innerSeq);
    }

    /**
     * Evaluate the inner part of the predicate as a boolean.
     *
     * @param contextSequence the context sequence
     * @param inner the inner expression
     *
     * @return The result of the boolean evaluation of the predicate.
     *
     * @throws XPathException if an error occurs
     */
    private Sequence evalBoolean(final Sequence contextSequence, final Expression inner, final int mode)
            throws XPathException {
        final Sequence result = new ValueSequence();
        int p;
        if (contextSequence instanceof NodeSet
                && ((NodeSet) contextSequence).getProcessInReverseOrder()) {
            // This one may be expensive...
            p = contextSequence.getItemCount();
            for (final SequenceIterator i = contextSequence.iterate(); i.hasNext(); p--) {
                // 0-based
                context.setContextSequencePosition(p - 1, contextSequence);
                final Item item = i.nextItem();
                final Sequence innerSeq = inner.eval(contextSequence, item);
                if (innerSeq.effectiveBooleanValue()) {
                    result.add(item);
                }
            }
        } else {
            // 0-based
            p = 0;

            final boolean reverseAxis = Type.subTypeOf(contextSequence.getItemType(),
                    Type.NODE) && (mode == Constants.ANCESTOR_AXIS ||
                    mode == Constants.ANCESTOR_SELF_AXIS || mode == Constants.PARENT_AXIS ||
                    mode == Constants.PRECEDING_AXIS || mode == Constants.PRECEDING_SIBLING_AXIS);

            // TODO : is this block also accurate in reverse-order processing ?
            // Compute each position in the boolean-like way...
            // ... but grab some context positions ! -<8-P
            if (Type.subTypeOfUnion(inner.returnsType(), Type.NUMERIC)
                    && Dependency.dependsOn(inner, Dependency.CONTEXT_ITEM)) {
                final Set<NumericValue> positions = new TreeSet<>();
                for (final SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
                    context.setContextSequencePosition(p, contextSequence);
                    final Item item = i.nextItem();
                    final Sequence innerSeq = inner.eval(contextSequence, item);
                    if (innerSeq.hasOne()) {
                        final NumericValue nv = (NumericValue) innerSeq.itemAt(0);
                        // Non integers return... nothing, not even an error !
                        if (!nv.hasFractionalPart() && !nv.isZero()) {
                            positions.add(nv);
                        }
                    }
                    //XXX: else error or nothing?
                }
                for (final NumericValue pos : positions) {
                    final int position = (reverseAxis ? contextSequence.getItemCount() - pos.getInt() : pos.getInt() - 1);
                    // TODO : move this test above ?
                    if (position <= contextSequence.getItemCount()) {
                        result.add(contextSequence.itemAt(position));
                    }
                }
            } else {
                final Set<NumericValue> positions = new TreeSet<>();
                for (final SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
                    context.setContextSequencePosition((reverseAxis ? contextSequence.getItemCount() - p - 1 : p), contextSequence);
                    final Item item = i.nextItem();
                    final Sequence innerSeq = inner.eval(contextSequence, item);
                    if (innerSeq.hasOne()
                            && Type.subTypeOfUnion(innerSeq.getItemType(), Type.NUMERIC)) {
                        // TODO : introduce a check in innerSeq.hasOne() ?
                        final NumericValue nv = (NumericValue) innerSeq;
                        // Non integers return... nothing, not even an error !
                        if (!nv.hasFractionalPart() && !nv.isZero()) {
                            positions.add(nv);
                        }
                    } else if (innerSeq.effectiveBooleanValue()) {
                        result.add(item);
                    }
                }
                for (final NumericValue pos : positions) {
                    final int position = (reverseAxis ? contextSequence.getItemCount() - pos.getInt() : pos.getInt() - 1);
                    // TODO : move this test above ?
                    if (position <= contextSequence.getItemCount()) {
                        result.add(contextSequence.itemAt(position));
                    }
                }
            }
        }
        return result;
    }

    /**
     * @param contextSequence the context sequence
     *
     * @return The result of the node set evaluation of the predicate.
     *
     * @throws XPathException if an error occurs
     */
    private Sequence selectByNodeSet(final Sequence contextSequence) throws XPathException {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        final NodeSet contextSet = contextSequence.toNodeSet();
        final boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
        contextSet.setTrackMatches(false);
        final NodeSet nodes = super.eval(contextSet, null).toNodeSet();
        /*
         * if the predicate expression returns results from the cache we can
         * also return the cached result.
         */
        if (cached != null && cached.isValid(contextSequence, null) && nodes.isCached()) {
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "Using cached results", result);
            }
            return cached.getResult();
        }
        DocumentImpl lastDoc = null;
        for (final NodeProxy currentNode : nodes) {
            int sizeHint = Constants.NO_SIZE_HINT;
            if (lastDoc == null || currentNode.getOwnerDocument() != lastDoc) {
                lastDoc = currentNode.getOwnerDocument();
                sizeHint = nodes.getSizeHint(lastDoc);
            }
            ContextItem contextItem = currentNode.getContext();
            if (contextItem == null) {
                throw new XPathException(this,
                        "Internal evaluation error: context is missing for node " +
                                currentNode.getNodeId() + " !");
            }
            // TODO : review to consider transverse context
            while (contextItem != null) {
                if (contextItem.getContextId() == getExpressionId()) {
                    final NodeProxy next = contextItem.getNode();
                    if (contextIsVirtual || contextSet.contains(next)) {
                        next.addMatches(currentNode);
                        result.add(next, sizeHint);
                    }
                }
                contextItem = contextItem.getNextDirect();
            }
        }
        if (contextSequence.isCacheable()) {
            cached = new CachedResult(contextSequence, null, result);
        }
        contextSet.setTrackMatches(true);
        return result;
    }

    /**
     * @param outerSequence the outer sequence
     * @param contextSequence the context sequence
     * @param mode the mode
     * @param innerSeq the inner sequence
     *
     * @return The result of the positional evaluation of the predicate.
     *
     * @throws XPathException if an error occurs
     */
    private Sequence selectByPosition(final Sequence outerSequence,
        final Sequence contextSequence, final int mode, final Sequence innerSeq) throws XPathException {
        if (outerSequence != null && !outerSequence.isEmpty()
                && Type.subTypeOf(contextSequence.getItemType(), Type.NODE)
                && contextSequence.isPersistentSet()
                && outerSequence.isPersistentSet()) {
            final Sequence result = new NewArrayNodeSet();
            final NodeSet contextSet = contextSequence.toNodeSet();
            switch (mode) {
                case Constants.CHILD_AXIS:
                case Constants.ATTRIBUTE_AXIS:
                case Constants.DESCENDANT_AXIS:
                case Constants.DESCENDANT_SELF_AXIS:
                case Constants.DESCENDANT_ATTRIBUTE_AXIS: {
                    final NodeSet outerNodeSet = outerSequence.toNodeSet();
                    // TODO: in some cases, especially with in-memory nodes,
                    // outerSequence.toNodeSet() will generate a document
                    // which will be different from the one(s) in contextSet
                    // ancestors will thus be empty :-(
                    // A special treatment of VirtualNodeSet does not seem to be
                    // required anymore
                    final Sequence ancestors = outerNodeSet.selectAncestors(contextSet,
                            true, getExpressionId());
                    if (contextSet.getDocumentSet().intersection(
                            outerNodeSet.getDocumentSet()).getDocumentCount() == 0) {
                        LOG.info("contextSet and outerNodeSet don't share any document");
                    }
                    final NewArrayNodeSet temp = new NewArrayNodeSet();
                    for (final SequenceIterator i = ancestors.iterate(); i.hasNext(); ) {
                        NodeProxy p = (NodeProxy) i.nextItem();
                        ContextItem contextNode = p.getContext();
                        temp.reset();
                        while (contextNode != null) {
                            if (contextNode.getContextId() == getExpressionId()) {
                                temp.add(contextNode.getNode());
                            }
                            contextNode = contextNode.getNextDirect();
                        }
                        p.clearContext(getExpressionId());
                        // TODO : understand why we sort here...
                        temp.sortInDocumentOrder();
                        for (final SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
                            final NumericValue v = (NumericValue) j.nextItem();
                            // Non integers return... nothing, not even an error !
                            if (!v.hasFractionalPart() && !v.isZero()) {
                                // ... whereas we don't want a sorted array here
                                // TODO : rename this method as getInDocumentOrder ? -pb
                                p = temp.get(v.getInt() - 1);
                                if (p != null) {
                                    result.add(p);
                                }
                                // TODO : does null make sense here ? Well... sometimes ;-)
                            }
                        }
                    }
                    break;
                }
                default:
                    for (final SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
                        NodeProxy p = (NodeProxy) i.nextItem();
                        Sequence temp;
                        boolean reverseAxis = true;
                        switch (mode) {
                            case Constants.ANCESTOR_AXIS:
                                temp = contextSet.selectAncestors(p, false, Expression.IGNORE_CONTEXT);
                                break;
                            case Constants.ANCESTOR_SELF_AXIS:
                                temp = contextSet.selectAncestors(p, true, Expression.IGNORE_CONTEXT);
                                break;
                            case Constants.PARENT_AXIS:
                                // TODO : understand why the contextSet is not involved
                                // here
                                // NodeProxy.getParent returns a *theoretical* parent
                                // which is *not* guaranteed to be in the context set !
                                temp = p.getParents(Expression.NO_CONTEXT_ID);
                                break;
                            case Constants.PRECEDING_AXIS:
                                temp = contextSet.selectPreceding(p, Expression.IGNORE_CONTEXT);
                                break;
                            case Constants.PRECEDING_SIBLING_AXIS:
                                temp = contextSet.selectPrecedingSiblings(p, Expression.IGNORE_CONTEXT);
                                break;
                            case Constants.FOLLOWING_SIBLING_AXIS:
                                temp = contextSet.selectFollowingSiblings(p, Expression.IGNORE_CONTEXT);
                                reverseAxis = false;
                                break;
                            case Constants.FOLLOWING_AXIS:
                                temp = contextSet.selectFollowing(p, Expression.IGNORE_CONTEXT);
                                reverseAxis = false;
                                break;
                            case Constants.SELF_AXIS:
                                temp = p;
                                reverseAxis = false;
                                break;
                            default:
                                throw new IllegalArgumentException("Tried to test unknown axis");
                        }
                        if (!temp.isEmpty()) {
                            for (final SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
                                final NumericValue v = (NumericValue) j.nextItem();
                                // Non integers return... nothing, not even an error !
                                if (!v.hasFractionalPart() && !v.isZero()) {
                                    final int pos = (reverseAxis ?
                                            temp.getItemCount() - v.getInt() : v.getInt() - 1);
                                    // Other positions are ignored
                                    if (pos >= 0 && pos < temp.getItemCount()) {
                                        final NodeProxy t = (NodeProxy) temp.itemAt(pos);
                                        // for the current context: filter out those
                                        // context items not selected by the positional predicate
                                        ContextItem ctx = t.getContext();
                                        t.clearContext(Expression.IGNORE_CONTEXT);
                                        while (ctx != null) {
                                            if (ctx.getContextId() == outerContextId) {
                                                if (ctx.getNode().getNodeId().equals(p.getNodeId())) {
                                                    t.addContextNode(outerContextId, ctx.getNode());
                                                }
                                            } else {
                                                t.addContextNode(ctx.getContextId(), ctx.getNode());
                                            }
                                            ctx = ctx.getNextDirect();
                                        }
                                        result.add(t);
                                    }
                                }
                            }
                        }
                    }
            }
            return result;
        } else {
            final boolean reverseAxis = Type.subTypeOf(contextSequence.getItemType(),
                    Type.NODE) && (mode == Constants.ANCESTOR_AXIS ||
                    mode == Constants.ANCESTOR_SELF_AXIS || mode == Constants.PARENT_AXIS ||
                    mode == Constants.PRECEDING_AXIS || mode == Constants.PRECEDING_SIBLING_AXIS);

            Sequence result = Sequence.EMPTY_SEQUENCE;
            if (innerSeq.hasOne()) {
                // optimise for single position lookup
                final NumericValue v = (NumericValue) innerSeq.itemAt(0);
                // Non integers return... nothing, not even an error !
                if (isNonZeroInteger(v)) {
                    final int pos = calculatePos(reverseAxis, contextSequence, v);
                    // Other positions are ignored
                    if (withinBounds(contextSequence, pos)) {
                        result = (Sequence) contextSequence.itemAt(pos);
                    }
                }
            } else {
                // multi-position lookup
                Set<NumericValue> set = null;
                for (final SequenceIterator i = innerSeq.iterate(); i.hasNext(); ) {
                    final NumericValue v = (NumericValue) i.nextItem();
                    // Non integers return... nothing, not even an error !
                    if (isNonZeroInteger(v)) {
                        final int pos = calculatePos(reverseAxis, contextSequence, v);
                        // Other positions are ignored
                        if (withinBounds(contextSequence, pos) && (set == null || !set.contains(v))) {
                            if (result == Sequence.EMPTY_SEQUENCE) {
                                result = new ValueSequence();
                            }
                            result.add(contextSequence.itemAt(pos));
                            if (set == null) {
                                set = new TreeSet<>();
                            }
                            set.add(v);
                        }
                    }
                }
            }
            return result;
        }
    }

    private static boolean isNonZeroInteger(final NumericValue v) {
        return !v.hasFractionalPart() && !v.isZero();
    }

    private static int calculatePos(final boolean reverseAxis, final Sequence contextSequence, final NumericValue v) throws XPathException {
        return (reverseAxis ? contextSequence.getItemCount() - v.getInt() : v.getInt() - 1);
    }

    private static boolean withinBounds(final Sequence contextSequence, final int pos) {
        return pos >= 0 && pos < contextSequence.getItemCount();
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        if (getSubExpressionCount() > 0) {
            getSubExpression(0).setContextDocSet(contextSet);
        }
    }

    ExecutionMode getExecutionMode() {
        return executionMode;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            cached = null;
        }
    }

    @Override
    public Expression getParent() {
        return parent;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitPredicate(this);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("[");
        super.dump(dumper);
        dumper.display("]");
    }

    @Override
    public String toString() {
        return "[" + super.toString() + "]";
    }

    @Override
    public Expression simplify() {
        return this;
    }
}
