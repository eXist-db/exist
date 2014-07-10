/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.*;
import org.exist.indexing.StructuralIndex;
import org.exist.memtree.InMemoryNodeSet;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.StaXUtil;
import org.exist.storage.ElementValue;
import org.exist.storage.UpdateListener;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Iterator;

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

	private static final int ATTR_DIRECT_SELECT_THRESHOLD = 10;

    private static final int INDEX_SCAN_THRESHOLD = 10000;

	protected NodeSet currentSet = null;

	protected DocumentSet currentDocs = null;

	protected UpdateListener listener = null;

	protected Expression parent = null;

	// Fields for caching the last result
	protected CachedResult cached = null;

	protected int parentDeps = Dependency.UNKNOWN_DEPENDENCY;

	protected boolean preloadedData = false;

	protected boolean optimized = false;

	protected boolean inUpdate = false;

	protected boolean useDirectChildSelect = false;

	protected boolean applyPredicate = true;

	// Cache for the current NodeTest type
	private Integer nodeTestType = null;

	/**
	 * Creates a new <code>LocationStep</code> instance.
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param axis
	 *            an <code>int</code> value
	 */
	public LocationStep(XQueryContext context, int axis) {
		super(context, axis);
	}

	/**
	 * Creates a new <code>LocationStep</code> instance.
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param axis
	 *            an <code>int</code> value
	 * @param test
	 *            a <code>NodeTest</code> value
	 */
	public LocationStep(XQueryContext context, int axis, NodeTest test) {
		super(context, axis, test);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;

		// self axis has an obvious dependency on the context item
		// TODO : I guess every other axis too... so we might consider using
		// Constants.UNKNOWN_AXIS here
		// BUT
		// in a predicate, the expression can't depend on... itself
		if (!this.inPredicate && this.axis == Constants.SELF_AXIS)
			{deps = deps | Dependency.CONTEXT_ITEM;}

		// TODO : normally, we should call this one...
		// int deps = super.getDependencies(); ???
		for (final Predicate pred : predicates) {
			deps |= pred.getDependencies();
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
	protected boolean hasPreloadedData() {
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
	 * @param docs
	 *            a <code>DocumentSet</code> value
	 * @param nodes
	 *            a <code>NodeSet</code> value
	 */
	public void setPreloadedData(DocumentSet docs, NodeSet nodes) {
		this.preloadedData = true;
		this.currentDocs = docs;
		this.currentSet = nodes;
		this.optimized = true;
	}

	/**
	 * The method <code>applyPredicate</code>
	 * 
	 * @param outerSequence
	 *            a <code>Sequence</code> value
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @return a <code>Sequence</code> value
	 * @exception XPathException
	 *                if an error occurs
	 */
	protected Sequence applyPredicate(Sequence outerSequence,
			Sequence contextSequence) throws XPathException {
		if (contextSequence == null)
			{return Sequence.EMPTY_SEQUENCE;}
		if (predicates.size() == 0
				|| !applyPredicate
				|| (!(contextSequence instanceof VirtualNodeSet) && contextSequence
						.isEmpty()))
			// Nothing to apply
			{return contextSequence;}
		Sequence result;
		final Predicate pred = (Predicate) predicates.get(0);
		// If the current step is an // abbreviated step, we have to treat the
		// predicate
		// specially to get the context position right. //a[1] translates to
		// /descendant-or-self::node()/a[1],
		// so we need to return the 1st a from any parent of a.
		//
		// If the predicate is known to return a node set, no special treatment
		// is required.
		if (abbreviatedStep
				&& (pred.getExecutionMode() != Predicate.NODE || !contextSequence
						.isPersistentSet())) {
			result = new ValueSequence();
			((ValueSequence)result).keepUnOrdered(unordered);
			if (contextSequence.isPersistentSet()) {
				final NodeSet contextSet = contextSequence.toNodeSet();
				outerSequence = contextSet.getParents(-1);
				for (final SequenceIterator i = outerSequence.iterate(); i.hasNext();) {
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
				for (final SequenceIterator i = outerSequence.iterate(); i.hasNext();) {
					final NodeValue node = (NodeValue) i.nextItem();
					final InMemoryNodeSet newSet = new InMemoryNodeSet();
					((NodeImpl) node).selectChildren(test, newSet);
					final Sequence temp = processPredicate(outerSequence, newSet);
					result.addAll(temp);
				}
			}
		} else
			{result = processPredicate(outerSequence, contextSequence);}
		return result;
	}

	private Sequence processPredicate(Sequence outerSequence,
			Sequence contextSequence) throws XPathException {
		Predicate pred;
		Sequence result = contextSequence;
		for (final Iterator<Predicate> i = predicates.iterator(); i.hasNext()
				&& (result instanceof VirtualNodeSet || !result.isEmpty());) {
			// TODO : log and/or profile ?
			pred = i.next();
			pred.setContextDocSet(getContextDocSet());
			result = pred.evalPredicate(outerSequence, result, axis);
			// subsequent predicates operate on the result of the previous one
			outerSequence = null;
            context.setContextSequencePosition(-1, null);
		}
		return result;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Step#analyze(org.exist.xquery.Expression)
	 */
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		this.parent = contextInfo.getParent();

        unordered = (contextInfo.getFlags() & UNORDERED) > 0;

		parentDeps = parent.getDependencies();
		if ((contextInfo.getFlags() & IN_UPDATE) > 0)
			{inUpdate = true;}
//		if ((contextInfo.getFlags() & SINGLE_STEP_EXECUTION) > 0) {
//			preloadedData = true;
//		}
		if ((contextInfo.getFlags() & USE_TREE_TRAVERSAL) > 0) {
			useDirectChildSelect = true;
		}
		// Mark ".", which is expanded as self::node() by the parser
		// even though it may *also* be relevant with atomic sequences
		if (this.axis == Constants.SELF_AXIS
				&& this.test.getType() == Type.NODE)
			{contextInfo.addFlag(DOT_TEST);}
		
		//Change axis from descendant-or-self to descendant for '//'
		if (this.axis == Constants.DESCENDANT_SELF_AXIS && isAbbreviated()) {
			this.axis = Constants.DESCENDANT_AXIS;
		}

		// static analysis for empty-sequence
		Expression contextStep;
		switch (axis) {
		case Constants.SELF_AXIS:
			if (getTest().getType() != Type.NODE) {
				
				contextStep = contextInfo.getContextStep();
				if (contextStep instanceof LocationStep) {
					final LocationStep cStep = (LocationStep) contextStep;

                    // WM: the following checks will only work on simple filters like //a[self::b], so we
                    // have to make sure they are not applied to more complex expression types
					if (parent.getSubExpressionCount() == 1 && !Type.subTypeOf(getTest().getType(), cStep.getTest().getType()))
						{throw new XPathException(this, 
								ErrorCodes.XPST0005, "Got nothing from self::"+getTest()+", because parent node kind "+Type.getTypeName(cStep.getTest().getType()));}
					
					if (parent.getSubExpressionCount() == 1 && !(cStep.getTest().isWildcardTest() || getTest().isWildcardTest()) && !cStep.getTest().equals(getTest()))
						{throw new XPathException(this,
								ErrorCodes.XPST0005, "Self::"+getTest()+" called on set of nodes which do not contain any nodes of this name.");}
				}
			}
			break;
//		case Constants.DESCENDANT_AXIS:
		case Constants.DESCENDANT_SELF_AXIS:
			contextStep = contextInfo.getContextStep();
			if (contextStep instanceof LocationStep) {
				final LocationStep cStep = (LocationStep) contextStep;
				
				if ((
						cStep.getTest().getType() == Type.ATTRIBUTE || 
						cStep.getTest().getType() == Type.TEXT
					)
						&& cStep.getTest() != getTest())
					{throw new XPathException(this, 
							ErrorCodes.XPST0005, "Descendant-or-self::"+getTest()+" from an attribute gets nothing.");}
			}
			break;
//		case Constants.PARENT_AXIS:
//		case Constants.ATTRIBUTE_AXIS:
		default:
			;
		}

		// TODO : log somewhere ?
		super.analyze(contextInfo);
	}

	/**
	 * The method <code>eval</code>
	 * 
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @param contextItem
	 *            an <code>Item</code> value
	 * @return a <code>Sequence</code> value
	 * @exception XPathException
	 *                if an error occurs
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES,
					"DEPENDENCIES",
					Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				{context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT SEQUENCE", contextSequence);}
			if (contextItem != null)
				{context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT ITEM", contextItem.toSequence());}
		}

		Sequence result;
		if (contextItem != null) {
			contextSequence = contextItem.toSequence();
		}
		/*
		 * if(contextSequence == null) //Commented because this the high level
		 * result nodeset is *really* null result = NodeSet.EMPTY_SET; //Try to
		 * return cached results else
		 */
		// TODO: disabled cache for now as it may cause concurrency issues
		// better use compile-time inspection and maybe a pragma to mark those
		// sections in the query that can be safely cached
		// if (cached != null && cached.isValid(contextSequence, contextItem)) {
		//
		// // WARNING : commented since predicates are *also* applied below !
		// // -pb
		// /*
		// * if (predicates.size() > 0) { applyPredicate(contextSequence,
		// * cached.getResult()); } else {
		// */
		// result = cached.getResult();
		// if (context.getProfiler().isEnabled()) {
		// LOG.debug("Using cached results");
		// }
		// context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
		// "Using cached results", result);
		//
		// // }
		if (needsComputation()) {
			if (contextSequence == null)
				{throw new XPathException(this,
						ErrorCodes.XPDY0002, "Undefined context sequence for '"
								+ this.toString() + "'");}
			switch (axis) {
			case Constants.DESCENDANT_AXIS:
			case Constants.DESCENDANT_SELF_AXIS:
				result = getDescendants(context, contextSequence);
				break;
			case Constants.CHILD_AXIS:
				// VirtualNodeSets may have modified the axis ; checking the
				// type
				// TODO : further checks ?
				if (this.test.getType() == Type.ATTRIBUTE) {
					this.axis = Constants.ATTRIBUTE_AXIS;
					result = getAttributes(context, contextSequence);
				} else {
					result = getChildren(context, contextSequence);
				}
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
								Type.ATOMIC)) {
					// This test is copied from the legacy method
					// getSelfAtomic()
					if (!test.isWildcardTest())
						{throw new XPathException(this, test.toString()
								+ " cannot be applied to an atomic value.");}
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
				result = getPreceding(context, contextSequence);
				break;
			case Constants.FOLLOWING_AXIS:
				result = getFollowing(context, contextSequence);
				break;
			case Constants.PRECEDING_SIBLING_AXIS:
			case Constants.FOLLOWING_SIBLING_AXIS:
				result = getSiblings(context, contextSequence);
				break;
			default:
				throw new IllegalArgumentException("Unsupported axis specified");
			}
		} else {
			result = NodeSet.EMPTY_SET;
		}
		// Caches the result
		if (axis != Constants.SELF_AXIS && contextSequence != null
				&& contextSequence.isCacheable()) {
			// TODO : cache *after* removing duplicates ? -pb
			cached = new CachedResult(contextSequence, contextItem, result);
			registerUpdateListener();
		}
		// Remove duplicate nodes
		result.removeDuplicates();
		// Apply the predicate
		result = applyPredicate(contextSequence, result);

		if (context.getProfiler().isEnabled())
			{context.getProfiler().end(this, "", result);}
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
			if (nodeTestType == null)
				{nodeTestType = Integer.valueOf(test.getType());}
			if (nodeTestType.intValue() != Type.NODE
					&& nodeTestType.intValue() != Type.ELEMENT
					&& nodeTestType.intValue() != Type.PROCESSING_INSTRUCTION) {
				if (context.getProfiler().isEnabled())
					{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
							"OPTIMIZATION", "avoid useless computations");}
				return false;
			}

		}
		return true;
	}

	/**
	 * The method <code>getSelf</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>NodeSet</code> value
	 * @return a <code>Sequence</code> value
	 */
	protected Sequence getSelf(XQueryContext context, Sequence contextSequence)
			throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.getSelf(test);
		}
		if (hasPreloadedData()) {
			NodeSet ns = null;
			if (contextSequence instanceof NodeSet) {
				ns = (NodeSet)contextSequence;
			}
			NodeProxy np = null;

			for (final Iterator<NodeProxy> i = currentSet.iterator(); i.hasNext(); ) {
				final NodeProxy p = i.next();
				p.addContextNode(contextId, p);
				
				if (ns != null) {
					np = ns.get(p);
					
					if (np != null && np.getMatches() != null)
						{p.addMatch( np.getMatches() );}
				}
			}
			return currentSet;
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (test.getType() == Type.PROCESSING_INSTRUCTION) {
			final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return vset;
		}

		if (test.isWildcardTest()) {
			if (nodeTestType == null) {
				nodeTestType = Integer.valueOf(test.getType());
			}
			if (Type.subTypeOf(nodeTestType.intValue(), Type.NODE)) {
				if (Expression.NO_CONTEXT_ID != contextId) {
					if (contextSet instanceof VirtualNodeSet) {
						((VirtualNodeSet) contextSet).setInPredicate(true);
						((VirtualNodeSet) contextSet).setContextId(contextId);
						((VirtualNodeSet) contextSet).setSelfIsContext();
					} else if (Type.subTypeOf(contextSet.getItemType(),
							Type.NODE)) {
						NodeProxy p;
						for (final Iterator<NodeProxy> i = contextSet.iterator(); i.hasNext();) {
							p = i.next();
							if (test.matches(p))
								{p.addContextNode(contextId, p);}
						}
					}
				}
				return contextSet;
			} else {
				final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(),
						axis, test, contextId, contextSet);
				vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
				return vset;
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			final NodeSelector selector = new SelfSelector(contextSet, contextId);
			return index.findElementsByTagName(ElementValue.ELEMENT, docs, test
					.getName(), selector, this);
		}
	}

	/**
	 * The method <code>getAttributes</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>NodeSet</code> value
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getAttributes(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)
				{return nodes.getDescendantAttributes(test);}
			else
				{return nodes.getAttributes(test);}
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (!hasPreloadedData() && test.isWildcardTest()) {
			final NodeSet result = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			((VirtualNodeSet) result)
					.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return result;
			// if there's just a single known node in the context, it is faster
			// do directly search for the attribute in the parent node.
		}
		if (hasPreloadedData()) {
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					// TODO : why a null selector here ? We have one below !
					currentSet = index.findElementsByTagName(ElementValue.ATTRIBUTE, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				switch (axis) {
				case Constants.ATTRIBUTE_AXIS:
					return currentSet.selectParentChild(contextSet,
							NodeSet.DESCENDANT, contextId);
				case Constants.DESCENDANT_ATTRIBUTE_AXIS:
					return currentSet.selectAncestorDescendant(contextSet,
							NodeSet.DESCENDANT, false, contextId, true);
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			if (!contextSet.getProcessInReverseOrder()) {
				return index.findDescendantsByTagName(ElementValue.ATTRIBUTE,
						test.getName(), axis, docs, contextSet,
						contextId, this);
			} else {
				NodeSelector selector;
				switch (axis) {
				case Constants.ATTRIBUTE_AXIS:
					selector = new ChildSelector(contextSet, contextId);
					break;
				case Constants.DESCENDANT_ATTRIBUTE_AXIS:
					selector = new DescendantSelector(contextSet, contextId);
					break;
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
				return index.findElementsByTagName(ElementValue.ATTRIBUTE, docs, test.getName(), selector, this);
			}
		}
	}

	/**
	 * The method <code>getChildren</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            the context sequence
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getChildren(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.getChildren(test);
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		// TODO : understand this. I guess comments should be treated in a
		// similar way ? -pb
		if ((!hasPreloadedData() && test.isWildcardTest())
				|| test.getType() == Type.PROCESSING_INSTRUCTION) {
			// test is one out of *, text(), node() including
			// processing-instruction(targetname)
			final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
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
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				// TODO : understand why this one is different from the other
				// ones
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				return currentSet.selectParentChild(contextSet,
						NodeSet.DESCENDANT, contextId);
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			if (!contextSet.getProcessInReverseOrder() && !(contextSet instanceof VirtualNodeSet) &&
					contextSet.getLength() < INDEX_SCAN_THRESHOLD) {
				return index.findDescendantsByTagName(ElementValue.ELEMENT,
						test.getName(), axis, docs, contextSet,
						contextId, parent);
			} else {
				// if (contextSet instanceof VirtualNodeSet)
				// ((VirtualNodeSet)contextSet).realize();
				final NodeSelector selector = new ChildSelector(contextSet, contextId);
				return index.findElementsByTagName(ElementValue.ELEMENT, docs,
						test.getName(), selector, this);
			}
		}
	}

	/**
	 * The method <code>getDescendants</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            the context sequence
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getDescendants(XQueryContext context,
			Sequence contextSequence) throws XPathException {
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
			final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return vset;
		} else if (hasPreloadedData()) {
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				// TODO : understand why this one is different from the other
				// ones
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				switch (axis) {
				case Constants.DESCENDANT_SELF_AXIS:
					final NodeSet tempSet = currentSet.selectAncestorDescendant(
							contextSet, NodeSet.DESCENDANT, true, contextId,
							true);
					return tempSet;
				case Constants.DESCENDANT_AXIS:
					return currentSet.selectAncestorDescendant(contextSet,
							NodeSet.DESCENDANT, false, contextId, true);
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
			}
		} else {
			final DocumentSet docs = contextSet.getDocumentSet();
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled()) {
				context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");
			}
			if (!contextSet.getProcessInReverseOrder() && (contextSet instanceof VirtualNodeSet || contextSet.getLength() < INDEX_SCAN_THRESHOLD)) {
				return index.findDescendantsByTagName(ElementValue.ELEMENT,
						test.getName(), axis, docs, contextSet,
						contextId, this);
			} else {
				NodeSelector selector;
				switch (axis) {
				case Constants.DESCENDANT_SELF_AXIS:
					selector = new DescendantOrSelfSelector(contextSet,
							contextId);
					break;
				case Constants.DESCENDANT_AXIS:
					selector = new DescendantSelector(contextSet, contextId);
					break;
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
				return index.findElementsByTagName(ElementValue.ELEMENT, docs,
						test.getName(), selector, this);
			}

		}
	}

	/**
	 * The method <code>getSiblings</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>NodeSet</code> value
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getSiblings(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			if (axis == Constants.PRECEDING_SIBLING_AXIS)
				{return nodes.getPrecedingSiblings(test);}
			else
				{return nodes.getFollowingSiblings(test);}
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
			final NewArrayNodeSet result = new NewArrayNodeSet(contextSet.getLength());
			try {
				for (final NodeProxy current : contextSet) {
					//ignore document elements to avoid NPE at getXMLStreamReader
					if (NodeId.ROOT_NODE.equals(current.getNodeId()))
						{continue;}
					
					final NodeProxy parent = new NodeProxy(current.getDocument(),
							current.getNodeId().getParentId());
					StreamFilter filter;
					if (axis == Constants.PRECEDING_SIBLING_AXIS)
						{filter = new PrecedingSiblingFilter(test, current,
								result, contextId);}
					else
						{filter = new FollowingSiblingFilter(test, current,
								result, contextId);}
					final EmbeddedXMLStreamReader reader = context.getBroker()
							.getXMLStreamReader(parent, false);
					reader.filter(filter);
				}
			} catch (final IOException e) {
				throw new XPathException(this, e);
			} catch (final XMLStreamException e) {
				throw new XPathException(this, e);
			}
			return result;
		} else {
			// TODO : no test on preloaded data ?
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null || currentDocs == null
						|| !(docs.equalDocs(currentDocs))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				switch (axis) {
				case Constants.PRECEDING_SIBLING_AXIS:
					return currentSet.selectPrecedingSiblings(contextSet,
							contextId);
				case Constants.FOLLOWING_SIBLING_AXIS:
					return currentSet.selectFollowingSiblings(contextSet,
							contextId);
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
			}
		}
	}

	@Deprecated
	private class SiblingVisitor implements NodeVisitor {

		private ExtNodeSet resultSet;
		private NodeProxy contextNode;

		public SiblingVisitor(ExtNodeSet resultSet) {
			this.resultSet = resultSet;
		}

		public void setContext(NodeProxy contextNode) {
			this.contextNode = contextNode;
		}

		public boolean visit(StoredNode current) {
			if (contextNode.getNodeId().getTreeLevel() == current.getNodeId()
					.getTreeLevel()) {
				final int cmp = current.getNodeId()
						.compareTo(contextNode.getNodeId());
				if (((axis == Constants.FOLLOWING_SIBLING_AXIS && cmp > 0) || (axis == Constants.PRECEDING_SIBLING_AXIS && cmp < 0))
						&& test.matches(current)) {
					NodeProxy sibling = resultSet.get((DocumentImpl) current
							.getOwnerDocument(), current.getNodeId());
					if (sibling == null) {
						sibling = new NodeProxy((DocumentImpl) current
								.getOwnerDocument(), current.getNodeId(),
								current.getInternalAddress());
						if (Expression.NO_CONTEXT_ID != contextId) {
							sibling.addContextNode(contextId, contextNode);
						} else
							{sibling.copyContext(contextNode);}
						resultSet.add(sibling);
						resultSet.setSorted(sibling.getDocument(), true);
					} else if (Expression.NO_CONTEXT_ID != contextId)
						{sibling.addContextNode(contextId, contextNode);}
				}
			}
			return true;
		}
	}

	/**
	 * The method <code>getPreceding</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @return a <code>NodeSet</code> value
	 * @exception XPathException
	 *                if an error occurs
	 */
	protected Sequence getPreceding(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		int position = -1;
		if (hasPositionalPredicate) {
			final Predicate pred = (Predicate) predicates.get(0);
			final Sequence seq = pred.preprocess();

			final NumericValue v = (NumericValue) seq.itemAt(0);
			// Non integers return... nothing, not even an error !
			if (!v.hasFractionalPart() && !v.isZero()) {
				position = v.getInt();
			}
		}
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			if (hasPositionalPredicate && position > -1)
				{applyPredicate = false;}
			return nodes.getPreceding(test, position);
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
			try {
				final NodeSet result = new NewArrayNodeSet();
				for (final NodeProxy next : contextSet) {
					final NodeList cl = next.getDocument().getChildNodes();
					for (int j = 0; j < cl.getLength(); j++) {
						final StoredNode node = (StoredNode) cl.item(j);
						final NodeProxy root = new NodeProxy(node);
						final PrecedingFilter filter = new PrecedingFilter(test,
								next, result, contextId);
						final EmbeddedXMLStreamReader reader = context.getBroker()
								.getXMLStreamReader(root, false);
						reader.filter(filter);
					}
				}
				return result;
			} catch (final XMLStreamException e) {
				throw new XPathException(this, e);
			} catch (final IOException e) {
				throw new XPathException(this, e);
			}
		} else {
			// TODO : no test on preloaded data ?
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null || currentDocs == null
						|| !(docs.equalDocs(currentDocs))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				if (hasPositionalPredicate) {
					try {
						applyPredicate = false;
						return currentSet.selectPreceding(contextSet, position,
								contextId);
					} catch (final UnsupportedOperationException e) {
						return currentSet
								.selectPreceding(contextSet, contextId);
					}
				} else
					{return currentSet.selectPreceding(contextSet, contextId);}
			}
		}
	}

	/**
	 * The method <code>getFollowing</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @return a <code>NodeSet</code> value
	 * @exception XPathException
	 *                if an error occurs
	 */
	protected Sequence getFollowing(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		int position = -1;
		if (hasPositionalPredicate) {
			final Predicate pred = (Predicate) predicates.get(0);
			final Sequence seq = pred.preprocess();

			final NumericValue v = (NumericValue) seq.itemAt(0);
			// Non integers return... nothing, not even an error !
			if (!v.hasFractionalPart() && !v.isZero()) {
				position = v.getInt();
			}
		}
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			if (hasPositionalPredicate && position > -1)
				{applyPredicate = false;}
			return nodes.getFollowing(test, position);
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
		if (test.isWildcardTest()
				&& test.getType() != Type.PROCESSING_INSTRUCTION) {
			// handle wildcard steps like following::node()
			try {
				final NodeSet result = new NewArrayNodeSet();
				for (final NodeProxy next : contextSet) {
					final NodeList cl = next.getDocument().getChildNodes();
					for (int j = 0; j < cl.getLength(); j++) {
						final StoredNode node = (StoredNode) cl.item(j);
						final NodeProxy root = new NodeProxy(node);
						final FollowingFilter filter = new FollowingFilter(test,
								next, result, contextId);
						final EmbeddedXMLStreamReader reader = context.getBroker()
								.getXMLStreamReader(root, false);
						reader.filter(filter);
					}
				}
				return result;
			} catch (final XMLStreamException e) {
				throw new XPathException(this, e);
			} catch (final IOException e) {
				throw new XPathException(this, e);
			}
		} else {
			// TODO : no test on preloaded data ?
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null || currentDocs == null
						|| !(docs.equalDocs(currentDocs))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				if (hasPositionalPredicate) {
					try {
						applyPredicate = false;
						return currentSet.selectFollowing(contextSet, position,
								contextId);
					} catch (final UnsupportedOperationException e) {
						return currentSet
								.selectFollowing(contextSet, contextId);
					}
				} else
					{return currentSet.selectFollowing(contextSet, contextId);}
			}
		}
	}

	/**
	 * The method <code>getAncestors</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getAncestors(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.getAncestors(axis == Constants.ANCESTOR_SELF_AXIS,
					test);
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (test.isWildcardTest()) {
			final NodeSet result = new NewArrayNodeSet();
			result.setProcessInReverseOrder(true);
			for (final NodeProxy current : contextSet) {
				NodeProxy ancestor;
				if (axis == Constants.ANCESTOR_SELF_AXIS
						&& test.matches(current)) {
					ancestor = new NodeProxy(current.getDocument(), current
							.getNodeId(), Node.ELEMENT_NODE, current
							.getInternalAddress());
					final NodeProxy t = result.get(ancestor);
					if (t == null) {
						if (Expression.NO_CONTEXT_ID != contextId)
							{ancestor.addContextNode(contextId, current);}
						else
							{ancestor.copyContext(current);}
						ancestor.addMatches(current);
						result.add(ancestor);
					} else {
						t.addContextNode(contextId, current);
						t.addMatches(current);
					}
				}
				NodeId parentID = current.getNodeId().getParentId();
				while (parentID != null) {
					ancestor = new NodeProxy(current.getDocument(), parentID,
							Node.ELEMENT_NODE);
					// Filter out the temporary nodes wrapper element
					if (parentID != NodeId.DOCUMENT_NODE
							&& !(parentID.getTreeLevel() == 1 && current
									.getDocument().getCollection()
									.isTempCollection())) {
						if (test.matches(ancestor)) {
							final NodeProxy t = result.get(ancestor);
							if (t == null) {
								if (Expression.NO_CONTEXT_ID != contextId)
									{ancestor.addContextNode(contextId, current);}
								else
									{ancestor.copyContext(current);}
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
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				switch (axis) {
				case Constants.ANCESTOR_SELF_AXIS:
					return currentSet.selectAncestors(contextSet, true,
							contextId);
				case Constants.ANCESTOR_AXIS:
					return currentSet.selectAncestors(contextSet, false,
							contextId);
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
            return index.findAncestorsByTagName(ElementValue.ELEMENT, test.getName(), axis, docs, contextSet, contextId);
		}
	}

	/**
	 * The method <code>getParents</code>
	 * 
	 * @param context
	 *            a <code>XQueryContext</code> value
	 * @param contextSequence
	 *            a <code>Sequence</code> value
	 * @return a <code>NodeSet</code> value
	 */
	protected Sequence getParents(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.getParents(test);
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (test.isWildcardTest()) {
			final NodeSet temp = contextSet.getParents(contextId);
			final NodeSet result = new NewArrayNodeSet();
			NodeProxy p;
			for (final Iterator<NodeProxy> i = temp.iterator(); i.hasNext();) {
				p = i.next();

				if (test.matches(p)) {
					result.add(p);
				}
			}
			return result;
		} else if (hasPreloadedData()) {
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					currentSet = index.findElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				return contextSet.selectParentChild(currentSet,
						NodeSet.ANCESTOR);
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
            return index.findAncestorsByTagName(ElementValue.ELEMENT, test.getName(), Constants.PARENT_AXIS, docs, contextSet, contextId);
		}
	}

	/**
	 * The method <code>getDocumentSet</code>
	 * 
	 * @param contextSet
	 *            a <code>NodeSet</code> value
	 * @return a <code>DocumentSet</code> value
	 */
	protected DocumentSet getDocumentSet(NodeSet contextSet) {
		DocumentSet ds = getContextDocSet();
		if (ds == null)
			{ds = contextSet.getDocumentSet();}
		return ds;
	}

	/**
	 * The method <code>getParent</code>
	 * 
	 * @return an <code>Expression</code> value
	 */
	public Expression getParentExpression() {
		return this.parent;
	}

	/**
	 * The method <code>registerUpdateListener</code>
	 * 
	 */
	protected void registerUpdateListener() {
		if (listener == null) {
			listener = new UpdateListener() {
				public void documentUpdated(DocumentImpl document, int event) {
					cached = null;
					if (document == null || event == UpdateListener.ADD
							|| event == UpdateListener.REMOVE) {
						// clear all
						currentDocs = null;
						currentSet = null;
					} else {
						if (currentDocs != null
								&& currentDocs
										.contains(document.getDocId())) {
							currentDocs = null;
							currentSet = null;
						}
					}
				}

				public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
				}

				public void unsubscribe() {
					LocationStep.this.listener = null;
				}

				public void debug() {
					LOG.debug("UpdateListener: Line: "
							+ LocationStep.this.toString() + "; id: "
							+ LocationStep.this.getExpressionId());
				}
			};
			context.registerUpdateListener(listener);
		}
	}

	/**
	 * The method <code>accept</code>
	 * 
	 * @param visitor
	 *            an <code>ExpressionVisitor</code> value
	 */
	public void accept(ExpressionVisitor visitor) {
		visitor.visitLocationStep(this);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.Step#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		if (!postOptimization) {
			// TODO : preloadedData = false ?
			// No : introduces a regression in testMatchCount
			// TODO : Investigate...
			currentSet = null;
			currentDocs = null;
			optimized = false;
			cached = null;
			listener = null;
		}
	}

	private static class FollowingSiblingFilter implements StreamFilter {

		private NodeTest test;
		private NodeProxy referenceNode;
		private NodeSet result;
		private int contextId;
		private boolean isAfter = false;

		private FollowingSiblingFilter(NodeTest test, NodeProxy referenceNode,
				NodeSet result, int contextId) {
			this.test = test;
			this.referenceNode = referenceNode;
			this.result = result;
			this.contextId = contextId;
		}

		public boolean accept(XMLStreamReader reader) {
			if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
				return true;
			}
			final NodeId refId = referenceNode.getNodeId();
			final NodeId currentId = (NodeId) reader
					.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
			if (!isAfter) {
				isAfter = currentId.equals(refId);
			} else if (currentId.getTreeLevel() == refId.getTreeLevel()
					&& test.matches(reader)) {
				NodeProxy sibling = result.get(referenceNode.getDocument(),
						currentId);
				if (sibling == null) {
					sibling = new NodeProxy(referenceNode.getDocument(),
							currentId, StaXUtil.streamType2DOM(reader
									.getEventType()),
							((EmbeddedXMLStreamReader) reader)
									.getCurrentPosition());

					if (Expression.IGNORE_CONTEXT != contextId) {
						if (Expression.NO_CONTEXT_ID == contextId) {
							sibling.copyContext(referenceNode);
						} else {
							sibling.addContextNode(contextId, referenceNode);
						}
					}
					result.add(sibling);
				} else if (Expression.NO_CONTEXT_ID != contextId)
					{sibling.addContextNode(contextId, referenceNode);}
			}
			return true;
		}
	}

	private static class PrecedingSiblingFilter implements StreamFilter {

		private NodeTest test;
		private NodeProxy referenceNode;
		private NodeSet result;
		private int contextId;

		private PrecedingSiblingFilter(NodeTest test, NodeProxy referenceNode,
				NodeSet result, int contextId) {
			this.test = test;
			this.referenceNode = referenceNode;
			this.result = result;
			this.contextId = contextId;
		}

		public boolean accept(XMLStreamReader reader) {
			if (reader.getEventType() == XMLStreamReader.END_ELEMENT) {
				return true;
			}
			final NodeId refId = referenceNode.getNodeId();
			final NodeId currentId = (NodeId) reader
					.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
			if (currentId.equals(refId)) {
				return false;
			} else if (currentId.getTreeLevel() == refId.getTreeLevel()
					&& test.matches(reader)) {
				NodeProxy sibling = result.get(referenceNode.getDocument(),
						currentId);
				if (sibling == null) {
					sibling = new NodeProxy(referenceNode.getDocument(),
							currentId, StaXUtil.streamType2DOM(reader
									.getEventType()),
							((EmbeddedXMLStreamReader) reader)
									.getCurrentPosition());
					if (Expression.IGNORE_CONTEXT != contextId) {
						if (Expression.NO_CONTEXT_ID == contextId) {
							sibling.copyContext(referenceNode);
						} else {
							sibling.addContextNode(contextId, referenceNode);
						}
					}
					result.add(sibling);
				} else if (Expression.NO_CONTEXT_ID != contextId)
					{sibling.addContextNode(contextId, referenceNode);}

			}
			return true;
		}
	}

	private static class FollowingFilter implements StreamFilter {

		private NodeTest test;
		private NodeProxy referenceNode;
		private NodeSet result;
		private int contextId;
		private boolean isAfter = false;

		private FollowingFilter(NodeTest test, NodeProxy referenceNode,
				NodeSet result, int contextId) {
			this.test = test;
			this.referenceNode = referenceNode;
			this.result = result;
			this.contextId = contextId;
		}

		public boolean accept(XMLStreamReader reader) {
			if (reader.getEventType() == XMLStreamReader.END_ELEMENT)
				{return true;}
			final NodeId refId = referenceNode.getNodeId();
			final NodeId currentId = (NodeId) reader
					.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
			if (!isAfter)
				{isAfter = currentId.compareTo(refId) > 0
						&& !currentId.isDescendantOf(refId);}
			if (isAfter && !refId.isDescendantOf(currentId)
					&& test.matches(reader)) {
				final NodeProxy proxy = new NodeProxy(referenceNode.getDocument(),
						currentId, StaXUtil.streamType2DOM(reader
								.getEventType()),
						((EmbeddedXMLStreamReader) reader).getCurrentPosition());
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

	private static class PrecedingFilter implements StreamFilter {

		private NodeTest test;
		private NodeProxy referenceNode;
		private NodeSet result;
		private int contextId;

		private PrecedingFilter(NodeTest test, NodeProxy referenceNode,
				NodeSet result, int contextId) {
			this.test = test;
			this.referenceNode = referenceNode;
			this.result = result;
			this.contextId = contextId;
		}

		public boolean accept(XMLStreamReader reader) {
			if (reader.getEventType() == XMLStreamReader.END_ELEMENT)
				{return true;}
			final NodeId refId = referenceNode.getNodeId();
			final NodeId currentId = (NodeId) reader
					.getProperty(EmbeddedXMLStreamReader.PROPERTY_NODE_ID);
			if (currentId.compareTo(refId) >= 0)
				{return false;}
			if (!refId.isDescendantOf(currentId) && test.matches(reader)) {
				final NodeProxy proxy = new NodeProxy(referenceNode.getDocument(),
						currentId, StaXUtil.streamType2DOM(reader
								.getEventType()),
						((EmbeddedXMLStreamReader) reader).getCurrentPosition());
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

	public Boolean match(Sequence contextSequence, Item contextItem)
			throws XPathException {
		if (context.getProfiler().isEnabled()) {
			context.getProfiler().start(this);
			context.getProfiler().message(this, Profiler.DEPENDENCIES,
					"DEPENDENCIES",
					Dependency.getDependenciesName(this.getDependencies()));
			if (contextSequence != null)
				{context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT SEQUENCE", contextSequence);}
			if (contextItem != null)
				{context.getProfiler().message(this, Profiler.START_SEQUENCES,
						"CONTEXT ITEM", contextItem.toSequence());}
		}

		Boolean result;
		if (needsComputation()) {
			if (contextSequence == null)
				{throw new XPathException(this,
						ErrorCodes.XPDY0002, "Undefined context sequence for '"
								+ this.toString() + "'");}
			switch (axis) {
			case Constants.DESCENDANT_AXIS:
			case Constants.DESCENDANT_SELF_AXIS:
				result = null;//getDescendants(context, contextSequence);
				break;
			case Constants.CHILD_AXIS:
				// VirtualNodeSets may have modified the axis ; checking the
				// type
				// TODO : further checks ?
				if (this.test.getType() == Type.ATTRIBUTE) {
					this.axis = Constants.ATTRIBUTE_AXIS;
					result = matchAttributes(context, contextSequence);
				} else {
					result = matchChildren(context, contextItem.toSequence());//matchChildren(context, contextSequence);
				}
				break;
			case Constants.ANCESTOR_SELF_AXIS:
			case Constants.ANCESTOR_AXIS:
				result = null;//getAncestors(context, contextSequence);
				break;
			case Constants.PARENT_AXIS:
				result = null;//getParents(context, contextSequence);
				break;
			case Constants.SELF_AXIS:
//				if (!(contextSequence instanceof VirtualNodeSet)
//						&& Type.subTypeOf(contextSequence.getItemType(),
//								Type.ATOMIC)) {
//					// This test is copied from the legacy method
//					// getSelfAtomic()
//					if (!test.isWildcardTest())
//						throw new XPathException(this, test.toString()
//								+ " cannot be applied to an atomic value.");
//					result = null;//contextSequence;
//				} else {
					result = matchSelf(context, contextItem.toSequence());
//				}
				break;
			case Constants.ATTRIBUTE_AXIS:
			case Constants.DESCENDANT_ATTRIBUTE_AXIS:
				result = null;//getAttributes(context, contextSequence);
				break;
			case Constants.PRECEDING_AXIS:
				result = null;//getPreceding(context, contextSequence);
				break;
			case Constants.FOLLOWING_AXIS:
				result = null;//getFollowing(context, contextSequence);
				break;
			case Constants.PRECEDING_SIBLING_AXIS:
			case Constants.FOLLOWING_SIBLING_AXIS:
				result = null;//getSiblings(context, contextSequence);
				break;
			default:
				throw new IllegalArgumentException("Unsupported axis specified");
			}
		} else {
			result = null;//NodeSet.EMPTY_SET;
		}

		result = matchPredicate(contextSequence, (Node)contextItem, result);

		if (context.getProfiler().isEnabled())
			{context.getProfiler().end(this, "", null);}
		// actualReturnType = result.getItemType();

		return result;
	}

	private Boolean matchPredicate(Sequence contextSequence, Node contextItem,
			Boolean result) throws XPathException {

		if (result == null) {return false;}
		
		if (!result)
			{return result;}

		if (contextSequence == null)
			{return false;}

		if (predicates.size() == 0)
			{return result;}
		
		Predicate pred;

		for (final Iterator<Predicate> i = predicates.iterator(); i.hasNext();) {
//				&& (result instanceof VirtualNodeSet || !result.isEmpty());) {
			// TODO : log and/or profile ?
			pred = i.next();
			pred.setContextDocSet(getContextDocSet());

			//result = pred.evalPredicate(outerSequence, result, axis);
			result = pred.matchPredicate(contextSequence, (Item)contextItem, axis);

			if (!result)
				{return false;}
			
			// subsequent predicates operate on the result of the previous one
//			outerSequence = null;
		}
		return result;
	}

	private Boolean matchSelf(XQueryContext context, Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.matchSelf(test);
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (test.getType() == Type.PROCESSING_INSTRUCTION) {
			final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return !vset.isEmpty();
		}

		if (test.isWildcardTest()) {
			if (nodeTestType == null) {
				nodeTestType = Integer.valueOf(test.getType());
			}
			if (Type.subTypeOf(nodeTestType.intValue(), Type.NODE)) {
				if (Expression.NO_CONTEXT_ID != contextId) {
					if (contextSet instanceof VirtualNodeSet) {
						((VirtualNodeSet) contextSet).setInPredicate(true);
						((VirtualNodeSet) contextSet).setContextId(contextId);
						((VirtualNodeSet) contextSet).setSelfIsContext();
					} else if (Type.subTypeOf(contextSet.getItemType(),
							Type.NODE)) {

						for (final NodeProxy p : contextSet) {
							if (test.matches(p))
								{return true;}
						}
					}
				}
				return false;
			} else {
				final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(),
						axis, test, contextId, contextSet);
				vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
				return !vset.isEmpty();
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			final NodeSelector selector = new SelfSelector(contextSet, contextId);
			return index.matchElementsByTagName(ElementValue.ELEMENT, docs, test
					.getName(), selector);
		}
	}

	protected Boolean matchChildren(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			return nodes.matchChildren(test);
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		// TODO : understand this. I guess comments should be treated in a
		// similar way ? -pb
		if (test.isWildcardTest()
				|| test.getType() == Type.PROCESSING_INSTRUCTION) {
			// test is one out of *, text(), node() including
			// processing-instruction(targetname)
			final VirtualNodeSet vset = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			vset.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return !vset.isEmpty();
		}

		// IndexStatistics stats = (IndexStatistics)
		// context.getBroker().getBrokerPool().
		// getIndexManager().getIndexById(IndexStatistics.ID);
		// int parentDepth = stats.getMaxParentDepth(test.getName());
		// LOG.debug("parentDepth for " + test.getName() + ": " + parentDepth);

		if (useDirectChildSelect) {
			//NewArrayNodeSet result = new NewArrayNodeSet();
			for (final NodeProxy p : contextSet) {
				if (p.directMatchChild(test.getName(), contextId))
					{return true;}
			}
			return false;
		} else if (hasPreloadedData()) {
			final DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				// TODO : understand why this one is different from the other
				// ones
//				if (currentSet == null
//						|| currentDocs == null
//						|| (!optimized && !(docs == currentDocs || docs
//								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					return index.matchElementsByTagName(
							ElementValue.ELEMENT, docs, test.getName(), null);
//					currentDocs = docs;
//UNDERSTAND: TODO:					registerUpdateListener();
				}
//				return currentSet.selectParentChild(contextSet,
//						NodeSet.DESCENDANT, contextId);
//			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			if (contextSet instanceof ExtNodeSet
					&& !contextSet.getProcessInReverseOrder()) {
				return index.matchDescendantsByTagName(ElementValue.ELEMENT,
						test.getName(), axis, docs, (ExtNodeSet) contextSet,
						contextId);
			} else {
				// if (contextSet instanceof VirtualNodeSet)
				// ((VirtualNodeSet)contextSet).realize();
				final NodeSelector selector = new ChildSelector(contextSet, contextId);
				return index.matchElementsByTagName(ElementValue.ELEMENT, docs,
						test.getName(), selector);
			}
		}
	}

	protected boolean matchAttributes(XQueryContext context,
			Sequence contextSequence) throws XPathException {
		if (!contextSequence.isPersistentSet()) {
			final MemoryNodeSet nodes = contextSequence.toMemNodeSet();
			if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)
				{return nodes.matchDescendantAttributes(test);}
			else
				{return nodes.matchAttributes(test);}
		}
		final NodeSet contextSet = contextSequence.toNodeSet();
		if (test.isWildcardTest()) {
			final NodeSet result = new VirtualNodeSet(context.getBroker(), axis,
					test, contextId, contextSet);
			((VirtualNodeSet) result)
					.setInPredicate(Expression.NO_CONTEXT_ID != contextId);
			return !result.isEmpty();
			// if there's just a single known node in the context, it is faster
			// do directly search for the attribute in the parent node.
		}
		if (hasPreloadedData()) {
			DocumentSet docs = getDocumentSet(contextSet);
			synchronized (context) {
				if (currentSet == null
						|| currentDocs == null
						|| (!optimized && !(docs == currentDocs || docs
								.equalDocs(currentDocs)))) {
					final StructuralIndex index = context.getBroker().getStructuralIndex();
					if (context.getProfiler().isEnabled())
						{context.getProfiler().message(
								this,
								Profiler.OPTIMIZATIONS,
								"OPTIMIZATION",
								"Using structural index '" + index.toString()
										+ "'");}
					// TODO : why a null selector here ? We have one below !
					currentSet = index.findElementsByTagName(
							ElementValue.ATTRIBUTE, docs, test.getName(), null, this);
					currentDocs = docs;
					registerUpdateListener();
				}
				switch (axis) {
				case Constants.ATTRIBUTE_AXIS:
					return currentSet.matchParentChild(contextSet,
							NodeSet.DESCENDANT, contextId);
				case Constants.DESCENDANT_ATTRIBUTE_AXIS:
					return currentSet.matchAncestorDescendant(contextSet,
							NodeSet.DESCENDANT, false, contextId, true);
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
			}
		} else {
			final DocumentSet docs = getDocumentSet(contextSet);
			final StructuralIndex index = context.getBroker().getStructuralIndex();
			if (context.getProfiler().isEnabled())
				{context.getProfiler().message(this, Profiler.OPTIMIZATIONS,
						"OPTIMIZATION",
						"Using structural index '" + index.toString() + "'");}
			if (contextSet instanceof ExtNodeSet
					&& !contextSet.getProcessInReverseOrder()) {
				return index.matchDescendantsByTagName(ElementValue.ATTRIBUTE,
						test.getName(), axis, docs, (ExtNodeSet) contextSet,
						contextId);
			} else {
				NodeSelector selector;
				switch (axis) {
				case Constants.ATTRIBUTE_AXIS:
					selector = new ChildSelector(contextSet, contextId);
					break;
				case Constants.DESCENDANT_ATTRIBUTE_AXIS:
					selector = new DescendantSelector(contextSet, contextId);
					break;
				default:
					throw new IllegalArgumentException(
							"Unsupported axis specified");
				}
				return index.matchElementsByTagName(ElementValue.ATTRIBUTE,
						docs, test.getName(), selector);
			}
		}
	}
}