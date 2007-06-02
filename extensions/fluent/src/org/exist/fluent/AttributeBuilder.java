package org.exist.fluent;

import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.dom.NodeListImpl;
import org.w3c.dom.*;

/**
 * Allows attributes to be added to, replaced in and removed from an existing
 * element in the database.  The updates are batched for efficiency; you must call
 * {@link #commit()} to apply them to the database.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class AttributeBuilder {

	interface CompletedCallback {
		public void completed(NodeList removeList, NodeList addList);
	}
	
	private static final Logger LOG = Logger.getLogger(AttributeBuilder.class);
	
	private final CompletedCallback callback;
	private final NamespaceMap namespaceBindings;
	private final Element element;
	private boolean done;
	private final org.w3c.dom.Document doc;
	private final NodeListImpl removedAttributes = new NodeListImpl(), addedAttributes = new NodeListImpl();
	private final Map<QName, Attr> removedMap = new HashMap<QName, Attr>(), addedMap = new HashMap<QName, Attr>();
	
	AttributeBuilder(Element element, NamespaceMap namespaceBindings, CompletedCallback callback) {
		this.element = element;
		this.callback = callback;
		this.namespaceBindings = namespaceBindings.extend();
		this.doc = ElementBuilder.createDocumentNode();
	}
	
	private void checkDone() {
		if (done) throw new IllegalStateException("builder already done");
	}
	
	/**
	 * Add a namespace binding to this builder's namespaces map.
	 *
	 * @param key the prefix to bind
	 * @param uri the URI to bind it to
	 * @return this attribute builder, for chaining calls
	 */
	public AttributeBuilder namespace(String key, String uri) {
		namespaceBindings.put(key, uri);
		return this;
	}
	
	/**
	 * Create a new attribute or change the value of an existing one.  Later calls will overwrite
	 * the values set by earlier ones.
	 *
	 * @param name the name of the attribute
	 * @param value the value of the attribute, if not a <code>String</code> will be converted using {@link DataUtils#toXMLString(Object)}
	 * @return this attribute builder, for chaining calls
	 */
	public AttributeBuilder attr(String name, Object value) {
		checkDone();
		QName qname = QName.parse(name, namespaceBindings, null);
		
		// if previously added, this value will overwrite so remove old attribute from list
		Attr attr = addedMap.get(qname);
		if (attr != null) addedAttributes.remove(attr);

		// should be removed iff it is currently set on the element
		attr = qname.getAttributeNode(element);
		if (attr != null) {
			if (!removedMap.containsKey(qname)) {
				removedAttributes.add(attr);
				removedMap.put(qname, attr);
			}
		} else {
			assert !removedMap.containsKey(qname);
		}
		
		attr = qname.createAttribute(doc);
		attr.setValue(DataUtils.toXMLString(value));
		addedAttributes.add(attr);
		addedMap.put(qname, attr);
		
		return this;
	}
	
	/**
	 * Add an attribute or change an existing attribute's value only if the given condition holds.
	 * Behaves just like {@link #attr(String, Object)} if <code>condition</code> is true, does
	 * nothing otherwise.
	 *
	 * @param condition the condition that must be satisfied before the attribute's value is set
	 * @param name the name of the attribute
	 * @param value the value of the attribute
	 * @return this attribute builder, for chaining calls
	 */
	public AttributeBuilder attrIf(boolean condition, String name, Object value) {
		checkDone();
		if (condition) attr(name, value);
		return this;
	}

	/**
	 * Delete an attribute.  Does nothing if the attribute does not exist.  It's allowed (though probably
	 * pointless) to delete attributes that were created using {@link #attr(String, Object)}, even if not
	 * yet committed.
	 *
	 * @param name the name of the attribute to delete
	 * @return this attribute builder, for chaining
	 */
	public AttributeBuilder delAttr(String name) {
		checkDone();
		QName qname = QName.parse(name, namespaceBindings, null);
		
		// override any previous addition
		Attr attr = addedMap.get(qname);
		if (attr != null) {
			addedAttributes.remove(attr);
			addedMap.remove(qname);
		}
		
		// if currently set on the element, and not already listed for removal, remove it
		attr = qname.getAttributeNode(element);
		if (attr != null && !removedMap.containsKey(qname)) {
			removedAttributes.add(attr);
			removedMap.put(qname, attr);
		}
		
		return this;
	}
	
	/**
	 * Commit the attribute changes recorded with the other methods to the database.
	 */
	public void commit() {
		checkDone();
		done = true;
		if (removedAttributes.isEmpty() && addedAttributes.isEmpty()) return;
		callback.completed(removedAttributes, addedAttributes);
	}

	@Override
	protected void finalize() {
		if (!done) LOG.warn("disposed without commit");
	}
	
}
