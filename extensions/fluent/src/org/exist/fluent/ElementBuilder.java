package org.exist.fluent;

import java.util.*;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.log4j.Logger;
import org.w3c.dom.*;
import org.w3c.dom.Node;

/**
 * A builder of DOM trees, meant to be either stand alone or be inserted into
 * pre-existing ones.  Cannot remove nodes from the base tree.  You must {@link #commit()}
 * the builder to persist the recorded changes in the database.
 * 
 * @param <K> the type of object returned upon completion of the builder,
 * 	depends on the context in which the builder is used
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.18 $ ($Date: 2006/04/13 19:12:16 $)
 */
public class ElementBuilder<K> {
	
	interface CompletedCallback<T> {
		public T completed(Node[] nodes);
	}
	
	private static final Logger LOG = Logger.getLogger(ElementBuilder.class);
	
	private /* final */ CompletedCallback<K> callback;
	private final boolean allowFragment;
	private final List<Element> stack = new LinkedList<Element>();
	private final List<Node> top = new LinkedList<Node>();
	private boolean done;
	private final org.w3c.dom.Document doc;
	private NamespaceMap namespaceBindings;

	static org.w3c.dom.Document createDocumentNode() {
		try {
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			dbf.setNamespaceAware(true);
			return dbf.newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e) {
			throw new RuntimeException("unable to create new memory DOM", e);
		}
	}
	
	private class ScratchCallback implements CompletedCallback<Node> {
		public Node completed(Node[] nodes) {
			if (nodes.length == 1) return nodes[0];
			DocumentFragment frag = doc.createDocumentFragment();
			for (int i = 0; i < nodes.length; i++) {
				frag.appendChild(adopt(nodes[i]));
			}
			return frag;
		}		
	}
	
	/**
	 * Create a new element builder that creates an in-memory DOM tree and
	 * returns the top node (possibly a fragment node) upon commit.
	 *
	 * @param namespaceBindings the namespace bindings to use, or <code>null</code> for none
	 * @return a scratch in-memory builder
	 */
	public static ElementBuilder<Node> createScratch(NamespaceMap namespaceBindings) {
		ElementBuilder<Node> builder = new ElementBuilder<Node>(namespaceBindings, true, null);
		builder.setCallback(builder.new ScratchCallback());
		return builder;
	}
	
	ElementBuilder(NamespaceMap namespaceBindings, boolean allowFragment, CompletedCallback<K> callback) {
		this.callback = callback;
		this.allowFragment = allowFragment;
		this.namespaceBindings = namespaceBindings == null ? new NamespaceMap() : namespaceBindings.extend();
		this.doc = createDocumentNode();
	}
	
	private void setCallback(CompletedCallback<K> callback) {
		this.callback = callback;
	}

	private Node current() {
		if (stack.isEmpty()) throw new IllegalStateException("no current node");
		return stack.get(0);
	}
	
	private void checkDone() {
		if (done) throw new IllegalStateException("builder already done");
	}
	
	/**
	 * Insert a namespace binding scoped to this builder only.
	 *
	 * @param key the prefix to bind
	 * @param uri the namespace uri
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> namespace(String key, String uri) {
		namespaceBindings.put(key, uri);
		return this;
	}
	
	/**
	 * Insert a copy of the given node.
	 * 
	 * @param node the node to insert
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> node(org.exist.fluent.Node node) {
		return node(node.getDOMNode());
	}
	
	/**
	 * Insert copies of the given nodes.
	 * 
	 * @param nodes the nodes to insert
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> nodes(ItemList.NodesFacet nodes) {
		for (org.exist.fluent.Node node : nodes) node(node);
		return this;
	}
	
	/**
	 * Insert a copy of the given node.  The node can be an element node, a text node,
	 * a document node or a fragment node.  In the case of the latter two, their children
	 * are inserted instead.
	 * 
	 * @param node the node to insert
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> node(Node node) {
		checkDone();
		if (node instanceof org.exist.memtree.NodeImpl) ((org.exist.memtree.NodeImpl) node).expand();
		
		switch (node.getNodeType()) {
			
			case Node.DOCUMENT_NODE:
			case Node.DOCUMENT_FRAGMENT_NODE: {
				NodeList children = node.getChildNodes();
				for (int i=0; i<children.getLength(); i++) node(children.item(i));
				break;
			}
			
			case Node.ELEMENT_NODE:
				appendElem((Element) node);
				break;

			case Node.TEXT_NODE:
				appendText(node.getTextContent());
				break;

			default:
				throw new RuntimeException("can't append node type " + node.getNodeType());
		}
		
		return this;
	}
	
	/**
	 * Open a new element with the given tag.  The tag should be in the same format as in
	 * XML files (i.e. "prefix:localName") and is parsed into a QName according to the namespace
	 * bindings in effect for this builder.  The element must be closed with {@link #end(String)}
	 * before the builder is committed. 
	 *
	 * @param tag the tag of the element to insert
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> elem(String tag) {
		checkDone();
		Element elem = QName.parse(tag, namespaceBindings).createElement(doc);
		appendElem(elem);
		stack.add(0, elem);
		return this;
	}
	
	private Node adopt(Node node) {
		if (node.getOwnerDocument() == doc) return node;
		if (node.getParentNode() == null) try {
			Node result = doc.adoptNode(node);
			if (result != null) return result;
		} catch (DOMException e) {}
		return doc.importNode(node, true);
	}
	
	private void appendElem(Element elem) {
		if (stack.isEmpty()) {
			if (!allowFragment && !top.isEmpty()) throw new IllegalStateException("unable to build document fragment with multiple root nodes in current context");
			top.add(elem);
		} else {
			current().appendChild(adopt(elem));
		}
	}

	/**
	 * Close the currently open element, matching it to the given tag.
	 *
	 * @param tag the tag of the element to be ended
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> end(String tag) {
		checkDone();
		try {
			Element elem = stack.remove(0);
			QName elemName = QName.of(elem);
			if (!elemName.equals(QName.parse(tag, namespaceBindings))) throw new IllegalStateException("element on top of stack is '" + elemName + "' not '" + tag + "'");
			return this;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalStateException("no open elements to match end(" + tag + ")");
		}
	}
	
	/**
	 * Close the currently open element, matching it to one of the given tags.  Use
	 * this method when the element could be one of many, but it's not convenient to remember
	 * precisely which element name was used.  This is still safer than just popping the top element
	 * off arbitrarily.
	 *
	 * @param tags the possible tags of the element to be ended
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> end(String... tags) {
		checkDone();
		try {
			Element elem = stack.remove(0);
			QName elemName = QName.of(elem);
			boolean matched = false;
			for (String tag : tags) {
				if (elemName.equals(QName.parse(tag, namespaceBindings))) {
					matched = true;
					break;
				}
			}
			if (!matched) throw new IllegalStateException("element on top of stack is '" + elemName + "' not one of " + Arrays.asList(tags));
			return this;
		} catch (IndexOutOfBoundsException e) {
			throw new IllegalStateException("no open elements to match end(" + Arrays.asList(tags) + ")");
		}
	}
	
	/**
	 * Add an attribute to the currently open element.  If an attribute with the same name was
	 * previously added to the element, overwrite its value.
	 *
	 * @param name the name of the attribute to add
	 * @param value the value of the attribute, will be converted to a string using {@link DataUtils#toXMLString(Object)}
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> attr(String name, Object value) {
		checkDone();
		if (current().getNodeType() != Node.ELEMENT_NODE) throw new IllegalStateException("current node is not an element");
		QName.parse(name, namespaceBindings, null).setAttribute((Element) current(), DataUtils.toXMLString(value));
		return this;
	}
	
	/**
	 * Add an attribute to the currently open element if the given condition holds.  If <code>condition</code>
	 * is true, this behaves as {@link #attr(String, Object)}, otherwise it does nothing.
	 *
	 * @param condition the condition to satisfy before adding the attribute
	 * @param name the name of the attribute to add
	 * @param value the value of the attribute
	 * @return the element builder, for chaining calls
	 */
	public ElementBuilder<K> attrIf(boolean condition, String name, Object value) {
		if (condition) attr(name, value);
		return this;
	}
	
	/**
	 * Insert text into the currenly open element.
	 *
	 * @param text the text to insert
	 * @return this element builder, for chaining calls
	 */
	public ElementBuilder<K> text(Object text) {
		checkDone();
		appendText(DataUtils.toXMLString(text));
		return this;
	}
	
	private void appendText(String text) {
		Node textNode = doc.createTextNode(text);
		if (stack.isEmpty()) {
			if (!allowFragment) throw new IllegalStateException("unable to add a root text node in current context");
			top.add(textNode);
		} else {
			if (current().getNodeType() != Node.ELEMENT_NODE) throw new IllegalStateException("current node is not an element");
			current().appendChild(textNode);
		}
	}

	/**
	 * Commit this element builder, persisting the recorded elements into the database.
	 *
	 * @return the newly created resource, as appropriate
	 */
	public K commit() {
		checkDone();
		done = true;
		if (stack.size() != 0) throw new IllegalStateException("can't commit with " + stack.size() + " elements left open");
		if (top.isEmpty()) return null;
		return callback.completed(top.toArray(new Node[top.size()]));
	}
	
	@Override
	protected void finalize() {
		if (!done) LOG.warn("disposed without commit");
	}

	
}
