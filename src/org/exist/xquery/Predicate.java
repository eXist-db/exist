
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 *@author     Wolfgang Meier
 */
public class Predicate extends PathExpr {

    private final static int NODE = 0;
    private final static int BOOLEAN = 1;
    private final static int POSITIONAL = 2;
    
	private CachedResult cached = null;
	
	private int executionMode = BOOLEAN;
	
	public Predicate(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		if(getLength() == 1) {
			return getExpression(0).getDependencies();
		} else {
			return super.getDependencies();
        }
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.PathExpr#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        flags |= IN_PREDICATE; // set flag to signal subexpression that we are in a predicate
        Expression inner = getExpression(0);
        if(inner == null)
            return;
        int type = inner.returnsType();
        // Case 1: predicate expression returns a node set. Check the returned node set
		// against the context set and return all nodes from the context, for which the
		// predicate expression returns a non-empty sequence.
		if (Type.subTypeOf(type, Type.NODE)) {
			if((inner.getDependencies() & Dependency.CONTEXT_ITEM) == 0)
			    executionMode = NODE;
			else
			    executionMode = BOOLEAN;
		// Case 2: predicate expression returns a number.
		} else if (Type.subTypeOf(type, Type.NUMBER)) {
		    executionMode = POSITIONAL;
		// Case 3: predicate expression evaluates to a boolean.
		} else
		    executionMode = BOOLEAN;
		if(executionMode == BOOLEAN)
		    flags |= SINGLE_STEP_EXECUTION;
		super.analyze(parent, flags);
    }
    
	public Sequence evalPredicate(
		Sequence outerSequence,
		Sequence contextSequence,
		int mode)
		throws XPathException {
		Expression inner = getExpression(0);
		if (inner == null)
			return Sequence.EMPTY_SEQUENCE;
		switch(executionMode) {
			case NODE:
			    return selectByNodeSet(contextSequence);
			case POSITIONAL:
			    return selectByPosition(outerSequence, contextSequence, mode, inner);
			default:
			    return evalBoolean(contextSequence, inner);
		}
	}

	/**
	 * @param contextSequence
	 * @param inner
	 * @return
	 * @throws XPathException
	 */
	private Sequence evalBoolean(Sequence contextSequence, Expression inner) throws XPathException {
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
	}

	/**
	 * @param contextSequence
	 * @return
	 * @throws XPathException
	 */
	private Sequence selectByNodeSet(Sequence contextSequence) throws XPathException {
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		NodeSet contextSet = contextSequence.toNodeSet();
		boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
		
		NodeSet nodes =
			super.eval(contextSequence, null).toNodeSet();
		
		/* if the predicate expression returns results from the cache
		 * we can also return the cached result. 
		 */
		if(cached != null && cached.isValid(contextSequence) && nodes.isCached())
			return cached.getResult();
		
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
		if(contextSequence instanceof NodeSet)
			cached = new CachedResult((NodeSet)contextSequence, result);
		return result;
	}

	/**
	 * @param outerSequence
	 * @param contextSequence
	 * @param mode
	 * @param inner
	 * @return
	 * @throws XPathException
	 */
	private Sequence selectByPosition(Sequence outerSequence, Sequence contextSequence, int mode, Expression inner) throws XPathException {
		if(Type.subTypeOf(contextSequence.getItemType(), Type.NODE) && outerSequence != null &&
			outerSequence.getLength() > 0) {
			Sequence result = new ArraySet(100);
			NodeSet contextSet = contextSequence.toNodeSet();
			boolean reverseAxis = isReverseAxis(mode);
			if(!(reverseAxis || mode == Constants.FOLLOWING_SIBLING_AXIS 
					|| mode == Constants.SELF_AXIS)) {
				Sequence ancestors = contextSet.selectAncestorDescendant(outerSequence.toNodeSet(),
						NodeSet.ANCESTOR, true, true);
				for(SequenceIterator i = ancestors.iterate(); i.hasNext(); ) {
					Item item = i.nextItem();
				    NodeProxy p = (NodeProxy)item;
				    Sequence innerSeq = inner.eval(contextSequence);
				    for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
				        Item next = j.nextItem();
				        NumericValue v = (NumericValue)next.convertTo(Type.NUMBER);
				        ContextItem contextNode = p.getContext();
				        int count = 0;
				        while(contextNode != null) {
				        	if(++count == v.getInt())
				        		result.add(contextNode.getNode());
				        	contextNode = contextNode.getNextItem();
				        }
				    }
				}
			} else {
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
				    	    temp = p.getParents(false);
				    		break;
				    	case Constants.ANCESTOR_AXIS:
				    	case Constants.ANCESTOR_SELF_AXIS:
				    	    temp = contextSet.selectAncestors(p, false, false);
				    		break;
				    	case Constants.SELF_AXIS:
				    	    temp = p;
				    		break;
				    	default:
				    	    temp = contextSet.selectAncestorDescendant(p, NodeSet.DESCENDANT, false, false);
				    		break;
				    }
				    Sequence innerSeq = inner.eval(contextSequence);
				    for(SequenceIterator j = innerSeq.iterate(); j.hasNext(); ) {
				        Item next = j.nextItem();
				        NumericValue v = (NumericValue)next.convertTo(Type.NUMBER);
				        int pos = (reverseAxis ? temp.getLength() - v.getInt() : v.getInt() - 1);
				        if(pos < temp.getLength() && pos > -1)
				            result.add(temp.itemAt(pos));
				    }
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
	}

	public final static boolean isReverseAxis(int axis) {
	    return axis < Constants.CHILD_AXIS;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		cached = null;
	}
}
