package org.exist.fluent;

import java.util.*;

import org.exist.dom.persistent.AVLTreeNodeSet;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * The result of a query on the database, holding a collection of items.
 * The items can be accessed either as structured resources or as string
 * values.  It is also possible to further refine the query by executing queries
 * within the context of the results.  
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.17 $ ($Date: 2006/08/14 23:18:22 $)
 */
public class ItemList extends Resource implements Iterable<Item> {

	private Sequence seq;
	private List<Item> items, modifiableItems;
	private ValuesFacet values;
	private NodesFacet nodes;

	/**
	 * A facet that treats each item in the list as its effective string value.  Atomic values
	 * are converted to strings, while nodes are converted to the concatenation of all their
	 * text descendants (note: not serialized!).
	 */
	public class ValuesFacet implements Iterable<String> {
		private ValuesFacet() {}
		
		/**
		 * Return an iterator over the effective string values of the item list.
		 * 
		 * @return a string value iterator
		 */
		public Iterator<String> iterator() {
			return new Iterator<String>() {
				private final Iterator<Item> delegate = ItemList.this.iterator();
				public boolean hasNext() {	return delegate.hasNext();}
				public String next() {return delegate.next().value();}
				public void remove() {throw new UnsupportedOperationException();}
			};
		}

		private ItemList itemList() {
			return ItemList.this;
		}
		
		@Override public boolean equals(Object o) {
			return (o instanceof ValuesFacet && ItemList.this.equals(((ValuesFacet) o).itemList()));
		}
		
		@Override public int hashCode() {
			return ItemList.this.hashCode() + 2;
		}
		
		/**
		 * Return an unmodifiable list view over the effective string values of the item list.
		 *
		 * @return a list view
		 */
		public List<String> asList() {
			return new AbstractList<String>() {
				@Override public String get(int index) {return items.get(index).value();}
				@Override public int size() {return items.size();}
			};
		}
		
		/**
		 * Convert the list of effective string values to an array.
		 * 
		 * @return an array of effective string values
		 */
		public String[] toArray() {
			return asList().toArray(new String[size()]);
		}
		
		/**
		 * Convert the list of effective string values to an array.  If the supplied array is sufficient
		 * for holding the strings, use it; if it's larger than necessary, put a <code>null</code> after
		 * the end of the list.  If the array is too small, allocate a new one.
		 *
		 * @param a an array to fill with effective string values
		 * @return an array of effective string values
		 */
		public String[] toArray(String[] a) {
			return asList().toArray(a);
		}
		
		@Override public String toString() {
			return ItemList.this.toString();
		}
	}
	
	/**
	 * A facet that treats each item in the list as a node.  If an operation accesses an item that
	 * is not a node, it will throw a <code>DatabaseException</code>.
	 */
	public class NodesFacet implements Iterable<Node> {
		private NodesFacet() {}
		
		/**
		 * Return an iterator over the list of nodes.
		 * 
		 * @return an iterator over the list of nodes
		 */
		public Iterator<Node> iterator() {
			return new Iterator<Node>() {
				private final Iterator<Item> delegate = ItemList.this.iterator();
				public boolean hasNext() {return delegate.hasNext();}
				public Node next() {
					try {
						return (Node) delegate.next();
					} catch (ClassCastException e) {
						throw new DatabaseException("item is not a node");
					}
				}
				public void remove() {throw new UnsupportedOperationException();}
			};
		}
		
		/**
		 * Return the set of documents to which the nodes in this list belong.
		 *
		 * @return the set of documents convering the nodes in the list
		 */
		public Set<XMLDocument> documents() {
			Set<XMLDocument> docs = new HashSet<XMLDocument>();
			for (Node node : this) {
				try {
					docs.add(node.document());
				} catch (UnsupportedOperationException e) {
					// ignore, must be a non-persistent node
				}
			}
			return docs;
		}
		
		private ItemList itemList() {
			return ItemList.this;
		}
		
		@Override public boolean equals(Object o) {
			return (o instanceof NodesFacet && ItemList.this.equals(((NodesFacet) o).itemList()));
		}
		
		@Override public int hashCode() {
			return ItemList.this.hashCode() + 1;
		}
		
		/**
		 * Return an unmodifiable list view over the list of nodes.
		 *
		 * @return a list view
		 */
		public List<Node> asList() {
			return new AbstractList<Node>() {
				@Override public Node get(int index) {
					try {
						return (Node) items.get(index);
					} catch (ClassCastException e) {
						throw new DatabaseException("item is not a node");
					}
				}
				@Override public int size() {
					return items.size();
				}
			};
		}
		
		/**
		 * Convert the list of nodes to an array.
		 *
		 * @return an array of nodes
		 */
		public Node[] toArray() {
			return asList().toArray(new Node[size()]);
		}
		
		/**
		 * Convert the list of nodes to an array.  If the given array is large enough, fill it; if it's
		 * larger than necessary, put a <code>null</code> marker after the end of the list.  If the
		 * array is not large enough, allocate a new one.
		 *
		 * @param a the array to fill with the list of nodes
		 * @return an array of nodes
		 */
		public Node[] toArray(Node[] a) {
			return asList().toArray(a);
		}
		
		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder();
			for (Node node : this) buf.append(node).append('\n');
			return buf.toString();
		}
	}

	private ItemList() {
		super(null, null);
		this.seq = Sequence.EMPTY_SEQUENCE;
		this.items = this.modifiableItems = Collections.emptyList();
	}

	ItemList(Sequence seq, NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
		this.seq = seq;
		modifiableItems = new ArrayList<Item>(seq.getItemCount());
		try {
			for (SequenceIterator it = seq.iterate(); it.hasNext(); ) {
				org.exist.xquery.value.Item existItem = it.nextItem();
				if (existItem instanceof NodeValue) {
					modifiableItems.add(new Node((NodeValue) existItem, namespaceBindings.extend(), db));
				} else {
					modifiableItems.add(new Item(existItem, namespaceBindings.extend(), db));
				}
			}
		} catch (XPathException xpe) {
			throw new DatabaseException(xpe);
		}
		this.items = Collections.unmodifiableList(modifiableItems);
	}
	
	/**
	 * Remove all deleted nodes from this list.  Trying to access the list in a query
	 * context when it contains a deleted node will cause a stale reference exception.
	 * You can call this method whenever you suspect this will be the case, preferably
	 * just prior to access.  This will also update the results for all direct access methods
	 * that wouldn't throw an exception, but could return a stale result.
	 * The updates aren't done automatically since they involve tricky synchronization
	 * issues and are expensive when not batched up, and since most clients won't need
	 * this feature.
	 */
	public void removeDeletedNodes() {
		boolean itemsRemoved = false;
		for (Iterator<Item> it = modifiableItems.iterator(); it.hasNext(); ) {
			Item item = it.next();
			if (item instanceof Node && ((Node) item).staleMarker.stale()) {
				it.remove();
				itemsRemoved = true;
			}
		}
		if (!itemsRemoved) return;
		// Code inlined from org.exist.xquery.XPathUtil to avoid creating temporary lists
		boolean nodesOnly = true;
		for (Item item : items) if (!(item instanceof Node)) {nodesOnly = false; break;}
		seq = nodesOnly ? new AVLTreeNodeSet() : new ValueSequence();
		try {
			for (Item item : items) seq.add(item.item);
		} catch (XPathException e) {
			throw new DatabaseException(e);
		}
	}
	
	@Override	Sequence convertToSequence() {
		for (Item item : items) if (item instanceof Node) ((Node) item).staleMarker.check();
		return seq;
	}
	
	/**
	 * Return the number of elements in this item list.
	 * 
	 * @return the number of elements in this item list
	 */
	public int size() {
		return items.size();
	}
	
	/**
	 * Return whether this item list is empty.
	 *
	 * @return <code>true</code> if this item list has no elements
	 */
	public boolean isEmpty() {
		return items.isEmpty();
	}

	/**
	 * Return the item at the given index in this result.  Indexing starts at 0.
	 *
	 * @param index the index of the desired item
	 * @return the item at the given index
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 */
	public Item get(int index) {
		return items.get(index);
	}
	
	/**
	 * Delete all nodes contained in this item list; skip over any items (values) that
	 * it doesn't make sense to try to delete.
	 */
	public void deleteAllNodes() {
		Transaction tx = Database.requireTransaction();
		try {
			for (Item item : items) if (item instanceof Node) ((Node) item).delete();
			tx.commit();
		} finally {
			tx.abortIfIncomplete();
		}
	}
	
	@Override public boolean equals(Object o) {
		if (!(o instanceof ItemList)) return false;
		return items.equals(((ItemList) o).items);
	}
	
	/**
	 * The hash code computation can be expensive, and the hash codes may not be very well distributed.
	 * You probably shouldn't use item lists in situations where they might get hashed.
	 */
	@Override public int hashCode() {
		int hashCode = 1;
		for (Item item : items) hashCode = hashCode * 31 + item.hashCode();
		return hashCode;
	}
	
	/**
	 * Return an iterator over all the items in this list.
	 * 
	 * @return an iterator over this item list
	 */
	public Iterator<Item> iterator() {
		return items.iterator();
	}
	
	/**
	 * Return an unmodifiable list view over the list of items.
	 *
	 * @return a list view
	 */
	public List<Item> asList() {
		return items;
	}
	
	/**
	 * Convert this list of items to an array.
	 *
	 * @return an array of items
	 */
	public Item[] toArray() {
		return items.toArray(new Item[size()]);
	}
	
	/**
	 * Convert this list of items to an array.  If the given array is large enough, fill it; if it's
	 * larger than necessary, put a <code>null</code> marker after the end of the list.  If the
	 * array is not large enough, allocate a new one.
	 *
	 * @param a the array to fill with items
	 * @return an array of items
	 */
	public Item[] toArray(Item[] a) {
		return items.toArray(a);
	}
	
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("(");
		boolean first = true;
		for (Item item : items) {
			if (first) first = false; else buf.append(", ");
			buf.append(item);
		}
		buf.append(")");
		return buf.toString();
	}
	
	/**
	 * Return a view of this item list as a list of effective string values.  Note that no extra
	 * memory is used to present this view; effective string values are computed on demand.
	 * 
	 * @return a virtual collection of string values contained in this item list
	 */
	public ValuesFacet values() {
		if (values == null) values = new ValuesFacet();
		return values;
	}
	
	/**
	 * Return a view of this item list as a list of nodes.  If this list contains any items that are
	 * not nodes, operations on the facet may fail.  Note that no extra memory is used to
	 * present this view.
	 *
	 * @return a virtual collection of nodes contained in this item list
	 */
	public NodesFacet nodes() {
		if (nodes == null) nodes = new NodesFacet();
		return nodes;
	}

	static final ItemList NULL = new ItemList() {
		@Override public QueryService query() {return QueryService.NULL;}
		@Override public ValuesFacet values() {return new ValuesFacet() {
			@Override	public Iterator<String> iterator() {return Database.emptyIterator();}
			// toArray/0 and toArray/1 take care of themselves thanks to size()
		};}
		@Override public NodesFacet nodes() {return new NodesFacet() {
			@Override public Iterator<Node> iterator() {return Database.emptyIterator();}
			// toArray/0 and toArray/1 take care of themselves thanks to size()
		};}
	};

}
