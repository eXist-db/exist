
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
package org.exist.xquery;

import java.util.Iterator;

import org.exist.dom.ArraySet;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 *  Handles predicate expressions.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class Predicate extends PathExpr {

	public Predicate(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#getDependencies()
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
	
	public Sequence evalPredicate(
		Sequence outerSequence,
		Sequence contextSequence,
		int mode)
		throws XPathException {
		setInPredicate(true);
		//long start = System.currentTimeMillis();
		Expression inner = getExpression(0);
		if (inner == null)
			return Sequence.EMPTY_SEQUENCE;
		int type = inner.returnsType();
		
		// Case 1: predicate expression returns a node set. Check the returned node set
		// against the context set and return all nodes from the context, for which the
		// predicate expression returns a non-empty sequence.
		if (Type.subTypeOf(type, Type.NODE)) {
			ExtArrayNodeSet result = new ExtArrayNodeSet();
			NodeSet contextSet = contextSequence.toNodeSet();
			boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
			NodeSet nodes =
				super.eval(contextSequence, null).toNodeSet();
			NodeProxy current;
			ContextItem contextNode;
			NodeProxy next;
			DocumentImpl lastDoc = null;
			int count = 0, sizeHint = -1;
			for (Iterator i = nodes.iterator(); i.hasNext(); count++) {
				current = (NodeProxy) i.next();
				if(lastDoc == null || current.getDocument() != lastDoc) {
					lastDoc = current.getDocument();
					sizeHint = nodes.getSizeHint(lastDoc);
				}
				contextNode = current.getContext();
				if (contextNode == null) {
					throw new XPathException("Internal evaluation error: context node is missing for node " +
						current.gid + "!");
				}
				while (contextNode != null) {
					next = contextNode.getNode();
					if(contextIsVirtual || contextSet.contains(next)) {
						next.addMatches(current);
						result.add(next, sizeHint);
					}
					contextNode = contextNode.getNextItem();
				}
			}
			return result;
		
		// Case 2: predicate expression returns a boolean. Call the
		// predicate expression for each item in the context. Add the item
		// to the result if the predicate expression yields true.
		} else if (
			Type.subTypeOf(type, Type.BOOLEAN)) {
			Sequence result = new ValueSequence();
			int p = 0;
			context.setContextPosition(0);
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); p++) {
				Item item = i.nextItem();
				context.setContextPosition(p);
				Sequence innerSeq = inner.eval(contextSequence, item);
//				LOG.debug("innerSeq = " + innerSeq.effectiveBooleanValue());
				if(innerSeq.effectiveBooleanValue())
					result.add(item);
			}
			return result;
			
		// Case 3: predicate expression returns a number. Call the predicate
		// expression once for each item in the context set.
		} else if (Type.subTypeOf(type, Type.NUMBER)) {
			if(Type.subTypeOf(contextSequence.getItemType(), Type.NODE) && outerSequence != null &&
				outerSequence.getLength() > 0) {
				Sequence result = new ArraySet(100);
				NodeSet contextSet = contextSequence.toNodeSet();
				for(SequenceIterator i = outerSequence.iterate(); i.hasNext(); ) {
				    Item item = i.nextItem();
				    NodeProxy p = (NodeProxy)item;
				    Sequence temp;
				    switch(mode) {
				    	case Constants.FOLLOWING_SIBLING_AXIS:
				    	    temp = contextSet.selectSiblings(p, NodeSet.FOLLOWING);
			    			break;
				    	case Constants.PRECEDING_SIBLING_AXIS:
				    	    temp = contextSet.selectSiblings(p, NodeSet.PRECEDING);
				    		break;
				    	case Constants.PARENT_AXIS:
				    	    temp = p.getParents();
				    		break;
				    	case Constants.ANCESTOR_AXIS:
				    	case Constants.ANCESTOR_SELF_AXIS:
				    	    temp = contextSet.selectAncestors(p, false, false);
				    		break;
				    	case Constants.SELF_AXIS:
				    	    temp = p;
				    		break;
				    	default:
				    	    temp = contextSet.selectAncestorDescendant(p, NodeSet.DESCENDANT);
				    		break;
				    }
				    boolean reverseAxis = isReverseAxis(mode);
				    Sequence innerSeq = inner.eval(contextSequence);
				    for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
				        Item next = j.nextItem();
				        NumericValue v = (NumericValue)next.convertTo(Type.NUMBER);
				        int pos = (reverseAxis ? temp.getLength() - v.getInt() : v.getInt() - 1);
				        if(pos < temp.getLength() && pos > -1)
				            result.add(temp.itemAt(pos));
				    }
				}
				return result;
			} else {
				Sequence innerSeq = inner.eval(contextSequence);
				ValueSequence result = new ValueSequence();
				for(SequenceIterator i = innerSeq.iterate(); i.hasNext(); ) {
					NumericValue v = (NumericValue)i.nextItem().convertTo(Type.NUMBER);
					int pos = v.getInt() - 1;
					if(pos < contextSequence.getLength() && pos > -1)
						result.add(contextSequence.itemAt(pos));
				}
				return result;
			}
		} else
			LOG.debug("unable to determine return type of predicate expression");
		return Sequence.EMPTY_SEQUENCE;
	}

	public final static boolean isReverseAxis(int axis) {
	    return axis < Constants.CHILD_AXIS;
	}
}
