/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
import org.exist.dom.QName;
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

	protected NodeSet applyPredicate(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		Predicate pred;
		NodeSet result = contextSet;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result = (NodeSet) pred.eval(context, documents, result).getNodeList();
		}
		return result;
	}

	public Value eval(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet,
		NodeProxy contextNode) {
		if (contextNode != null)
			contextSet = new SingleNodeSet(contextNode);

		NodeSet temp;
		switch (axis) {
			case Constants.DESCENDANT_AXIS :
			case Constants.DESCENDANT_SELF_AXIS :
				temp = getDescendants(context, documents, contextSet);
				break;
			case Constants.CHILD_AXIS :
				temp = getChildren(context, documents, contextSet);
				break;
			case Constants.ANCESTOR_AXIS :
			case Constants.ANCESTOR_SELF_AXIS :
				temp = getAncestors(context, documents, contextSet);
				break;
			case Constants.SELF_AXIS :
				temp = contextSet;
				if (inPredicate) {
					if (contextSet instanceof VirtualNodeSet) {
						((VirtualNodeSet) contextSet).setInPredicate(true);
						((VirtualNodeSet) contextSet).setSelfIsContext();
					} else {
						NodeProxy p;
						for (Iterator i = temp.iterator(); i.hasNext();) {
							p = (NodeProxy) i.next();
							p.addContextNode(p);
						}
					}
				}
				break;
			case Constants.PARENT_AXIS :
				temp = getParents(context, documents, contextSet);
				break;
			case Constants.ATTRIBUTE_AXIS :
				temp = getAttributes(context, documents, contextSet);
				break;
			case Constants.PRECEDING_SIBLING_AXIS :
			case Constants.FOLLOWING_SIBLING_AXIS :
				temp = getSiblings(context, documents, contextSet);
				break;
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
		temp = (predicates.size() == 0) ? temp : applyPredicate(context, documents, temp);
		return new ValueNodeSet(temp);
	}

	protected NodeSet getAttributes(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		NodeSet result;
		switch (test.getType()) {
			case NodeTest.TYPE_TEST :
				result = new VirtualNodeSet(axis, (TypeTest) test, contextSet);
				((VirtualNodeSet) result).setInPredicate(inPredicate);
				break;
			case NodeTest.NAME_TEST :
				if (buf == null) {
					DBBroker broker = null;
					try {
						broker = pool.get();
						QName qname = QName.parse(test.getName());
						if (qname.getPrefix() != null) {
							String namespaceURI = context.getURIForPrefix(qname.getPrefix());
							if (namespaceURI == null)
								throw new IllegalArgumentException(
									"no namespace defined for prefix " + qname.getPrefix());
							LOG.debug(
								"using namespace \""
									+ namespaceURI
									+ "\" for prefix "
									+ qname.getPrefix());
							qname.setNamespaceURI(namespaceURI);
						}
						buf = (NodeSet) broker.getAttributesByName(documents, qname);
					} catch (EXistException e) {
						LOG.debug("exception while retrieving elements", e);
					} finally {
						pool.release(broker);
					}
				}
				result = ((ArraySet) buf).getChildren(contextSet, ArraySet.DESCENDANT, inPredicate);
				LOG.debug("found " + result.getLength() + " attributes");
				break;
			default :
				Node n;
				Node attr;
				NamedNodeMap map;
				result = new ArraySet(contextSet.getLength());
				for (int i = 0; i < contextSet.getLength(); i++) {
					n = contextSet.item(i);
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

	protected NodeSet getChildren(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (test.getType() == NodeTest.TYPE_TEST) {
			// test is one out of *, text(), node()
			VirtualNodeSet vset = new VirtualNodeSet(axis, (TypeTest) test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		} else {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null) {
					QName qname = QName.parse(test.getName());
					if (qname.getPrefix() != null) {
						String namespaceURI = context.getURIForPrefix(qname.getPrefix());
						if (namespaceURI == null)
							throw new IllegalArgumentException(
								"no namespace defined for prefix " + qname.getPrefix());
						LOG.debug(
							"using namespace \""
								+ namespaceURI
								+ "\" for prefix "
								+ qname.getPrefix());
						qname.setNamespaceURI(namespaceURI);
					}
					buf = (NodeSet) broker.findElementsByTagName(documents, qname);
				}
				return buf.getChildren(contextSet, ArraySet.DESCENDANT, inPredicate);
			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		}
	}

	protected NodeSet getDescendants(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null) {
					QName qname = QName.parse(test.getName());
					if (qname.getPrefix() != null) {
						String namespaceURI = context.getURIForPrefix(qname.getPrefix());
						if (namespaceURI == null)
							throw new IllegalArgumentException(
								"no namespace defined for prefix " + qname.getPrefix());
						LOG.debug(
							"using namespace \""
								+ namespaceURI
								+ "\" for prefix "
								+ qname.getPrefix());
						qname.setNamespaceURI(namespaceURI);
					}
					buf = (NodeSet) broker.findElementsByTagName(documents, qname);
				}
				return buf.getDescendants(
					contextSet,
					ArraySet.DESCENDANT,
					axis == Constants.DESCENDANT_SELF_AXIS,
					inPredicate);

			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		} else {
			VirtualNodeSet vset = new VirtualNodeSet(axis, (TypeTest) test, contextSet);
			vset.setInPredicate(inPredicate);
			return vset;
		}
	}

	protected NodeSet getSiblings(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null) {
					QName qname = QName.parse(test.getName());
					if (qname.getPrefix() != null) {
						String namespaceURI = context.getURIForPrefix(qname.getPrefix());
						if (namespaceURI == null)
							throw new IllegalArgumentException(
								"no namespace defined for prefix " + qname.getPrefix());
						LOG.debug(
							"using namespace \""
								+ namespaceURI
								+ "\" for prefix "
								+ qname.getPrefix());
						qname.setNamespaceURI(namespaceURI);
					}
					buf = (NodeSet) broker.findElementsByTagName(documents, qname);
				}
				return contextSet.getSiblings(
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
			ArraySet result = new ArraySet(contextSet.getLength());
			NodeProxy p;
			NodeImpl n;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				n = (NodeImpl) p.getNode();
				while ((n = getNextSibling(n)) != null) {
					if (((TypeTest) test).isOfType(n.getNodeType()))
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
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null) {
					QName qname = QName.parse(test.getName());
					if (qname.getPrefix() != null) {
						String namespaceURI = context.getURIForPrefix(qname.getPrefix());
						if (namespaceURI == null)
							throw new IllegalArgumentException(
								"no namespace defined for prefix " + qname.getPrefix());
						LOG.debug(
							"using namespace \""
								+ namespaceURI
								+ "\" for prefix "
								+ qname.getPrefix());
						qname.setNamespaceURI(namespaceURI);
					}
					buf = (NodeSet) broker.findElementsByTagName(documents, qname);
				}
				NodeSet r =
					contextSet.getAncestors(
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
			NodeSet result = new ArraySet(contextSet.getLength());
			NodeProxy p;
			for (Iterator i = contextSet.iterator(); i.hasNext();) {
				p = (NodeProxy) i.next();
				if (axis == Constants.ANCESTOR_SELF_AXIS
					&& ((TypeTest) test).isOfType(p, p.nodeType))
					result.add(new NodeProxy(p.doc, p.gid, p.internalAddress));
				while ((p.gid = XMLUtil.getParentId(p.doc, p.gid)) > 0) {
					if (((TypeTest) test).isOfType(p, Node.ELEMENT_NODE))
						result.add(new NodeProxy(p.doc, p.gid));
				}
			}
			return result;
		}
	}

	protected NodeSet getParents(
		StaticContext context,
		DocumentSet documents,
		NodeSet contextSet) {
		return contextSet.getParents();
	}

	public DocumentSet preselect(DocumentSet inDocs) {
		return super.preselect(inDocs);
	}

	public void setKeepVirtual(boolean virtual) {
		keepVirtual = virtual;
	}
}
