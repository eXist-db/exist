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
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    30. September 2002
 */
public class LocationStep extends Step {

	protected static Category LOG =
		Category.getInstance(LocationStep.class.getName());
	protected NodeSet buf = null;
	protected boolean keepVirtual = false;

	/**
	 *  Constructor for the LocationStep object
	 *
	 *@param  axis  Description of the Parameter
	 */
	public LocationStep(BrokerPool pool, int axis) {
		super(pool, axis);
	}

	/**
	 *  Constructor for the LocationStep object
	 *
	 *@param  axis  Description of the Parameter
	 *@param  test  Description of the Parameter
	 */
	public LocationStep(BrokerPool pool, int axis, NodeTest test) {
		super(pool, axis, test);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@return            Description of the Return Value
	 */
	protected NodeSet applyPredicate(DocumentSet documents, NodeSet context) {
		Predicate pred;
		NodeSet result = context;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result = (NodeSet) pred.eval(documents, result, null).getNodeList();
		}
		return result;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@param  node       Description of the Parameter
	 *@return            Description of the Return Value
	 */
	public Value eval(DocumentSet documents, NodeSet context, NodeProxy node) {
		NodeSet temp;
		switch (axis) {
			case Constants.DESCENDANT_AXIS :
				temp = getDescendants(documents, context);
				break;
			case Constants.CHILD_AXIS :
				temp = getChildren(documents, context);
				break;
			case Constants.SELF_AXIS :
				temp = context;
				break;
			case Constants.PARENT_AXIS :
				temp = getParents(documents, context);
				break;
			case Constants.ATTRIBUTE_AXIS :
				temp = getAttributes(documents, context);
				break;
			default :
				throw new IllegalArgumentException("Unsupported axis specified");
		}
		temp =
			(predicates.size() == 0) ? temp : applyPredicate(documents, temp);
		return new ValueNodeSet(temp);
	}

	/**
	 *  Gets the attributes attribute of the LocationStep object
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@return            The attributes value
	 */
	protected NodeSet getAttributes(DocumentSet documents, NodeSet context) {
		ArraySet result = new ArraySet(5);
		switch (test.getType()) {
			case NodeTest.TYPE_TEST :
				return new VirtualNodeSet(axis, (TypeTest) test, context);
			case NodeTest.NAME_TEST :
				if (buf == null) {
					DBBroker broker = null;
					try {
						broker = pool.get();
						buf =
							(NodeSet) broker.getAttributesByName(
								documents,
								test.getName());
					} catch (EXistException e) {
						LOG.debug("exception while retrieving elements", e);
					} finally {
						pool.release(broker);
					}
				}
				result = ( (ArraySet) buf ).getChildren( context,
				    ArraySet.DESCENDANT);
				return result;
				//NodeProxy p;
				//for (Iterator iter = buf.iterator(); iter.hasNext();) {
				//	p = (NodeProxy) iter.next();
				//	if (context.nodeHasParent(p.doc, p.gid, true, true))
				//		result.add(p);
				//}
				//break;
			default :
				Node n;
				Node attr;
				NamedNodeMap map;
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

	/**
	 *  Gets the children attribute of the LocationStep object
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@return            The children value
	 */
	protected NodeSet getChildren(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.TYPE_TEST)
			// test is one out of *, text(), node()
			return new VirtualNodeSet(axis, (TypeTest) test, context);
		else {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf =
						(NodeSet) broker.findElementsByTagName(
							documents,
							test.getName());
				long start = System.currentTimeMillis();
				ArraySet result;
				/*if (context instanceof VirtualNodeSet) {
					result = new ArraySet(buf.getLength());
					NodeProxy p;
					for (Iterator iter = buf.iterator(); iter.hasNext();) {
						p = (NodeProxy) iter.next();
						if (p.gid == p.doc.getDocumentElementId()
							|| context.nodeHasParent(p, true))
							result.add(p);
					}
				} else {*/
					result = buf.getChildren(context, NodeSet.DESCENDANT);
				//}

				// for every node in the list of element-occurrences, check if it has
				// a parent in the context node-set:
				//                
				LOG.debug(
					"getChildren found "
						+ result.getLength()
						+ " in "
						+ (System.currentTimeMillis() - start));
				return result;

			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		}
	}

	/**
	 *  Gets the descendants attribute of the LocationStep object
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@return            The descendants value
	 */
	protected NodeSet getDescendants(DocumentSet documents, NodeSet context) {
		if (test.getType() == NodeTest.NAME_TEST) {
			DBBroker broker = null;
			try {
				broker = pool.get();
				if (buf == null)
					buf =
						(NodeSet) broker.findElementsByTagName(
							documents,
							test.getName());
				ArraySet result = null;
				/*if (context instanceof VirtualNodeSet) {
					long start = System.currentTimeMillis();
					NodeProxy current;
					result = new ArraySet(buf.getLength());
					//( (ArraySet) result ).setIsSorted( true );
					for (Iterator iter = buf.iterator(); iter.hasNext();) {
						current = (NodeProxy) iter.next();
						if (current != null
							&& context.nodeHasParent(
								current.doc,
								current.gid,
								false))
							result.add(current);
					}
					LOG.debug(
						"getDescendants found "
							+ result.getLength()
							+ " in "
							+ (System.currentTimeMillis() - start));
				} else {*/
					result =
						((ArraySet) buf).getDescendants(
							(ArraySet) context,
							ArraySet.DESCENDANT);
				//}
				return result;
			} catch (EXistException e) {
				e.printStackTrace();
				return null;
			} finally {
				pool.release(broker);
			}
		} else
			return new VirtualNodeSet(axis, (TypeTest) test, context);
	}

	/**
	 *  Gets the parents attribute of the LocationStep object
	 *
	 *@param  documents  Description of the Parameter
	 *@param  context    Description of the Parameter
	 *@return            The parents value
	 */
	protected NodeSet getParents(DocumentSet documents, NodeSet context) {
		return context.getParents();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  in_docs  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	public DocumentSet preselect(DocumentSet in_docs) {
		DocumentSet out_docs = super.preselect(in_docs);

		/*
		 *  if this is a name test and axis is child or descendant, we are
		 *  able to restrict the range of valid documents
		 *  if(out_docs.getLength() == in_docs.getLength() &&
		 *  test.getType() == NodeTest.NAME_TEST &&
		 *  (axis == Constants.CHILD_AXIS || axis == Constants.DESCENDANT_AXIS)) {
		 *  buf = broker.findElementsByTagName(out_docs, test.getName());
		 *  out_docs = new DocumentSet();
		 *  NodeProxy p;
		 *  for(Iterator i = buf.iterator(); i.hasNext(); ) {
		 *  p = (NodeProxy)i.next();
		 *  if(!out_docs.contains(p.doc.getDocId()))
		 *  out_docs.add(p.doc);
		 *  }
		 *  }
		 */
		return out_docs;
	}

	/**
	 *  Sets the keepVirtual attribute of the LocationStep object
	 *
	 *@param  virtual  The new keepVirtual value
	 */
	public void setKeepVirtual(boolean virtual) {
		keepVirtual = virtual;
	}
}
