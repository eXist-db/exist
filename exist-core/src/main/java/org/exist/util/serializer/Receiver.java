/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.serializer;

import org.exist.Namespaces;
import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * A receiver is similar to the SAX content handler and lexical handler interfaces, but
 * uses some higher level types as arguments. For example, element names are internally
 * stored as QName objects, so startElement and endElement expect a QName. This way,
 * we avoid copying objects.
 * 
 * @author wolf
 */
public interface Receiver<T extends INodeHandle> {

    QName MATCH_ELEMENT =
            new QName("match", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);

    void startDocument() throws SAXException;

	void endDocument() throws SAXException;

	void startPrefixMapping(String prefix, String namespaceURI) throws SAXException;
	
	void endPrefixMapping(String prefix) throws SAXException;
	
	void startElement(QName qname, AttrList attribs) throws SAXException;

	void endElement(QName qname) throws SAXException;

	void characters(CharSequence seq) throws SAXException;

	void attribute(QName qname, String value)
			throws SAXException;

	void comment(char[] ch, int start, int length)
			throws SAXException;
    
    void cdataSection(char[] ch, int start, int len) throws SAXException;
	
	void processingInstruction(String target, String data) throws SAXException;
	
	void documentType(String name, String publicId, String systemId) throws SAXException;

    void highlightText(CharSequence seq) throws SAXException;
    
    void setCurrentNode(T node);
    
    Document getDocument();
}