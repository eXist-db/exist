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
package org.exist.xpath.value;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ValueSequence extends AbstractSequence {

	List values;
	
	public ValueSequence() {
		values = new ArrayList();
	}
	
	public ValueSequence(Sequence otherSequence) {
		this();
		addAll(otherSequence);
	}
	
	public void add(Item item) {
		values.add(item);
	}
	
	public void addAll(Sequence otherSequence) {
		for(SequenceIterator iterator = otherSequence.iterate(); iterator.hasNext(); )
			values.add(iterator.nextItem());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.ITEM;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
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
			return (Item)iter.next();
		}
	}
}
