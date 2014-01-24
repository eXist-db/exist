/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2008-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.config;

import org.exist.dom.*;
import org.exist.numbering.NodeId;
import org.w3c.dom.*;

/**
 * Node proxy object. Help to provide single interface for in-memory & store nodes.
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class ProxyNode<E extends NodeAtExist> implements NodeAtExist, Proxy<E> {
	
	private E node;
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node newChild) throws DOMException {
		return getProxyObject().appendChild(newChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		return getProxyObject().cloneNode(deep);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		return getProxyObject().compareDocumentPosition(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
		return  getProxyObject().getAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
		return getProxyObject().getBaseURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	public NodeList getChildNodes() {
		return getProxyObject().getChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		return getProxyObject().getFeature(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		return getProxyObject().getFirstChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		return getProxyObject().getLastChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return getProxyObject().getLocalName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return getProxyObject().getNamespaceURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
		return getProxyObject().getNextSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		return getProxyObject().getNodeName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return getProxyObject().getNodeType();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return getProxyObject().getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return getProxyObject().getOwnerDocument();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		return getProxyObject().getParentNode();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return getProxyObject().getPrefix();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
		return getProxyObject().getPreviousSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		return getProxyObject().getTextContent();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		return getProxyObject().getUserData(key);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
		return getProxyObject().hasAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
		return getProxyObject().hasChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node insertBefore(Node newChild, Node refChild) throws DOMException {
		return getProxyObject().insertBefore(newChild, refChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		return getProxyObject().isDefaultNamespace(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		return getProxyObject().isEqualNode(arg);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		return getProxyObject().isSameNode(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported(String feature, String version) {
		return getProxyObject().isSupported(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		return getProxyObject().lookupNamespaceURI(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		return getProxyObject().lookupPrefix(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
		getProxyObject().normalize();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild(Node oldChild) throws DOMException {
		return getProxyObject().removeChild(oldChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
		return getProxyObject().replaceChild(newChild, oldChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	public void setNodeValue(String nodeValue) throws DOMException {
		getProxyObject().setNodeValue(nodeValue);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setPrefix(java.lang.String)
	 */
	public void setPrefix(String prefix) throws DOMException {
		getProxyObject().setPrefix(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		getProxyObject().setTextContent(textContent);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		return getProxyObject().setUserData(key, data, handler);
	}

	/* (non-Javadoc)
	 * @see org.exist.i.dom.NodeAteXist#getNodeId()
	 */
	public NodeId getNodeId() {
		return getProxyObject().getNodeId();
	}

	/* (non-Javadoc)
	 * @see org.exist.i.dom.NodeAteXist#getNodeNumber()
	 */
	public int getNodeNumber() {
		return getProxyObject().getNodeNumber();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.QNameable#getQName()
	 */
	public QName getQName() {
		return getProxyObject().getQName();
	}

	public DocumentAtExist getDocumentAtExist() {
		return getProxyObject().getDocumentAtExist();
	}

	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return getProxyObject().compareTo(o);
	}
	
	public E getProxyObject() {
		return node;
	}

	public void setProxyObject(E object) {
		if (object instanceof NodeAtExist) {
			this.node = (E) object;
		} else
			{throw new IllegalArgumentException("Only NodeAtExist allowed");}
	}
}
