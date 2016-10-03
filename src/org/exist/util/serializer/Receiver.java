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

    public final static QName MATCH_ELEMENT =
            new QName("match", Namespaces.EXIST_NS, Namespaces.EXIST_NS_PREFIX);

    public void startDocument() throws SAXException;

	public void endDocument() throws SAXException;

	public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException;
	
	public void endPrefixMapping(String prefix) throws SAXException;
	
	public void startElement(QName qname, AttrList attribs) throws SAXException;

	public void endElement(QName qname) throws SAXException;

	public void characters(CharSequence seq) throws SAXException;

	public void attribute(QName qname, String value)
			throws SAXException;

	public void comment(char[] ch, int start, int length)
			throws SAXException;
    
    public void cdataSection(char[] ch, int start, int len) throws SAXException;
	
	public void processingInstruction(String target, String data) throws SAXException;
	
	public void documentType(String name, String publicId, String systemId) throws SAXException;

    public void highlightText(CharSequence seq) throws SAXException;
    
    public void setCurrentNode(T node);
    
    public Document getDocument();
}