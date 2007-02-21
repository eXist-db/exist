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
package org.exist.util.serializer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Stack;

import org.exist.dom.QName;
import org.exist.memtree.ReferenceNode;
import org.w3c.dom.Attr;
import org.w3c.dom.CharacterData;
import org.w3c.dom.Comment;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.NamespaceSupport;

/**
 * General purpose class to stream a DOM node to SAX.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class DOMStreamer {

	private ContentHandler contentHandler = null;
	private LexicalHandler lexicalHandler = null;
	private NamespaceSupport nsSupport = new NamespaceSupport();
	private HashMap namespaceDecls = new HashMap();
    private Stack stack = new Stack();
    
	public DOMStreamer() {
		super();
	}

	public DOMStreamer(ContentHandler contentHandler, LexicalHandler lexicalHandler) {
		this.contentHandler = contentHandler;
		this.lexicalHandler = lexicalHandler;
	}
	
	public void setContentHandler(ContentHandler handler) {
		contentHandler = handler;
	}

	public void setLexicalHandler(LexicalHandler handler) {
		lexicalHandler = handler;
	}

	/**
	 * Reset internal state for reuse. Registered handlers will be set
	 * to null.
	 *
	 */
	public void reset() {
		nsSupport.reset();
		namespaceDecls.clear();
		stack.clear();
		contentHandler = null;
		lexicalHandler = null;
	}
	
	/**
	 * Serialize the given node and all its descendants to SAX.
	 * 
	 * @param node
	 * @throws SAXException
	 */
    public void serialize(Node node) throws SAXException {
        serialize(node, false);
    }
    
    /**
     * Serialize the given node and all its descendants to SAX. If
     * callDocumentEvents is set to false, startDocument/endDocument
     * events will not be fired.
     * 
     * @param node
     * @param callDocumentEvents
     * @throws SAXException
     */
	public void serialize(Node node, boolean callDocumentEvents) throws SAXException {
        if(callDocumentEvents)
            contentHandler.startDocument();
		Node top = node;
		while (node != null) {
			startNode(node);
			Node nextNode = node.getFirstChild();
			//TODO : make it happy
			if (node instanceof ReferenceNode)
				nextNode = null;
			while (nextNode == null) {
				endNode(node);
				if (top != null && top.equals(node))
					break;
				nextNode = node.getNextSibling();
				if (nextNode == null) {
					node = node.getParentNode();
					if (node == null || (top != null && top.equals(node))) {
						endNode(node);
						nextNode = null;
						break;
					}
				}
			}			
			node = nextNode;
		}
        if(callDocumentEvents)
            contentHandler.endDocument();
	}

	protected void startNode(Node node) throws SAXException {
		String cdata;
		switch (node.getNodeType()) {
			case Node.DOCUMENT_NODE :
			case Node.DOCUMENT_FRAGMENT_NODE :
				break;
			case Node.ELEMENT_NODE :
				namespaceDecls.clear();
				nsSupport.pushContext();
				String uri = node.getNamespaceURI();
				String prefix = node.getPrefix();
				if (uri == null)
					uri = "";
				if (prefix == null)
					prefix = "";
				if (nsSupport.getURI(prefix) == null) {
					namespaceDecls.put(prefix, uri);
					nsSupport.declarePrefix(prefix, uri);
				}
				// check attributes for required namespace declarations
				NamedNodeMap attrs = node.getAttributes();
				Attr nextAttr;
				String attrName;
				for (int i = 0; i < attrs.getLength(); i++) {
					nextAttr = (Attr) attrs.item(i);
					attrName = nextAttr.getName();
					if (attrName.equals("xmlns")) {
						if (nsSupport.getURI("") == null) {
							uri = nextAttr.getValue();
							namespaceDecls.put("", uri);
							nsSupport.declarePrefix("", uri);
						}
					} else if (attrName.startsWith("xmlns:")) {
						prefix = attrName.substring(6);
						if (nsSupport.getURI(prefix) == null) {
							uri = nextAttr.getValue();
							namespaceDecls.put(prefix, uri);
							nsSupport.declarePrefix(prefix, uri);
						}
					} else if (attrName.indexOf(':') > 0) {
						prefix = nextAttr.getPrefix();
                        uri = nextAttr.getNamespaceURI();
						if (nsSupport.getURI(prefix) == null) {
							namespaceDecls.put(prefix, uri);
							nsSupport.declarePrefix(prefix, uri);
						}
					}
				}
				ElementInfo info = new ElementInfo(node);
				String[] declaredPrefixes = null;
				if(namespaceDecls.size() > 0)
					declaredPrefixes = new String[namespaceDecls.size()];
				// output all namespace declarations
				Map.Entry nsEntry;
				int j = 0;
				for (Iterator i = namespaceDecls.entrySet().iterator(); i.hasNext(); j++) {
					nsEntry = (Map.Entry) i.next();
					declaredPrefixes[j] = (String) nsEntry.getKey();
					contentHandler.startPrefixMapping(
						declaredPrefixes[j],
						(String) nsEntry.getValue());
				}
				info.prefixes = declaredPrefixes;
                stack.push(info);
				// output attributes
				AttributesImpl saxAttrs = new AttributesImpl();
				String attrNS, attrLocalName;
				for (int i = 0; i < attrs.getLength(); i++) {
					nextAttr = (Attr) attrs.item(i);
					attrNS = nextAttr.getNamespaceURI();
					if(attrNS == null)
					    attrNS = "";
					attrLocalName = nextAttr.getLocalName();
					if(attrLocalName == null)
					    attrLocalName = QName.extractLocalName(nextAttr.getNodeName());
					saxAttrs.addAttribute(
						attrNS,
						attrLocalName,
						nextAttr.getNodeName(),
						"CDATA",
						nextAttr.getValue());
				}
				String localName = node.getLocalName();
				if(localName == null)
				    localName = QName.extractLocalName(node.getNodeName());
				contentHandler.startElement(node.getNamespaceURI(), localName, 
					node.getNodeName(), saxAttrs);
				break;
			case Node.TEXT_NODE :
                cdata = ((CharacterData) node).getData();
                contentHandler.characters(cdata.toCharArray(), 0, cdata.length());
                break;
            case Node.CDATA_SECTION_NODE :
                cdata = ((CharacterData) node).getData();
                if(lexicalHandler != null)
					lexicalHandler.startCDATA();
				contentHandler.characters(cdata.toCharArray(), 0, cdata.length());
				if(lexicalHandler != null)
					lexicalHandler.endCDATA();
				break;
			case Node.ATTRIBUTE_NODE :
				break;
			case Node.PROCESSING_INSTRUCTION_NODE :
				contentHandler.processingInstruction(
					((ProcessingInstruction) node).getTarget(),
					((ProcessingInstruction) node).getData());
				break;
			case Node.COMMENT_NODE :
				if(lexicalHandler != null) {
					cdata = ((Comment) node).getData();
					lexicalHandler.comment(cdata.toCharArray(), 0, cdata.length());
				}
				break;
			default :
                //TODO : what kind of default here ? -pb
				System.out.println("Found node: " + node.getNodeType());
				break;
		}
	}

	protected void endNode(Node node) throws SAXException {
		if (node == null)
			return;
		if (node.getNodeType() == Node.ELEMENT_NODE) {
            ElementInfo info = (ElementInfo)stack.pop();
			nsSupport.popContext();
			contentHandler.endElement(node.getNamespaceURI(), node.getLocalName(), node.getNodeName());
            if(info.prefixes != null) {
                for(int i = 0; i < info.prefixes.length; i++) {
                    contentHandler.endPrefixMapping(info.prefixes[i]);
                }
            }
		}
	}
    
    private class ElementInfo {
        Node element;
        String[] prefixes = null;
        
        public ElementInfo(Node element) {
            this.element = element;
        }
    }
}