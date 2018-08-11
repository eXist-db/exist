package org.exist.fluent;

import java.io.IOException;
import java.util.*;

import javax.xml.datatype.*;

import org.exist.collections.Collection;
import org.exist.collections.triggers.*;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.storage.io.VariableByteOutputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.*;

/**
 * A node in the database.  Nodes are most often contained in XML documents, but can also
 * be transient in-memory nodes created by a query.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class Node extends Item {
	
	private XMLDocument document;
	final StaleMarker staleMarker = new StaleMarker();
	
	private Node() {}
	
	Node(org.exist.xquery.value.NodeValue item, NamespaceMap namespaceBindings, Database db) {
		super(item, namespaceBindings, db);
		if (item instanceof NodeProxy) {
			NodeProxy proxy = (NodeProxy) item;
			String docPath = proxy.getOwnerDocument().getURI().getCollectionPath();
			staleMarker.track(docPath.substring(0, docPath.lastIndexOf('/')));	// folder
			staleMarker.track(docPath);	// document
			staleMarker.track(docPath + "#" + proxy.getNodeId());	// node
		}
	}
	
	@Override	Sequence convertToSequence() {
		staleMarker.check();
		return super.convertToSequence();
	}

	public boolean extant() {
		return !staleMarker.stale();
	}
	
	org.w3c.dom.Node getDOMNode() {
		staleMarker.check();	
		try {
			org.w3c.dom.Node domNode = ((NodeValue) item).getNode();
			if (domNode == null) throw new DatabaseException("unable to load node data");
			return domNode;
		} catch (org.exist.util.sanity.AssertFailure e) {
			throw new DatabaseException(e);
		}
	}
	
	/**
	 * Return this node.
	 * 
	 * @return this node
	 */
	@Override public Node node() {
		return this;
	}
	
	@Override public Comparable<Object> comparableValue() {
		throw new DatabaseException("nodes are not comparable");
	}
	
	/**
	 * Return whether this node represents the same node in the database as the given object.
	 */
	@Override public boolean equals(Object o) {
		if (!(o instanceof Node)) return false;
		Node that = (Node) o;
		if (item == that.item) return true;
		if (this.item instanceof NodeProxy && that.item instanceof NodeProxy) {
			try {
				return ((NodeProxy) this.item).equals((NodeProxy) that.item);
			} catch (XPathException e) {
				// fall through to return false below
			}
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
			try(final VariableByteOutputStream buf = new VariableByteOutputStream()) {
				proxy.getNodeId().write(buf);
				return proxy.getOwnerDocument().getURI().hashCode() ^ Arrays.hashCode(buf.toByteArray());
			} catch (IOException e) {
				throw new RuntimeException("unable to serialize node's id to compute hashCode", e);
			}
		} else {
			return item.hashCode();
		}
	}
	
	/**
	 * Return the namespace bindings in force in the scope of this node.  Only works on nodes
	 * that are XML elements.  Namespaces reserved by the XML spec, and implicitly in scope
	 * for all XML elements, are not reported.
	 *
	 * @return the namespace bindings in force for this node
	 */
	public NamespaceMap inScopeNamespaces() {
		NamespaceMap namespaceMap = new NamespaceMap();
		for (final Iterator<String> it = query().all(
				"for $prefix in in-scope-prefixes($_1) return ($prefix, namespace-uri-for-prefix($prefix, $_1))", this).values().iterator(); it.hasNext(); ) {
			final String prefix = it.next();
			final String namespace = it.next();
			if (!NamespaceMap.isReservedPrefix(prefix)) {
				namespaceMap.put(prefix, namespace);
			}
		}
		return namespaceMap;
	}
	
	/**
	 * Compare the order of two nodes in a document. 
	 *
	 * @param node the node to compare this one to
	 * @return node 0 if this node is the same as the given node, a value less than 0 if it precedes the
	 * 	given node in the document, and a value great than 0 if it follows the given node in the document
	 * @throws DatabaseException if this node and the given one are not in the same document
	 */
	public int compareDocumentOrderTo(Node node) {
		if (this.item == node.item) return 0;
		NodeValue nv1 = (NodeValue) this.item;
		NodeValue nv2 = (NodeValue) node.item;
		if (nv1.getImplementationType() != nv2.getImplementationType())
			throw new DatabaseException("can't compare different node types, since they can never be in the same document");
		if (nv1.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			NodeProxy n1 = (NodeProxy) item;
			NodeProxy n2 = (NodeProxy) node.item;
			if (n1.getOwnerDocument().getDocId() != n2.getOwnerDocument().getDocId()) 
				throw new DatabaseException("can't compare document order of nodes in disparate documents:  this node is in " + document() + " and the argument node in " + node.document());
			if (n1.getNodeId().equals(n2.getNodeId())) return 0;
			try {
				return n1.before(n2, false) ? -1 : +1;
			} catch (XPathException e) {
				throw new DatabaseException("unable to compare nodes", e);
			}
		} else if (nv1.getImplementationType() == NodeValue.IN_MEMORY_NODE) {
			org.exist.dom.memtree.NodeImpl n1 = (org.exist.dom.memtree.NodeImpl) nv1;
			org.exist.dom.memtree.NodeImpl n2 = (org.exist.dom.memtree.NodeImpl) nv2;
			final org.exist.dom.memtree.DocumentImpl n1Doc = n1.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE ? (org.exist.dom.memtree.DocumentImpl)n1 : n1.getOwnerDocument();
			final org.exist.dom.memtree.DocumentImpl n2Doc = n2.getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE ? (org.exist.dom.memtree.DocumentImpl)n2 : n2.getOwnerDocument();

			if (n1Doc != n2Doc)
				throw new DatabaseException("can't compare document order of in-memory nodes created separately");
			try {
				return n1.before(n2, false) ? -1 : +1;
			} catch (XPathException e) {
				throw new DatabaseException("unable to compare nodes", e);
			}
		} else {
			throw new DatabaseException("unknown node implementation type: " + nv1.getImplementationType());
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
			document = Document.newInstance(((NodeProxy) item).getOwnerDocument(), this).xml();
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
					Transaction tx = db.requireTransactionWithBroker();
					try {
						final DocumentImpl ownerDoc = node.getOwnerDocument();
						tx.lockWrite(ownerDoc);
						DocumentTrigger trigger = fireTriggerBefore(tx);
						node.appendChildren(tx.tx, toNodeList(nodes), 0);
						NodeHandle result = (NodeHandle) node.getLastChild();
						touchDefragAndFireTriggerAfter(tx, trigger);
						tx.commit();
						if (result == null) {
                            return null;
                        }
						NodeProxy proxy = new NodeProxy(result);
						return new Node(proxy, namespaceBindings.extend(), db);
					} catch (DOMException e) {
						throw new DatabaseException(e);
					} catch (TriggerException e) {
						throw new DatabaseException("append aborted by listener", e);
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (ClassCastException e) {
			if (getDOMNode() instanceof org.exist.dom.memtree.NodeImpl) {
				throw new UnsupportedOperationException("appends to in-memory nodes are not supported");
			} else {
				throw new UnsupportedOperationException("cannot append to a " + Type.getTypeName(item.getType()));
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
			Transaction tx = db.requireTransactionWithBroker();
			try {
				if (parent instanceof NodeHandle) {
                    tx.lockWrite(((NodeHandle) parent).getOwnerDocument());
                }
				DocumentTrigger trigger = fireTriggerBefore(tx);
				parent.removeChild(tx.tx, child);
				touchDefragAndFireTriggerAfter(tx, trigger);
				tx.commit();
			} catch (DOMException e) {
				throw new DatabaseException(e);
			} catch (TriggerException e) {
				throw new DatabaseException("delete aborted by listener", e);
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
			if (oldNode.getParentNode().getNodeType() == org.w3c.dom.Node.DOCUMENT_NODE)
				return document().folder().documents().build(Name.overwrite(db, document().name()));
			return new ElementBuilder<Object>(namespaceBindings, false, new ElementBuilder.CompletedCallback<Object>() {
				public Object completed(org.w3c.dom.Node[] nodes) {
					assert nodes.length == 1;
					Transaction tx = db.requireTransactionWithBroker();
					try {
						DocumentImpl doc = (DocumentImpl) oldNode.getOwnerDocument();
						tx.lockWrite(doc);
						DocumentTrigger trigger = fireTriggerBefore(tx);
						((NodeImpl) oldNode.getParentNode()).replaceChild(tx.tx, nodes[0], oldNode);
						touchDefragAndFireTriggerAfter(tx, trigger);
						tx.commit();
						// no point in returning the old node; we'd rather return the newly inserted one,
						// but it's not easily available
						return null;
					} catch (DOMException e) {
						throw new DatabaseException(e);
					} catch (TriggerException e) {
						throw new DatabaseException("append aborted by listener", e);
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (ClassCastException e) {
			if (getDOMNode() instanceof org.exist.dom.memtree.NodeImpl) {
				throw new UnsupportedOperationException("replacement of in-memory nodes is not supported");
			} else {
				throw new UnsupportedOperationException("cannot replace a " + Type.getTypeName(item.getType()));
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
					Transaction tx = db.requireTransactionWithBroker();
					try {
						DocumentImpl doc = elem.getOwnerDocument();
						tx.lockWrite(doc);
						DocumentTrigger trigger = fireTriggerBefore(tx);
						elem.removeAppendAttributes(tx.tx, removeList, addList);
						touchDefragAndFireTriggerAfter(tx, trigger);
						tx.commit();
					} catch (TriggerException e) {
						throw new DatabaseException("append aborted by listener", e);
					} finally {
						tx.abortIfIncomplete();
					}
				}
			});
		} catch (ClassCastException e) {
			if (getDOMNode() instanceof org.exist.dom.memtree.ElementImpl) {
				throw new UnsupportedOperationException("updates on in-memory nodes are not supported");
			} else {
				throw new UnsupportedOperationException("cannot update attributes on a " + Type.getTypeName(item.getType()));
			}
		}
	}
	
	private DocumentTrigger fireTriggerBefore(Transaction tx) throws TriggerException {
		if (!(item instanceof NodeProxy)) return null;
		
		DocumentImpl docimpl = ((NodeProxy) item).getOwnerDocument();
		Collection col = docimpl.getCollection();
		
		DocumentTrigger trigger = new DocumentTriggers(tx.broker, null, col, col.getConfiguration(tx.broker));
			
		trigger.beforeUpdateDocument(tx.broker, tx.tx, docimpl);

		return trigger;
	}
	
	private void touchDefragAndFireTriggerAfter(Transaction tx, DocumentTrigger trigger) throws TriggerException {
		DocumentImpl doc = ((NodeProxy) item).getOwnerDocument();
		doc.getMetadata().setLastModified(System.currentTimeMillis());
		tx.broker.storeXMLResource(tx.tx, doc);
		if (item instanceof NodeProxy) Database.queueDefrag(((NodeProxy) item).getOwnerDocument());
		if (trigger == null) return;
		DocumentImpl docimpl = ((NodeProxy) item).getOwnerDocument();
		
		trigger.afterUpdateDocument(tx.broker, tx.tx, docimpl);
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
		@Override public String valueWithDefault(String defaultValue) {return Item.NULL.value();}
		@Override	Sequence convertToSequence() {return Item.NULL.convertToSequence();}
		
		@Override public String toString() {	return "NULL Node";}
	};

}
