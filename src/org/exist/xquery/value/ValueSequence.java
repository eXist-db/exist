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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xquery.XPathException;

public class ValueSequence extends AbstractSequence {

	List values;
	
	// used to keep track of the type of added items.
	// will be Type.ANY_TYPE if the type is unknown
	// and Type.ITEM if there are items of mixed type.
	int itemType = Type.ANY_TYPE;
	
	public ValueSequence() {
		values = new ArrayList();
	}
	
	public ValueSequence(Sequence otherSequence) {
		this();
		addAll(otherSequence);
	}
	
	public void add(Item item) {
		values.add(item);
		if(itemType == item.getType())
			return;
		else if(itemType == Type.ANY_TYPE)
			itemType = item.getType();
		else
			itemType = Type.getCommonSuperType(item.getType(), itemType);
	}
	
	public void addAll(Sequence otherSequence) {
		for(SequenceIterator iterator = otherSequence.iterate(); iterator.hasNext(); )
			add(iterator.nextItem());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return itemType;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		return new ValueSequenceIterator(values.iterator());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return new ValueSequenceIterator(values.iterator());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getLength()
	 */
	public int getLength() {
		return values.size();
	}

	public Item itemAt(int pos) {
		return (Item)values.get(pos);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		if(Type.subTypeOf(itemType, Type.NODE)) {
			NodeSet set = new ExtArrayNodeSet();
			NodeValue v;
			for(Iterator i = values.iterator(); i.hasNext(); ) {
				v = (NodeValue)i.next();
				if(v.getImplementationType() != NodeValue.PERSISTENT_NODE)
					throw new XPathException("Cannot query constructed nodes.");
				set.add((NodeProxy)v);
			}
			return set;
		} else
			throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));
	}
	
	private class ValueSequenceIterator implements SequenceIterator {
		
		private Iterator iter;
		
		public ValueSequenceIterator(Iterator iterator) {
			iter = iterator;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if(iter.hasNext())
				return (Item)iter.next();
			return null;
		}
	}
}
