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
import java.util.Iterator;
import java.util.Map;

import org.exist.dom.*;
import org.exist.interpreter.ContextAtExist;
import org.exist.numbering.NodeId;
import org.exist.xquery.Expression;
//import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
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
public class XSLElement implements org.w3c.dom.Element, INode, Names {
	
	protected XSLPathExpr expr = null;
	protected final org.w3c.dom.Element element;
	
	public XSLElement(final org.w3c.dom.Element element) {
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
	
	private SimpleConstructor getNodeConstructor(ContextAtExist context, Node node, XSLPathExpr content) throws XPathException {
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
			XSLPathExpr xslExpr = (XSLPathExpr) content;
			xslExpr.addText(node.getNodeValue());
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
		if (node instanceof org.exist.dom.persistent.ElementImpl) {
			org.exist.dom.persistent.ElementImpl elementAtExist = (org.exist.dom.persistent.ElementImpl) node;

			for(final Iterator<String> itPrefix = elementAtExist.getPrefixes(); itPrefix.hasNext();) {
				final String prefix = itPrefix.next();
				context.declareNamespace(prefix, elementAtExist.getNamespaceForPrefix(prefix));
			}
		}
		
		if (!node.hasChildNodes())
			return;
		
		SimpleConstructor constructer = null;
		
		NodeList children = node.getChildNodes();
		for (int i=0; i<children.getLength(); i++) {
			constructer = null;
			
			INode child = (INode)children.item(i);
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
			INode child = (INode)children.item(i);
			if (isXSLElement(child)) {
				XSLElement xslElement = (XSLElement) child;
				xslElement.preprocess(context);
			} else {
				preprocessNode(context, child);
			}
		}
	}
	
	private boolean isXSLElement(INode child) {
		return Factory.qns.containsKey(child.getQName());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	@Override
	public Node appendChild(Node newChild) throws DOMException {
		return element.appendChild(newChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	@Override
	public Node cloneNode(boolean deep) {
		return element.cloneNode(deep);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	@Override
	public short compareDocumentPosition(Node other) throws DOMException {
		return element.compareDocumentPosition(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	@Override
	public NamedNodeMap getAttributes() {
		return element.getAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getBaseURI()
	 */
	@Override
	public String getBaseURI() {
		return element.getBaseURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	@Override
	public NodeList getChildNodes() {
		return element.getChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	@Override
	public Object getFeature(String feature, String version) {
		return element.getFeature(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	@Override
	public Node getFirstChild() {
		return element.getFirstChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	@Override
	public Node getLastChild() {
		return element.getLastChild();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalPart()
	 */
	@Override
	public String getLocalName() {
		return element.getLocalName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	@Override
	public String getNamespaceURI() {
		return element.getNamespaceURI();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	@Override
	public Node getNextSibling() {
		return element.getNextSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	@Override
	public String getNodeName() {
		return element.getNodeName();
	}

	@Override
	public NodeId getNodeId() {
		return null;
	}

	/* (non-Javadoc)
         * @see org.w3c.dom.Node#getNodeType()
         */
	@Override
	public short getNodeType() {
		return element.getNodeType();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	@Override
	public String getNodeValue() throws DOMException {
		return element.getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	@Override
	public Document getOwnerDocument() {
		return element.getOwnerDocument();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	@Override
	public Node getParentNode() {
		return element.getParentNode();
	}

	public boolean isParentNode() {
		return (((INode)element).getNodeId().getTreeLevel() == 1);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	@Override
	public String getPrefix() {
		return element.getPrefix();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	@Override
	public Node getPreviousSibling() {
		return element.getPreviousSibling();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getTextContent()
	 */
	@Override
	public String getTextContent() throws DOMException {
		return element.getTextContent();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	@Override
	public Object getUserData(String key) {
		return element.getUserData(key);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	@Override
	public boolean hasAttributes() {
		return element.hasAttributes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	@Override
	public boolean hasChildNodes() {
		return element.hasChildNodes();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	@Override
	public Node insertBefore(Node newChild, Node refChild) throws DOMException {
		return element.insertBefore(newChild, refChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	@Override
	public boolean isDefaultNamespace(String namespaceURI) {
		return element.isDefaultNamespace(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	@Override
	public boolean isEqualNode(Node arg) {
		return element.isEqualNode(arg);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	@Override
	public boolean isSameNode(Node other) {
		return element.isSameNode(other);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean isSupported(String feature, String version) {
		return element.isSupported(feature, version);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	@Override
	public String lookupNamespaceURI(String prefix) {
		return element.lookupNamespaceURI(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	@Override
	public String lookupPrefix(String namespaceURI) {
		return element.lookupPrefix(namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#normalize()
	 */
	@Override
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
	@Override
	public Node replaceChild(Node newChild, Node oldChild) throws DOMException {
		return element.replaceChild(newChild, oldChild);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	@Override
	public void setNodeValue(String nodeValue) throws DOMException {
		element.setNodeValue(nodeValue);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setPrefix(java.lang.String)
	 */
	@Override
	public void setPrefix(String prefix) throws DOMException {
		element.setPrefix(prefix);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	@Override
	public void setTextContent(String textContent) throws DOMException {
		element.setTextContent(textContent);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	@Override
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		return element.setUserData(key, data, handler);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttribute(java.lang.String)
	 */
	@Override
	public String getAttribute(String name) {
		return element.getAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNS(java.lang.String, java.lang.String)
	 */
	@Override
	public String getAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNode(java.lang.String)
	 */
	@Override
	public Attr getAttributeNode(String name) {
		return element.getAttributeNode(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getAttributeNodeNS(java.lang.String, java.lang.String)
	 */
	@Override
	public Attr getAttributeNodeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getAttributeNodeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagName(java.lang.String)
	 */
	@Override
	public NodeList getElementsByTagName(String name) {
		return element.getElementsByTagName(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getElementsByTagNameNS(java.lang.String, java.lang.String)
	 */
	@Override
	public NodeList getElementsByTagNameNS(String namespaceURI, String localName)
			throws DOMException {
		return element.getElementsByTagNameNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getSchemaTypeInfo()
	 */
	@Override
	public TypeInfo getSchemaTypeInfo() {
		return element.getSchemaTypeInfo();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#getTagName()
	 */
	@Override
	public String getTagName() {
		return element.getTagName();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttribute(java.lang.String)
	 */
	@Override
	public boolean hasAttribute(String name) {
		return element.hasAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#hasAttributeNS(java.lang.String, java.lang.String)
	 */
	@Override
	public boolean hasAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		return element.hasAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttribute(java.lang.String)
	 */
	@Override
	public void removeAttribute(String name) throws DOMException {
		element.removeAttribute(name);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNS(java.lang.String, java.lang.String)
	 */
	@Override
	public void removeAttributeNS(String namespaceURI, String localName)
			throws DOMException {
		element.removeAttributeNS(namespaceURI, localName);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#removeAttributeNode(org.w3c.dom.Attr)
	 */
	@Override
	public Attr removeAttributeNode(Attr oldAttr) throws DOMException {
		return element.removeAttributeNode(oldAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttribute(java.lang.String, java.lang.String)
	 */
	@Override
	public void setAttribute(String name, String value) throws DOMException {
		element.setAttribute(name, value);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNS(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void setAttributeNS(String namespaceURI, String qualifiedName,
			String value) throws DOMException {
		element.setAttributeNS(namespaceURI, qualifiedName, value);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNode(org.w3c.dom.Attr)
	 */
	@Override
	public Attr setAttributeNode(Attr newAttr) throws DOMException {
		return element.setAttributeNode(newAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setAttributeNodeNS(org.w3c.dom.Attr)
	 */
	@Override
	public Attr setAttributeNodeNS(Attr newAttr) throws DOMException {
		return element.setAttributeNodeNS(newAttr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttribute(java.lang.String, boolean)
	 */
	@Override
	public void setIdAttribute(String name, boolean isId) throws DOMException {
		element.setIdAttribute(name, isId);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttributeNS(java.lang.String, java.lang.String, boolean)
	 */
	@Override
	public void setIdAttributeNS(String namespaceURI, String localName,
			boolean isId) throws DOMException {
		element.setIdAttributeNS(namespaceURI, localName, isId);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Element#setIdAttributeNode(org.w3c.dom.Attr, boolean)
	 */
	@Override
	public void setIdAttributeNode(Attr idAttr, boolean isId)
			throws DOMException {
		element.setIdAttributeNode(idAttr, isId);
	}

	@Override
	public QName getQName() {
		return ((INode)element).getQName();
	}

	@Override
	public void setQName(final QName qname) {
		((INode)element).setQName(qname);
	}

	public String toString() {
		return element.toString();
	}

	@Override
	public int compareTo(final Object other) {
		return ((INode)element).compareTo(other);
	}
}
