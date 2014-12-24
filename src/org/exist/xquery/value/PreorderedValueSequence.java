/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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
package org.exist.xquery.value;

import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;

/**
 * A sequence that sorts its items in the order specified by the order specs
 * of an "order by" clause. Used by {@link org.exist.xquery.ForExpr}.
 * 
 * For better performance, the whole input sequence is sorted in one single step.
 * However, this only works if every order expression returns a result of type
 * node.
 * 
 * @author wolf
 */
public class PreorderedValueSequence extends AbstractSequence {
	
	private OrderSpec orderSpecs[];
	private OrderedNodeProxy[] nodes;
	
	public PreorderedValueSequence(OrderSpec specs[], Sequence input, int contextId) throws XPathException {
		this.orderSpecs = specs;
		nodes = new OrderedNodeProxy[input.getItemCount()];
		int j = 0;
		for(final SequenceIterator i = input.unorderedIterator(); i.hasNext(); j++) {
			final NodeProxy p = (NodeProxy)i.nextItem();
			nodes[j] = new OrderedNodeProxy(p);
			p.addContextNode(contextId, nodes[j]);
		}
		processAll();
	}
	
	private void processAll() throws XPathException {
		for(int i = 0; i < orderSpecs.length; i++) {
			final Expression expr = orderSpecs[i].getSortExpression();
			final NodeSet result = expr.eval(null).toNodeSet();
			for(final Iterator j = result.iterator(); j.hasNext(); ) {
				final NodeProxy p = (NodeProxy)j.next();
				ContextItem context = p.getContext();
				//TODO : review to consider transverse context
				while(context != null) {
					if(context.getNode() instanceof OrderedNodeProxy) {
						final OrderedNodeProxy cp = (OrderedNodeProxy)context.getNode();
						cp.values[i] = p.atomize();
					}
					context = context.getNextDirect();
				}
			}
		}
	}

    public void clearContext(int contextId) throws XPathException {
        for (int i = 0; i < nodes.length; i++) {
            nodes[i].clearContext(contextId);
        }
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#getItemType()
	 */
	public int getItemType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
		sort();
		return new PreorderedValueSequenceIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() throws XPathException{
		return new PreorderedValueSequenceIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#getLength()
	 */
	public int getItemCount() {
		return nodes.length;
	}
	
    public boolean isEmpty() {
    	return nodes.length == 0;
    }

    public boolean hasOne() {
    	return nodes.length == 1;
    }    

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return nodes[pos];
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		return null;
	}

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return null;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // TODO: is this ever relevant?
    }
    
	private void sort() {
		Arrays.sort(nodes, new OrderedComparator());
	}
	
	private class OrderedComparator implements Comparator<OrderedNodeProxy> {
		
		/* (non-Javadoc)
		 * @see java.util.Comparator#compare(java.lang.Object, java.lang.Object)
		 */
		public int compare(OrderedNodeProxy p1, OrderedNodeProxy p2) {
			int cmp = 0;
			AtomicValue a, b;
			for(int i = 0; i < p1.values.length; i++) {
				try {
					a = p1.values[i];
					b = p2.values[i];
					if(a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							{cmp = Constants.INFERIOR;}
						else
							{cmp = Constants.SUPERIOR;}
					} else if(a != AtomicValue.EMPTY_VALUE && b == AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							{cmp = Constants.SUPERIOR;}
						else
							{cmp = Constants.INFERIOR;}
					} else
						{cmp = a.compareTo(orderSpecs[i].getCollator(), b);}
					if((orderSpecs[i].getModifiers() & OrderSpec.DESCENDING_ORDER) != 0)
						{cmp = cmp * -1;}
					if(cmp != Constants.EQUAL)
						{break;}
				} catch (final XPathException e) {
				}
			}
			return cmp;
		}
	}
	
	private class OrderedNodeProxy extends NodeProxy {
		
		AtomicValue[] values;
		
		public OrderedNodeProxy(NodeProxy p) {
			super(p);
			values = new AtomicValue[orderSpecs.length];
			for(int i = 0; i < values.length; i++)
				values[i] = AtomicValue.EMPTY_VALUE;
		}
	}
	
	private class PreorderedValueSequenceIterator implements SequenceIterator {
		
		int pos = 0;
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return pos < nodes.length;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if(pos < nodes.length)
				{return nodes[pos++];}
			return null;
		}
	}
}
