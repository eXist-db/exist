/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.Namespaces;
import org.exist.dom.NamedNodeMapImpl;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.QNameable;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;

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
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	public NodeList getChildNodes() {
		NodeListImpl nl = new NodeListImpl();
		int nextNode = document.getFirstChildFor(nodeNumber);
		while (nextNode > nodeNumber) {
			Node n = document.getNode(nextNode);
			nl.add(n);
            nextNode = document.next[nextNode];
        }
		return nl;
	}
	
    public int getChildCount() {
        return document.getChildCountFor(nodeNumber);
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
		return document.alpha[nodeNumber] > -1 || document.alphaLen[nodeNumber] > -1;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttribute(java.lang.String)
	 */
	public String getAttribute(String name) {
		int attr = document.alpha[nodeNumber];
		if (-1 < attr) {
			while (attr < document.nextAttr
				&& document.attrParent[attr] == nodeNumber) {
				QName attrQName = (QName)document.namePool.get(document.attrName[attr]);
				if (attrQName.getStringValue().equals(name))
					return document.attrValue[attr];
				++attr;
			}
		}
		
		if(name.startsWith("xmlns:")) {
			int ns = document.alphaLen[nodeNumber];
			if (-1 < ns) {
				while (ns < document.nextNamespace
						&& document.namespaceParent[ns] == nodeNumber) {
					QName nsQName=(QName)document.namePool.get(document.namespaceCode[ns]);
					if (nsQName.getStringValue().equals(name))
						return nsQName.getNamespaceURI();
					++ns;
				}
			}
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

    public int getAttributesCount() {
       return document.getAttributesCountFor(nodeNumber)+document.getNamespacesCountFor(nodeNumber);
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
			NamespaceNode node = new NamespaceNode(document, ns);
			map.add(node);
			++ns;
		}
		return map;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
	 */
	public Attr getAttributeNode(String name) {
		int attr = document.alpha[nodeNumber];
		if (-1 < attr) {
			while (attr < document.nextAttr
				&& document.attrParent[attr] == nodeNumber) {
				QName attrQName = (QName)document.namePool.get(document.attrName[attr]);
				if (attrQName.getStringValue().equals(name))
					return new AttributeImpl(document, attr);
				++attr;
			}
		}
		
		if(name.startsWith("xmlns:")) {
			int ns = document.alphaLen[nodeNumber];
			if (-1 < ns) {
				while (ns < document.nextNamespace
						&& document.namespaceParent[ns] == nodeNumber) {
					QName nsQName=(QName)document.namePool.get(document.namespaceCode[ns]);
					if (nsQName.getStringValue().equals(name))
						return new NamespaceNode(document, ns);
					++ns;
				}
			}
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
	public NodeList getElementsByTagName(String name) {
		NodeListImpl nl = new NodeListImpl();		
		int nextNode = nodeNumber;
		while (++nextNode < document.size) {
			if (document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
    			QName qn = (QName) document.namePool.get(document.nodeName[nextNode]);
    			if(qn.getStringValue().equals(name))
    				nl.add(document.getNode(nextNode));
    		}
			if (document.next[nextNode] <= nodeNumber) break;
		}
		return nl;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
	 */
	public String getAttributeNS(String namespaceURI, String localName) {
		int attr = document.alpha[nodeNumber];
		if (-1 < attr) {
			QName name;
			while (attr < document.nextAttr
				&& document.attrParent[attr] == nodeNumber) {
				name = (QName)document.namePool.get(document.attrName[attr]);
				if (name.getLocalName().equals(localName)
					&& name.getNamespaceURI().equals(namespaceURI))
					return document.attrValue[attr];
				++attr;
			}
		}
		
		if(Namespaces.XMLNS_NS.equals(namespaceURI)) {
			int ns = document.alphaLen[nodeNumber];
			if (-1 < ns) {
				while (ns < document.nextNamespace
						&& document.namespaceParent[ns] == nodeNumber) {
					QName nsQName=(QName)document.namePool.get(document.namespaceCode[ns]);
					if (nsQName.getLocalName().equals(localName))
						return nsQName.getNamespaceURI();
					++ns;
				}
			}
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
		if (-1 < attr) {
			QName name;
			while (attr < document.nextAttr
				&& document.attrParent[attr] == nodeNumber) {
				name = (QName)document.namePool.get(document.attrName[attr]);
				if (name.getLocalName().equals(localName)
					&& name.getNamespaceURI().equals(namespaceURI))
					return new AttributeImpl(document, attr);
				++attr;
			}
		}
		
		if(Namespaces.XMLNS_NS.equals(namespaceURI)) {
			int ns = document.alphaLen[nodeNumber];
			if (-1 < ns) {
				while (ns < document.nextNamespace
						&& document.namespaceParent[ns] == nodeNumber) {
					QName nsQName=(QName)document.namePool.get(document.namespaceCode[ns]);
					if (nsQName.getLocalName().equals(localName))
						return new NamespaceNode(document, ns);
					++ns;
				}
			}
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
	public NodeList getElementsByTagNameNS(String namespaceURI, String name) {
		QName qname = new QName(name, namespaceURI);
		NodeListImpl nl = new NodeListImpl();		
		int nextNode = nodeNumber;
		while (++nextNode < document.size) {
			if (document.nodeKind[nextNode] == Node.ELEMENT_NODE) {
    			QName qn = (QName) document.namePool.get(document.nodeName[nextNode]);
    			if(qname.compareTo(qn) == 0)
    				nl.add(document.getNode(nextNode));
    		}
			if (document.next[nextNode] <= nodeNumber) break;
		}
		return nl;
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

    public String getBaseURI() {
        return getAttributeNS(Namespaces.XML_NS, "base");
    }
    
	/** ? @see org.w3c.dom.Element#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
		// maybe TODO - new DOM interfaces - Java 5.0
		return null;
	}

	/** ? @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
	 */
	public void setIdAttribute(String name, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
	 */
	public void setIdAttributeNS(String namespaceURI, String localName, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}

	/** ? @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
	 */
	public void setIdAttributeNode(Attr idAttr, boolean isId) throws DOMException {
		// maybe TODO - new DOM interfaces - Java 5.0
		
	}
	
	public String toString() {
    		StringBuffer result = new StringBuffer();
    		result.append("in-memory#");
    		result.append("element {");
    		result.append(getQName().getStringValue());
    		result.append("} {");
		NamedNodeMap theAttrs;
        	if((theAttrs=getAttributes()) != null) {
			for(int i = 0; i < theAttrs.getLength(); i++) {
				if(i > 0)
					result.append(" ");
				Node natt=theAttrs.item(i);
				if("org.exist.memtree.AttributeImpl".equals(natt.getClass().getName())) {
					result.append(((AttributeImpl)natt).toString());
				} else {
					result.append(((NamespaceNode)natt).toString());
				}
			}
		}
        	for(int i = 0; i < this.getChildCount(); i++ ) {  
        		if(i > 0)
            		result.append(" ");        	
    			Node child = getChildNodes().item(i);
        	    result.append(child.toString());           
        	}        
        	result.append("} ");        
        	return result.toString();
	}
}
