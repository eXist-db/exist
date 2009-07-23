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

import org.exist.Indexer;
import org.exist.Namespaces;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.Constants;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;

import java.util.Arrays;

/**
 * Use this class to build a new in-memory DOM document.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class MemTreeBuilder {
	
	protected DocumentImpl doc;
	protected short level = 1;
	protected int[] prevNodeInLevel;
	protected XQueryContext context = null;
	
	public MemTreeBuilder() {
		this(null);
	}
	
	public MemTreeBuilder(XQueryContext context) {
		super();
		this.context = context;
		prevNodeInLevel = new int[15];
		Arrays.fill(prevNodeInLevel, -1);
		prevNodeInLevel[0] = 0;
	}

	/**
	 * Returns the created document object.
	 * 
	 */
	public DocumentImpl getDocument() {
		return doc;
	}

	public XQueryContext getContext() {
	    return context;
	}
	
	public int getSize() {
	    return doc.getSize();
	}
	
	/**
	 * Start building the document.
	 */
	public void startDocument() {
		this.doc = new DocumentImpl(context, false);
	}

	/**
	 * Start building the document.
	 */
	public void startDocument(boolean explicitCreation) {
		this.doc = new DocumentImpl(context, explicitCreation);
	}
	
	/**
	 * End building the document.
	 */
	public void endDocument() {
	}

	/**
	 * Create a new element.
	 * 
	 * @return the node number of the created element
	 */
	public int startElement(String namespaceURI, String localName, String qname, Attributes attributes) {
        int p = qname.indexOf(':');
		String prefix = null;
		if(context != null) {
			prefix = context.getPrefixForURI(namespaceURI);
		}
		if(prefix == null)
			prefix = (p != Constants.STRING_NOT_FOUND) ? qname.substring(0, p) : "";
        QName qn = new QName(localName, namespaceURI, prefix);
		return startElement(qn, attributes);
	}

	/**
	 * Create a new element.
	 * 
	 * @return the node number of the created element
	 */
	public int startElement(QName qn, Attributes attributes) {
        int nodeNr = doc.addNode(Node.ELEMENT_NODE, level, qn);
        if(attributes != null) {
			// parse attributes	
			for (int i = 0; i < attributes.getLength(); i++) {
                String attrNS = attributes.getURI(i);
                String attrLocalName = attributes.getLocalName(i);
                String attrQName = attributes.getQName(i);
                // skip xmlns-attributes and attributes in eXist's namespace
				if (!(attrQName.startsWith("xmlns"))) {
//					|| attrNS.equals(Namespaces.EXIST_NS))) {
                    int p = attrQName.indexOf(':');
                    String attrPrefix = (p != Constants.STRING_NOT_FOUND) ? attrQName.substring(0, p) : null;
                    QName attrQn = new QName(attrLocalName, attrNS, attrPrefix);
                    int type = getAttribType(attrQn, attributes.getType(i));
                    doc.addAttribute(nodeNr, attrQn, attributes.getValue(i), type);
                }
			}
		}
		// update links
		if (level + 1 >= prevNodeInLevel.length) {
			int[] t = new int[level + 2];
			System.arraycopy(prevNodeInLevel, 0, t, 0, prevNodeInLevel.length);
			prevNodeInLevel = t;
		}
		int prevNr = prevNodeInLevel[level];    // TODO: remove potential ArrayIndexOutOfBoundsException
		if (prevNr > -1)
			doc.next[prevNr] = nodeNr;
		doc.next[nodeNr] = prevNodeInLevel[level - 1];
		prevNodeInLevel[level] = nodeNr;
		++level;
		return nodeNr;
	}

    private int getAttribType(QName qname, String type) {
        if (qname.equalsSimple(Namespaces.XML_ID_QNAME)) {
            // an xml:id attribute.
		    return AttributeImpl.ATTR_CDATA_TYPE;
        }
        if (type.equals(Indexer.ATTR_ID_TYPE))
            return AttributeImpl.ATTR_ID_TYPE;
        else if (type.equals(Indexer.ATTR_IDREF_TYPE))
            return AttributeImpl.ATTR_IDREF_TYPE;
        else if (type.equals(Indexer.ATTR_IDREFS_TYPE))
            return AttributeImpl.ATTR_IDREFS_TYPE;
        else
            return AttributeImpl.ATTR_CDATA_TYPE;
    }

    /**
	 * Close the last element created.
	 */
	public void endElement() {
//		System.out.println("end-element: level = " + level);
		prevNodeInLevel[level] = -1;
		--level;
	}

	public int addReferenceNode(NodeProxy proxy) {
        int lastNode = doc.getLastNode();
        if (0 < lastNode && level == doc.getTreeLevel(lastNode)) {
            if (doc.getNodeType(lastNode) == Node.TEXT_NODE && proxy.getNodeType() == Node.TEXT_NODE) {
                // if the last node is a text node, we have to append the
                // characters to this node. XML does not allow adjacent text nodes.
                doc.appendChars(lastNode, proxy.getNodeValue());
                return lastNode;
            }
            if (doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
                // check if the previous node is a reference node. if yes, check if it is a text node
                int p = doc.alpha[lastNode];
                if (doc.references[p].getNodeType() == Node.TEXT_NODE && proxy.getNodeType() == Node.TEXT_NODE) {
                    // found a text node reference. create a new char sequence containing
                    // the concatenated text of both nodes
                    String s = doc.references[p].getStringValue() + proxy.getStringValue();
                    doc.replaceReferenceNode(lastNode, s);
                    return lastNode;
                }
            }
        }
        final int nodeNr = doc.addNode(NodeImpl.REFERENCE_NODE, level, null);
		doc.addReferenceNode(nodeNr, proxy);		
		linkNode(nodeNr);
		return nodeNr;
	}
	
	public int addAttribute(QName qname, String value) {
		int lastNode = doc.getLastNode();
		//if(0 < lastNode && doc.nodeKind[lastNode] != Node.ELEMENT_NODE) {
			//Definitely wrong !
			//lastNode = characters(value);
		//} else {
			//lastNode = doc.addAttribute(lastNode, qname, value);
		//}
		int nodeNr = doc.addAttribute(lastNode, qname, value, AttributeImpl.ATTR_CDATA_TYPE);
		//TODO :
		//1) call linkNode(nodeNr); ?
		//2) is there a relationship between lastNode and nodeNr ?
		return nodeNr;
	}
	
	/**
	 * Create a new text node.
	 * 
	 * @return the node number of the created node
	 */
	public int characters(char[] ch, int start, int len) {
        int lastNode = doc.getLastNode();
        if (0 < lastNode && level == doc.getTreeLevel(lastNode)) {
            if (doc.getNodeType(lastNode) == Node.TEXT_NODE) {
                // if the last node is a text node, we have to append the
                // characters to this node. XML does not allow adjacent text nodes.
                doc.appendChars(lastNode, ch, start, len);
                return lastNode;
            }
            if (doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
                // check if the previous node is a reference node. if yes, check if it is a text node
                int p = doc.alpha[lastNode];
                if (doc.references[p].getNodeType() == Node.TEXT_NODE) {
                    // found a text node reference. create a new char sequence containing
                    // the concatenated text of both nodes
                    StringBuilder s = new StringBuilder(doc.references[p].getStringValue());
                    s.append(ch, start, len);
                    doc.replaceReferenceNode(lastNode, s);
                    return lastNode;
                }
                // fall through and add the node below
            }
        }
        int nodeNr = doc.addNode(Node.TEXT_NODE, level, null);
        doc.addChars(nodeNr, ch, start, len);
        linkNode(nodeNr);
        return nodeNr;
	}

	/**
	 * Create a new text node.
	 * 
	 * @return the node number of the created node
	 */
	public int characters(CharSequence s) {
        int lastNode = doc.getLastNode();
        if (0 < lastNode && level == doc.getTreeLevel(lastNode)) {
            if (doc.getNodeType(lastNode) == Node.TEXT_NODE ||
                    doc.getNodeType(lastNode) == Node.CDATA_SECTION_NODE) {
                // if the last node is a text node, we have to append the
                // characters to this node. XML does not allow adjacent text nodes.
                doc.appendChars(lastNode, s);
                return lastNode;
            }
            if (doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
                // check if the previous node is a reference node. if yes, check if it is a text node
                int p = doc.alpha[lastNode];
                if (doc.references[p].getNodeType() == Node.TEXT_NODE ||
                        doc.references[p].getNodeType() == Node.CDATA_SECTION_NODE) {
                    // found a text node reference. create a new char sequence containing
                    // the concatenated text of both nodes
                    doc.replaceReferenceNode(lastNode, doc.references[p].getStringValue() + s);
                    return lastNode;
                }
                // fall through and add the node below
            }
        }
        int nodeNr = doc.addNode(Node.TEXT_NODE, level, null);
        doc.addChars(nodeNr, s);
        linkNode(nodeNr);
        return nodeNr;
	}
	
	public int comment(CharSequence data) {
		int nodeNr = doc.addNode(Node.COMMENT_NODE, level, null);
		doc.addChars(nodeNr, data);
		linkNode(nodeNr);
		return nodeNr;
	}
	
	public int comment(char ch[], int start, int len) {
	    int nodeNr = doc.addNode(Node.COMMENT_NODE, level, null);
		doc.addChars(nodeNr, ch, start, len);
		linkNode(nodeNr);
		return nodeNr;
	}
    
    public int cdataSection(CharSequence data) {
        int lastNode = doc.getLastNode();
        if (0 < lastNode && level == doc.getTreeLevel(lastNode)) {
            if (doc.getNodeType(lastNode) == Node.TEXT_NODE ||
                    doc.getNodeType(lastNode) == Node.CDATA_SECTION_NODE) {
                // if the last node is a text node, we have to append the
                // characters to this node. XML does not allow adjacent text nodes.
                doc.appendChars(lastNode, data);
                return lastNode;
            }
            if (doc.getNodeType(lastNode) == NodeImpl.REFERENCE_NODE) {
                // check if the previous node is a reference node. if yes, check if it is a text node
                int p = doc.alpha[lastNode];
                if (doc.references[p].getNodeType() == Node.TEXT_NODE ||
                        doc.references[p].getNodeType() == Node.CDATA_SECTION_NODE) {
                    // found a text node reference. create a new char sequence containing
                    // the concatenated text of both nodes
                    doc.replaceReferenceNode(lastNode, doc.references[p].getStringValue() + data);
                    return lastNode;
                }
                // fall through and add the node below
            }
        }
        int nodeNr = doc.addNode(Node.CDATA_SECTION_NODE, level, null);
        doc.addChars(nodeNr, data);
        linkNode(nodeNr);
        return nodeNr;
    }
	
	public int processingInstruction(String target, String data) {
		QName qn = new QName(target, null, null);
		int nodeNr = doc.addNode(Node.PROCESSING_INSTRUCTION_NODE, level, qn);
                doc.addChars(nodeNr, data==null ? "" : data);
		linkNode(nodeNr);
		return nodeNr;
	}
	
	public int namespaceNode(String prefix, String uri) {
		return namespaceNode(new QName(prefix, uri, "xmlns"));
	}
	
	public int namespaceNode(QName qn) {
		int lastNode = doc.getLastNode();
		int nodeNr = doc.addNamespace(lastNode, qn);
		return nodeNr;
	}
    
	public int documentType(String publicId, String systemId) {
//		int nodeNr = doc.addNode(Node.DOCUMENT_TYPE_NODE, level, null);
//		doc.addChars(nodeNr, data);
//		linkNode(nodeNr);
//		return nodeNr;
		return -1;
	}
	
	public void documentType(String name, String publicId, String systemId) {
	}
	
    private void linkNode(int nodeNr) {
        int prevNr = prevNodeInLevel[level];
        if (prevNr > -1)
            doc.next[prevNr] = nodeNr;
        doc.next[nodeNr] = prevNodeInLevel[level - 1];
        prevNodeInLevel[level] = nodeNr;
    }
}
