package org.exist.fluent;

import java.io.IOException;
import java.util.Arrays;
import java.util.Date;

import javax.xml.datatype.Duration;
import javax.xml.datatype.XMLGregorianCalendar;

import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xquery.value.*;
import org.w3c.dom.DOMException;
import org.w3c.dom.NodeList;

/**
 * A node in the database.  Nodes are most often contained in XML documents, but can also
 * be transient in-memory nodes created by a query.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Node extends Item {
	
	private XMLDocument document;
	private final StaleMarker staleMarker = new StaleMarker();
	
	private Node() {}
	
	Node(org.exist.xquery.value.Item item, NamespaceMap namespaceBindings, Database db) {
		super(item, namespaceBindings, db);
		if (!(item instanceof NodeValue)) throw new IllegalArgumentException("item is not a node");
		if (item instanceof NodeProxy) {
			NodeProxy proxy = (NodeProxy) item;
			String docPath = proxy.getDocument().getURI().getCollectionPath();
			staleMarker.track(docPath.substring(0, docPath.lastIndexOf('/')));	// folder
			staleMarker.track(docPath);	// document
			staleMarker.track(docPath + "#" + proxy.getNodeId());	// node
		}
	}
	
	org.w3c.dom.Node getDOMNode() {
		staleMarker.check();
		org.w3c.dom.Node domNode = ((NodeValue) item).getNode();
		if (domNode == null) throw new DatabaseException("unable to load node data");
		return domNode;
	}
	
	/**
	 * Return this node.
	 * 
	 * @return this node
	 */
	@Override public Node node() {
		return this;
	}
	
	/**
	 * Return whether this node represents the same node in the database as the given object.
	 */
	@Override public boolean equals(Object o) {
		if (!(o instanceof Node)) return false;
		Node that = (Node) o;
		if (item == that.item) return true;
		if (this.item instanceof NodeProxy && that.item instanceof NodeProxy) {
			NodeProxy thisProxy = (NodeProxy) this.item, thatProxy = (NodeProxy) that.item;
			return
				thisProxy.getDocument().getURI().equals(thatProxy.getDocument().getURI()) &&
				thisProxy.getNodeId().equals(thatProxy.getNodeId());
		}
		return false;
	}
	
	/**
	 * Warning:  computing a node's hash code is surprisingly expensive, and the value is not cached.
	 * You should not use nodes in situations where they might get hashed.
	 */
	@Override public int hashCode() {
		return computeHashCode();
	}
	
	private int computeHashCode() {
		if (item instanceof NodeProxy) {
			NodeProxy proxy = (NodeProxy) item;
			VariableByteOutputStream buf = new VariableByteOutputStream();
			try {
				proxy.getNodeId().write(buf);
			} catch (IOException e) {
				throw new RuntimeException("unable to serialize node's id to compute hashCode", e);
			}
			return proxy.getDocument().getURI().hashCode() ^ Arrays.hashCode(buf.toByteArray());
		} else {
			return item.hashCode();
		}
	}

	/**
	 * Return the document to which this node belongs.
	 * 
	 * @return the document to which this node belongs
	 * @throws UnsupportedOperationException if this node does not belong to a document
	 */
	public XMLDocument document() {
		staleMarker.check();
		if (document == null) try {
			document = Document.newInstance(((NodeProxy) item).getDocument(), this).xml();
		} catch (ClassCastException e) {
			throw new UnsupportedOperationException("node is not part of a document in the database");
		}
		return document;
	}

	/**
	 * Return a builder that will append elements to this node's children.  The builder will return the
	 * appended node if a single node was appended, otherwise <code>null</code>.
	 *
	 * @return a builder that will append nodes to this node
	 */
	public ElementBuilder<Node> append() {
		staleMarker.check();	// do an early check to fail-fast, we'll check again on completion
		try {
			final StoredNode node = (StoredNode) getDOMNode(); 
			return new ElementBuilder<Node>(namespaceBindings, true, new ElementBuilder.CompletedCallback<Node>() {
				public Node completed(org.w3c.dom.Node[] nodes) {
					Transaction tx = Database.requireTransaction();
					try {
						StoredNode result = null;
						if (nodes.length == 1) {
							result = (StoredNode) node.appendChild(nodes[0]);
						} else {
							node.appendChildren(tx.tx, toNodeList(nodes), 0);
						}
						defrag(tx);
						tx.commit();
						if (result == null) return null;
						NodeProxy proxy = new NodeProxy((DocumentImpl) result.getOwnerDocument(), result.getNodeId(), result.getNodeType(), result.getInternalAddress());
						Database.trackNode(proxy);
						return new Node(proxy, namespaceBindings.extend(), db);
					} catch (DOMException e) {
						throw new DatabaseException(e);
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (ClassCastException e) {
			if (getDOMNode() instanceof org.exist.memtree.NodeImpl) {
				throw new UnsupportedOperationException("updates on in-memory nodes are not yet supported, but calling query().single(\"self::*\").node() on the node will implicitly materialize the result in a temporary area of the database");
			} else {
				throw new UnsupportedOperationException("cannot update attributes on a " + Type.getTypeName(item.getType()));
			}
		}
	}

	/**
	 * Delete this node from its parent.  This can delete an element from a document,
	 * or an attribute from an element, etc.  Trying to delete the root element of a
	 * document will delete the document instead.  If the node cannot be found, assume
	 * it's already been deleted and return silently.
	 */
	public void delete() {
		org.w3c.dom.Node child;
		try {
			child = getDOMNode();
		} catch (DatabaseException e) {
			return;
		}
		NodeImpl parent = (NodeImpl) child.getParentNode();
		if (child instanceof org.w3c.dom.Document || parent instanceof org.w3c.dom.Document) {
			document().delete();
		} else if (parent == null) {
			throw new DatabaseException("cannot delete node with no parent");
		} else {
			Transaction tx = Database.requireTransaction();
			try {
				parent.removeChild(tx.tx, child);
				tx.commit();
			} catch (DOMException e) {
				throw new DatabaseException(e);
			} finally {
				tx.abortIfIncomplete();
			}
		}
	}

	/**
	 * Return the name of this node, in the "prefix:localName" form.
	 *
	 * @return the name of this node
	 */
	public String name() {
		return getDOMNode().getNodeName();
	}

	/**
	 * Return the qualified name of this node, including its namespace URI, local name and prefix.
	 *
	 * @return the qname of this node
	 */
	public QName qname() {
		org.w3c.dom.Node node = getDOMNode();
		String localName = node.getLocalName();
		if (localName == null) localName = node.getNodeName();
		return new QName(node.getNamespaceURI(), localName, node.getPrefix());
	}

	/**
	 * Return a builder that will replace this node.  The builder returns <code>null</code>.
	 *
	 * @return a builder that will replace this node
	 * @throws UnsupportedOperationException if the node does not have a parent
	 */
	public ElementBuilder<?> replace() {
		// TODO: right now, can only replace an element; what about other nodes?
		// TODO: right now, can only replace with a single node, investigate multiple replace
		try {
			final NodeImpl oldNode = (NodeImpl) getDOMNode();
			if (oldNode.getParentNode() == null) throw new UnsupportedOperationException("cannot replace a " + Type.getTypeName(item.getType()) + " with no parent");
			return new ElementBuilder<Object>(namespaceBindings, false, new ElementBuilder.CompletedCallback<Object>() {
				public Object completed(org.w3c.dom.Node[] nodes) {
					assert nodes.length == 1;
					Transaction tx = Database.requireTransaction();
					try {
						((NodeImpl) oldNode.getParentNode()).replaceChild(tx.tx, nodes[0], oldNode);
						defrag(tx);
						tx.commit();
						// no point in returning the old node; we'd rather return the newly inserted one,
						// but it's not easily available
						return null;
					} catch (DOMException e) {
						throw new DatabaseException(e);
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (RuntimeException e) {
			if (getDOMNode() instanceof org.exist.memtree.NodeImpl) {
				throw new UnsupportedOperationException("updates on in-memory nodes are not yet supported, but calling query().single(\"self::*\").node() on the node will implicitly materialize the result in a temporary area of the database");
			} else {
				throw new UnsupportedOperationException("cannot update attributes on a " + Type.getTypeName(item.getType()));
			}
		}
	}

	/**
	 * Return a builder for updating the attribute values of this element.
	 *
	 * @return an attribute builder for this element
	 * @throws UnsupportedOperationException if this node is not an element
	 */
	public AttributeBuilder update() {
		try {
			final ElementImpl elem = (ElementImpl) getDOMNode();
			return new AttributeBuilder(elem, namespaceBindings, new AttributeBuilder.CompletedCallback() {
				public void completed(NodeList removeList, NodeList addList) {
					Transaction tx = Database.requireTransaction();
					try {
						elem.removeAppendAttributes(tx.tx, removeList, addList);
						defrag(tx);
						tx.commit();
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (ClassCastException e) {
			if (getDOMNode() instanceof org.exist.memtree.ElementImpl) {
				throw new UnsupportedOperationException("updates on in-memory nodes are not yet supported, but calling query().single(\"self::*\").node() on the node will implicitly materialize the result in a temporary area of the database");
			} else {
				throw new UnsupportedOperationException("cannot update attributes on a " + Type.getTypeName(item.getType()));
			}
		}
	}

	private void defrag(Transaction tx) {
		if (!(item instanceof NodeProxy)) return;
		DBBroker broker = null;
		try {
			broker = db.acquireBroker();
			Integer fragmentationLimit = (Integer) broker.customProperties.get(DBBroker.PROPERTY_XUPDATE_FRAGMENTATION_FACTOR);
			if (fragmentationLimit == null) fragmentationLimit = Integer.valueOf(0);
			DocumentImpl doc = ((NodeProxy) item).getDocument();
			if (doc.getMetadata().getSplitCount() > fragmentationLimit) broker.defragXMLResource(tx.tx, doc);
		} finally {
			db.releaseBroker(broker);
		}
	}
	
	static NodeList toNodeList(final org.w3c.dom.Node[] nodes) {
		return new NodeList() {
			public int getLength() {return nodes.length;}
			public org.w3c.dom.Node item(int index) {return nodes[index];}
		};
	}
	
	/**
	 * A null node, used as a placeholder where an actual <code>null</code> would be inappropriate.
	 */
	@SuppressWarnings("hiding")
	static final Node NULL = new Node() {
		@Override public ElementBuilder<Node> append() {throw new UnsupportedOperationException("cannot append to a null resource");}
		@Override public void delete() {}
		@Override public XMLDocument document() {throw new UnsupportedOperationException("null resource does not have a document");}
		@Override public String name() {throw new UnsupportedOperationException("null resource does not have a name");}
		@Override public QName qname() {throw new UnsupportedOperationException("null resource does not have a qname");}
		@Override public ElementBuilder<?> replace() {throw new UnsupportedOperationException("cannot replace a null resource");}
		@Override public AttributeBuilder update() {throw new UnsupportedOperationException("cannot update a null resource");}
		
		@Override public boolean booleanValue() {return Item.NULL.booleanValue();}
		@Override public double doubleValue() {return Item.NULL.doubleValue();}
		@Override public int intValue() {return Item.NULL.intValue();}
		@Override public long longValue() {return Item.NULL.longValue();}
		@Override public Duration durationValue() {return Item.NULL.durationValue();}
		@Override public XMLGregorianCalendar dateTimeValue() {return Item.NULL.dateTimeValue();}
		@Override public Date instantValue() {return Item.NULL.instantValue();}
		@Override public Node node() {return Item.NULL.node();}
		@Override public boolean extant() {return Item.NULL.extant();}
		@Override public QueryService query() {return Item.NULL.query();}
		@Override public String value() {return Item.NULL.value();}
		@Override	Sequence convertToSequence() {return Item.NULL.convertToSequence();}
		
		@Override public String toString() {	return "NULL Node";}
	};

}