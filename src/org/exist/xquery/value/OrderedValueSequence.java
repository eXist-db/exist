/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.AVLTreeNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.util.FastQSort;
import org.exist.xquery.Constants;
import org.exist.xquery.OrderSpec;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.ExpressionDumper;

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
	
	// used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;
    
	public OrderedValueSequence(OrderSpec orderSpecs[], int size) {
		this.orderSpecs = orderSpecs;
		this.items = new Entry[size];
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
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
	public int getItemCount() {
		return (items == null) ? 0 : count;
	}
	
	public boolean isEmpty() {
		return isEmpty;
	}

    public boolean hasOne() {
    	return hasOne;
    }

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
		if (hasOne)
			hasOne = false;
		if (isEmpty)
			hasOne = true;
        isEmpty = false;
		if(count == 0 && items.length == 1) {
			items = new Entry[2];
		} else if (count == items.length) {
			Entry newItems[] = new Entry[count * 2];
			System.arraycopy(items, 0, newItems, 0, count);
			items = newItems;
		}
		items[count++] = new Entry(item);
		checkItemType(item.getType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#addAll(org.exist.xquery.value.Sequence)
	 */
	public void addAll(Sequence other) throws XPathException {
		if(other.hasOne())
			add(other.itemAt(0));		
		else if(!other.isEmpty()) {
			for(SequenceIterator i = other.iterate(); i.hasNext(); ) { 
				Item next = i.nextItem();
				if(next != null)
					add(next);
			}
		} 
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

	private void checkItemType(int type) {
        if(itemType == Type.NODE || itemType == type)
            return;
        if(itemType == Type.ANY_TYPE)
            itemType = type;
        else
            itemType = Type.NODE;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    public int getItemType() {
        return itemType;
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		//return early
		if (isEmpty())
			return NodeSet.EMPTY_SET;
        // for this method to work, all items have to be nodes
		if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
			//Was ExtArrayNodeset() which orders the nodes in document order
			//The order seems to change between different invocations !!!
			NodeSet set = new AVLTreeNodeSet();	
			//We can't make it from an ExtArrayNodeSet (probably because it is sorted ?)
			//NodeSet set = new ArraySet(100);
			for (int i = 0; i < items.length; i++) {
				//TODO : investigate why we could have null here
				if (items[i] != null) {
				
					NodeValue v = (NodeValue)items[i].item;
					if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
						
						/*
	                    // found an in-memory document
	                    DocumentImpl doc = ((NodeImpl)v).getDocument();
	                    // make this document persistent: doc.makePersistent()
	                    // returns a map of all root node ids mapped to the corresponding
	                    // persistent node. We scan the current sequence and replace all
	                    // in-memory nodes with their new persistent node objects.
	                    Int2ObjectHashMap newRoots = doc.makePersistent();
	                    for (int j = i; j < items.length; j++) {
	                        v = (NodeValue) items[j];
	                        if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
	                            NodeImpl node = (NodeImpl) v;
	                            if (node.getDocument() == doc) {
	                                NodeProxy p = (NodeProxy) newRoots.get(node.getNodeNumber());
	                                if (p != null) {
	                                    // replace the node by the NodeProxy
	                                    items[j] = p;
	                                }
	                            }
	                        }
	                    }
	                    */
						
	                    set.add((NodeProxy)v);
					} else {
						set.add((NodeProxy)v);
					}
				}
			}			
			return set;
		} else
			throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));

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
			this.item = item;
			values = new AtomicValue[orderSpecs.length];
			for(int i = 0; i < orderSpecs.length; i++) {
				Sequence seq = orderSpecs[i].getSortExpression().eval(null);
				values[i] = AtomicValue.EMPTY_VALUE;
				if(seq.hasOne()) {
					values[i] = seq.itemAt(0).atomize();
				} else if(seq.hasMany())
					throw new XPathException("expected a single value for order expression " +
						ExpressionDumper.dump(orderSpecs[i].getSortExpression()) + 
						" ; found: " + seq.getItemCount());
			}
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
                    if ((a.isEmpty() || (Type.subTypeOf(a.getType(), Type.NUMBER) && ((NumericValue) a).isNaN()))) { 
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = Constants.INFERIOR;							
                        else
                            cmp = Constants.SUPERIOR;
                    } else if ((b.isEmpty() || (Type.subTypeOf(b.getType(), Type.NUMBER) && ((NumericValue) b).isNaN()))) { 
                        if ((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = Constants.SUPERIOR;
						else
							cmp = Constants.INFERIOR;
                    } else if (a == AtomicValue.EMPTY_VALUE && b != AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = Constants.INFERIOR;
						else
							cmp = Constants.SUPERIOR;
					} else if (b == AtomicValue.EMPTY_VALUE && a != AtomicValue.EMPTY_VALUE) {
						if((orderSpecs[i].getModifiers() & OrderSpec.EMPTY_LEAST) != 0)
							cmp = Constants.SUPERIOR;
						else
							cmp = Constants.INFERIOR;
					} else
						cmp = a.compareTo(orderSpecs[i].getCollator(), b);
					if((orderSpecs[i].getModifiers() & OrderSpec.DESCENDING_ORDER) != 0)
						cmp = cmp * -1;
					if(cmp != Constants.EQUAL)
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
