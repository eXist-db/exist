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

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.dom.NewArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.StoredNode;
import org.exist.memtree.DocumentImpl;
import org.exist.memtree.NodeImpl;
import org.exist.numbering.NodeId;
import org.exist.util.FastQSort;
import org.exist.xquery.*;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A sequence that may contain a mixture of atomic values and nodes.
 * 
 * @author wolf
 */
public class ValueSequence extends AbstractSequence implements MemoryNodeSet {

	private final Logger LOG = Logger.getLogger(ValueSequence.class);
	
    //Do not change the -1 value since size computation relies on this start value
    private final static int UNSET_SIZE = -1;
    private final static int INITIAL_SIZE = 64;
	
	protected Item[] values;
	protected int size = UNSET_SIZE;
	
	// used to keep track of the type of added items.
	// will be Type.ANY_TYPE if the typlexe is unknown
	// and Type.ITEM if there are items of mixed type.
	protected int itemType = Type.ANY_TYPE;
	
	private boolean noDuplicates = false;

    private boolean inMemNodeSet = false;

    private boolean isOrdered = false;

    private boolean enforceOrder = false;
    
    private boolean keepUnOrdered = false;

    private Variable holderVar = null;

    private int state = 0;

    private NodeSet cachedSet = null;
    
    public ValueSequence() {
		this(false);
	}

    public ValueSequence(boolean ordered) {
        values = new Item[INITIAL_SIZE];
        enforceOrder = ordered;
    }
	
	public ValueSequence(int initialSize) {
		values = new Item[initialSize];
	}

    public ValueSequence(Sequence otherSequence) throws XPathException {
        this(otherSequence, false);
    }

    public ValueSequence(Sequence otherSequence, boolean ordered) throws XPathException {
		values = new Item[otherSequence.getItemCount()];
		addAll(otherSequence);
        this.enforceOrder = ordered;
    }
    
    public ValueSequence(final Item... items) throws XPathException {
        values = new Item[items.length];
        for(final Item item : items) {
            add(item);
        }
    }
    
    public void keepUnOrdered(boolean flag) {
    	keepUnOrdered = flag;
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
            {hasOne = false;}
        if (isEmpty)
            {hasOne = true;}
        cachedSet = null;
        isEmpty = false;
        ++size;
        ensureCapacity();
        values[size] = item;
        if (itemType == item.getType())
            {return;}
        else if (itemType == Type.ANY_TYPE)
            {itemType = item.getType();}
        else
            {itemType = Type.getCommonSuperType(item.getType(), itemType);}
        noDuplicates = false;
        isOrdered = false;
        setHasChanged();
    }

//    public void addAll(ValueSequence otherSequence) throws XPathException {
//        if (otherSequence == null)
//			return;
//        enforceOrder = otherSequence.enforceOrder;
//        for (SequenceIterator i = otherSequence.iterate(); i.hasNext(); ) {
//          add(i.nextItem());
//        }
//    }

    public void addAll(Sequence otherSequence) throws XPathException {
		if (otherSequence == null)
			{return;}
        final SequenceIterator iterator = otherSequence.iterate();
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
        sortInDocumentOrder();
		return new ValueSequenceIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AbstractSequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() throws XPathException {
        sortInDocumentOrder();
        return new ValueSequenceIterator();
	}

    public boolean isOrdered() {
        return enforceOrder;
    }

    public void setIsOrdered(boolean ordered) {
        this.enforceOrder = ordered;
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getItemCount() {
        sortInDocumentOrder();
		return size + 1;
	}

	public Item itemAt(int pos) {
        sortInDocumentOrder();
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
			{return NodeSet.EMPTY_SET;}
        // for this method to work, all items have to be nodes
		if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
			final NodeSet set = new NewArrayNodeSet();
			NodeValue v;
			for (int i = 0; i <= size; i++) {
				v = (NodeValue)values[i];
				if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                    // found an in-memory document
                    final DocumentImpl doc = ((NodeImpl)v).getDocument();
                    if (doc==null) {
                       continue;
                    }
                    // make this document persistent: doc.makePersistent()
                    // returns a map of all root node ids mapped to the corresponding
                    // persistent node. We scan the current sequence and replace all
                    // in-memory nodes with their new persistent node objects.
                    final DocumentImpl expandedDoc = doc.expandRefs(null);
                    final org.exist.dom.DocumentImpl newDoc = expandedDoc.makePersistent();
                    if (newDoc != null) {
                        NodeId rootId = newDoc.getBrokerPool().getNodeFactory().createInstance();
                        for (int j = i; j <= size; j++) {
                            v = (NodeValue) values[j];
                            if(v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                                NodeImpl node = (NodeImpl) v;
                                if (node.getDocument() == doc) {
                                    if (node.getNodeType() == Node.ATTRIBUTE_NODE)
                                        {node = expandedDoc.getAttribute(node.getNodeNumber());}
                                    else
                                        {node = expandedDoc.getNode(node.getNodeNumber());}
                                    NodeId nodeId = node.getNodeId();
                                    if (nodeId == null)
                                        {throw new XPathException("Internal error: nodeId == null");}
                                    if (node.getNodeType() == Node.DOCUMENT_NODE)
                                        {nodeId = rootId;}
                                    else
                                        {nodeId = rootId.append(nodeId);}
                                    NodeProxy p = new NodeProxy(newDoc, nodeId, node.getNodeType());
                                    if (p != null) {
                                        // replace the node by the NodeProxy
                                        values[j] = p;
                                    }
                                }
                            }
                        }
                        set.add((NodeProxy) values[i]);
                    }
                } else {
					set.add((NodeProxy)v);
				}
			}
            if (holderVar != null)
                {holderVar.setValue(set);}
            return set;
		} else
			{throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));}
	}

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        if(size == UNSET_SIZE)
            {return MemoryNodeSet.EMPTY;}
        if(itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE)) {
            throw new XPathException("Type error: the sequence cannot be converted into" +
				" a node set. Item type is " + Type.getTypeName(itemType));
        }
        NodeValue v;
        for (int i = 0; i <= size; i++) {
            v = (NodeValue)values[i];
            if(v.getImplementationType() == NodeValue.PERSISTENT_NODE)
                {throw new XPathException("Type error: the sequence cannot be converted into" +
				    " a MemoryNodeSet. It contains nodes from stored resources.");}
        }
        expand();
        inMemNodeSet = true;
        return this;
    }

    public boolean isInMemorySet() {
        if(size == UNSET_SIZE)
            {return true;}
        if(itemType == Type.ANY_TYPE || !Type.subTypeOf(itemType, Type.NODE))
            {return false;}
        NodeValue v;
        for (int i = 0; i <= size; i++) {
            v = (NodeValue)values[i];
            if(v.getImplementationType() == NodeValue.PERSISTENT_NODE)
                {return false;}
        }
        return true;
    }

    public boolean isPersistentSet() {
        if(size == UNSET_SIZE)
            {return true;}
        if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.NODE)) {
            NodeValue v;
            for (int i = 0; i <= size; i++) {
                v = (NodeValue)values[i];
                if(v.getImplementationType() != NodeValue.PERSISTENT_NODE)
                    {return false;}
            }
            return true;
        }
        return false;
    }

    /**
     * Scan the sequence and check all in-memory documents.
     * They may contains references to nodes stored in the database.
     * Expand those references to get a pure in-memory DOM tree.
     */
    private void expand() {
        final Set<DocumentImpl> docs = new HashSet<DocumentImpl>();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.getDocument().hasReferenceNodes())
                {docs.add(node.getDocument());}
        }
        for (final DocumentImpl doc : docs) {
            doc.expand();
        }
    }

    @Override
    public void destroy(XQueryContext context, Sequence contextSequence) {
        for (int i = 0; i <= size; i++) {
            values[i].destroy(context, contextSequence);
        }
    }

    public boolean containsValue(AtomicValue value) {
        for (int i = 0; i <= size; i++) {
            if (values[i] == value)
                {return true;}
        }
        return false;
    }

    public void sortInDocumentOrder() {
        if (size == UNSET_SIZE)
            {return;}
        if (keepUnOrdered) {
            removeDuplicateNodes();
        	return;
        }
        if (!enforceOrder || isOrdered)
            {return;}
        inMemNodeSet = inMemNodeSet || isInMemorySet();
        if (inMemNodeSet) {
            FastQSort.sort(values, new InMemoryNodeComparator(), 0, size);
        }
        removeDuplicateNodes();
        isOrdered = true;
    }

    public void removeDuplicates() {
        enforceOrder = true;
        isOrdered = false;
        sortInDocumentOrder();
    }

    private void ensureCapacity() {
		if(size == values.length) {
			final int newSize = (int)Math.round((size == 0 ? 1 : size * 3) / (double) 2);
			Item newValues[] = new Item[newSize];
			System.arraycopy(values, 0, newValues, 0, size);
			values = newValues;
		}
	}
	
	private void removeDuplicateNodes() {
        if(noDuplicates || size < 1)
			{return;}
        if (inMemNodeSet) {
            int j = 0;
            for (int i = 1; i <= size; i++) {
                if (!values[i].equals(values[j])) {
                    if (i != ++j) {
                        values[j] = values[i];
                    }
                }
            }
            size = j;
        } else {
            if(itemType != Type.ANY_TYPE && Type.subTypeOf(itemType, Type.ATOMIC))
                {return;}
            // check if the sequence contains nodes
            boolean hasNodes = false;
            for(int i = 0; i <= size; i++) {
                if(Type.subTypeOf(values[i].getType(), Type.NODE))
                    {hasNodes = true;}
            }
            if(!hasNodes)
            {return;}
            final Map<Item, Item> nodes = new TreeMap<Item, Item>();
            int j = 0;
            for (int i = 0; i <= size; i++) {
                if(Type.subTypeOf(values[i].getType(), Type.NODE)) {
                	final Item found = nodes.get(values[i]);
                    if(found == null) {
                        Item item = values[i];
                        values[j++] = item;
                        nodes.put(item, item);
                    } else {
                    	final NodeValue nv = (NodeValue) found;
                    	if (nv.getImplementationType() == NodeValue.PERSISTENT_NODE)
                    		{((NodeProxy) nv).addMatches((NodeProxy) values[i]);}
                    }
                } else
                    {values[j++] = values[i];}
            }
            size = j - 1;
        }
        noDuplicates = true;
	}
	
    public void clearContext(int contextId) throws XPathException {
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE))
                {((NodeValue) values[i]).clearContext(contextId);}
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
        if (cachedSet != null)
            {return cachedSet.getDocumentSet();}
        try {
            boolean isPersistentSet = true;
            NodeValue node;
            for (int i = 0; i <= size; i++) {
                if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                    node = (NodeValue) values[i];
                    if (node.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                        isPersistentSet = false;
                        break;
                    }
                } else {
                    isPersistentSet = false;
                    break;
                }
            }
            if (isPersistentSet) {
                cachedSet = toNodeSet();
                return cachedSet.getDocumentSet();
            }
        } catch (final XPathException e) {
        }
        return extractDocumentSet();
    }

    private DocumentSet extractDocumentSet() {
        final MutableDocumentSet docs = new DefaultDocumentSet();
        NodeValue node;
        for (int i = 0; i <= size; i++) {
            if (Type.subTypeOf(values[i].getType(), Type.NODE)) {
                node = (NodeValue) values[i];
                if (node.getImplementationType() == NodeValue.PERSISTENT_NODE)
                    {docs.add((org.exist.dom.DocumentImpl) node.getOwnerDocument());}
            }
        }
        return docs;
    }

    /* Methods of MemoryNodeSet */

    public Sequence getAttributes(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAttributes(test, nodes);
        }
        return nodes;
    }

    public Sequence getDescendantAttributes(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendantAttributes(test, nodes);
        }
        return nodes;
    }

    public Sequence getChildren(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectChildren(test, nodes);
        }
        return nodes;
    }

    public Sequence getChildrenForParent(NodeImpl parent) {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.getNodeId().isChildOf(parent.getNodeId()))
                {nodes.add(node);}
        }
        return nodes;
    }

    public Sequence getDescendants(boolean includeSelf, NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectDescendants(includeSelf, test, nodes);
        }
        return nodes;
    }

    public Sequence getAncestors(boolean includeSelf, NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectAncestors(includeSelf, test, nodes);
        }
        return nodes;
    }

    public Sequence getParents(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            final NodeImpl parent = (NodeImpl) node.selectParentNode();
            if (parent != null && test.matches(parent))
                {nodes.add(parent);}
        }
        return nodes;
    }

    public Sequence getSelf(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                    test.matches(node))
                {nodes.add(node);}
        }
        return nodes;
    }

    public Sequence getPrecedingSiblings(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectPrecedingSiblings(test, nodes);
        }
        return nodes;
    }

    public Sequence getPreceding(NodeTest test, int position) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectPreceding(test, nodes, position);
        }
        return nodes;
    }

    public Sequence getFollowingSiblings(NodeTest test) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectFollowingSiblings(test, nodes);
        }
        return nodes;
    }

    public Sequence getFollowing(NodeTest test, int position) throws XPathException {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            node.selectFollowing(test, nodes, position);
        }
        return nodes;
    }

    public Sequence selectDescendants(MemoryNodeSet descendants) {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            for (int j = 0; j < descendants.size(); j++) {
                final NodeImpl descendant = descendants.get(j);
                if (descendant.getNodeId().isDescendantOrSelfOf(node.getNodeId()))
                    {nodes.add(node);}
            }
        }
        return nodes;
    }

    public Sequence selectChildren(MemoryNodeSet children) {
        sortInDocumentOrder();
        final ValueSequence nodes = new ValueSequence(true);
        nodes.keepUnOrdered(keepUnOrdered);
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            for (int j = 0; j < children.size(); j++) {
                final NodeImpl descendant = children.get(j);
                if (descendant.getNodeId().isChildOf(node.getNodeId()))
                    {nodes.add(node);}
            }
        }
        return nodes;
    }

    public int size() {
        return size + 1;
    }

    public NodeImpl get(int which) {
        return (NodeImpl) values[which];
    }

    /* END methods of MemoryNodeSet */
    
    public Iterator<Collection> getCollectionIterator() {
        return new CollectionIterator();
    }

    private class CollectionIterator implements Iterator<Collection> {

        Collection nextCollection = null;
        int pos = 0;

        CollectionIterator() {
            next();
        }

        public boolean hasNext() {
            return nextCollection != null;
        }

        public Collection next() {
            final Collection oldCollection = nextCollection;
            nextCollection = null;
            while (pos <= size) {
                if (Type.subTypeOf(values[pos].getType(), Type.NODE)) {
                    final NodeValue node = (NodeValue) values[pos];
                    if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeProxy p = (NodeProxy) node;
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
			final StringBuilder result = new StringBuilder();
			result.append("(");
			boolean moreThanOne = false;
			for (final SequenceIterator i = iterate(); i.hasNext(); ) {
				final Item next = i.nextItem();
				if (moreThanOne) {result.append(", ");}				
				moreThanOne = true;
				result.append(next.toString());						
			}
			result.append(")");
			return result.toString();
		} catch (final XPathException e) {
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
    		for(final SequenceIterator i = iterate();i.hasNext();){
     			final Item current = i.nextItem();     			
    			hashKey+=current.getStringValue();
    			hashKey+="&&";  //bv : sentinel value to separate grouping keys values
     		}
      		return hashKey;
    	} catch (final XPathException e) {
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
				{return values[pos++];}
			return null;
		}
	}

    private static class InMemoryNodeComparator implements Comparator<Item> {

        public int compare(Item o1, Item o2) {
            final NodeImpl n1 = (NodeImpl) o1;
            final NodeImpl n2 = (NodeImpl) o2;
            final int docCmp = n1.getDocument().compareTo(n2.getDocument());
            if (docCmp == 0) {
                return n1.getNodeNumber() == n2.getNodeNumber() ? Constants.EQUAL :
                    (n1.getNodeNumber() > n2.getNodeNumber() ? Constants.SUPERIOR : Constants.INFERIOR);
            } else
                {return docCmp;}
        }
    }

	public boolean matchSelf(NodeTest test) throws XPathException {
		//UNDERSTAND: is it required? -shabanovd
		sortInDocumentOrder();
		for (int i = 0; i <= size; i++) {
			final NodeImpl node = (NodeImpl) values[i];
			if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
					test.matches(node))
				{return true;}
			}
		return false;
	}

    public boolean matchChildren(NodeTest test) throws XPathException {
		//UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchChildren(test))
            	{return true;}
        }
        return false;
    }

    public boolean matchAttributes(NodeTest test) throws XPathException {
		//UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchAttributes(test))
            	{return true;}
        }
        return false;
    }

    public boolean matchDescendantAttributes(NodeTest test) throws XPathException {
		//UNDERSTAND: is it required? -shabanovd
        sortInDocumentOrder();
        for (int i = 0; i <= size; i++) {
            final NodeImpl node = (NodeImpl) values[i];
            if (node.matchDescendantAttributes(test))
            	{return true;}
        }
        return false;
    }
}
