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
package org.exist.xquery.value;

import org.exist.dom.NodeSet;
import org.exist.util.FastQSort;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;

/**
 * A sequence that sorts its entries in the order specified by the order specs of
 * an "order by" clause. Used by {@link org.exist.xquery.ForExpr}.
 * 
 * Contrary to class {@link org.exist.xquery.value.PreorderedValueSequence},
 * all order expressions are evaluated once for each item in the sequence 
 * <b>while</b> items are added.
 * 
 * @author wolf
 */
public class OrderedValueSequence extends AbstractSequence {

	private OrderSpec orderSpecs[];
	private Entry[] items = null;
	private int count = 0;
	
	private long execTime = 0;
	
	public OrderedValueSequence(OrderSpec orderSpecs[], int size) {
		this.orderSpecs = orderSpecs;
		this.items = new Entry[size];
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.ATOMIC;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		return new OrderedValueSequenceIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return new OrderedValueSequenceIterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getLength() {
		return (items == null) ? 0 : count;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
		if(count == items.length) {
			Entry newItems[] = new Entry[count * 2];
			System.arraycopy(items, 0, newItems, 0, count);
			items = newItems;
		}
		items[count++] = new Entry(item);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#addAll(org.exist.xquery.value.Sequence)
	 */
	public void addAll(Sequence other) throws XPathException {
		if(other.getLength() > 0) {
			Item next;
			for(SequenceIterator i = other.iterate(); i.hasNext(); ) {
				next = i.nextItem();
				if(next != null)
					add(next);
			}
		} else if(other.getLength() == 1)
			add(other.itemAt(0));
	}
	
	public void sort() {
		FastQSort.sort(items, 0, count - 1);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		if(items != null && pos > -1 && pos < count)
			return items[pos].item;
		else
			return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		throw new XPathException("Operation not supported");
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // TODO: is this ever relevant?
    }
    
	private class Entry implements Comparable {
		
		Item item;
		AtomicValue values[];
		
		public Entry(Item item) throws XPathException {
			long start = System.currentTimeMillis();
			this.item = item;
			values = new AtomicValue[orderSpecs.length];
			for(int i = 0; i < orderSpecs.length; i++) {
				Sequence seq = orderSpecs[i].getSortExpression().eval(null);
				values[i] = AtomicValue.EMPTY_VALUE;
				if(seq.getLength() == 1) {
					values[i] = seq.itemAt(0).atomize();
				} else if(seq.getLength() > 1)
					throw new XPathException("expected a single value for order expression " +
						orderSpecs[i].getSortExpression().pprint() + " ; found: " + seq.getLength());
			}
			execTime = execTime + (System.currentTimeMillis() - start);
		}

		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			Entry other = (Entry)o;
			int cmp = 0;
			AtomicValue a, b;
			for(int i = 0; i < values.length; i++) {
				try {
					a = values[i];
					b = other.values[i];
					if(a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = -1;
						else
							cmp = 1;
					} else if(b == AtomicValue.EMPTY_VALUE && a != AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = 1;
						else
							cmp = -1;
					} else
						cmp = a.compareTo(orderSpecs[i].getCollator(), b);
					if((orderSpecs[i].getModifiers() & OrderSpec.DESCENDING_ORDER) != 0)
						cmp = cmp * -1;
					if(cmp != 0)
						break;
				} catch (XPathException e) {
				}
			}
			return cmp;
		}
	}
	
	private class OrderedValueSequenceIterator implements SequenceIterator {
		
		int pos = 0;
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return pos < count;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if(pos < count) {
				return items[pos++].item;
			}
			return null;
		}
	}
}
