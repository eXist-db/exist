
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 *  You should have received a copy of the GNU Library General Public
 *  License along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xpath;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.util.LongLinkedList;

/**
 *  Handles predicate expressions.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class Predicate extends PathExpr {

	public Predicate() {
		super();
	}

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, NodeProxy contextNode) throws XPathException {
		//long start = System.currentTimeMillis();
		ArraySet result = new ArraySet(100);
		Expression first = getExpression(0);
		if (first == null)
			return new ValueNodeSet(contextSet);
		if(contextNode != null) 
			contextSet = new SingleNodeSet(contextNode);
		switch (first.returnsType()) {
			case Constants.TYPE_NODELIST :
				{
					setInPredicate(true);
					NodeSet nodes = (NodeSet) super.eval(context, docs, contextSet, null).getNodeList();
					NodeProxy current, parent;
					LongLinkedList contextNodes;
					LongLinkedList.ListItem next;
					for(Iterator i = nodes.iterator(); i.hasNext(); ) {
						current = (NodeProxy)i.next();
						contextNodes = current.getContext();
						if(contextNodes == null) {
							throw new XPathException("Internal evaluation error: context node is missing!");
						}
						for(Iterator j = contextNodes.iterator(); j.hasNext(); ) {
							next = (LongLinkedList.ListItem)j.next();
							if((parent = contextSet.get(current.doc, next.l)) != null) {
								parent.addMatches(current.matches);
								if(!result.contains(parent))
									result.add(parent);
							}
						}
					}
					break;
				}
			case Constants.TYPE_BOOL :
			case Constants.TYPE_STRING :
				{
					//string has no special meaning

					NodeProxy p;
					NodeSet set;
					DocumentSet dset;
					Value v;
					for (Iterator i = contextSet.iterator(); i.hasNext();) {
						p = (NodeProxy) i.next();
						set = new ArraySet(1);
						set.add(p);
						dset = new DocumentSet();
						dset.add(p.doc);
						v = first.eval(context, dset, set, p);
						if (v.getBooleanValue())
							result.add(p);
					}
					break;
				}
			case Constants.TYPE_NUM :
				{
					NodeProxy p;
					NodeProxy n;
					NodeSet set;
					int level;
					int count;
					double pos;
					long pid;
					long last_pid = 0;
					long f_gid;
					long e_gid;
					DocumentImpl doc;
					DocumentImpl last_doc = null;
					// evaluate predicate expression for each context node
					for (Iterator i = contextSet.iterator(); i.hasNext();) {
						p = (NodeProxy) i.next();
						pos = first.eval(context, docs, contextSet, p).getNumericValue();
						doc = (DocumentImpl) p.getDoc();
						level = doc.getTreeLevel(p.getGID());
						pid =
							(p.getGID() - doc.getLevelStartPoint(level))
								/ doc.getTreeLevelOrder(level)
								+ doc.getLevelStartPoint(level - 1);
						if (pid == last_pid
							&& last_doc != null
							&& doc.getDocId() == last_doc.getDocId())
							continue;
						last_pid = pid;
						last_doc = doc;
						f_gid =
							(pid - doc.getLevelStartPoint(level - 1))
								* doc.getTreeLevelOrder(level)
								+ doc.getLevelStartPoint(level);
						e_gid = f_gid + doc.getTreeLevelOrder(level);

						count = 1;
						set = contextSet.getRange(doc, f_gid, e_gid);
						for (Iterator j = set.iterator(); j.hasNext(); count++) {
							n = (NodeProxy) j.next();
							if (count == pos) {
								result.add(n);
								break;
							}
						}
					}
				}
		}
//		LOG.debug(
//			"predicate expression found "
//				+ result.getLength()
//				+ " in "
//				+ (System.currentTimeMillis() - start)
//				+ "ms.");
		return new ValueNodeSet(result);
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		DocumentSet docs = in_docs;
		for (Iterator iter = steps.iterator(); iter.hasNext();)
			docs = ((Expression) iter.next()).preselect(docs, context);
		return docs;
	}

	public Value evalBody(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		if (docs.getLength() == 0)
			return new ValueNodeSet(NodeSet.EMPTY_SET);
		Value r;
		if (contextSet != null)
			r = new ValueNodeSet(contextSet);
		else
			r = new ValueNodeSet(NodeSet.EMPTY_SET);
		NodeSet set;
		Expression expr;
		for (Iterator iter = steps.iterator(); iter.hasNext();) {
			set = (NodeSet) r.getNodeList();
			expr = (Expression) iter.next();
			LOG.debug("processing " + expr.pprint());
			if (expr.returnsType() != Constants.TYPE_NODELIST) {
				if (expr instanceof Literal || expr instanceof IntNumber)
					return expr.eval(context, docs, set, null);
				ValueSet values = new ValueSet();
				for (Iterator iter2 = set.iterator(); iter2.hasNext();)
					values.add(expr.eval(context, docs, set, (NodeProxy) iter2.next()));
				return values;
			}
			r = expr.eval(context, docs, set, contextNode);
		}
		return r;
	}
}
