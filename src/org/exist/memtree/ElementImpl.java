/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.memtree;

import org.exist.dom.NamedNodeMapImpl;
import org.exist.dom.QName;
import org.exist.dom.QNameable;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class ElementImpl extends NodeImpl implements Element, QNameable {

	public ElementImpl(DocumentImpl doc, int nodeNumber) {
		super(doc, nodeNumber);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getTagName()
	 */
	public String getTagName() {
		return getNodeName();
	}

	public QName getQName() {
		return (QName)
			document.namePool.get(document.nodeName[nodeNumber]);
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
		return nodeNumber + 1 < document.size
			&& document.treeLevel[nodeNumber + 1] > document.treeLevel[nodeNumber];
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		short level = document.treeLevel[nodeNumber];
		int nextNode = nodeNumber + 1;
		if (nextNode < document.size && document.treeLevel[nextNode] > level) {
			return document.getNode(nextNode);
		} else
			return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return getQName().getNamespaceURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return getQName().getPrefix();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return getQName().getLocalName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
		return document.alpha[nodeNumber] > -1;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttribute(java.lang.String)
	 */
	public String getAttribute(String name) {
		int attr = document.alpha[nodeNumber];
		if (attr < 0)
			return null;
		while (attr < document.nextAttr
			&& document.attrParent[attr] == nodeNumber) {
			QName attrQName = (QName)document.namePool.get(document.attrName[attr]);
			if (attrQName.getLocalName().equals(name))
				return document.attrValue[attr];
			++attr;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttribute(java.lang.String, java.lang.String)
	 */
	public void setAttribute(String arg0, String arg1) throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
	 */
	public void removeAttribute(String arg0) throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
		NamedNodeMapImpl map = new NamedNodeMapImpl();
		int attr = document.alpha[nodeNumber];
		if(-1 < attr) {
			while (attr < document.nextAttr
				&& document.attrParent[attr] == nodeNumber) {
				map.add(new AttributeImpl(document, attr));
				++attr;
			}
		}
		// add namespace declarations attached to this element
		int ns = document.alphaLen[nodeNumber];
		if (ns < 0)
			return map;
		while (ns < document.nextNamespace
				&& document.namespaceParent[ns] == nodeNumber) {
			map.add(new NamespaceNode(document, ns));
			++ns;
		}
		return map;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
	 */
	public Attr getAttributeNode(String name) {
		int attr = document.alpha[nodeNumber];
		if (attr < 0)
			return null;
		while (attr < document.nextAttr
			&& document.attrParent[attr] == nodeNumber) {
			QName attrQName = (QName)document.namePool.get(document.attrName[attr]);
			if (attrQName.equals(name))
				return new AttributeImpl(document, attr);
			++attr;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNode(org.w3c.dom.Attr)
	 */
	public Attr setAttributeNode(Attr arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNode(org.w3c.dom.Attr)
	 */
	public Attr removeAttributeNode(Attr arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
	 */
	public NodeList getElementsByTagName(String arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
	 */
	public String getAttributeNS(String namespaceURI, String localName) {
		int attr = document.alpha[nodeNumber];
		if (attr < 0)
			return null;
		QName name;
		while (attr < document.nextAttr
			&& document.attrParent[attr] == nodeNumber) {
			name = (QName)document.namePool.get(document.attrName[attr]);
			if (name.getLocalName().equals(localName)
				&& name.getNamespaceURI().equals(namespaceURI))
				return document.attrValue[attr];
			++attr;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNS(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void setAttributeNS(String arg0, String arg1, String arg2)
		throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
	 */
	public void removeAttributeNS(String arg0, String arg1)
		throws DOMException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
	 */
	public Attr getAttributeNodeNS(String namespaceURI, String localName) {
		int attr = document.alpha[nodeNumber];
		if (attr < 0)
			return null;
		QName name;
		while (attr < document.nextAttr
			&& document.attrParent[attr] == nodeNumber) {
			name = (QName)document.namePool.get(document.attrName[attr]);
			if (name.getLocalName().equals(localName)
				&& name.getNamespaceURI().equals(namespaceURI))
				return new AttributeImpl(document, attr);
			++attr;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNodeNS(org.w3c.dom.Attr)
	 */
	public Attr setAttributeNodeNS(Attr arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
	 */
	public NodeList getElementsByTagNameNS(String arg0, String arg1) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		return getAttribute(name) != null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
	 */
	public boolean hasAttributeNS(String namespaceURI, String localName) {
		return getAttributeNS(namespaceURI, localName) != null;
	}

}
