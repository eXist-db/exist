/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 *  $Id:
 */
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.XMLUtil;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

public class LocationStep extends Step {

	protected static Category LOG = Category.getInstance(LocationStep.class.getName());
	protected NodeSet buf = null;
	protected boolean keepVirtual = false;

	public LocationStep(BrokerPool pool, int axis) {
		super(pool, axis);
	}

	public LocationStep(BrokerPool pool, int axis, NodeTest test) {
		super(pool, axis, test);
	}

	protected NodeSet applyPredicate(DocumentSet documents, NodeSet context) {
		Predicate pred;
		NodeSet result = context;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result = (NodeSet) pred.eval(documents, result, null).getNodeList();
		}
		return result;
	}

	public Value eval(DocumentSet documents, NodeSet context, NodeProxy node) {
		if(node != null) {
			context = new SingleNodeSet(node);
		}
		NodeSet temp;
		switch (axis) {
			case Constants.DESCENDANT_AXIS :
			case Constants.DESCENDANT_SELF_AXIS :
				temp = getDescendants(documents, context);
				break;
			case Constants.CHILD_AXIS :
				temp = getChildren(documents, context);
				break;
			case Constants.ANCESTOR_AXIS :
			case Constants.ANCESTOR_SELF_AXIS :
				temp = getAncestors(documents, context);
				break;
			case Constants.SELF_AXIS :
				temp = context;
				if(inPredicate) {
					if(context instanceof VirtualNodeSet) {
						((VirtualNodeSet)context).setInPredicate(true);
						((VirtualNodeSet)context).setSelfIsContext();
					} else {
						NodeProxy p;
						for(Iterator i = temp.iterator(); i.hasNext(); ) {
							p = (NodeProxy)i.next();
							p.addContextNode(p);
						}
					}
				}
				break;
			case Constants.PARENT_AXIS :
				temp = getParents(documents, context);
				break;
			case Constants.ATTRIBUTE_AXIS :
				temp = getAttributes(documents, context);
				break;
			case Constants.PRECEDING_SIBLING_AXIS :
			case Constants.FOLLOWING_SIBLING_AXIS :
				temp = getSiblings(documents, context);
				break;
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
		temp = (predicates.size() == 0) ? temp : applyPredicate(documents, temp);
		return new ValueNodeSet(temp);
	}

	protected NodeSet getAttributes(DocumentSet documents, NodeSet context) {
		NodeSet result;
		switch (test.getType()) {
			case NodeTest.TYPE_TEST :
				result = new VirtualNodeSet(axis, (TypeTest) test, context);
				((VirtualNodeSet)result).setInPredicate(inPredicate);
				break;
			case NodeTest.NAME_TEST :
				if (buf == null) {
					DBBroker broker = null;
					try {
						broker = pool.get();
						buf = (NodeSet) broker.getAttributesByName(documents, test.getName());
					} catch (EXistException e) {
						LOG.debug("exception while retrieving elements", e);
					} finally {
						pool.release(broker);
					}
				}
				result = ((ArraySet) buf).getChildren(context, ArraySet.DESCENDANT, inPredicate);
				LOG.debug("found " + result.getLength() + " attributes");
				break;
			default :
				Node n;
				Node attr;
				NamedNodeMap map;
				result = new ArraySet(context.getLength());
				for (int i = 0; i < context.getLength(); i++) {
					n = context.item(i);
					if (n.getNodeType() == Node.ELEMENT_NODE) {
						map = ((Element) n).getAttributes();
						for (int j = 0; j < map.getLength(); j++) {
							attr = map.item(j);
							result.add(attr);
						}
					}
				}
		}
		return result;
	}

	protected NodeSet getChildren(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.TYPE_TEST) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, (TypeTest) test, context);
			vset.setInPredicate(inPredicate);
			return vset;
		} else {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf = (NodeSet) broker.findElementsByTagName(documents, test.getName());
				return buf.getChildren(context, ArraySet.DESCENDANT, inPredicate);
			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		}
	}

	protected NodeSet getDescendants(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf = (NodeSet) broker.findElementsByTagName(documents, test.getName());
				return buf.getDescendants(
					context,
					ArraySet.DESCENDANT,
					axis == Constants.DESCENDANT_SELF_AXIS, inPredicate);

			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		} else {
			VirtualNodeSet vset = new VirtualNodeSet(axis, (TypeTest) test, context);
			vset.setInPredicate(inPredicate);
			return vset;
		}
	}

	protected NodeSet getSiblings(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf = (NodeSet) broker.findElementsByTagName(documents, test.getName());
				return context.getSiblings(
					buf,
					axis == Constants.PRECEDING_SIBLING_AXIS
						? NodeSet.PRECEDING
						: NodeSet.FOLLOWING);
			} catch (EXistException e) {
				LOG.debug(e.getMessage(), e);
				return null;
			} finally {
				pool.release(broker);
			}
		} else {
			ArraySet result = new ArraySet(context.getLength());
			NodeProxy p;
			NodeImpl n;
			for(Iterator i = context.iterator(); i.hasNext(); ) {
				p = (NodeProxy)i.next();
				n = (NodeImpl)p.getNode();
				while((n = getNextSibling(n)) != null) {
					if(((TypeTest)test).isOfType(n.getNodeType()))
						result.add(new NodeProxy((DocumentImpl)n.getOwnerDocument(),
							n.getGID(), n.getInternalAddress()));
				}
			}
			return result;
		}
	}

	protected NodeImpl getNextSibling(NodeImpl last) {
		switch(axis) {
			case Constants.FOLLOWING_SIBLING_AXIS :
				return (NodeImpl)last.getNextSibling();
			default :
				return (NodeImpl)last.getPreviousSibling();
		}
	}
	
	protected NodeSet getAncestors(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf = (NodeSet) broker.findElementsByTagName(documents, test.getName());
				NodeSet r =
					context.getAncestors(
						(ArraySet) buf,
						axis == Constants.ANCESTOR_SELF_AXIS,
						inPredicate);
				LOG.debug("getAncestors found " + r.getLength());
				return r;
			} catch (EXistException e) {
				LOG.debug(e.getMessage(), e);
				return null;
			} finally {
				pool.release(broker);
			}
		} else {
			NodeSet result = new ArraySet(context.getLength());
			NodeProxy p;
			for(Iterator i = context.iterator(); i.hasNext(); ) {
				p = (NodeProxy)i.next();
				if(axis == Constants.ANCESTOR_SELF_AXIS &&
					((TypeTest)test).isOfType(p, p.nodeType))
					result.add(new NodeProxy(p.doc, p.gid, p.internalAddress));
				while((p.gid = XMLUtil.getParentId(p.doc, p.gid)) > 0) {
					if(((TypeTest)test).isOfType(p, Node.ELEMENT_NODE))
						result.add(new NodeProxy(p.doc, p.gid));
				}
			}
			return result;
		}
	}

	protected NodeSet getParents(DocumentSet documents, NodeSet context) {
		return context.getParents();
	}

	public DocumentSet preselect(DocumentSet inDocs) {
		return super.preselect(inDocs);
	}

	public void setKeepVirtual(boolean virtual) {
		keepVirtual = virtual;
	}
}
