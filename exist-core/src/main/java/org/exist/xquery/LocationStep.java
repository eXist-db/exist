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

import org.exist.dom.persistent.*;
import org.exist.indexing.StructuralIndex;
import org.exist.dom.memtree.InMemoryNodeSet;
import org.exist.dom.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.stax.*;
import org.exist.storage.ElementValue;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nullable;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;

/**
 * Processes all location path steps (like descendant::*, ancestor::XXX).
 *
 * The results of the first evaluation of the expression are cached for the
 * lifetime of the object and only reloaded if the context sequence (as passed
 * to the {@link #eval(Sequence, Item)} method) has changed.
 *
 * @author wolf
 */
public class LocationStep extends Step {

    private static final int INDEX_SCAN_THRESHOLD = 10000;

    private NodeSet currentSet = null;
    private DocumentSet currentDocs = null;
    protected UpdateListener listener = null;
    protected Expression parent = null;

    //private int parentDeps = Dependency.UNKNOWN_DEPENDENCY;
    private boolean preloadedData = false;
    protected boolean optimized = false;
//    private boolean inUpdate = false;
    private boolean useDirectChildSelect = false;
    private boolean applyPredicate = true;

    // Cache for the current NodeTest type
    private Integer nodeTestType = null;

    /**
     * Creates a new instance.
     *
     * @param context the XQuery context
     * @param axis    the axis of the location step
     */
    public LocationStep(final XQueryContext context, final int axis) {
        super(context, axis);
    }

    /**
     * Creates a new instance.
     *
     * @param context the XQuery context
     * @param axis the axis of the location step
     * @param test a node test on the axis
     */
    public LocationStep(final XQueryContext context, final  int axis, final NodeTest test) {
        super(context, axis, test);
    }

    @Override
    public int getDependencies() {
        int deps = Dependency.CONTEXT_SET;

        // self axis always has a dependency on the context item — that is its definition.
        // Previously this was suppressed inside predicates as a node-set optimization,
        // but that caused XPDY0002 when predicates operated on atomic values from
        // map/array navigation (e.g., map{"a":1}/a[. <= 1]).
        if (this.axis == Constants.SELF_AXIS) {
            deps = deps | Dependency.CONTEXT_ITEM;
        } else if (!this.inPredicate &&
                (parent != null && parent.getSubExpressionCount() > 0 && parent.getSubExpression(0) == this)) {
            // single path step (outside a predicate) also depends on context item
            deps = deps | Dependency.CONTEXT_ITEM;
        }

        // TODO : normally, we should call this one...
        // int deps = super.getDependencies(); ???
        if (predicates != null) {
            for (final Predicate pred : predicates) {
                deps |= pred.getDependencies();
            }
        }

        // TODO : should we remove the CONTEXT_ITEM dependency returned by the
        // predicates ? See the comment above.
        // consider nested predicates however...

        return deps;
    }

    /**
     * If the current path expression depends on local variables from a for
     * expression, we can optimize by preloading entire element or attribute
     * sets.
     *
     * @return Whether or not we can optimize
     */
    private boolean hasPreloadedData() {
        // TODO : log elsewhere ?
        if (preloadedData) {
            context.getProfiler().message(this, Profiler.OPTIMIZATIONS, null,
                    "Preloaded NodeSets");
            return true;
        }
//		if (inUpdate)
//			return false;
//		if ((parentDeps & Dependency.LOCAL_VARS) == Dependency.LOCAL_VARS) {
//			context.getProfiler().message(this, Profiler.OPTIMIZATIONS, null,
//					"Preloaded NodeSets");
//			return true;
//		}
        return false;
    }

    /**
     * The method <code>setPreloadedData</code>
     *
     * @param docs  a <code>DocumentSet</code> value
     * @param nodes a <code>NodeSet</code> value
     */
    public void setPreloadedData(final DocumentSet docs, final NodeSet nodes) {
        this.preloadedData = true;
        this.currentDocs = docs;
        this.currentSet = nodes;
        this.optimized = true;
    }

    /**
     * The method <code>applyPredicate</code>
     *
     * @param outerSequence   a <code>Sequence</code> value
     * @param contextSequence a <code>Sequence</code> value
     * @return a <code>Sequence</code> value
     * @throws XPathException if an error occurs
     */
    private Sequence applyPredicate(Sequence outerSequence, final Sequence contextSequence) throws XPathException {
        if (contextSequence == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (predicates == null
                || !applyPredicate
                || (!(contextSequence instanceof VirtualNodeSet) && contextSequence
                .isEmpty()))
        // Nothing to apply
        {
            return contextSequence;
        }
        Sequence result;
        final Predicate pred = predicates[0];
        // If the current step is an // abbreviated step, we have to treat the
        // predicate
        // specially to get the context position right. //a[1] translates to
        // /descendant-or-self::node()/a[1],
        // so we need to return the 1st a from any parent of a.
        //
        // If the predicate is known to return a node set, no special treatment
        // is required.
        if (abbreviatedStep
                && (pred.getExecutionMode() == Predicate.ExecutionMode.POSITIONAL || !contextSequence
                .isPersistentSet())) {
            result = new ValueSequence();
            ((ValueSequence) result).keepUnOrdered(unordered);
            if (contextSequence.isPersistentSet()) {
                final NodeSet contextSet = contextSequence.toNodeSet();
                outerSequence = contextSet.getParents(-1);
                for (final SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
                    final NodeValue node = (NodeValue) i.nextItem();
                    final Sequence newContextSeq = contextSet.selectParentChild(
                            (NodeSet) node, NodeSet.DESCENDANT,
                            getExpressionId());
                    final Sequence temp = processPredicate(outerSequence,
                            newContextSeq);
                    result.addAll(temp);
                }
            } else {
                final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
                outerSequence = nodes.getParents(new AnyNodeTest());
                for (final SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
                    final NodeValue node = (NodeValue) i.nextItem();
                    final InMemoryNodeSet newSet = new InMemoryNodeSet();
                    ((NodeImpl) node).selectChildren(test, newSet);
                    final Sequence temp = processPredicate(outerSequence, newSet);
                    result.addAll(temp);
                }
            }
        } else {
            result = processPredicate(outerSequence, contextSequence);
        }
        return result;
    }

    private Sequence processPredicate(Sequence outerSequence, final Sequence contextSequence) throws XPathException {
        Sequence result = contextSequence;
        if (predicates != null) {
            for (int i = 0; i < predicates.length && (result instanceof VirtualNodeSet || !result.isEmpty()); i++) {
                // TODO : log and/or profile ?
                final Predicate pred = predicates[i];
                pred.setContextDocSet(getContextDocSet());
                result = pred.evalPredicate(outerSequence, result, axis);
                // subsequent predicates operate on the result of the previous one
                outerSequence = null;
                context.setContextSequencePosition(0, null);
            }
        }
        return result;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        this.parent = contextInfo.getParent();

        unordered = (contextInfo.getFlags() & UNORDERED) > 0;

//        parentDeps = parent.getDependencies();
//        if ((contextInfo.getFlags() & IN_UPDATE) > 0) {
//            inUpdate = true;
//        }
//		if ((contextInfo.getFlags() & SINGLE_STEP_EXECUTION) > 0) {
//			preloadedData = true;
//		}
        if ((contextInfo.getFlags() & USE_TREE_TRAVERSAL) > 0) {
            useDirectChildSelect = true;
        }
        // Mark ".", which is expanded as self::node() by the parser
        // even though it may *also* be relevant with atomic sequences
        if (this.axis == Constants.SELF_AXIS
                && this.test.getType() == Type.NODE) {
            contextInfo.addFlag(DOT_TEST);
        }

        //Change axis from descendant-or-self to descendant for '//'
        if (this.axis == Constants.DESCENDANT_SELF_AXIS && isAbbreviated()) {
            this.axis = Constants.DESCENDANT_AXIS;
        }

        final Expression contextStep;
        final NodeTest stepTest;
        final NodeTest contextStepTest;

        // static analysis for empty-sequence
        switch (axis) {
            case Constants.SELF_AXIS:
                if (getTest().getType() == Type.NODE) {
                    break;
                }

                // WM: the following checks will only work on simple filters like //a[self::b], so we
                // have to make sure they are not applied to more complex expression types
                if (parent.getSubExpressionCount() > 1) {
                    break;
                }

                contextStep = contextInfo.getContextStep();
                if (!(contextStep instanceof LocationStep cStep)) {
                    break;
                }

                stepTest = getTest();
                contextStepTest = cStep.getTest();

                if (!Type.subTypeOf(stepTest.getType(), contextStepTest.getType())) {
                    // return empty sequence
                    contextInfo.setStaticType(Type.EMPTY_SEQUENCE);
                    staticReturnType = Type.EMPTY_SEQUENCE;
                    break;
                }

                if (!stepTest.isWildcardTest() &&
                        !contextStepTest.isWildcardTest() &&
                        !contextStepTest.equals(stepTest)) {
                    // return empty sequence
                    contextInfo.setStaticType(Type.EMPTY_SEQUENCE);
                    staticReturnType = Type.EMPTY_SEQUENCE;
                }
                break;
//		      case Constants.DESCENDANT_AXIS:
            case Constants.DESCENDANT_SELF_AXIS:
                contextStep = contextInfo.getContextStep();
                if (!(contextStep instanceof LocationStep cStep)) {
                    break;
                }

                stepTest = getTest();
                contextStepTest = cStep.getTest();

                if ((contextStepTest.getType() == Type.ATTRIBUTE || contextStepTest.getType() == Type.TEXT) &&
                        contextStepTest != stepTest) {
                    // return empty sequence
                    contextInfo.setStaticType(Type.EMPTY_SEQUENCE);
                    staticReturnType = Type.EMPTY_SEQUENCE;
                }
                break;
//		case Constants.PARENT_AXIS:
//		case Constants.ATTRIBUTE_AXIS:
            default:
        }

        // TODO : log somewhere ?
        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }

        Sequence result;
        if (needsComputation()) {
            if (contextSequence == null) {
                throw new XPathException(this,
                        ErrorCodes.XPDY0002, "Undefined context sequence for '"
                        + this + "'");
            }

            // === XQuery 4.0 JNode / Map / Array axis navigation ===
            // Check if any item in the context sequence is navigable (JNode, map, array)
            if (contextSequence.getItemCount() > 0) {
                boolean hasJNode = false;
                boolean hasMapArray = false;
                for (int ci = 0; ci < Math.min(contextSequence.getItemCount(), 10); ci++) {
                    final Item item = contextSequence.itemAt(ci);
                    if (item instanceof org.exist.xquery.value.jnode.JNode) {
                        hasJNode = true;
                        break;
                    }
                    if (item instanceof org.exist.xquery.functions.map.MapType
                            || item instanceof org.exist.xquery.functions.array.ArrayType) {
                        hasMapArray = true;
                    }
                }
                if (hasJNode) {
                    result = evalJNodeAxis(contextSequence);
                    // Apply predicates and return
                    result = applyPredicate(contextSequence, result);
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().end(this, "", result);
                    }
                    return result;
                }
                if (hasMapArray) {
                    result = evalMapArrayAxis(contextSequence);
                    // Apply predicates and return
                    result = applyPredicate(contextSequence, result);
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().end(this, "", result);
                    }
                    return result;
                }
            }
            // === End JNode / Map / Array axis navigation ===

            try {
                switch (axis) {

                    case Constants.DESCENDANT_AXIS:
                    case Constants.DESCENDANT_SELF_AXIS:
                        result = getDescendants(context, contextSequence);
                        break;

                    case Constants.CHILD_AXIS:
                        // VirtualNodeSets may have modified the axis ; checking the
                        // type
                        // TODO : further checks ?
//				if (this.test.getType() == Type.ATTRIBUTE) {
//					this.axis = Constants.ATTRIBUTE_AXIS;
//					result = getAttributes(context, contextSequence);
//				} else {
                        result = getChildren(context, contextSequence);
//				}
                        break;

                    case Constants.ANCESTOR_SELF_AXIS:
                    case Constants.ANCESTOR_AXIS:
                        result = getAncestors(context, contextSequence);
                        break;

                    case Constants.PARENT_AXIS:
                        result = getParents(context, contextSequence);
                        break;

                    case Constants.SELF_AXIS:
                        if (!(contextSequence instanceof VirtualNodeSet)
                                && Type.subTypeOf(contextSequence.getItemType(),
                                Type.ANY_ATOMIC_TYPE)) {
                            // This test is copied from the legacy method
                            // getSelfAtomic()
                            if (!test.isWildcardTest()) {
                                throw new XPathException(this, test.toString()
                                        + " cannot be applied to an atomic value.");
                            }
                            result = contextSequence;
                        } else {
                            result = getSelf(context, contextSequence);
                        }
                        break;

                    case Constants.ATTRIBUTE_AXIS:
                    case Constants.DESCENDANT_ATTRIBUTE_AXIS:
                        result = getAttributes(context, contextSequence);
                        break;

                    case Constants.PRECEDING_AXIS:
                    case Constants.FOLLOWING_AXIS:
                        result = getPrecedingOrFollowing(context, contextSequence);
                        break;

                    case Constants.PRECEDING_SIBLING_AXIS:
                    case Constants.FOLLOWING_SIBLING_AXIS:
                        result = getSiblings(context, contextSequence);
                        break;

                    // --- XQuery 4.0 combined axes ---
                    case Constants.FOLLOWING_OR_SELF_AXIS:
                    case Constants.PRECEDING_OR_SELF_AXIS:
                        result = getOrSelfAxis(context, contextSequence,
                                axis == Constants.FOLLOWING_OR_SELF_AXIS
                                        ? Constants.FOLLOWING_AXIS : Constants.PRECEDING_AXIS);
                        break;

                    case Constants.FOLLOWING_SIBLING_OR_SELF_AXIS:
                    case Constants.PRECEDING_SIBLING_OR_SELF_AXIS:
                        result = getOrSelfAxis(context, contextSequence,
                                axis == Constants.FOLLOWING_SIBLING_OR_SELF_AXIS
                                        ? Constants.FOLLOWING_SIBLING_AXIS : Constants.PRECEDING_SIBLING_AXIS);
                        break;

                    default:
                        throw new IllegalArgumentException("Unsupported axis specified");
                }
            } catch (final XPathException e) {
                if (e.getLine() <= 0) {
                    e.setLocation(getLine(), getColumn(), getSource());
                }
                throw e;
            }
        } else {
            result = NodeSet.EMPTY_SET;
        }
        if (axis != Constants.SELF_AXIS && contextSequence != null
                && contextSequence.isCacheable()) {
            registerUpdateListener();
        }
        // Remove duplicate nodes
        result.removeDuplicates();
        // Apply the predicate
        result = applyPredicate(contextSequence, result);

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        // actualReturnType = result.getItemType();

        return result;
    }

    // Avoid unnecessary tests (these should be detected by the parser)
    private boolean needsComputation() {
        // TODO : log this ?
        switch (axis) {
            // Certainly not exhaustive
            case Constants.ANCESTOR_SELF_AXIS:
            case Constants.PARENT_AXIS:
                // case Constants.SELF_AXIS:
                if (nodeTestType == null) {
                    nodeTestType = test.getType();
                }
                // JNode, map, and array types always need computation
                if (Type.subTypeOf(nodeTestType, Type.JSON_NODE)
                        || Type.subTypeOf(nodeTestType, Type.MAP_ITEM)
                        || Type.subTypeOf(nodeTestType, Type.ARRAY_ITEM)) {
                    return true;
                }
                if (nodeTestType != Type.DOCUMENT
                        && nodeTestType != Type.NODE
                        && nodeTestType != Type.ELEMENT
                        && nodeTestType != Type.PROCESSING_INSTRUCTION) {
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION", "avoid useless computations");
                    }
                    return false;
                }

        }
        return true;
    }

    private Sequence getSelf(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            return nodes.getSelf(test);
        }

        if (hasPreloadedData()) {
            @Nullable final NodeSet ns;
            if (contextSequence instanceof NodeSet) {
                ns = (NodeSet) contextSequence;
            } else {
                ns = null;
            }

            final NewArrayNodeSet newCurrentSet = new NewArrayNodeSet(currentSet.getItemCount());
            for (NodeProxy p : currentSet) {
                if (test.isWildcardTest()) {
                    if (ns != null) {
                        @Nullable final NodeProxy np = ns.get(p);
                        if (np != null) {
                            p = np;
                        }
                    }
                } else {
                    if (ns != null) {
                        @Nullable final NodeProxy np = ns.get(p);
                        if (np != null && np.getMatches() != null) {
                            p.addMatch(np.getMatches());
                        }
                    }
                }
                p.addContextNode(contextId, p);
                newCurrentSet.add(p);
            }
            currentSet = newCurrentSet;
            return newCurrentSet;
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
//        if (test.getType() == Type.PROCESSING_INSTRUCTION) {
//            final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
//            vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
//            return vset;
//        }

        if (test.isWildcardTest()) {
            if (nodeTestType == null) {
                nodeTestType = test.getType();
            }

            if (Type.subTypeOf(nodeTestType, Type.NODE)) {
                if (Expression.NO_CONTEXT_ID != contextId) {
                    if (contextSet instanceof VirtualNodeSet) {
                        ((VirtualNodeSet) contextSet).setInPredicate(true);
                        ((VirtualNodeSet) contextSet).setContextId(contextId);
                        ((VirtualNodeSet) contextSet).setSelfIsContext();
                    } else if (Type.subTypeOf(contextSet.getItemType(), Type.NODE)) {
                        for (final NodeProxy p : contextSet) {
                            if (test.matches(p)) {
                                p.addContextNode(contextId, p);
                            }
                        }
                    }
                    return contextSet;
                } else {
                    final NewArrayNodeSet results = new NewArrayNodeSet();
                    for (final NodeProxy p : contextSet) {
                        if(test.matches(p)) {
                            results.add(p);
                        }
                    }
                    return results;
                }
            } else {
                final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
                vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
                return vset;
            }
        } else {
            final DocumentSet docs = getDocumentSet(contextSet);
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            final NodeSelector selector = new SelfSelector(contextSet, contextId);
            return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector, this);
        }
    }

    protected Sequence getAttributes(final XQueryContext context, final Sequence contextSequence)
            throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
                return nodes.getDescendantAttributes(test);
            } else {
                return nodes.getAttributes(test);
            }
        }
        final NodeSet contextSet = contextSequence.toNodeSet();
        if (!hasPreloadedData() && test.isWildcardTest()) {
            final NodeSet result = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
            ((VirtualNodeSet) result).setInPredicate(Expression.NO_CONTEXT_ID != contextId);
            return result;
            // if there's just a single known node in the context, it is faster
            // do directly search for the attribute in the parent node.
        }
        if (hasPreloadedData()) {
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                if (currentSet == null
                        || currentDocs == null
                        || (!optimized && !(docs == currentDocs || docs
                        .equalDocs(currentDocs)))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    // TODO : why a null selector here ? We have one below !
                    currentSet = index.findElementsByTagName(ElementValue.ATTRIBUTE, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }
                return switch (axis) {
                    case Constants.ATTRIBUTE_AXIS ->
                            currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, contextId);
                    case Constants.DESCENDANT_ATTRIBUTE_AXIS ->
                            currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, false, contextId,
                                    true);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
            }
        } else {
            final DocumentSet docs = getDocumentSet(contextSet);
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            if (!contextSet.getProcessInReverseOrder()) {
                return index.findDescendantsByTagName(ElementValue.ATTRIBUTE, test.getName(), axis, docs, contextSet,
                        contextId, this);
            } else {
                final NodeSelector selector = switch (axis) {
                    case Constants.ATTRIBUTE_AXIS -> new ChildSelector(contextSet, contextId);
                    case Constants.DESCENDANT_ATTRIBUTE_AXIS -> new DescendantSelector(contextSet, contextId);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
                return index.findElementsByTagName(ElementValue.ATTRIBUTE, docs, test.getName(), selector, this);
            }
        }
    }

    private Sequence getChildren(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            return nodes.getChildren(test);
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        // TODO : understand this. I guess comments should be treated in a
        // similar way ? -pb
        if ((!hasPreloadedData() && test.isWildcardTest()) || test.getType() == Type.PROCESSING_INSTRUCTION) {
            // test is one out of *, text(), node() including
            // processing-instruction(targetname)
            final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
            vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
            return vset;
        }

        // IndexStatistics stats = (IndexStatistics)
        // context.getBroker().getBrokerPool().
        // getIndexManager().getIndexById(IndexStatistics.ID);
        // int parentDepth = stats.getMaxParentDepth(test.getName());
        // LOG.debug("parentDepth for " + test.getName() + ": " + parentDepth);

        if (useDirectChildSelect) {
            final NewArrayNodeSet result = new NewArrayNodeSet();
            for (final NodeProxy p : contextSet) {
                result.addAll(p.directSelectChild(test.getName(), contextId));
            }
            return result;
        } else if (hasPreloadedData()) {
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                // TODO : understand why this one is different from the other
                // ones
                if (currentSet == null
                        || currentDocs == null
                        || (!optimized && !(docs == currentDocs || docs
                        .equalDocs(currentDocs)))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }
                return currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, contextId);
            }
        } else {
            final DocumentSet docs = getDocumentSet(contextSet);
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            if (!contextSet.getProcessInReverseOrder() && !(contextSet instanceof VirtualNodeSet) &&
                    contextSet.getLength() < INDEX_SCAN_THRESHOLD) {
                return index.findDescendantsByTagName(ElementValue.ELEMENT,
                        test.getName(), axis, docs, contextSet,
                        contextId, parent);
            } else {
                // if (contextSet instanceof VirtualNodeSet)
                // ((VirtualNodeSet)contextSet).realize();
                final NodeSelector selector = new ChildSelector(contextSet, contextId);
                return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector, this);
            }
        }
    }

    private Sequence getDescendants(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            return nodes.getDescendants(axis == Constants.DESCENDANT_SELF_AXIS,
                    test);
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        // TODO : understand this. I guess comments should be treated in a
        // similar way ? -pb
        if ((!hasPreloadedData() && test.isWildcardTest())
                || test.getType() == Type.PROCESSING_INSTRUCTION) {
            // test is one out of *, text(), node() including
            // processing-instruction(targetname)
            final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
            vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
            return vset;
        } else if (hasPreloadedData()) {
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                // TODO : understand why this one is different from the other
                // ones
                if (currentSet == null
                        || currentDocs == null
                        || (!optimized && !(docs == currentDocs || docs
                        .equalDocs(currentDocs)))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }

                return switch (axis) {
                    case Constants.DESCENDANT_SELF_AXIS ->
                            currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, true, contextId,
                                    true);
                    case Constants.DESCENDANT_AXIS ->
                            currentSet.selectAncestorDescendant(contextSet, NodeSet.DESCENDANT, false, contextId,
                                    true);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
            }
        } else {
            final DocumentSet docs = contextSet.getDocumentSet();
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            if (!contextSet.getProcessInReverseOrder()
                    && (contextSet instanceof VirtualNodeSet || contextSet.getLength() < INDEX_SCAN_THRESHOLD)) {
                return index.findDescendantsByTagName(ElementValue.ELEMENT, test.getName(), axis, docs, contextSet,
                        contextId, this);
            } else {
                final NodeSelector selector = switch (axis) {
                    case Constants.DESCENDANT_SELF_AXIS -> new DescendantOrSelfSelector(contextSet, contextId);
                    case Constants.DESCENDANT_AXIS -> new DescendantSelector(contextSet, contextId);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
                return index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), selector, this);
            }

        }
    }

    /**
     * Get's the sibling nodes of the context set
     *
     * @param context         a <code>XQueryContext</code> value
     * @param contextSequence a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     * @throws XPathException in case of dynamic error
     */
    protected Sequence getSiblings(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            if (axis == Constants.PRECEDING_SIBLING_AXIS) {
                return nodes.getPrecedingSiblings(test);
            } else {
                return nodes.getFollowingSiblings(test);
            }
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        // TODO : understand this. I guess comments should be treated in a
        // similar way ? -pb
        if (test.getType() == Type.PROCESSING_INSTRUCTION) {
            final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
                    test, contextId, contextSet);
            vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
            return vset;
        }

        if (test.isWildcardTest()) {
            final AVLTreeNodeSet result = new AVLTreeNodeSet();
            try {
                final int limit = computeLimit();
                for (final NodeProxy current : contextSet) {
                    // document-node() does not have any preceding or following elements
                    if (NodeId.DOCUMENT_NODE.equals(current.getNodeId())) {
                        continue;
                    }

                    final IEmbeddedXMLStreamReader reader;
                    final StreamFilter filter;
                    if (axis == Constants.PRECEDING_SIBLING_AXIS) {
                        final NodeId startNodeId;
                        if (NodeId.DOCUMENT_NODE.equals(current.getNodeId().getParentId())) {
                            // parent would be document-node(), start from document-node()/node()[1]
                            startNodeId = NodeId.ROOT_NODE;
                        } else {
                            startNodeId = current.getNodeId().getParentId().getChild(1);
                        }
                        final NodeProxy startNode = new NodeProxy(this, current.getOwnerDocument(), startNodeId);

                        reader = context.getBroker().getXMLStreamReader(startNode, false);
                        filter = new PrecedingSiblingFilter(test, startNode, current, result, contextId);
                    } else {
                        reader = context.getBroker().getXMLStreamReader(current, false);
                        filter = new FollowingSiblingFilter(test, current, result, contextId, limit);
                    }

                    reader.filter(filter);
                }
            } catch (final IOException | XMLStreamException e) {
                throw new XPathException(this, e);
            }

            return result;
        } else {
            // TODO : no test on preloaded data ?
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                if (currentSet == null || currentDocs == null || !(docs.equalDocs(currentDocs))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }
                return switch (axis) {
                    case Constants.PRECEDING_SIBLING_AXIS -> currentSet.selectPrecedingSiblings(contextSet, contextId);
                    case Constants.FOLLOWING_SIBLING_AXIS -> currentSet.selectFollowingSiblings(contextSet, contextId);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
            }
        }
    }

    /**
     * Get the preceding or following axis nodes
     *
     * @param context the xquery context
     * @param contextSequence the context sequence
     *
     * @return the nodes from the preceding or following axis
     *
     * @throws XPathException if an error occurs
     */
    /**
     * Evaluates an XQuery 4.0 combined axis (e.g., following-or-self, preceding-sibling-or-self).
     * Returns the union of the self axis result and the base axis result, preserving document order.
     *
     * @param context the XQuery context
     * @param contextSequence the context sequence
     * @param baseAxis the base axis constant (e.g., Constants.FOLLOWING_AXIS)
     * @return the combined result in document order
     */
    private Sequence getOrSelfAxis(final XQueryContext context, final Sequence contextSequence,
            final int baseAxis) throws XPathException {
        // Save and temporarily switch axis to get results
        final int savedAxis = this.axis;
        try {
            final Sequence selfOrRelatedResult;
            final Sequence baseResult;

            if (baseAxis == Constants.FOLLOWING_AXIS) {
                // following-or-self = descendant-or-self | following
                // (all nodes at or after context node in document order)
                this.axis = Constants.DESCENDANT_SELF_AXIS;
                selfOrRelatedResult = getDescendants(context, contextSequence);
                this.axis = Constants.FOLLOWING_AXIS;
                baseResult = getPrecedingOrFollowing(context, contextSequence);
            } else if (baseAxis == Constants.PRECEDING_AXIS) {
                // preceding-or-self = ancestor-or-self | preceding
                // (all nodes at or before context node in document order)
                this.axis = Constants.ANCESTOR_SELF_AXIS;
                selfOrRelatedResult = getAncestors(context, contextSequence);
                this.axis = Constants.PRECEDING_AXIS;
                baseResult = getPrecedingOrFollowing(context, contextSequence);
            } else {
                // following-sibling-or-self / preceding-sibling-or-self = self | sibling
                this.axis = Constants.SELF_AXIS;
                selfOrRelatedResult = getSelf(context, contextSequence);
                this.axis = baseAxis;
                baseResult = getSiblings(context, contextSequence);
            }

            // Union preserving document order
            if (selfOrRelatedResult.isEmpty()) {
                return baseResult;
            }
            if (baseResult.isEmpty()) {
                return selfOrRelatedResult;
            }
            final ValueSequence combined = new ValueSequence();
            combined.addAll(selfOrRelatedResult);
            combined.addAll(baseResult);
            combined.removeDuplicates();
            combined.sortInDocumentOrder();
            return combined;
        } finally {
            this.axis = savedAxis;
        }
    }

    private Sequence getPrecedingOrFollowing(final XQueryContext context, final Sequence contextSequence)
            throws XPathException {
        final int position = computeLimit();

        // process an in-memory node set
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            if (position > -1) {
                applyPredicate = false;
            }

            if (axis == Constants.PRECEDING_AXIS) {
                return nodes.getPreceding(test, position);
            } else {
                return nodes.getFollowing(test, position);
            }
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        // TODO : understand this. I guess comments should be treated in a
        // similar way ? -pb
        if (test.getType() == Type.PROCESSING_INSTRUCTION) {
            final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis, test, contextId, contextSet);
            vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
            return vset;
        }

        // handle node(), * etc.
        if (test.isWildcardTest()) {
            try {
                final NodeSet result = new NewArrayNodeSet();
                for (final NodeProxy next : contextSet) {
                    final NodeList cl = next.getOwnerDocument().getChildNodes();
                    for (int j = 0; j < cl.getLength(); j++) {
                        final NodeHandle node = (NodeHandle) cl.item(j);
                        final NodeProxy root = new NodeProxy(this, node);
                        final StreamFilter filter;
                        if (axis == Constants.PRECEDING_AXIS) {
                            filter = new PrecedingFilter(test, root, next, result, contextId);
                        } else {
                            filter = new FollowingFilter(test, root, next, result, contextId, position);
                        }
                        final IEmbeddedXMLStreamReader reader = context.getBroker().getXMLStreamReader(root, false);
                        reader.filter(filter);
                    }
                }
                return result;
            } catch (final XMLStreamException | IOException e) {
                throw new XPathException(this, e);
            }
        } else {
            // TODO : no test on preloaded data ?
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                if (currentSet == null || currentDocs == null || !(docs.equalDocs(currentDocs))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(this, Profiler.OPTIMIZATIONS, "OPTIMIZATION",
                                "Using structural index '" + index.toString() + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }

                if (position > -1) {
                    try {
                        applyPredicate = false;
                        if (axis == Constants.PRECEDING_AXIS) {
                            return currentSet.selectPreceding(contextSet, position, contextId);
                        } else {
                            return currentSet.selectFollowing(contextSet, position, contextId);
                        }
                    } catch (final UnsupportedOperationException e) {
                        if (axis == Constants.PRECEDING_AXIS) {
                            return currentSet.selectPreceding(contextSet, contextId);
                        } else {
                            return currentSet.selectFollowing(contextSet, contextId);
                        }
                    }
                } else {
                    if (axis == Constants.PRECEDING_AXIS) {
                        return currentSet.selectPreceding(contextSet, contextId);
                    } else {
                        return currentSet.selectFollowing(contextSet, contextId);
                    }
                }
            }
        }
    }

    /**
     * If the optimizer has determined that the first filter after this step is a simple positional
     * predicate and can be optimized, try to precompute the position and return it to limit the
     * number of items being processed by axes.
     *
     * @return max number of nodes to be processed until position is reached
     * @throws XPathException if a dynamic error occurs
     */
    private int computeLimit() throws XPathException {
        int position = -1;
        if (this.checkPositionalFilters(this.inPredicate)) {
            final Predicate pred = predicates[0];
            final Sequence seq = pred.preprocess();

            final NumericValue v = (NumericValue) seq.itemAt(0);
            // Non integers return... nothing, not even an error !
            if (!v.hasFractionalPart() && !v.isZero()) {
                position = v.getInt();
            }
        }
        return position;
    }

    /**
     * Get the ancestor axis nodes
     *
     * @param context the xquery context
     * @param contextSequence the context sequence
     *
     * @return the ancestor nodes
     *
     * @throws XPathException if an error occurs
     */
    protected Sequence getAncestors(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            return nodes.getAncestors(axis == Constants.ANCESTOR_SELF_AXIS, test);
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        if (test.isWildcardTest()) {
            final NodeSet result = new NewArrayNodeSet();
            result.setProcessInReverseOrder(true);
            for (final NodeProxy current : contextSet) {
                NodeProxy ancestor;
                if (axis == Constants.ANCESTOR_SELF_AXIS && test.matches(current)) {
                    ancestor = new NodeProxy(this, current);
                    ancestor.setNodeType(Node.ELEMENT_NODE);
                    final NodeProxy t = result.get(ancestor);
                    if (t == null) {
                        if (Expression.NO_CONTEXT_ID != contextId) {
                            ancestor.addContextNode(contextId, current);
                        } else {
                            ancestor.copyContext(current);
                        }
                        ancestor.addMatches(current);
                        result.add(ancestor);
                    } else {
                        t.addContextNode(contextId, current);
                        t.addMatches(current);
                    }
                }

                NodeId parentID = current.getNodeId().getParentId();
                while (parentID != null) {
                    ancestor = new NodeProxy(this, current.getOwnerDocument(), parentID, parentID == NodeId.DOCUMENT_NODE ? Node.DOCUMENT_NODE : Node.ELEMENT_NODE);
                    // Filter out the temporary nodes wrapper element
                    if (!(parentID.getTreeLevel() == 1 && current.getOwnerDocument().getCollection().isTempCollection())) {
                        if (test.matches(ancestor)) {
                            final NodeProxy t = result.get(ancestor);
                            if (t == null) {
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    ancestor.addContextNode(contextId, current);
                                } else {
                                    ancestor.copyContext(current);
                                }
                                ancestor.addMatches(current);
                                result.add(ancestor);
                            } else {
                                t.addContextNode(contextId, current);
                                t.addMatches(current);
                            }
                        }
                    }
                    parentID = parentID.getParentId();
                }
            }
            return result;
        } else if (hasPreloadedData()) {
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                if (currentSet == null || currentDocs == null || (!optimized && !(docs == currentDocs || docs.equalDocs(currentDocs)))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }
                return switch (axis) {
                    case Constants.ANCESTOR_SELF_AXIS -> currentSet.selectAncestors(contextSet, true, contextId);
                    case Constants.ANCESTOR_AXIS -> currentSet.selectAncestors(contextSet, false, contextId);
                    default -> throw new IllegalArgumentException("Unsupported axis specified");
                };
            }
        } else {
            final DocumentSet docs = getDocumentSet(contextSet);
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            return index.findAncestorsByTagName(ElementValue.ELEMENT, test.getName(), axis, docs, contextSet, contextId);
        }
    }

    /**
     * Get the parent axis nodes
     *
     * @param context the xquery context
     * @param contextSequence the context sequence
     *
     * @return the parent nodes
     *
     * @throws XPathException if an error occurs
     */
    protected Sequence getParents(final XQueryContext context, final Sequence contextSequence) throws XPathException {
        if (!contextSequence.isPersistentSet()) {
            final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
            return nodes.getParents(test);
        }

        final NodeSet contextSet = contextSequence.toNodeSet();
        if (test.isWildcardTest()) {
            final NodeSet temp = contextSet.getParents(contextId);
            final NodeSet result = new NewArrayNodeSet();
            for (final NodeProxy p : temp) {
                if (test.matches(p)) {
                    result.add(p);
                }
            }
            return result;
        } else if (hasPreloadedData()) {
            final DocumentSet docs = getDocumentSet(contextSet);
            synchronized (context) {
                if (currentSet == null || currentDocs == null || (!optimized && !(docs == currentDocs || docs.equalDocs(currentDocs)))) {
                    final StructuralIndex index = context.getBroker().getStructuralIndex();
                    if (context.getProfiler().isEnabled()) {
                        context.getProfiler().message(
                                this,
                                Profiler.OPTIMIZATIONS,
                                "OPTIMIZATION",
                                "Using structural index '" + index.toString()
                                        + "'");
                    }
                    currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
                    currentDocs = docs;
                    registerUpdateListener();
                }
                return contextSet.selectParentChild(currentSet, NodeSet.ANCESTOR);
            }
        } else {
            final DocumentSet docs = getDocumentSet(contextSet);
            final StructuralIndex index = context.getBroker().getStructuralIndex();
            if (context.getProfiler().isEnabled()) {
                context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
                        "OPTIMIZATION",
                        "Using structural index '" + index.toString() + "'");
            }
            return index.findAncestorsByTagName(ElementValue.ELEMENT, test.getName(), Constants.PARENT_AXIS, docs, contextSet, contextId);
        }
    }

    /**
     * Get the document set
     *
     * @param contextSet the context set
     * @return the document set
     */
    protected DocumentSet getDocumentSet(final NodeSet contextSet) {
        DocumentSet ds = getContextDocSet();
        if (ds == null) {
            ds = contextSet.getDocumentSet();
        }
        return ds;
    }

    /**
     * Get the parent expression
     *
     * @return the parent expression
     */
    public Expression getParentExpression() {
        return this.parent;
    }

    /**
     * Register the update listener
     */
    protected void registerUpdateListener() {
        if (listener == null) {
            listener = new UpdateListener() {
                @Override
                public void documentUpdated(final DocumentImpl document, final int event) {
                    if (document == null || event == UpdateListener.ADD || event == UpdateListener.REMOVE) {
                        // clear all
                        currentDocs = null;
                        currentSet = null;
                    } else {
                        if (currentDocs != null && currentDocs.contains(document.getDocId())) {
                            currentDocs = null;
                            currentSet = null;
                        }
                    }
                }

                @Override
                public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
                    //no-op
                }

                @Override
                public void unsubscribe() {
                    LocationStep.this.listener = null;
                }

                @Override
                public void debug() {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("UpdateListener: Line: {}; id: {}", LocationStep.this.toString(), LocationStep.this.getExpressionId());
                    }
                }
            };
            context.registerUpdateListener(listener);
        }
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitLocationStep(this);
    }

    /**
     * Set the parent expression
     *
     * @param parent the parent expression
     */
    public void setParent(final Expression parent) {
        this.parent = parent;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        if (!postOptimization) {
            // TODO : preloadedData = false ?
            // No : introduces a regression in testMatchCount
            // TODO : Investigate...
            currentSet = null;
            currentDocs = null;
            optimized = false;
            listener = null;
        }
    }

    private abstract class AbstractFilterBase implements StreamFilter {
        final NodeTest test;
        final NodeSet result;
        final int limit;
        int nodesRead = 0;
        final int contextId;

        AbstractFilterBase(final NodeTest test, final NodeSet result, final int contextId, final int limit) {
            this.test = test;
            this.result = result;
            this.contextId = contextId;
            this.limit = limit;
            if (limit > -1 && context.getProfiler().traceFunctions()) {
                context.getProfiler().traceOptimization(context, PerformanceStats.OptimizationType.POSITIONAL_PREDICATE,
                        LocationStep.this);
            }
        }
    }

    private class FollowingSiblingFilter extends AbstractFilterBase {
        final NodeProxy start;
        final int level;
        boolean sibling = false;

        FollowingSiblingFilter(final NodeTest test, final NodeProxy start, final NodeSet result,
                final int contextId, final int limit) {
            super(test, result, contextId, limit);
            this.start = start;
            this.level = start.getNodeId().getTreeLevel();
        }

        @Override
        public boolean accept(final XMLStreamReader reader) {
            final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
            final int currentLevel = currentId.getTreeLevel();

            if (!sibling) {
                // skip over the start node to the first sibling
                sibling = currentId.equals(start.getNodeId());

            } else if (currentLevel == level && !reader.isEndElement() && test.matches(reader)) {
                // sibling which matches the test
                NodeProxy sibling = result.get(start.getOwnerDocument(), currentId);
                if (sibling == null) {
                    sibling = new NodeProxy(null, start.getOwnerDocument(), currentId,
                            StaXUtil.streamType2DOM(reader.getEventType()), ((EmbeddedXMLStreamReader) reader).getCurrentPosition());

                    if (Expression.IGNORE_CONTEXT != contextId) {
                        if (Expression.NO_CONTEXT_ID == contextId) {
                            sibling.copyContext(start);
                        } else {
                            sibling.addContextNode(contextId, start);
                        }
                    }
                    result.add(sibling);
                } else if (Expression.NO_CONTEXT_ID != contextId) {
                    sibling.addContextNode(contextId, start);
                }
                nodesRead++;
                if (this.limit > -1 && nodesRead == this.limit) {
                    return false;
                }

            } else if (currentLevel < level) {
                // exited the parent node, so stop filtering
                return false;
            }

            return true;
        }
    }

    private class PrecedingSiblingFilter extends AbstractFilterBase {
        final int level;
        final NodeProxy referenceNode;

        PrecedingSiblingFilter(final NodeTest test, final NodeProxy start, final NodeProxy referenceNode,
                final NodeSet result, final int contextId) {
            super(test, result, contextId, -1);
            this.level = start.getNodeId().getTreeLevel();
            this.referenceNode = referenceNode;
        }

        @Override
        public boolean accept(final XMLStreamReader reader) {
            final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);

            final NodeId refId = referenceNode.getNodeId();
            if (currentId.equals(refId)) {
                // reached the reference node
                return false;
            }

            if (reader.isEndElement()) {
                return true;
            }
            if (currentId.getTreeLevel() == level && test.matches(reader)) {
                // sibling which matches the test
                NodeProxy sibling = result.get(referenceNode.getOwnerDocument(), currentId);
                if (sibling == null) {
                    sibling = new NodeProxy(null, referenceNode.getOwnerDocument(), currentId,
                            StaXUtil.streamType2DOM(reader.getEventType()), ((EmbeddedXMLStreamReader) reader).getCurrentPosition());
                    if (Expression.IGNORE_CONTEXT != contextId) {
                        if (Expression.NO_CONTEXT_ID == contextId) {
                            sibling.copyContext(referenceNode);
                        } else {
                            sibling.addContextNode(contextId, referenceNode);
                        }
                    }
                    result.add(sibling);
                } else if (Expression.NO_CONTEXT_ID != contextId) {
                    sibling.addContextNode(contextId, referenceNode);
                }
            }

            return true;
        }
    }

    private class FollowingFilter extends AbstractFilterBase {
        final NodeProxy root;
        final NodeProxy referenceNode;
        boolean isAfter = false;

        FollowingFilter(final NodeTest test, final NodeProxy root, final NodeProxy referenceNode, final NodeSet result,
                final int contextId, final int limit) {
            super(test, result, contextId, limit);
            this.root = root;
            this.referenceNode = referenceNode;
        }

        @Override
        public boolean accept(final XMLStreamReader reader) {
            final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
            if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                if (currentId.getTreeLevel() == root.getNodeId().getTreeLevel()) {
                    // exited the root element, so  stop filtering
                    return false;
                }

                return true;
            }

            final NodeId refId = referenceNode.getNodeId();

            if (!isAfter) {
                isAfter = currentId.compareTo(refId) > 0 && !currentId.isDescendantOf(refId);
            }

            if (isAfter && !refId.isDescendantOf(currentId) && test.matches(reader)) {
                final NodeProxy proxy = new NodeProxy(null, referenceNode.getOwnerDocument(), currentId,
                        StaXUtil.streamType2DOM(reader.getEventType()), ((EmbeddedXMLStreamReader) reader).getCurrentPosition());
                if (Expression.IGNORE_CONTEXT != contextId) {
                    if (Expression.NO_CONTEXT_ID == contextId) {
                        proxy.copyContext(referenceNode);
                    } else {
                        proxy.addContextNode(contextId, referenceNode);
                    }
                }
                result.add(proxy);
                nodesRead++;
                if (this.limit > -1 && nodesRead == this.limit) {
                    return false;
                }
            }
            return true;
        }
    }

    private class PrecedingFilter extends AbstractFilterBase {
        final NodeProxy root;
        final NodeProxy referenceNode;

        PrecedingFilter(final NodeTest test, final NodeProxy root, final NodeProxy referenceNode, final NodeSet result,
                final int contextId) {
            super(test, result, contextId, -1);
            this.root = root;
            this.referenceNode = referenceNode;
        }

        @Override
        public boolean accept(final XMLStreamReader reader) {
            final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);

            if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
                // exited the root element, so  stop filtering
                return currentId.getTreeLevel() != root.getNodeId().getTreeLevel();
            }

            final NodeId refId = referenceNode.getNodeId();
            if (currentId.compareTo(refId) >= 0) {
                return false;
            }

            if (!refId.isDescendantOf(currentId) && test.matches(reader)) {
                final NodeProxy proxy = new NodeProxy(null, referenceNode.getOwnerDocument(), currentId,
                        StaXUtil.streamType2DOM(reader.getEventType()), ((EmbeddedXMLStreamReader) reader).getCurrentPosition());
                if (Expression.IGNORE_CONTEXT != contextId) {
                    if (Expression.NO_CONTEXT_ID == contextId) {
                        proxy.copyContext(referenceNode);
                    } else {
                        proxy.addContextNode(contextId, referenceNode);
                    }
                }
                result.add(proxy);
            }
            return true;
        }
    }

    // === XQuery 4.0 Map/Array path navigation ===

    /**
     * Evaluate axis navigation when the context contains maps or arrays.
     * Implements XQuery 4.0 path navigation on maps/arrays:
     *   $map/key     → map lookup by key name
     *   $map/*       → all map values
     *   $array/*     → all array members
     *   $map//key    → recursive descent lookup
     */
    private Sequence evalMapArrayAxis(final Sequence contextSequence) throws XPathException {
        final ValueSequence result = new ValueSequence();
        final org.exist.dom.QName stepName = test != null ? test.getName() : null;
        final boolean isWildcard = stepName == null; // TypeTest like node() or * → wildcard

        for (final SequenceIterator it = contextSequence.iterate(); it.hasNext(); ) {
            final Item item = it.nextItem();
            switch (axis) {
                case Constants.CHILD_AXIS:
                    navigateChildren(item, stepName, isWildcard, result);
                    break;
                case Constants.DESCENDANT_AXIS:
                    navigateDescendants(item, stepName, isWildcard, false, result);
                    break;
                case Constants.DESCENDANT_SELF_AXIS:
                    navigateDescendants(item, stepName, isWildcard, true, result);
                    break;
                case Constants.SELF_AXIS:
                    // Self on map/array returns the item itself
                    result.add(item);
                    break;
                default:
                    // Other axes (parent, ancestor, sibling) don't apply to raw maps/arrays
                    break;
            }
        }
        return result;
    }

    /**
     * Navigate children of a map or array item.
     * For maps: lookup by name or return all values.
     * For arrays: return all members.
     */
    private void navigateChildren(final Item item, @Nullable final org.exist.dom.QName stepName,
                                   final boolean isWildcard,
                                   final ValueSequence result) throws XPathException {
        if (item instanceof org.exist.xquery.functions.map.MapType) {
            final org.exist.xquery.functions.map.MapType map =
                    (org.exist.xquery.functions.map.MapType) item;
            if (isWildcard) {
                // Return all map values
                for (final io.lacuna.bifurcan.IEntry<AtomicValue, Sequence> entry : map) {
                    addSequenceItems(entry.value(), result);
                }
            } else {
                // Lookup by name
                final String key = stepName.getLocalPart();
                final Sequence value = map.get(new StringValue(this, key));
                if (value != null && !value.isEmpty()) {
                    addSequenceItems(value, result);
                }
            }
        } else if (item instanceof org.exist.xquery.functions.array.ArrayType) {
            final org.exist.xquery.functions.array.ArrayType array =
                    (org.exist.xquery.functions.array.ArrayType) item;
            // Arrays: wildcard returns all members, named step returns nothing
            if (isWildcard) {
                for (int i = 0; i < array.getSize(); i++) {
                    addSequenceItems(array.get(i), result);
                }
            }
            // Named steps on arrays: navigate into each member that is a map
            if (!isWildcard) {
                for (int i = 0; i < array.getSize(); i++) {
                    final Sequence member = array.get(i);
                    if (member.getItemCount() == 1 &&
                            member.itemAt(0) instanceof org.exist.xquery.functions.map.MapType) {
                        navigateChildren(member.itemAt(0), stepName, false, result);
                    }
                }
            }
        }
    }

    /**
     * Recursive descent into maps/arrays for descendant axis.
     */
    private void navigateDescendants(final Item item, @Nullable final org.exist.dom.QName stepName,
                                      final boolean isWildcard, final boolean includeSelf,
                                      final ValueSequence result) throws XPathException {
        if (includeSelf) {
            result.add(item);
        }

        if (item instanceof org.exist.xquery.functions.map.MapType) {
            final org.exist.xquery.functions.map.MapType map =
                    (org.exist.xquery.functions.map.MapType) item;
            for (final io.lacuna.bifurcan.IEntry<AtomicValue, Sequence> entry : map) {
                final String key = entry.key().getStringValue();
                // If name matches, add the value
                if (!isWildcard && stepName != null && key.equals(stepName.getLocalPart())) {
                    addSequenceItems(entry.value(), result);
                } else if (isWildcard) {
                    addSequenceItems(entry.value(), result);
                }
                // Recurse into nested maps/arrays
                final Sequence value = entry.value();
                for (int i = 0; i < value.getItemCount(); i++) {
                    final Item child = value.itemAt(i);
                    if (child instanceof org.exist.xquery.functions.map.MapType
                            || child instanceof org.exist.xquery.functions.array.ArrayType) {
                        navigateDescendants(child, stepName, isWildcard, false, result);
                    }
                }
            }
        } else if (item instanceof org.exist.xquery.functions.array.ArrayType) {
            final org.exist.xquery.functions.array.ArrayType array =
                    (org.exist.xquery.functions.array.ArrayType) item;
            for (int i = 0; i < array.getSize(); i++) {
                final Sequence member = array.get(i);
                for (int j = 0; j < member.getItemCount(); j++) {
                    final Item child = member.itemAt(j);
                    if (isWildcard) {
                        result.add(child);
                    }
                    if (child instanceof org.exist.xquery.functions.map.MapType
                            || child instanceof org.exist.xquery.functions.array.ArrayType) {
                        navigateDescendants(child, stepName, isWildcard, false, result);
                    }
                }
            }
        }
    }

    /**
     * Add all items from a sequence to the result.
     */
    private void addSequenceItems(final Sequence seq, final ValueSequence result) throws XPathException {
        for (int i = 0; i < seq.getItemCount(); i++) {
            result.add(seq.itemAt(i));
        }
    }

    // === XQuery 4.0 JNode axis navigation ===

    /**
     * Evaluate axis navigation when the context contains JNodes.
     * Handles child, parent, self, descendant, descendant-or-self,
     * ancestor, ancestor-or-self, following-sibling, and preceding-sibling axes.
     */
    private Sequence evalJNodeAxis(final Sequence contextSequence) throws XPathException {
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator it = contextSequence.iterate(); it.hasNext(); ) {
            final Item item = it.nextItem();
            if (!(item instanceof org.exist.xquery.value.jnode.JNode)) {
                continue;
            }
            final org.exist.xquery.value.jnode.JNode jnode = (org.exist.xquery.value.jnode.JNode) item;

            switch (axis) {
                case Constants.CHILD_AXIS:
                    for (final org.exist.xquery.value.jnode.JNode child : jnode.getChildren()) {
                        if (matchesJNode(child)) {
                            result.add(child);
                        }
                    }
                    break;

                case Constants.PARENT_AXIS:
                    if (jnode.getParent() != null && matchesJNode(jnode.getParent())) {
                        result.add(jnode.getParent());
                    }
                    break;

                case Constants.SELF_AXIS:
                    if (matchesJNode(jnode)) {
                        result.add(jnode);
                    }
                    break;

                case Constants.DESCENDANT_AXIS:
                    addDescendants(jnode, result, false);
                    break;

                case Constants.DESCENDANT_SELF_AXIS:
                    addDescendants(jnode, result, true);
                    break;

                case Constants.ANCESTOR_AXIS:
                    addAncestors(jnode, result, false);
                    break;

                case Constants.ANCESTOR_SELF_AXIS:
                    addAncestors(jnode, result, true);
                    break;

                case Constants.FOLLOWING_SIBLING_AXIS:
                    for (final org.exist.xquery.value.jnode.JNode sibling : jnode.getFollowingSiblings()) {
                        if (matchesJNode(sibling)) {
                            result.add(sibling);
                        }
                    }
                    break;

                case Constants.PRECEDING_SIBLING_AXIS:
                    for (final org.exist.xquery.value.jnode.JNode sibling : jnode.getPrecedingSiblings()) {
                        if (matchesJNode(sibling)) {
                            result.add(sibling);
                        }
                    }
                    break;

                case Constants.FOLLOWING_AXIS:
                    for (final org.exist.xquery.value.jnode.JNode following : jnode.getFollowing()) {
                        if (matchesJNode(following)) {
                            result.add(following);
                        }
                    }
                    break;

                case Constants.PRECEDING_AXIS:
                    for (final org.exist.xquery.value.jnode.JNode preceding : jnode.getPreceding()) {
                        if (matchesJNode(preceding)) {
                            result.add(preceding);
                        }
                    }
                    break;

                // XQuery 4.0 *-or-self axis variants
                case Constants.FOLLOWING_OR_SELF_AXIS:
                    if (matchesJNode(jnode)) {
                        result.add(jnode);
                    }
                    for (final org.exist.xquery.value.jnode.JNode following : jnode.getFollowing()) {
                        if (matchesJNode(following)) {
                            result.add(following);
                        }
                    }
                    break;

                case Constants.PRECEDING_OR_SELF_AXIS:
                    if (matchesJNode(jnode)) {
                        result.add(jnode);
                    }
                    for (final org.exist.xquery.value.jnode.JNode preceding : jnode.getPreceding()) {
                        if (matchesJNode(preceding)) {
                            result.add(preceding);
                        }
                    }
                    break;

                case Constants.FOLLOWING_SIBLING_OR_SELF_AXIS:
                    if (matchesJNode(jnode)) {
                        result.add(jnode);
                    }
                    for (final org.exist.xquery.value.jnode.JNode sibling : jnode.getFollowingSiblings()) {
                        if (matchesJNode(sibling)) {
                            result.add(sibling);
                        }
                    }
                    break;

                case Constants.PRECEDING_SIBLING_OR_SELF_AXIS:
                    if (matchesJNode(jnode)) {
                        result.add(jnode);
                    }
                    for (final org.exist.xquery.value.jnode.JNode sibling : jnode.getPrecedingSiblings()) {
                        if (matchesJNode(sibling)) {
                            result.add(sibling);
                        }
                    }
                    break;

                default:
                    throw new XPathException(this, ErrorCodes.XPTY0019,
                            "Axis " + axis + " is not supported for JNodes");
            }
        }

        // Apply predicates
        if (result.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        Sequence filtered = result;
        final Predicate[] predicates = getPredicates();
        if (predicates != null) {
            for (final Predicate pred : predicates) {
                filtered = pred.evalPredicate(contextSequence, filtered, axis);
            }
        }
        return filtered;
    }

    /**
     * Check if a JNode matches this step's node test.
     */
    private boolean matchesJNode(final org.exist.xquery.value.jnode.JNode jnode) {
        if (test == null) {
            return true;
        }
        // AnyNodeTest — matches any JNode
        if (test instanceof AnyNodeTest) {
            return true;
        }
        // NameTest MUST be checked before TypeTest (NameTest extends TypeTest)
        if (test instanceof NameTest) {
            final org.exist.dom.QName name = test.getName();
            if (name != null) {
                final String local = name.getLocalPart();
                // Wildcard * matches any JNode
                if ("*".equals(local) || local == null) {
                    return true;
                }
                // Named test: match against JNode key
                try {
                    final AtomicValue jnodeKey = jnode.getKey();
                    if (jnodeKey != null) {
                        return local.equals(jnodeKey.getStringValue());
                    }
                } catch (final XPathException e) {
                    return false;
                }
            }
            return false;
        }
        if (test instanceof TypeTest) {
            final int testType = ((TypeTest) test).getType();
            // TypeTest with NODE or ELEMENT means any node — wildcard for JNodes
            // (child::* produces TypeTest(ELEMENT) which should match JNodes)
            if (testType == Type.NODE || testType == Type.JSON_NODE
                    || testType == Type.ELEMENT) {
                return true;
            }
            return Type.subTypeOf(jnode.getType(), testType);
        }
        // AnyNodeTest — matches any JNode
        if (test instanceof AnyNodeTest) {
            return true;
        }
        // NameTest — check if wildcard (*) or named key
        if (test instanceof NameTest) {
            final org.exist.dom.QName name = test.getName();
            if (name != null) {
                final String local = name.getLocalPart();
                // Wildcard * matches any JNode
                if ("*".equals(local) || local == null) {
                    return true;
                }
                // Named test: match against JNode key
                try {
                    final AtomicValue jnodeKey = jnode.getKey();
                    if (jnodeKey != null) {
                        return local.equals(jnodeKey.getStringValue());
                    }
                } catch (final XPathException e) {
                    return false;
                }
            }
            return false;
        }
        return false;
    }

    private void addDescendants(final org.exist.xquery.value.jnode.JNode jnode,
                                final ValueSequence result, final boolean includeSelf) throws XPathException {
        if (includeSelf && matchesJNode(jnode)) {
            result.add(jnode);
        }
        for (final org.exist.xquery.value.jnode.JNode child : jnode.getChildren()) {
            if (matchesJNode(child)) {
                result.add(child);
            }
            addDescendants(child, result, false);
        }
    }

    private void addAncestors(final org.exist.xquery.value.jnode.JNode jnode,
                              final ValueSequence result, final boolean includeSelf) {
        if (includeSelf && matchesJNode(jnode)) {
            result.add(jnode);
        }
        org.exist.xquery.value.jnode.JNode parent = jnode.getParent();
        while (parent != null) {
            if (matchesJNode(parent)) {
                result.add(parent);
            }
            parent = parent.getParent();
        }
    }

}
