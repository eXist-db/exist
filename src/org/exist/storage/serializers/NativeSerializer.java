/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.storage.serializers;

import org.exist.Namespaces;
import org.exist.dom.AttrImpl;
import org.exist.dom.CDATASectionImpl;
import org.exist.dom.CommentImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentTypeImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.Match;
import org.exist.dom.NodeProxy;
import org.exist.dom.ProcessingInstructionImpl;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.serializer.AttrList;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Serializer implementation for the native database backend.
 * 
 * @author wolf
 */
public class NativeSerializer extends Serializer {

    // private final static AttributesImpl EMPTY_ATTRIBUTES = new AttributesImpl();
    
    private final static QName TEXT_ELEMENT = new QName("text", Namespaces.EXIST_NS, "exist");
    private final static QName ATTRIB_ELEMENT = new QName("attribute", Namespaces.EXIST_NS, "exist");
    private final static QName SOURCE_ATTRIB = new QName("source", Namespaces.EXIST_NS, "exist");
    private final static QName ID_ATTRIB = new QName("id", Namespaces.EXIST_NS, "exist");

    public NativeSerializer(DBBroker broker, Configuration config) {
        super(broker, config);
    }
    
    protected void serializeToReceiver(NodeProxy p, boolean generateDocEvent, boolean checkAttributes)
    throws SAXException {
    	if(Type.subTypeOf(p.getType(), Type.DOCUMENT) || p.getNodeId() == NodeId.DOCUMENT_NODE) {
    			serializeToReceiver(p.getDocument(), generateDocEvent);
    			return;
    	}
    	setDocument(p.getDocument());
    	if (generateDocEvent) receiver.startDocument();
        Iterator domIter = broker.getNodeIterator(new StoredNode(p));
        serializeToReceiver(null, domIter, p.getDocument(), checkAttributes, p.getMatches(), new TreeSet());
        if (generateDocEvent) receiver.endDocument();
    }
    
    protected void serializeToReceiver(DocumentImpl doc, boolean generateDocEvent)
    throws SAXException {
    	long start = System.currentTimeMillis();
    	setDocument(doc);
    	NodeList children = doc.getChildNodes();
    	if (generateDocEvent) 
    		receiver.startDocument();
		if (doc.getDoctype() != null){
			if (getProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "no").equals("yes")) {
				final StoredNode n = (StoredNode) doc.getDoctype();
				serializeToReceiver(n, null, (DocumentImpl) n.getOwnerDocument(), true, null, new TreeSet());
			}
		}
    	// iterate through children
    	for (int i = 0; i < children.getLength(); i++) {
    		StoredNode node = (StoredNode) children.item(i);
    		Iterator domIter = broker.getNodeIterator(node);
    		domIter.next();
    		NodeProxy p = new NodeProxy(node);
    		serializeToReceiver(node, domIter, (DocumentImpl)node.getOwnerDocument(), 
    				true, p.getMatches(), new TreeSet());
    	}
    	DocumentImpl documentImpl = doc;
		LOG.debug("serializing document " + documentImpl.getDocId()
				+ " (" + documentImpl.getURI() + ")"
    			+ " to SAX took " + (System.currentTimeMillis() - start));
    	if (generateDocEvent) receiver.endDocument();
    }
    
    
    protected void serializeToReceiver(StoredNode node, Iterator iter,
            DocumentImpl doc, boolean first, Match match, Set namespaces) throws SAXException {
        if (node == null) 
        	node = (StoredNode) iter.next();
        if (node == null) 
        	return;
        // char ch[];
        String cdata;
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
        	String defaultNS = null;
	        if (((ElementImpl) node).declaresNamespacePrefixes()) {
	        	// declare namespaces used by this element
	        	String prefix, uri;
	        	for (Iterator i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
	        		prefix = (String) i.next();
	        		if (prefix.length() == 0) {
	        			defaultNS = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping("", defaultNS);
	        			namespaces.add(defaultNS);
	        		} else {
	        			uri = ((ElementImpl) node).getNamespaceForPrefix(prefix);
	        			receiver.startPrefixMapping(prefix, uri);
	        			namespaces.add(uri);
	        		}
	        	}
	        }
	        String ns = defaultNS == null ? node.getNamespaceURI() : defaultNS;
	        if (ns.length() > 0 && (!namespaces.contains(ns)))
	        	receiver.startPrefixMapping(node.getPrefix(), ns);
        	AttrList attribs = new AttrList();
        	if ((first && showId == EXIST_ID_ELEMENT) || showId == EXIST_ID_ALL) {
                attribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
            /* 
             * This is a proposed fix-up that the serializer could do
             * to make sure elements always have the namespace declarations
             *
            } else {
               // This is fix-up for when the node has a namespace but there is no
               // namespace declaration.
               String elementNS = node.getNamespaceURI();
               Node parent = node.getParentNode();
               if (parent instanceof ElementImpl) {
                  ElementImpl parentElement = (ElementImpl)parent;
                  String declaredNS = parentElement.getNamespaceForPrefix(node.getPrefix());
                  if (elementNS!=null && declaredNS==null) {
                     // We need to declare the prefix as it was missed somehow
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  } else if (elementNS==null && declaredNS!=null) {
                     // We need to declare the default namespace to be the no namespace
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  } else if (!elementNS.equals(defaultNS)) {
                     // Same prefix but different namespace
                     receiver.startPrefixMapping(node.getPrefix(), elementNS);
                  }
               } else if (elementNS!=null) {
                  // If the parent is the document, we must have a namespace
                  // declaration when there is a namespace URI.
                  receiver.startPrefixMapping(node.getPrefix(), elementNS);
               }
             */
            }
            if (first && showId > 0) {
            	// String src = doc.getCollection().getName() + "/" + doc.getFileName();
                attribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
            }
            int children = node.getChildCount();
            int count = 0;
            // int childLen;
            StoredNode child = null;
            while (count < children) {
                child = (StoredNode) iter.next();
                if (child!=null && child.getNodeType() == Node.ATTRIBUTE_NODE) {
                    if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) > 0)
                        cdata = processAttribute(((AttrImpl) child).getValue(), node.getNodeId(), match);
                    else
                        cdata = ((AttrImpl) child).getValue();
                    attribs.addAttribute(child.getQName(), cdata);
                    count++;
                    child.release();
                } else
                    break;
            }
            receiver.setCurrentNode(node);
            receiver.startElement(node.getQName(), attribs);
            while (count < children) {
                serializeToReceiver(child, iter, doc, false, match, namespaces);
                if (++count < children) {
                    child = (StoredNode) iter.next();
                } else
                    break;
            }
            receiver.setCurrentNode(node);
            receiver.endElement(node.getQName());
            if (((ElementImpl) node).declaresNamespacePrefixes()) {
                String prefix;
                for (Iterator i = ((ElementImpl) node).getPrefixes(); i.hasNext();) {
                    prefix = (String) i.next();
                    receiver.endPrefixMapping(prefix);
                }
            }
            if (ns.length() > 0 && (!namespaces.contains(ns)))
                receiver.endPrefixMapping(node.getPrefix());
            node.release();
            break;
        case Node.TEXT_NODE:
        	if (first && createContainerElements) {
                AttrList tattribs = new AttrList();
                if (showId > 0) {
                    tattribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
                    tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
                }
                receiver.startElement(TEXT_ELEMENT, tattribs);
            }
            receiver.setCurrentNode(node);
            receiver.characters(((TextImpl) node).getXMLString());
            if (first && createContainerElements)
                receiver.endElement(TEXT_ELEMENT);
            node.release();
            break;
        case Node.ATTRIBUTE_NODE:
            if ((getHighlightingMode() & TAG_ATTRIBUTE_MATCHES) == TAG_ATTRIBUTE_MATCHES)
                cdata = processAttribute(((AttrImpl) node).getValue(), node.getNodeId(), match);
            else
                cdata = ((AttrImpl) node).getValue();
        	if(first) {
                if (createContainerElements) {               
            		AttrList tattribs = new AttrList();
                    if (showId > 0) {
                        tattribs.addAttribute(ID_ATTRIB, node.getNodeId().toString());
                        tattribs.addAttribute(SOURCE_ATTRIB, doc.getFileURI().toString());
                    }
                    tattribs.addAttribute(node.getQName(), cdata);
                    receiver.startElement(ATTRIB_ELEMENT, tattribs);
                    receiver.endElement(ATTRIB_ELEMENT);
                }
                else {
                    LOG.warn("Error SENR0001: attribute '" + node.getQName() + "' has no parent element. " +
                        "While serializing document " + doc.getURI());
                    throw new SAXException("Error SENR0001: attribute '" + node.getQName() + "' has no parent element");
                }
            } else
        		receiver.attribute(node.getQName(), cdata);
            node.release();
            break;
		case Node.DOCUMENT_TYPE_NODE:
			String systemId = ((DocumentTypeImpl) node).getSystemId();
			String publicId =  ((DocumentTypeImpl) node).getPublicId();
			String name = ((DocumentTypeImpl) node).getName();
			receiver.documentType(name, publicId, systemId);
			break;
        case Node.PROCESSING_INSTRUCTION_NODE:
            receiver.processingInstruction(
                    ((ProcessingInstructionImpl) node).getTarget(),
                    ((ProcessingInstructionImpl) node).getData());
            node.release();
            break;
        case Node.COMMENT_NODE:
            String comment = ((CommentImpl) node).getData();
            char data[] = new char[comment.length()];
            comment.getChars(0, data.length, data, 0);
            receiver.comment(data, 0, data.length);
            node.release();
            break;
        case Node.CDATA_SECTION_NODE:
            String str = ((CDATASectionImpl)node).getData();
            if (first)
                receiver.characters(str);
            else {
                data = new char[str.length()];
                str.getChars(0,str.length(), data, 0);   
                receiver.cdataSection(data, 0, data.length);
            }
            break;
        //TODO : how to process other types ? -pb
        }
    }

    private final String processAttribute(String data, NodeId nodeId, Match match) {
        if (match == null) return data;
        // prepare a regular expression to mark match-terms
        StringBuilder expr = null;
        Match next = match;
        while (next != null) {
            if (next.getNodeId().equals(nodeId)) {
                if (expr == null) {
                    expr = new StringBuilder();
                    expr.append("\\b(");
                }
                if (expr.length() > 5) expr.append('|');
                expr.append("");
            }
            next = next.getNextMatch();
        }
        if (expr != null) {
            expr.append(")\\b");
            Pattern pattern = Pattern.compile(expr.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
            Matcher matcher = pattern.matcher(data);
            return matcher.replaceAll("||$1||");
        }
        return data;
    }
}
