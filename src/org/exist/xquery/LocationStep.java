/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.dom.XMLUtil;
import org.exist.storage.ElementValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

/**
 * Processes all location path steps (like descendant::*, ancestor::XXX).
 * 
 * The results of the first evaluation of the expression  are cached for the 
 * lifetime of the object and only reloaded if the context sequence
 * (as passed to the {@link #eval(Sequence, Item)} method) has changed.
 * 
 * @author wolf
 */
public class LocationStep extends Step {

	protected NodeSet currentSet = null;
	protected DocumentSet currentDocs = null;
	
	// Fields for caching the last result
	protected Sequence cachedResult = null;
	protected Sequence cachedContext = null;
	protected int timestamp = 0;
	
	public LocationStep(XQueryContext context, int axis) {
		super(context, axis);
	}

	public LocationStep(XQueryContext context, int axis, NodeTest test) {
		super(context, axis, test);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#returnsType()
	 */
//	public int returnsType() {
//		if(axis == Constants.SELF_AXIS)
//			return Type.ITEM;
//		else
//			return Type.NODE;
//	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		for(Iterator i = predicates.iterator(); i.hasNext(); ) {
			deps |= ((Predicate)i.next()).getDependencies();
		}
		return deps;
	}
	
	protected Sequence applyPredicate(
		Sequence outerSequence,
		Sequence contextSequence)
		throws XPathException {
		if(contextSequence == null)
			return Sequence.EMPTY_SEQUENCE;
		Predicate pred;
		Sequence result = contextSequence;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result = pred.evalPredicate(outerSequence, result, axis);
		}
		return result;
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		if(contextSequence == null)
			return Sequence.EMPTY_SEQUENCE;
		if(cachedResult != null &&
			cachedContext == contextSequence &&
			Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {
			if(!((NodeSet)contextSequence).hasChanged(timestamp)) {
//				LOG.debug("returning cached result");
				return 
					(predicates.size() == 0)
					? cachedResult :
					 applyPredicate(contextSequence, cachedResult);
			}
		}
//		LOG.debug("processing " + pprint());
		Sequence temp;
		switch (axis) {
			case Constants.DESCENDANT_AXIS :
			case Constants.DESCENDANT_SELF_AXIS :
				temp =
					getDescendants(
						context,
						contextSequence.toNodeSet());
				break;
			case Constants.CHILD_AXIS :
				temp =
					getChildren(context, contextSequence.toNodeSet());
				break;
			case Constants.ANCESTOR_AXIS :
			case Constants.ANCESTOR_SELF_AXIS :
				temp =
					getAncestors(context, contextSequence.toNodeSet());
				break;
			case Constants.SELF_AXIS :
				if (inPredicate) {
					if (contextSequence instanceof VirtualNodeSet) {
						((VirtualNodeSet) contextSequence).setInPredicate(true);
						((VirtualNodeSet) contextSequence).setSelfIsContext();
					} else if(Type.subTypeOf(contextSequence.getItemType(), Type.NODE)) {
						NodeProxy p;
						for (Iterator i = contextSequence.toNodeSet().iterator(); i.hasNext();) {
							p = (NodeProxy) i.next();
							p.addContextNode(p);
						}
					}
				}
				temp = contextSequence;
				break;
			case Constants.PARENT_AXIS :
				temp =
					getParents(context, contextSequence.toNodeSet());
				break;
			case Constants.ATTRIBUTE_AXIS :
				// combines /descendant-or-self::node()/attribute:*
			case Constants.DESCENDANT_ATTRIBUTE_AXIS :
				temp =
					getAttributes(
						context,
						contextSequence.toNodeSet());
				break;
			case Constants.PRECEDING_SIBLING_AXIS :
			case Constants.FOLLOWING_SIBLING_AXIS :
				temp =
					getSiblings(context, contextSequence.toNodeSet());
				break;
			case Constants.FOLLOWING_AXIS:
				//temp = getFollowing(context, contextSequence.toNodeSet());
				throw new XPathException("The following axis is not yet supported");
			case Constants.PRECEDING_AXIS:
				throw new XPathException("The preceding axis is not yet supported");
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
		if(contextSequence instanceof NodeSet)
			timestamp = ((NodeSet)contextSequence).getState();
		cachedResult = temp;
		cachedContext = contextSequence;
		return
			(predicates.size() == 0)
				? temp
				: applyPredicate(contextSequence, temp);
	}

	protected NodeSet getAttributes(
		XQueryContext context,
		NodeSet contextSet) {
		NodeSet result;
		if (test.isWildcardTest()) {
			result = new VirtualNodeSet(axis, test, contextSet);
			((VirtualNodeSet) result).setInPredicate(inPredicate);
		} else {
		    DocumentSet docs = getDocumentSet(contextSet);
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getElementIndex().getAttributesByName(
						currentDocs, test.getName());
			}
			if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS) {
				result =
					currentSet.selectAncestorDescendant(
						contextSet,
						NodeSet.DESCENDANT,
						inPredicate);
			} else {
				result =
					currentSet.selectParentChild(
						contextSet,
						NodeSet.DESCENDANT,
						inPredicate);
			}
		}
		return result;
	}

	protected NodeSet getChildren(
		XQueryContext context,
		NodeSet contextSet) {
		if (test.isWildcardTest()) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else {
		    NodeSet result = null;
		    DocumentSet docs = getDocumentSet(contextSet);
		    if(cacheResults()) {
				if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
					currentDocs = docs;
						currentSet =
							(NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
								ElementValue.ELEMENT,
								currentDocs,
								test.getName(), null);
				}
				result =
					currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, inPredicate);
			} else {
			    NodeSelector selector = new ChildSelector(contextSet, inPredicate);
			    result = context.getBroker().getElementIndex().findElementsByTagName(
			            ElementValue.ELEMENT, docs, test.getName(), selector
			    );
			}
			return result;
		}
	}

	protected NodeSet getDescendants(
		XQueryContext context,
		NodeSet contextSet) {
		if (test.isWildcardTest()) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else {
		    DocumentSet docs = getDocumentSet(contextSet);
//		    DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName(), null);
			}
			NodeSet result = currentSet.selectAncestorDescendant(
				contextSet,
				NodeSet.DESCENDANT,
				axis == Constants.DESCENDANT_SELF_AXIS,
				inPredicate);
//			DocumentSet docs = contextSet.getDocumentSet();
//			NodeSelector selector = axis == Constants.DESCENDANT_SELF_AXIS ?
//			        new DescendantOrSelfSelector(contextSet, inPredicate) :
//			    new DescendantSelector(contextSet, inPredicate);
//			NodeSet result = context.getBroker().findElementsByTagName(
//					ElementValue.ELEMENT, docs, test.getName(), selector
//			);
			return result;
		}
	}

	protected NodeSet getSiblings(
		XQueryContext context,
		NodeSet contextSet) {
		NodeSet result;
		if (!test.isWildcardTest()) {
		    DocumentSet docs = getDocumentSet(contextSet);
//			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName(), null);
			}
			result = currentSet.selectSiblings(
				contextSet,
				axis == Constants.PRECEDING_SIBLING_AXIS
					? NodeSet.PRECEDING
					: NodeSet.FOLLOWING);
		} else {
			result = new ArraySet(contextSet.getLength());
			NodeProxy p;
			NodeImpl n;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				n = (NodeImpl) p.getNode();
				while ((n = getNextSibling(n)) != null) {
					if (test.matches(n))
						result.add(
							new NodeProxy(
								(DocumentImpl) n.getOwnerDocument(),
								n.getGID(),
								n.getInternalAddress()));
				}
			}
		}
		return result;
	}

	protected NodeImpl getNextSibling(NodeImpl last) {
		switch (axis) {
			case Constants.FOLLOWING_SIBLING_AXIS :
				return (NodeImpl) last.getNextSibling();
			default :
				return (NodeImpl) last.getPreviousSibling();
		}
	}

	protected NodeSet getFollowing(XQueryContext context, NodeSet contextSet)
	throws XPathException {
		NodeSet result = NodeSet.EMPTY_SET;
		if(!test.isWildcardTest()) {
		    DocumentSet docs = getDocumentSet(contextSet);
//			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName(), null);
			}
			result = contextSet.selectFollowing(currentSet);
		}
		return result;
	}
	
	protected NodeSet getAncestors(
		XQueryContext context,
		NodeSet contextSet) {
		NodeSet result;
		if (!test.isWildcardTest()) {
//			DocumentSet docs = contextSet.getDocumentSet();
			DocumentSet docs = getDocumentSet(contextSet);
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getElementIndex().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName(), null);
			}
			result =
				currentSet.selectAncestors(
					contextSet,
					axis == Constants.ANCESTOR_SELF_AXIS,
					inPredicate);
//			LOG.debug("getAncestors found " + result.getLength());
		} else {
			result = new ExtArrayNodeSet();
			NodeProxy p;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				if (axis == Constants.ANCESTOR_SELF_AXIS && test.matches(p))
					result.add(
						new NodeProxy(p.doc, p.gid, p.getInternalAddress()));
				while ((p.gid = XMLUtil.getParentId(p.doc, p.gid)) > 0) {
					p.nodeType = Node.ELEMENT_NODE;
					if (test.matches(p))
						result.add(new NodeProxy(p.doc, p.gid));
				}
			}
		}
		return result;
	}

	/**
	 * TODO: works only for ..
	 */
	protected NodeSet getParents(
		XQueryContext context,
		NodeSet contextSet) {
		return contextSet.getParents();
	}

	protected DocumentSet getDocumentSet(NodeSet contextSet) {
	    DocumentSet ds = getContextDocSet();
	    if(ds == null)
	        ds = contextSet.getDocumentSet();
	    return ds;
	}
	
	protected boolean cacheResults() {
	    //return getContextDocSet() != null;
	    return true;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#resetState()
	 */
	public void resetState() {
		super.resetState();
//		System.out.println(pprint() + ": reset!!!!");
		currentSet = null;
		currentDocs = null;
		cachedContext = null;
		cachedResult = null;
	}
}
