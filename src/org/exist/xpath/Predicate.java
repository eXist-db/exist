
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

import org.exist.dom.AVLTreeNodeSet;
import org.exist.dom.ArraySet;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.XMLUtil;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;

/**
 *  Handles predicate expressions.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class Predicate extends PathExpr {

	public Predicate(StaticContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		if(getLength() == 1) {
            getExpression(0).setInPredicate(true);
			//if(Type.subTypeOf(getExpression(0).returnsType(), Type.NODE)) {
				return getExpression(0).getDependencies();
			//} else {
			//	return Dependency.CONTEXT_ITEM + Dependency.CONTEXT_SET;
            //}
		} else {
			return super.getDependencies();
        }
	}
	
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		setInPredicate(true);
		//long start = System.currentTimeMillis();
		Expression inner = getExpression(0);
		if (inner == null)
			return Sequence.EMPTY_SEQUENCE;
		int type = inner.returnsType();
		//LOG.debug("inner expr " + inner.pprint() + " returns " + Type.getTypeName(type));
		
		// Case 1: predicate expression returns a node set. Check the returned node set
		// against the context set and return all nodes from the context, for which the
		// predicate expression returns a non-empty sequence.
		if (Type.subTypeOf(type, Type.NODE)) {
			ExtArrayNodeSet result = new ExtArrayNodeSet();
			NodeSet nodes =
				super.eval(docs, contextSequence, null).toNodeSet();
			NodeProxy current;
			ContextItem contextNode;
			NodeProxy next;
			DocumentImpl lastDoc = null;
			int count = 0, sizeHint = -1;
			for (Iterator i = nodes.iterator(); i.hasNext(); count++) {
				current = (NodeProxy) i.next();
				if(lastDoc == null || current.doc != lastDoc) {
					lastDoc = current.doc;
					sizeHint = nodes.getSizeHint(lastDoc);
				}
				contextNode = current.getContext();
				if (contextNode == null) {
					throw new XPathException("Internal evaluation error: context node is missing for node " +
						current.gid + "!");
				}
				int c = 0;
				while (contextNode != null) {
					c++;
					next = contextNode.getNode();
					next.addMatches(current.match);
					if (!result.contains(next))
						result.add(next, sizeHint);
					contextNode = contextNode.getNextItem();
				}
			}
			return result;
		
		// Case 2: predicate expression returns a boolean. Call the
		// predicate expression for each item in the context. Add the item
		// to the result if the predicate expression yields true.
		} else if (
			Type.subTypeOf(type, Type.BOOLEAN)) {
			NodeSet result = new ExtArrayNodeSet();
			int p = 0;
			context.setContextPosition(0);
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
				Item item = i.nextItem();
				context.setContextPosition(p);
				Sequence innerSeq = inner.eval(docs, contextSequence, item);
				if(innerSeq.effectiveBooleanValue())
					result.add(item);
			}
			return result;
			
		// Case 3: predicate expression returns a number. Call the predicate
		// expression once for each item in the context set.
		} else if (Type.subTypeOf(type, Type.NUMBER)) {
			Sequence result = new ArraySet(100);
			long last = -1;
			DocumentImpl lastDoc = null;
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
				NodeProxy p = (NodeProxy) i.nextItem();
				
				Sequence innerSeq = inner.eval(docs, p);
				int level = p.doc.getTreeLevel(p.gid);
				long pid = XMLUtil.getParentId(p.doc, p.gid, level);
				if(pid == last && lastDoc != null && lastDoc.getDocId() == p.doc.getDocId())
					continue;
				long firstChild = XMLUtil.getFirstChildId(p.doc, pid);
				long lastChild = firstChild + p.doc.getTreeLevelOrder(level);
				
				Sequence sub = ((NodeSet)contextSequence).getRange(p.doc, firstChild, lastChild);
				for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
					NumericValue v = (NumericValue)j.nextItem().convertTo(Type.NUMBER);
					int pos = v.getInt() - 1;
					if(pos < sub.getLength() && pos > -1)
						result.add(sub.itemAt(pos));
				}
				last = pid;
				lastDoc = p.doc;
			}
			return result;
		} else
			LOG.debug("unable to determine return type of predicate expression");
		return Sequence.EMPTY_SEQUENCE;
	}

}
