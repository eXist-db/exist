/*
*  eXist Open Source Native XML Database
*  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
*  and others (see http://exist-db.org)
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

import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.w3c.dom.Document;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

/**
 * A wrapper class that forwards the method calls defined in the
 * {@link org.exist.util.serializer.Receiver} interface to a
 * SAX content handler and lexical handler.
 * 
 * @author wolf
 */
public class ReceiverToSAX implements Receiver {

	private ContentHandler contentHandler;
	private LexicalHandler lexicalHandler = null;
	
	private char[] charBuf = new char[2048];
	
	/**
	 * 
	 */
	public ReceiverToSAX(ContentHandler handler) {
		super();
		this.contentHandler = handler;
		
		if (handler instanceof LexicalHandler) {
            lexicalHandler = (LexicalHandler) handler;
        }
	}

	public void setLexicalHandler(LexicalHandler handler) {
		this.lexicalHandler = handler;
	}
	
	public void setContentHandler(ContentHandler handler) {
		this.contentHandler = handler;
	}
	
	public ContentHandler getContentHandler() {
		return contentHandler;
	}
	
	public LexicalHandler getLexicalHandler() {
		return lexicalHandler;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#startDocument()
	 */
	public void startDocument() throws SAXException {
		contentHandler.startDocument();
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#endDocument()
	 */
	public void endDocument() throws SAXException {
		contentHandler.endDocument();
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	public void startPrefixMapping(String prefix, String namespaceURI)
			throws SAXException {
		contentHandler.startPrefixMapping(prefix, namespaceURI);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#endPrefixMapping(java.lang.String)
	 */
	public void endPrefixMapping(String prefix) throws SAXException {
		contentHandler.endPrefixMapping(prefix);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#startElement(org.exist.dom.QName, org.exist.util.serializer.AttrList)
	 */
	public void startElement(QName qname, AttrList attribs) throws SAXException {
		final AttributesImpl a = new AttributesImpl();
		if(attribs != null) {
			QName attrQName;
			for(int i = 0; i < attribs.getLength(); i++) {
				attrQName = attribs.getQName(i);
				a.addAttribute(attrQName.getNamespaceURI(), attrQName.getLocalPart(), attrQName.getStringValue(),
						"CDATA", attribs.getValue(i));
			}
		}
		contentHandler.startElement(qname.getNamespaceURI(), qname.getLocalPart(), qname.getStringValue(), a);
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#endElement(org.exist.dom.QName)
	 */
	public void endElement(QName qname) throws SAXException {
		contentHandler.endElement(qname.getNamespaceURI(), qname.getLocalPart(), qname.getStringValue());
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#characters(java.lang.CharSequence)
	 */
	public void characters(CharSequence seq) throws SAXException {
		final int len = seq.length();
		if(len < charBuf.length) {
			for (int i = 0; i < len; i++)
				charBuf[i] = seq.charAt(i);
			contentHandler.characters(charBuf, 0, len);
		} else {
			contentHandler.characters(seq.toString().toCharArray(), 0, seq.length());
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#attribute(org.exist.dom.QName, java.lang.String)
	 */
	public void attribute(QName qname, String value) throws SAXException {
		contentHandler.characters(value.toCharArray(), 0, value.length());
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#comment(char[], int, int)
	 */
	public void comment(char[] ch, int start, int length) throws SAXException {
		if(lexicalHandler != null)
			{lexicalHandler.comment(ch, start, length);}
	}

	/* (non-Javadoc)
	 * @see org.exist.util.serializer.Receiver#processingInstruction(java.lang.String, java.lang.String)
	 */
	public void processingInstruction(String target, String data)
			throws SAXException {
		contentHandler.processingInstruction(target, data);
	}

    /* (non-Javadoc)
     * @see org.exist.util.serializer.Receiver#cdataSection(char[], int, int)
     */
    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        if(lexicalHandler != null)
            {lexicalHandler.startCDATA();}
        contentHandler.characters(ch, start, len);
        if(lexicalHandler != null)
            {lexicalHandler.endCDATA();}
    }

	public void documentType(String name, String publicId, String systemId) 
	throws SAXException {
		if(lexicalHandler != null){
			lexicalHandler.startDTD( name, publicId, systemId);
			lexicalHandler.endDTD();
		}
	}

    public void highlightText(CharSequence seq) {
        // not supported with this receiver
    }

    @Override
    public void setCurrentNode(INodeHandle node) {
        // just ignore
    }
    
    public Document getDocument() {
    	//just ignore
    	return null;
    }    
}
