
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * 
 * $Id$
 */
package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public final class EmptyNodeSet extends AbstractNodeSet {

	public final static EmptyNodeSetIterator EMPTY_ITERATOR = new EmptyNodeSetIterator();
	
    public NodeSetIterator iterator() {
	    return EMPTY_ITERATOR;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
		return SequenceIterator.EMPTY_ITERATOR;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() throws XPathException {
		return SequenceIterator.EMPTY_ITERATOR;
	}
	
    public boolean contains(DocumentImpl doc, long nodeId) {
	    return false;
    }
    
    public boolean contains(NodeProxy proxy) {
	    return false;
    }

    public boolean contains(DocumentImpl doc) {
	return false;
    }
    
    public boolean isEmpty() {
    	return true;
    }
    
    public boolean hasOne() {
    	return false;
    }    

    public void add(DocumentImpl doc, long nodeId) {
    }

    public void add(Node node) {
    }

    public void add(NodeProxy proxy) {
    }

    public void addAll(NodeList other) {
    }

    public void addAll(NodeSet other) {
    }

    public void remove(NodeProxy node) {
    }

    public int getLength() {
	    return 0;
    }

    //TODO : evaluate both semantics
    public int getItemCount() {
	    return 0;
    }
    
    public Node item(int pos) {
	    return null;
    }
    
    public Item itemAt(int pos) {
    	return null;
    }
    
    public NodeProxy get(int pos) {
	    return null;
    }

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        return null;
    }

    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
        return null;
    }

    public NodeProxy get(NodeProxy proxy) {
        return null;
    }

    public NodeSet intersection(NodeSet other) {
        return this;
    }

    public NodeSet deepIntersection(NodeSet other) {
        return this;
    }

    public NodeSet union(NodeSet other) {
        return other;
    }

    private final static class EmptyNodeSetIterator implements NodeSetIterator {

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return false;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public Object next() {
			return null;
		}
        
        public NodeProxy peekNode() {
            return null;
        }
        
        public void setPosition(NodeProxy proxy) {
        }
        
        public String toString() {
            StringBuilder result = new StringBuilder();
            result.append("Empty#").append(super.toString());
            return result.toString();
        }          
		
	} 
}
