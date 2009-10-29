/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
package org.exist.memtree;

import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Adapter class to build an internal, in-memory DOM from a SAX stream.
 * 
 * @author wolf
 */
public class SAXAdapter implements ContentHandler, LexicalHandler {

	private MemTreeBuilder builder;
	private HashMap namespaces = null;
	
	public SAXAdapter() {
		builder = new MemTreeBuilder();
 	}

	public SAXAdapter(XQueryContext context) {
		builder = new MemTreeBuilder(context);
	}
	
	public Document getDocument() {
		return builder.getDocument();
	}
	
	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	public void endDocument() throws SAXException {
		builder.endDocument();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	public void startDocument() throws SAXException {
		builder.startDocument();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	public void characters(char[] ch, int start, int length) throws SAXException {
		builder.characters(ch, start, length);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        builder.characters(ch, start, length);
    }

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	public void skippedEntity(String name) throws SAXException {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	public void setDocumentLocator(Locator locator) {
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data) throws SAXException {
		builder.processingInstruction(target, data);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		if(namespaces == null)
			namespaces = new HashMap();
		namespaces.put(prefix, uri);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	public void endElement(String namespaceURI, String localName, String qName) throws SAXException {
		builder.endElement();
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	public void startElement(String namespaceURI, String localName,	String qName, Attributes atts) 
            throws SAXException {
		builder.startElement(namespaceURI, localName, qName, atts);
		if(namespaces != null) {
			for(Iterator i = namespaces.entrySet().iterator(); i.hasNext(); ) {
				Map.Entry entry = (Map.Entry) i.next();
				builder.namespaceNode(entry.getKey().toString(), entry.getValue().toString());
			}
		}
		for(int i = 0; i < atts.getLength(); i++) {
			if(atts.getQName(i).startsWith("xmlns")) {
				String prefix = atts.getLocalName(i);
				String uri = atts.getValue(i);
                if(namespaces == null || !namespaces.containsKey(prefix))
					builder.namespaceNode(prefix, uri);
			}
		}
		namespaces = null;
	}

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endCDATA()
     */
    public void endCDATA() throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endDTD()
     */
    public void endDTD() throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startCDATA()
     */
    public void startCDATA() throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
     */
    public void comment(char[] ch, int start, int length) throws SAXException {
        builder.comment(ch, start, length);
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
     */
    public void endEntity(String name) throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
     */
    public void startEntity(String name) throws SAXException {
    }

    /* (non-Javadoc)
     * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
     */
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
    }
}
