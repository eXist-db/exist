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
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.value;

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.memtree.NodeImpl;
import org.exist.util.FastQSort;
import org.exist.xquery.XPathException;

/**
 * A sequence that may contain a mixture of atomic values and nodes.
 * 
 * @author wolf
 */
public class ValueSequence extends AbstractSequence {

	private final Logger LOG = Logger.getLogger(ValueSequence.class);
	
	private final static int INITIAL_SIZE = 64;
	
	private Item[] values;
	private int size = -1;
	
	// used to keep track of the type of added items.
	// will be Type.ANY_TYPE if the type is unknown
	// and Type.ITEM if there are items of mixed type.
	private int itemType = Type.ANY_TYPE;
	
	private boolean noDuplicates = false;
	
	public ValueSequence() {
		values = new Item[INITIAL_SIZE];
	}
	
	public ValueSequence(Sequence otherSequence) {
		values = new Item[otherSequence.getLength()];
		addAll(otherSequence);
	}
	
	public void clear() {
		Arrays.fill(values, null);
		size = -1;
		itemType = Type.ANY_TYPE;
		noDuplicates = false;
	}
	
	public void add(Item item) {
		++size;
		ensureCapacity();
		values[size] = item;
		if(itemType == item.getType())
			return;
		else if(itemType == Type.ANY_TYPE)
			itemType = item.getType();
		else
			itemType = Type.getCommonSuperType(item.getType(), itemType);
		noDuplicates = false;
	}
	
	public void addAll(Sequence otherSequence) {
		for(SequenceIterator iterator = otherSequence.iterate(); iterator.hasNext(); )
			add(iterator.nextItem());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return itemType == Type.ANY_TYPE ? Type.ITEM : itemType;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
//		removeDuplicates();
		return new ValueSequenceIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
//		removeDuplicates();
		return new ValueSequenceIterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getLength() {
//		removeDuplicates();
		return size + 1;
	}

	public Item itemAt(int pos) {
		return values[pos];
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		if(size == -1)
			return NodeSet.EMPTY_SET;
		if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
			NodeSet set = new ExtArrayNodeSet();
			NodeValue v;
			for (int i = 0; i <= size; i++) {
				v = (NodeValue)values[i];
				if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
//					throw new XPathException("Cannot query constructed nodes.");
					NodeSet p = ((NodeImpl)v).toNodeSet();
                    if (p == null) {
                        LOG.warn("Node " + ((NodeImpl)v).getQName() + " not found!");
                        continue;
                    }
					set.addAll(p);
                    LOG.debug("Added " + ((NodeImpl)v).getQName());
				} else {
					set.add((NodeProxy)v);
				}
			}
			return set;
		} else
			throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));
	}
	
    public boolean isPersistentSet() {
        if(size == -1)
            return true;
        if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            NodeValue v;
            for (int i = 0; i <= size; i++) {
                v = (NodeValue)values[i];
                if(v.getImplementationType() != NodeValue.PERSISTENT_NODE)
                    return false;
            }
            return true;
        }
        return false;
    }
    
    public void sortInDocumentOrder() {
        removeDuplicates();
        FastQSort.sort(values, new MixedNodeValueComparator(), 0, size);
    }
    
	private void ensureCapacity() {
		if(size == values.length) {
			int newSize = (size * 3) / 2;
			Item newValues[] = new Item[newSize];
			System.arraycopy(values, 0, newValues, 0, size);
			values = newValues;
		}
	}
	
	public void removeDuplicates() {
		if(noDuplicates)
			return;
		if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.ATOMIC))
			return;
		// check if the sequence contains nodes
		boolean hasNodes = false;
		for(int i = 0; i <= size; i++) {
			if(Type.subTypeOf(values[i].getType(), Type.NODE))
				hasNodes = true;
		}
		if(!hasNodes)
			return;
		Set nodes = new TreeSet();
		int j = 0;
		for (int i = 0; i <= size; i++) {
			if(Type.subTypeOf(values[i].getType(), Type.NODE)) {
				if(!nodes.contains(values[i])) {
					Item item = values[i];
					values[j++] = item;
					nodes.add(item);
				}
			} else
				values[j++] = values[i];
		}
		size = j - 1;
		noDuplicates = true;
	}
	
	private class ValueSequenceIterator implements SequenceIterator {
		
		private int pos = 0;
		
		public ValueSequenceIterator() {
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return pos <= size;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if(pos <= size)
				return values[pos++];
			return null;
		}
	}
}
