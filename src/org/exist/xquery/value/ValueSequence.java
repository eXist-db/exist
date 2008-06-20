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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.dom.NewArrayNodeSet;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.util.FastQSort;
import org.exist.util.hashtable.Int2ObjectHashMap;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;

/**
 * A sequence that may contain a mixture of atomic values and nodes.
 * @author wolf
 */
public class ValueSequence extends AbstractSequence {

	private final Logger LOG = Logger.getLogger(ValueSequence.class);
	
    //Do not change the -1 value since size computation relies on this start value
    private final static int UNSET_SIZE = -1;
    private final static int INITIAL_SIZE = 64;
	
	private Item[] values;
	private int size = UNSET_SIZE;
	
	// used to keep track of the type of added items.
	// will be Type.ANY_TYPE if the type is unknown
	// and Type.ITEM if there are items of mixed type.
	private int itemType = Type.ANY_TYPE;
	
	private boolean noDuplicates = false;

    private Variable holderVar = null;

    private int state = 0;
    
    public ValueSequence() {
		values = new Item[INITIAL_SIZE];
	}
	
	public ValueSequence(int initialSize) {
		values = new Item[initialSize];
	}
	
	public ValueSequence(Sequence otherSequence) throws XPathException {
		values = new Item[otherSequence.getItemCount()];
		addAll(otherSequence);
	}
	
	public void clear() {
		Arrays.fill(values, null);
		size = UNSET_SIZE;
		itemType = Type.ANY_TYPE;
		noDuplicates = false;
	}
	
    public boolean isEmpty() {
    	return isEmpty;
    }
    
    public boolean hasOne() {
    	return hasOne;
    }
	
	public void add(Item item) {
		if (hasOne)
			hasOne = false;
		if (isEmpty)
			hasOne = true;
        isEmpty = false;
        ++size;
        ensureCapacity();
        values[size] = item;
        if (itemType == item.getType())
            return;
        else if (itemType == Type.ANY_TYPE)
            itemType = item.getType();
        else
            itemType = Type.getCommonSuperType(item.getType(), itemType);
        noDuplicates = false;
        setHasChanged();
    }

    public void addAll(Sequence otherSequence) throws XPathException {
		if (otherSequence == null)
			return;
		SequenceIterator iterator = otherSequence.iterate();
		if (iterator == null) {
			LOG.warn("Iterator == null: " + otherSequence.getClass().getName());
		}
		for(; iterator.hasNext(); )
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
	public SequenceIterator iterate() throws XPathException {
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
	public int getItemCount() {
//		removeDuplicates();
		return size + 1;
	}

	public Item itemAt(int pos) {
		return values[pos];
	}

    public void setHolderVariable(Variable var) {
        this.holderVar = var;
    }
    
    /**
     * Makes all in-memory nodes in this sequence persistent,
     * so they can be handled like other node sets.
     * 
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		if(size == UNSET_SIZE)
			return NodeSet.EMPTY_SET;
        // for this method to work, all items have to be nodes
		if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
			NodeSet set = new NewArrayNodeSet();
			NodeValue v;
			for (int i = 0; i <= size; i++) {
				v = (NodeValue)values[i];
				if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    // found an in-memory document
                    DocumentImpl doc = ((NodeImpl)v).getDocument();
                    if (doc==null) {
                       continue;
                    }
                    // make this document persistent: doc.makePersistent()
                    // returns a map of all root node ids mapped to the corresponding
                    // persistent node. We scan the current sequence and replace all
                    // in-memory nodes with their new persistent node objects.
                    Int2ObjectHashMap newRoots = doc.makePersistent();
                    if (newRoots == null)
                    	return NodeSet.EMPTY_SET; 
                    for (int j = i; j <= size; j++) {
                        v = (NodeValue) values[j];
                        if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                            NodeImpl node = (NodeImpl) v;
                            if (node.getDocument() == doc) {
                                NodeProxy p = (NodeProxy) newRoots.get(node.getNodeNumber());
                                if (p != null) {
                                    // replace the node by the NodeProxy
                                    values[j] = p;
                                }
                            }
                        }
                    }
                    set.add((NodeProxy) values[i]);
				} else {
					set.add((NodeProxy)v);
				}
			}
            if (holderVar != null)
                holderVar.setValue(set);
            return set;
		} else
			throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));
	}
	
    public boolean isPersistentSet() {
        if(size == UNSET_SIZE)
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
	
    public void clearContext(int contextId) {
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE))
                ((NodeValue) values[i]).clearContext(contextId);
        }
    }


    public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
        for (int i = 0; i <= size; i++) {
            values[i].nodeMoved(oldNodeId, newNode);
        }
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 0 : state + 1);
    }

    public int getState() {
        return state;
    }

    public boolean hasChanged(int previousState) {
        return state != previousState;
    }

    public boolean isCacheable() {
        return true;
    }

    /* (non-Javadoc)
    * @see org.exist.xquery.value.Sequence#getDocumentSet()
    */
    public DocumentSet getDocumentSet() {
        DocumentSet docs = new DocumentSet();
        NodeValue node;
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                node = (NodeValue) values[i];
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE)
                    docs.add(node.getOwnerDocument());
            }
        }
        return docs;
    }


    public Iterator getCollectionIterator() {
        return new CollectionIterator();
    }

    private class CollectionIterator implements Iterator {

        Collection nextCollection = null;
        int pos = 0;

        CollectionIterator() {
            next();
        }

        public boolean hasNext() {
            return nextCollection != null;
        }

        public Object next() {
            Collection oldCollection = nextCollection;
            nextCollection = null;
            while (pos <= size) {
                if (Type.subTypeOf(values[pos].getType(), Type.NODE)) {
                    NodeValue node = (NodeValue) values[pos];
                    if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        NodeProxy p = (NodeProxy) node;
                        if (!p.getDocument().getCollection().equals(oldCollection)) {
                            nextCollection = p.getDocument().getCollection();
                            break;
                        }
                    }
                }
                pos++;
            }
            return oldCollection;
        }

        public void remove() {
             // not needed
            throw new IllegalStateException();
        }
    }
    
    public String toString() {
		try {
			StringBuffer result = new StringBuffer();
			result.append("(");
			boolean moreThanOne = false;
			for (SequenceIterator i = iterate(); i.hasNext(); ) {
				Item next = i.nextItem();
				if (moreThanOne) result.append(", ");				
				moreThanOne = true;
				result.append(next.toString());						
			}
			result.append(")");
			return result.toString();
		} catch (XPathException e) {
			return "ValueSequence.toString() failed: " + e.getMessage();
		}
		
	}
    
    /**
    * Returns a hashKey based on sequence item string values.
    * This function is faster than toString() but need to be enhanced.
    * 
    * Warning : don't use except for experimental GroupBy clause.
    * author Boris Verhaegen
    *
    * @see org.exist.xquery.value.GroupedValueSequenceTable 
    * 
    *
    */
    public String getHashKey(){
    	try{
    		String hashKey = "";
    		for(SequenceIterator i = iterate();i.hasNext();){
     			Item current = i.nextItem();     			
    			hashKey+=current.getStringValue();
    			hashKey+="&&";  //bv : sentinel value to separate grouping keys values
     		}
      		return hashKey;
    	} catch (XPathException e) {
      		return "ValueSequence.getHashKey() failed: " + e.getMessage();
      	}
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
