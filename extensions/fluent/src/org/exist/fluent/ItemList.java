package org.exist.fluent;

import java.util.*;

import org.exist.dom.NodeProxy;
import org.exist.xquery.XPathException;
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
			try {
				return new Iterator<String>() {
					private final SequenceIterator delegate = seq.iterate();
					public boolean hasNext() {
						return delegate.hasNext();
					}
					public String next() {
						try {
							return delegate.nextItem().getStringValue();
						} catch (XPathException e) {
							throw new DatabaseException(e);
						}
					}
					public void remove() {throw new UnsupportedOperationException();}
				};
			} catch (XPathException e) {
				throw new DatabaseException("failed to construct iterator over sequence", e);
			}
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
				@Override public String get(int index) {
					try {
						return seq.itemAt(index).getStringValue();
					} catch (XPathException e) {
						throw new DatabaseException(e);
					}
				}
				@Override public int size() {
					return seq.getItemCount();
				}
			};
		}
		
		/**
		 * Convert the list of effective string values to an array.
		 * 
		 * @return an array of effective string values
		 */
		public String[] toArray() {
			return toArray(new String[size()]);
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
			if (a == null && size() == 0) return null;
			if (a == null || a.length < size()) a = new String[size()];
			for (int i = 0; i < size(); i++) {
				try {
					a[i] = seq.itemAt(i).getStringValue();
				} catch (XPathException e) {
					throw new DatabaseException(e);
				}
			}
			if (a.length > size()) a[size()] = null;
			return a;
		}
		
		@Override
		public String toString() {
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
			try {
				return new Iterator<Node>() {
					private final SequenceIterator delegate = seq.iterate();
					public boolean hasNext() {
						return delegate.hasNext();
					}
					public Node next() {
						return new Node(delegate.nextItem(), namespaceBindings.extend(), db);
					}
					public void remove() {throw new UnsupportedOperationException();}
				};
			} catch (XPathException e) {
				throw new DatabaseException("failed to construct iterator over sequence", e);
			}
		}
		
		/**
		 * Return the set of documents to which the nodes in this list belong.
		 *
		 * @return the set of documents convering the nodes in the list
		 */
		public Set<XMLDocument> documents() {
			Set<XMLDocument> docs = new HashSet<XMLDocument>();
			for (Node node : this) docs.add(node.document());
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
					return new Node(seq.itemAt(index), namespaceBindings.extend(), db);
				}
				@Override public int size() {
					return seq.getItemCount();
				}
			};
		}
		
		/**
		 * Convert the list of nodes to an array.
		 *
		 * @return an array of nodes
		 */
		public Node[] toArray() {
			return toArray(new Node[size()]);
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
			if (a.length < size()) a = new Node[size()];
			for (int i = 0; i < size(); i++) {
				a[i] = new Node(seq.itemAt(i), namespaceBindings.extend(), db);
			}
			if (a.length > size()) a[size()] = null;
			return a;
		}
		
		@Override
		public String toString() {
			StringBuilder buf = new StringBuilder();
			for (Node node : this) {
				buf.append(node).append('\n');
			}
			return buf.toString();
		}
	}

	
	private final Sequence seq;
	private ValuesFacet values;
	private NodesFacet nodes;

	private ItemList() {
		super(null, null);
		this.seq = null;
	}

	ItemList(Sequence seq, NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
		this.seq = seq;
		for (SequenceIterator it = seq.unorderedIterator(); it.hasNext(); ) {
			org.exist.xquery.value.Item item = it.nextItem();
			if (item instanceof NodeProxy) Database.trackNode((NodeProxy) item);
		}
	}
	
	@Override	Sequence convertToSequence() {
		return seq;
	}
	
	/**
	 * Return the number of elements in this item list.
	 * 
	 * @return the number of elements in this item list
	 */
	public int size() {
		return seq.getItemCount();
	}
	
	/**
	 * Return whether this item list is empty.
	 *
	 * @return <code>true</code> if this item list has no elements
	 */
	public boolean isEmpty() {
		return seq.isEmpty();
	}
	
	Item wrap(org.exist.xquery.value.Item x) {
		if (x instanceof NodeValue) return new Node(x, namespaceBindings.extend(), db);
		return new Item(x, namespaceBindings.extend(), db);
	}

	/**
	 * Return the item at the given index in this result.  Indexing starts at 0.
	 *
	 * @param index the index of the desired item
	 * @return the item at the given index
	 * @throws IndexOutOfBoundsException if the index is out of bounds
	 */
	public Item get(int index) {
		if (index < 0 || index >= size()) throw new IndexOutOfBoundsException("index " + index + " out of bounds (upper bound at " + size() + ")");
		return wrap(seq.itemAt(index));
	}
	
	/**
	 * Delete all nodes contained in this item list; skip over any items (values) that
	 * it doesn't make sense to try to delete.
	 */
	public void deleteAllNodes() {
		Transaction tx = Database.requireTransaction();
		try {
			for (Item item : this) if (item instanceof Node) ((Node) item).delete();
			tx.commit();
		} finally {
			tx.abortIfIncomplete();
		}
	}
	
	@Override public boolean equals(Object o) {
		if (!(o instanceof ItemList)) return false;
		ItemList that = (ItemList) o;
		if (this.size() != that.size()) return false;
		for (int i=0; i<size(); i++) if (!this.get(i).equals(that.get(i))) return false;
		return true;
	}
	
	/**
	 * The hash code computation can be expensive, and the hash codes may not be very well distributed.
	 * You probably shouldn't use item lists in situations where they might get hashed.
	 */
	@Override public int hashCode() {
		int hashCode = 1;
		for (Item item : this) hashCode = hashCode * 31 + item.hashCode();
		return hashCode;
	}
	
	/**
	 * Return an iterator over all the items in this list.
	 * 
	 * @return an iterator over this item list
	 */
	public Iterator<Item> iterator() {
		try {
			return new Iterator<Item>() {
				private final SequenceIterator delegate = seq.iterate();
				public boolean hasNext() {
					return delegate.hasNext();
				}
				public Item next() {
					return wrap(delegate.nextItem());
				}
				public void remove() {throw new UnsupportedOperationException();}
			};
		} catch (XPathException e) {
			throw new DatabaseException("failed to construct iterator over sequence", e);
		}
	}
	
	/**
	 * Return an unmodifiable list view over the list of items.
	 *
	 * @return a list view
	 */
	public List<Item> asList() {
		return new AbstractList<Item>() {
			@Override public Item get(int index) {
				return wrap(seq.itemAt(index));
			}
			@Override public int size() {
				return seq.getItemCount();
			}
		};
	}
	
	/**
	 * Convert this list of items to an array.
	 *
	 * @return an array of items
	 */
	public Item[] toArray() {
		return toArray(new Item[size()]);
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
		if (a.length < size()) a = new Item[size()];
		for (int i = 0; i < size(); i++) {
			a[i] = wrap(seq.itemAt(i));
		}
		if (a.length > size()) a[size()] = null;
		return a;
	}
	
	@Override
	public String toString() {
		StringBuilder buf = new StringBuilder();
		buf.append("(");
		boolean first = true;
		for (Item item : this) {
			if (!first) {
				buf.append(", ");
				first = false;
			}
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
		@Override public int size() {return 0;}
		@Override public Iterator<Item> iterator() {return Database.emptyIterator();}
		@Override public ValuesFacet values() {return new ValuesFacet() {
			@Override	public Iterator<String> iterator() {return Database.emptyIterator();}
			// toArray/0 and toArray/1 take care of themselves thanks to size()
		};}
		@Override public NodesFacet nodes() {return new NodesFacet() {
			@Override public Iterator<Node> iterator() {return Database.emptyIterator();}
			// toArray/0 and toArray/1 take care of themselves thanks to size()
		};}
		@Override Sequence convertToSequence() {
			return Sequence.EMPTY_SEQUENCE;
		}
	};

}
