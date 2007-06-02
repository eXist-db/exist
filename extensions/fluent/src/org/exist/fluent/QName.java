package org.exist.fluent;

import javax.xml.XMLConstants;

import org.w3c.dom.*;
import org.w3c.dom.Document;

/**
 * A qualified name, consisting of a namespace and a local name.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class QName extends javax.xml.namespace.QName {
	
	private final String tag;
	
	/**
	 * Create a qualified name.
	 *
	 * @param namespace the namespace of the qualified name, <code>null</code> if none
	 * @param localName the local part of the qualified name, must not be <code>null</code> or empty
	 * @param prefix the prefix to use for the qualified name, <code>null</code> if default (empty) prefix
	 */
	public QName(String namespace, String localName, String prefix) {
		super(namespace == null ? XMLConstants.NULL_NS_URI : namespace, localName, prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix);
		if (localName == null || localName.length() == 0) throw new IllegalArgumentException("null or empty local name");
		if (prefix == null || prefix.equals(XMLConstants.DEFAULT_NS_PREFIX)) {
			tag = localName;
		} else {
			tag = prefix + ":" + localName;
		}
	}
	
	/**
	 * Return whether this qualified name is actually qualified by a namespace or not.
	 *
	 * @return <code>true</code> if the qualified name has a namespace set, <code>false</code> if it's just a local name
	 */
	public boolean hasNamespace() {
		return !getNamespaceURI().equals(XMLConstants.NULL_NS_URI);
	}
	
	/**
	 * Create an element in the given document whose tag is this qualified name.  Correctly calls
	 * <code>createElement</code> or <code>createElementNS</code> depending on whether
	 * this name is actually qualified or not.
	 * 
	 * @param doc the document to use to create the element
	 * @return a new element whose tag is this qualified name
	 */
	public Element createElement(Document doc) {
		if (hasNamespace()) return doc.createElementNS(getNamespaceURI(), tag);
		return doc.createElement(tag);
	}
	
	/**
	 * Create an attribute in the given document whose name is this qualified name.  Correctly calls
	 * <code>createAttribute</code> or <code>createAttributeNS</code> depending on whether
	 * this name is actually qualified or not.
	 * 
	 * @param doc the document to use to create the attribute
	 * @return a new attribute whose name is this qualified name
	 */
	public Attr createAttribute(Document doc) {
		if (hasNamespace()) return doc.createAttributeNS(getNamespaceURI(), tag);
		return doc.createAttribute(tag);
	}
	
	/**
	 * Set an attribute value on the given element, where the attribute's name is this qualified name.
	 * Correctly calls <code>setAttribute</code> or <code>setAttributeNS</code> depending on whether
	 * this name is actually qualified or not.
	 *
	 * @param elem the element on which to set the attribute
	 * @param value the value of the attribute
	 */
	public void setAttribute(Element elem, String value) {
		if (hasNamespace()) {
			elem.setAttributeNS(getNamespaceURI(), tag, value);
		} else {
			elem.setAttribute(tag, value);
		}
	}
	
	/**
	 * Get the attribute with this qualified name from the given element.  Correctly calls
	 * <code>getAttributeNode</code> or <code>getAttributeNodeNS</code> depending on whether
	 * this name is actually qualified or not.
	 *
	 * @param elem the element to read the attribute from
	 * @return the attribute node with this qualified name
	 */
	public Attr getAttributeNode(Element elem) {
		if (hasNamespace()) return elem.getAttributeNodeNS(getNamespaceURI(), getLocalPart());
		return elem.getAttributeNode(tag);
	}
	
	/**
	 * Return the qualified name of the given node.
	 *
	 * @param node the target node
	 * @return the node's qualified name
	 */
	public static QName of(org.w3c.dom.Node node) {
		String localName = node.getLocalName();
		if (localName == null) localName = node.getNodeName();
		return new QName(node.getNamespaceURI(), localName, node.getPrefix());
	}
	
	/**
	 * Parse the given tag into a qualified name within the context of the given namespace bindings.
	 *
	 * @param tag the tag to parse, in standard XML format
	 * @param namespaces the namespace bindings to use
	 * @return the qualified name of the given tag
	 */
	public static QName parse(String tag, NamespaceMap namespaces) {
		return parse(tag, namespaces, namespaces.get(""));
	}
	
	/**
	 * Parse the given tag into a qualified name within the context of the given namespace bindings,
	 * overriding the default namespace binding with the given one.  This is useful for parsing
	 * attribute names, where a lack of prefix should be interpreted as no namespace rather
	 * than the default namespace currently in effect.
	 *
	 * @param tag the tag to parse, in standard XML format
	 * @param namespaces the namespace bindings to use
	 * @param defaultNamespace the URI to use as the default namespace, in preference to any specified in the namespace bindings
	 * @return the qualified name of the given tag
	 */
	public static QName parse(String tag, NamespaceMap namespaces, String defaultNamespace) {
		int colonIndex = tag.indexOf(':');
		if (colonIndex == 0 || colonIndex == tag.length()-1) throw new IllegalArgumentException("illegal tag syntax '" + tag + "'");
		String prefix = colonIndex == -1 ? "" : tag.substring(0, colonIndex);
		String ns = prefix.length() > 0 ? namespaces.get(prefix) : defaultNamespace;
		if (ns == null && prefix.length() > 0) throw new IllegalArgumentException("no namespace registered for tag prefix '" + prefix + "'");
		return new QName(ns, tag.substring(colonIndex+1), prefix);
	}

}
