/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
 *  http://exist-db.org
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
package org.exist.xslt.compiler;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;

import org.exist.interpreter.ContextAtExist;
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
//import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.dom.NodeListImpl;
import org.exist.dom.QName;
import org.exist.dom.DocumentAtExist;
import org.exist.dom.ElementAtExist;
import org.exist.dom.NodeAtExist;
import org.exist.xslt.XSLContext;
import org.exist.xslt.XSLStylesheet;
import org.exist.xslt.expression.Element;
import org.exist.xslt.expression.SimpleConstructor;
import org.exist.xslt.expression.Text;
import org.exist.xslt.expression.XSLExpression;
import org.exist.xslt.expression.XSLPathExpr;
import org.w3c.dom.Attr;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.TypeInfo;
import org.w3c.dom.UserDataHandler;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class XSLElement implements ElementAtExist, Names {
	
	protected XSLPathExpr expr = null;
	protected ElementAtExist element;
	
	public XSLElement(ElementAtExist element) {
		this.element = element;
	}
	
//	protected XQueryContext getContext() {
//		return (XQueryContext) getDocument().getContext();
//	}
//	
	@SuppressWarnings("unchecked")
	protected XSLPathExpr getExpressionInstance(ContextAtExist context) throws XPathException {
		if (expr != null)
			return expr;
		
		if (getQName() == null) //TODO: remove
			System.out.println(getQName());
		
		Class<XSLPathExpr> clazz = Factory.qns.get(getQName());
		
		try {
			Constructor<XSLPathExpr> constructor = clazz.getConstructor(XSLContext.class);
			expr = constructor.newInstance(context);
			
			return expr;
		} catch (SecurityException e) {
			throw new XPathException("SecurityException",e);
		} catch (NoSuchMethodException e) {
			throw new XPathException("NoSuchMethodException",e);
		} catch (IllegalArgumentException e) {
			throw new XPathException("IllegalArgumentException",e);
		} catch (InstantiationException e) {
			throw new XPathException("InstantiationException",e);
		} catch (IllegalAccessException e) {
			throw new XPathException("IllegalAccessException",e);
		} catch (InvocationTargetException e) {
			throw new XPathException("InvocationTargetException",e);
		}
	}

	public Expression compile(ContextAtExist context) throws XPathException {
		XSLPathExpr exec;
		
		if ((!isXSLElement(this)) && (isParentNode())) { //UNDERSTAND: put to XSLStylesheet?
			expr = new XSLStylesheet((XSLContext) context, true);
			
			SimpleConstructor constructer = getNodeConstructor(context, this, expr);
			if (constructer != null) {
				expr.add(constructer);

				compileNode(context, expr, this);
			}
			
			exec = expr;
		} else {
			preprocess(context);

			exec = getExpressionInstance(context);

			compileNode(context, exec, this);
		}
		
		exec.validate();//UNDERSTAND: at compile time??? analyze ???
		
		return exec;
	}
	
	private SimpleConstructor getNodeConstructor(ContextAtExist context, NodeAtExist node, XSLPathExpr content) throws XPathException {
		SimpleConstructor constructer = null;
		if (node.getNodeType() == Node.ELEMENT_NODE) {
			Element element = new Element((XSLContext) context, node.getNodeName());
			content.add(element);
			XSLPathExpr content_sub = new XSLPathExpr((XSLContext) context); 
			element.setContent(content_sub);
		
			NamedNodeMap attrs = node.getAttributes();
			for (int index = 0; index < attrs.getLength(); index++) {
				Node attr = attrs.item(index);
				if (("xmlns:".equals(attr.getNodeName())) && ("".equals(attr.getLocalName()))) { //getNodeValue
					continue;//UNDERSTAND: is it required, to have empty namespace
				}
	        	if (isParentNode()) {
	        		if ("xmlns:xsl".equals(attr.getNodeName())) {
		        		continue;
	        		} else if (attr.getNodeName().startsWith("xsl")) {
		        		continue;
	        		}
	        	}
				element.prepareAttribute(context, (Attr) attr); 
			}
		
			compileNode(context, content_sub, node);
		} else if (node.getNodeType() == Node.COMMENT_NODE) {
//UNDERSTAND:			constructer = new CommentConstructor((XQueryContext) context, node.getNodeName());
		} else if (node.getNodeType() == Node.TEXT_NODE) {
			if (content instanceof XSLPathExpr) {
				XSLPathExpr xslExpr = (XSLPathExpr) content;
				xslExpr.addText(node.getNodeValue());
			} else
				constructer = new Text((XSLContext) context, node.getNodeValue());
		} else if (node.getNodeType() == Node.CDATA_SECTION_NODE) {
//UNDERSTAND:			constructer = new CDATAConstructor((XQueryContext) context, node.getNodeName());
		} else if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
		} else {
			throw new XPathException("not supported node type: "+node.getNodeType());
//	    	ATTRIBUTE_NODE            = 2;
//	    	CDATA_SECTION_NODE        = 4;
//	    	ENTITY_REFERENCE_NODE     = 5;
//	    	ENTITY_NODE               = 6;
//	    	DOCUMENT_NODE             = 9;
//	    	DOCUMENT_TYPE_NODE        = 10;
//	    	DOCUMENT_FRAGMENT_NODE    = 11;
//	    	NOTATION_NODE             = 12;
		}
		return constructer;
	}
	
	public void compileNode(ContextAtExist context, XSLPathExpr content, Node node) throws XPathException {
		//namespaces
		if (node instanceof ElementAtExist) {
			ElementAtExist elementAtExist = (ElementAtExist) node;
			Map<String, String> namespaceMap = elementAtExist.getNamespaceMap();
	        for (String name : namespaceMap.keySet()) {
	        	//getContext().declareInScopeNamespace(name, namespaceMap.get(name));
	        	context.declareNamespace(name, namespaceMap.get(name));
	        	//TODO: rewrite, changes at xquery.parser. it use static 
	        }
		}
		
		if (!node.hasChildNodes())
			return;
		
		SimpleConstructor constructer = null;
		
		NodeList children = node.getChildNodes();
		for (int i=0; i<children.getLength(); i++) {
			constructer = null;
			
			NodeAtExist child = (NodeAtExist)children.item(i);
			if (isXSLElement(child)) {
				XSLElement xslElement = (XSLElement) child;
				content.add(xslElement.compile(context));
			} else {
				constructer = getNodeConstructor(context, child, content);
			}
			
			if (constructer != null) {
				content.add(constructer);

				compileNode(context, content, child);
			}
		}
	}

	protected void prepareAttributes(ContextAtExist context) throws XPathException { 
    	XSLExpression exec = getExpressionInstance(context);

    	NamedNodeMap attrs = getAttributes();
    	for (int i = 0; i < attrs.getLength(); i++)
    		exec.prepareAttribute(context, (Attr)attrs.item(i));
    }
    
	protected void preprocess(ContextAtExist context) throws XPathException {
//		XSLPathExpr exec = getExpressionInstance();
		
		prepareAttributes(context);
		
		preprocessNode(context, this);
	}

	protected void preprocessNode(ContextAtExist context, Node node) throws XPathException {
		if (!node.hasChildNodes())
			return;
		
		NodeList children = node.getChildNodes();
		for (int i=0; i<children.getLength(); i++) {
			NodeAtExist child = (NodeAtExist)children.item(i);
			if (isXSLElement(child)) {
				XSLElement xslElement = (XSLElement) child;
				xslElement.preprocess(context);
			} else {
				preprocessNode(context, child);
			}
		}
	}
	
	private boolean isXSLElement(NodeAtExist child) {
		return Factory.qns.containsKey(child.getQName());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.i.NodeAtExist#getDocumentAtExist()
	 */
	public DocumentAtExist getDocumentAtExist() {
		return element.getDocumentAtExist();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node newChild) throws DOMException {
		return element.appendChild(newChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean deep) {
		return element.cloneNode(deep);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		return element.compareDocumentPosition(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
		return element.getAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
		return element.getBaseURI();
	}

	NodeListImpl nl; //TODO: handle changes some how
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	public NodeList getChildNodes() {
		if (nl != null)
			return nl;
		
		DocumentAtExist document = getDocumentAtExist();
		
		nl = new NodeListImpl();
		int nextNode = document.getFirstChildFor(getNodeNumber());
		while (nextNode > getNodeNumber()) {
			NodeAtExist n = document.getNode(nextNode);

			if (n instanceof ElementAtExist) {
				n = new XSLElement((ElementAtExist) n);
			}
			
			nl.add(n);
            nextNode = document.getNextNodeNumber(nextNode);
        }
		return nl;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		return element.getFeature(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		return element.getFirstChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		return element.getLastChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return element.getLocalName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return element.getNamespaceURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
		return element.getNextSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		return element.getNodeName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return element.getNodeType();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return element.getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return element.getOwnerDocument();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		return element.getParentNode();
	}

	public boolean isParentNode() {
		return (element.getNodeId().getTreeLevel() == 1);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return element.getPrefix();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
		return element.getPreviousSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		return element.getTextContent();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		return element.getUserData(key);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
		return element.hasAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
		return element.hasChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node insertBefore(Node newChild, Node refChild) throws DOMException {
		return element.insertBefore(newChild, refChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		return element.isDefaultNamespace(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		return element.isEqualNode(arg);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		return element.isSameNode(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported(String feature, String version) {
		return element.isSupported(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		return element.lookupNamespaceURI(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		return element.lookupPrefix(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
		element.normalize();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild(Node oldChild) throws DOMException {
		return element.removeChild(oldChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
		return element.replaceChild(newChild, oldChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	public void setNodeValue(String nodeValue) throws DOMException {
		element.setNodeValue(nodeValue);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setPrefix(java.lang.String)
	 */
	public void setPrefix(String prefix) throws DOMException {
		element.setPrefix(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		element.setTextContent(textContent);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		return element.setUserData(key, data, handler);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttribute(java.lang.String)
	 */
	public String getAttribute(String name) {
		return element.getAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
	 */
	public String getAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
	 */
	public Attr getAttributeNode(String name) {
		return element.getAttributeNode(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
	 */
	public Attr getAttributeNodeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getAttributeNodeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
	 */
	public NodeList getElementsByTagName(String name) {
		return element.getElementsByTagName(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
	 */
	public NodeList getElementsByTagNameNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getElementsByTagNameNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getSchemaTypeInfo()
	 */
	public TypeInfo getSchemaTypeInfo() {
		return element.getSchemaTypeInfo();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getTagName()
	 */
	public String getTagName() {
		return element.getTagName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
	 */
	public boolean hasAttribute(String name) {
		return element.hasAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
	 */
	public boolean hasAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.hasAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
	 */
	public void removeAttribute(String name) throws DOMException {
		element.removeAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
	 */
	public void removeAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		element.removeAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNode(org.w3c.dom.Attr)
	 */
	public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
		return element.removeAttributeNode(oldAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttribute(java.lang.String, java.lang.String)
	 */
	public void setAttribute(String name, String value) throws DOMException {
		element.setAttribute(name, value);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNS(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void setAttributeNS(String namespaceURI, String qualifiedName,
			String value) throws DOMException {
		element.setAttributeNS(namespaceURI, qualifiedName, value);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNode(org.w3c.dom.Attr)
	 */
	public Attr setAttributeNode(Attr newAttr) throws DOMException {
		return element.setAttributeNode(newAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNodeNS(org.w3c.dom.Attr)
	 */
	public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
		return element.setAttributeNodeNS(newAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
	 */
	public void setIdAttribute(String name, boolean isId) throws DOMException {
		element.setIdAttribute(name, isId);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
	 */
	public void setIdAttributeNS(String namespaceURI, String localName,
			boolean isId) throws DOMException {
		element.setIdAttributeNS(namespaceURI, localName, isId);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
	 */
	public void setIdAttributeNode(Attr idAttr, boolean isId)
			throws DOMException {
		element.setIdAttributeNode(idAttr, isId);
	}

	
	/* (non-Javadoc)
	 * @see org.exist.dom.QNameable#getQName()
	 */
	public QName getQName() {
		return element.getQName();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.i.NodeAtExist#getNodeNumber()
	 */
	public int getNodeNumber() {
		return element.getNodeNumber();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	public int compareTo(Object o) {
		return element.compareTo(o);
	}

//	/* (non-Javadoc)
//	 * @see org.exist.dom.i.NodeAtExist#matchChildren(org.exist.xquery.NodeTest)
//	 */
//	public Boolean matchChildren(NodeTest test) throws XPathException {
//		return element.matchChildren(test);
//	}

	public String toString() {
		return element.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.i.ElementAtExist#getNamespaceMap()
	 */
	public Map<String, String> getNamespaceMap() {
		return element.getNamespaceMap();
	}

	
	/* (non-Javadoc)
	 * @see org.exist.dom.i.NodeAtExist#getNodeId()
	 */
	public NodeId getNodeId() {
		return element.getNodeId();
	}
}
