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
package org.exist.xpath;

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
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.w3c.dom.Node;

/**
 * Processes all location path steps (like descendant::*, ancestor::XXX).
 * 
 * @author wolf
 */
public class LocationStep extends Step {

	protected NodeSet currentSet = null;
	protected DocumentSet currentDocs = null;
	
	protected boolean keepVirtual = false;

	public LocationStep(XQueryContext context, int axis) {
		super(context, axis);
	}

	public LocationStep(XQueryContext context, int axis, NodeTest test) {
		super(context, axis, test);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Step#returnsType()
	 */
//	public int returnsType() {
//		if(axis == Constants.SELF_AXIS)
//			return Type.ITEM;
//		else
//			return Type.NODE;
//	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
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
			result = pred.evalPredicate(outerSequence, result);
		}
		return result;
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
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
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
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
			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().getAttributesByName(
						currentDocs, test.getName());
			}
			if (axis == Constants.DESCENDANT_ATTRIBUTE_AXIS)
				result =
					currentSet.selectAncestorDescendant(
						contextSet,
						NodeSet.DESCENDANT,
						inPredicate);
			else
				result =
					currentSet.selectParentChild(
						contextSet,
						NodeSet.DESCENDANT,
						inPredicate);
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
			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().findElementsByTagName(
						ElementValue.ELEMENT,
						currentDocs,
						test.getName());
			}
			return currentSet.selectParentChild(contextSet, NodeSet.DESCENDANT, inPredicate);
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
			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName());
			}
			return currentSet.selectAncestorDescendant(
				contextSet,
				NodeSet.DESCENDANT,
				axis == Constants.DESCENDANT_SELF_AXIS,
				inPredicate);
		}
	}

	protected NodeSet getSiblings(
		XQueryContext context,
		NodeSet contextSet) {
		if (!test.isWildcardTest()) {
			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName());
			}
			return contextSet.selectSiblings(
				currentSet,
				axis == Constants.PRECEDING_SIBLING_AXIS
					? NodeSet.PRECEDING
					: NodeSet.FOLLOWING);
		} else {
			ArraySet result = new ArraySet(contextSet.getLength());
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
			return result;
		}
	}

	protected NodeImpl getNextSibling(NodeImpl last) {
		switch (axis) {
			case Constants.FOLLOWING_SIBLING_AXIS :
				return (NodeImpl) last.getNextSibling();
			default :
				return (NodeImpl) last.getPreviousSibling();
		}
	}

	protected NodeSet getAncestors(
		XQueryContext context,
		NodeSet contextSet) {
		if (!test.isWildcardTest()) {
			DocumentSet docs = contextSet.getDocumentSet();
			if (currentSet == null || currentDocs == null || !(docs.equals(currentDocs))) {
				currentDocs = docs;
				currentSet =
					(NodeSet) context.getBroker().findElementsByTagName(
						ElementValue.ELEMENT, currentDocs,
						test.getName());
			}
			NodeSet r =
				contextSet.selectAncestors(
					currentSet,
					axis == Constants.ANCESTOR_SELF_AXIS,
					inPredicate);
			//LOG.debug("getAncestors found " + r.getLength());
			return r;
		} else {
			NodeSet result = new ExtArrayNodeSet();
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
			return result;
		}
	}

	protected NodeSet getParents(
		XQueryContext context,
		NodeSet contextSet) {
		return contextSet.getParents();
	}

	public void setKeepVirtual(boolean virtual) {
		keepVirtual = virtual;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Step#resetState()
	 */
	public void resetState() {
		super.resetState();
		currentSet = null;
	}
}
