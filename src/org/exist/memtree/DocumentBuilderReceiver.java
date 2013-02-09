/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-10 The eXist-db project
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

import org.w3c.dom.DOMException;
import org.w3c.dom.Document;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.XQueryContext;

import java.util.HashMap;
import java.util.Map;


/**
 * Builds an in-memory DOM tree from SAX {@link org.exist.util.serializer.Receiver} events.
 *
 * @author  Wolfgang <wolfgang@exist-db.org>
 */
public class DocumentBuilderReceiver implements ContentHandler, LexicalHandler, Receiver {

    private MemTreeBuilder builder = null;

    private Map<String, String> namespaces = null;
    private boolean explicitNSDecl = false;
    
    public boolean checkNS = false;
    
    public DocumentBuilderReceiver() {
        super();
    }

    public DocumentBuilderReceiver(MemTreeBuilder builder) {
        this(builder, false);
    }

    public DocumentBuilderReceiver(MemTreeBuilder builder, boolean declareNamespaces) {
        super();
        this.builder        = builder;
        this.explicitNSDecl = declareNamespaces;
    }

    @Override
    public Document getDocument() {
        return builder.getDocument();
    }

    public XQueryContext getContext() {
        return builder.getContext();
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
     */
    @Override
    public void setDocumentLocator(Locator locator) {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startDocument()
     */
    @Override
    public void startDocument() throws SAXException {
        if(builder == null) {
            builder = new MemTreeBuilder();
            builder.startDocument();
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endDocument()
     */
    @Override
    public void endDocument() throws SAXException {
        builder.endDocument();
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
     */
    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if(prefix == null || prefix.length() == 0) {
            builder.setDefaultNamespace(namespaceURI);
        }
        if (!explicitNSDecl) {
            return;
        }
        if(namespaces == null) {
            namespaces = new HashMap<String, String>();
        }
        namespaces.put(prefix, namespaceURI);
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
     */
    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if(prefix == null || prefix.length() == 0) {
            builder.setDefaultNamespace("");
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String namespaceURI, String localName, String qName,
            Attributes attrs) throws SAXException {
        builder.startElement(namespaceURI, localName, qName, attrs);
        declareNamespaces();
    }

    private void declareNamespaces() {
        if (explicitNSDecl && namespaces != null) {
            for(final Map.Entry<String, String> entry : namespaces.entrySet()) {
                builder.namespaceNode(entry.getKey(), entry.getValue());
            }
            namespaces.clear();
        }
    }

    @Override
    public void startElement(QName qname, AttrList attribs) {
        qname = checkNS(true, qname);
        builder.startElement(qname, null);
        declareNamespaces();
        if (attribs != null) {
            for (int i = 0; i < attribs.getLength(); i++) {
                builder.addAttribute( attribs.getQName(i), attribs.getValue(i));
            }
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
        builder.endElement();
    }

    @Override
    public void endElement(QName qname) throws SAXException {
        builder.endElement();
    }

    public void addReferenceNode(NodeProxy proxy) throws SAXException {
        builder.addReferenceNode(proxy);
    }

    public void addNamespaceNode(QName qname) throws SAXException {
        builder.namespaceNode(qname);
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {
        builder.characters(seq);
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#characters(char[], int, int)
     */
    @Override
    public void characters(char[] ch, int start, int len) throws SAXException {
        builder.characters(ch, start, len);
    }

    @Override
    public void attribute(QName qname, String value) throws SAXException {
        try {
            qname = checkNS(false, qname);
            builder.addAttribute(qname, value);
        } catch(final DOMException e) {
            throw new SAXException(e.getMessage());
        }
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
     */
    @Override
    public void ignorableWhitespace(char[] ch, int start, int len) throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
     */
    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        builder.processingInstruction(target, data);
    }

    /* (non-Javadoc)
     * @see org.exist.util.serializer.Receiver#cdataSection(char[], int, int)
     */
    @Override
    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        builder.cdataSection(new String(ch, start, len));
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
     */
    @Override
    public void skippedEntity(String arg0) throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endCDATA()
     */
    @Override
    public void endCDATA() throws SAXException {
        // TODO ignored
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endDTD()
     */
    @Override
    public void endDTD() throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startCDATA()
     */
    @Override
    public void startCDATA() throws SAXException {
        // TODO Ignored
    }

    @Override
    public void documentType(String name, String publicId, String systemId) throws SAXException {
        builder.documentType(name, publicId, systemId);
    }
    
    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
     */
    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        builder.comment(ch, start, length);
    }
    
    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
     */
    @Override
    public void endEntity(String name) throws SAXException{
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
     */
    @Override
    public void startEntity(String name) throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
     */
    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }

    @Override
    public void highlightText(CharSequence seq) {
        // not supported with this receiver
    }

    @Override
    public void setCurrentNode(StoredNode node) {
        // ignored
    }
    
    public QName checkNS(boolean isElement, QName qname) {
        if(checkNS) {
            final XQueryContext context = builder.getContext();
            if(qname.getPrefix() == null) {
            	if (qname.getNamespaceURI() == null || qname.getNamespaceURI().isEmpty()) {
            		return qname;
            	
            	} else if (isElement) {
	                return qname; 

            	} else {
	                final String prefix = generatePrfix(context, context.getInScopePrefix(qname.getNamespaceURI()));

	                context.declareInScopeNamespace(prefix, qname.getNamespaceURI());
	                qname.setPrefix(prefix);
	                return qname; 
	            }
            }
        	if(qname.getPrefix().isEmpty() && qname.getNamespaceURI() == null)
                {return qname;}

            final String inScopeNamespace = context.getInScopeNamespace(qname.getPrefix());
            if(inScopeNamespace == null) {
                context.declareInScopeNamespace(qname.getPrefix(), qname.getNamespaceURI());
                
            } else if(!inScopeNamespace.equals(qname.getNamespaceURI())) {
                
                final String prefix = generatePrfix(context, context.getInScopePrefix(qname.getNamespaceURI()));

                context.declareInScopeNamespace(prefix, qname.getNamespaceURI());
                qname.setPrefix(prefix);
            }
        }
        return qname;
    }
    
    private String generatePrfix(XQueryContext context, String prefix) {
        int i = 0;
        while (prefix == null) {
            prefix = "XXX";
            if(i > 0) {
                prefix += String.valueOf(i);
            }
            if(context.getInScopeNamespace(prefix) != null) {
                prefix = null;
                i++;
            }
        }
        return prefix;
    }
}