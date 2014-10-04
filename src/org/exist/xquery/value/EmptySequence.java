/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import org.exist.dom.persistent.NodeSet;
import org.exist.xquery.XPathException;

public class EmptySequence extends AbstractSequence {

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.EMPTY;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
		return EmptySequenceIterator.EMPTY_ITERATOR;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() throws XPathException {
		return EmptySequenceIterator.EMPTY_ITERATOR;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemCount()
	 */
	public int getItemCount() {
		return 0;
	}

	public Item itemAt(int pos) {
		return null;
	}
	
	public boolean isEmpty() {
		return true;
	}

	public boolean hasOne() {
		return false;
	}
	
	public void add(Item item) throws XPathException {
		throw new XPathException("cannot add an item to an empty sequence");
	}
	
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.BOOLEAN:
				return new BooleanValue(false);
			case Type.STRING:
				return new StringValue("");
			default:
				throw new XPathException("cannot convert empty sequence to " + requiredType);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		return NodeSet.EMPTY_SET;
	}

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return MemoryNodeSet.EMPTY;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // nothing to do
    }
    
    public String toString() {
    	return "()";
    }
}
