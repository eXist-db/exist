/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.dom.ContextItem;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.VirtualNodeSet;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Abstract superclass for the variable binding expressions "for" and "let".
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public abstract class BindingExpression extends AbstractExpression {

	protected final static Logger LOG =
		Logger.getLogger(BindingExpression.class);

	protected String varName;
	protected SequenceType sequenceType = null;
	protected Expression inputSequence;
	protected Expression returnExpr;
	protected Expression whereExpr;
	protected OrderSpec orderSpecs[] = null;

	public BindingExpression(XQueryContext context) {
		super(context);
	}

	public void setVariable(String qname) {
		varName = qname;
	}
	
	/**
	 * Set the sequence type of the variable (as specified in the "as" clause).
	 * 
	 * @param type
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}

	public void setInputSequence(Expression sequence) {
		this.inputSequence = sequence;
	}

	public void setReturnExpression(Expression expr) {
		this.returnExpr = expr;
	}

	public void setWhereExpression(Expression expr) {
		this.whereExpr = expr;
	}

	public void setOrderSpecs(OrderSpec specs[]) {
		this.orderSpecs = specs;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		return eval(contextSequence, contextItem, null);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem,
		Sequence resultSequence)
		throws XPathException;

	protected Sequence applyWhereExpression(
		Sequence contextSequence)
		throws XPathException {
//		long start = System.currentTimeMillis();
		whereExpr.setInPredicate(true);
		if (Type.subTypeOf(whereExpr.returnsType(), Type.NODE) &&
			(contextSequence == null || 
			Type.subTypeOf(contextSequence.getItemType(), Type.NODE))) {
			// if the where expression returns a node set, check the context
			// node of each node in the set
			NodeSet contextSet = contextSequence.toNodeSet();
			boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
			NodeSet nodes = whereExpr.eval(contextSequence).toNodeSet();
			NodeProxy current;
			ContextItem contextNode;
			NodeProxy next;
			DocumentImpl lastDoc = null;
			int count = 0, sizeHint = -1;
			NodeSet result = new ExtArrayNodeSet();
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
				while (contextNode != null) {
					next = contextNode.getNode();
					if(contextIsVirtual || contextSet.contains(next)) {
						next.addMatches(current);
						result.add(next, sizeHint);
					}
					contextNode = contextNode.getNextItem();
				}
			}
//			LOG.debug("where expression took " + (System.currentTimeMillis() - start));
			return result;
		} else if (contextSequence == null) {
			Sequence innerSeq = whereExpr.eval(null);
			return innerSeq.effectiveBooleanValue() ? BooleanValue.TRUE : BooleanValue.FALSE;
		} else {
			// general where clause: just check the effective boolean value
			ValueSequence result = new ValueSequence();
			int p = 0;
			context.setContextPosition(0);
			for (SequenceIterator i = contextSequence.iterate();
				i.hasNext();
				p++) {
				Item item = i.nextItem();
				context.setContextPosition(p);
				Sequence innerSeq = whereExpr.eval(contextSequence, item);
				if (innerSeq.effectiveBooleanValue())
					result.add(item);
			}
			return result;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#preselect(org.exist.dom.DocumentSet, org.exist.xquery.StaticContext)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public abstract String pprint();

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		inputSequence.resetState();
		if(whereExpr != null) whereExpr.resetState();
		returnExpr.resetState();
		if(orderSpecs != null) {
		    for(int i = 0; i < orderSpecs.length; i++) {
		        orderSpecs[i].resetState();
		    }
		}
	}
}
