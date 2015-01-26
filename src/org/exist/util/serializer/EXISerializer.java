/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.util.serializer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import com.siemens.ct.exi.EXIFactory;
import com.siemens.ct.exi.GrammarFactory;
import com.siemens.ct.exi.api.sax.SAXEncoder;
import com.siemens.ct.exi.exceptions.EXIException;
import com.siemens.ct.exi.grammars.Grammars;
import com.siemens.ct.exi.helpers.DefaultEXIFactory;

public class EXISerializer implements ContentHandler, Receiver {
	
	static final String UNKNOWN_TYPE = "";
	
	private SAXEncoder encoder;
	
	public EXISerializer(OutputStream exiOutputStream) throws EXIException, IOException {
		final EXIFactory exiFactory = DefaultEXIFactory.newInstance();
		encoder = new SAXEncoder(exiFactory);
		encoder.setOutputStream(exiOutputStream);
	}
	
	public EXISerializer(OutputStream exiOutputStream, InputStream xsdInputStream) throws EXIException, IOException {
		final EXIFactory exiFactory = DefaultEXIFactory.newInstance();
		final GrammarFactory grammarFactory = GrammarFactory.newInstance();
		final Grammars g = grammarFactory.createGrammars(xsdInputStream);
		exiFactory.setGrammars(g);
		encoder = new SAXEncoder(exiFactory);
		encoder.setOutputStream(exiOutputStream);
	}
	
	public void startDocument() throws SAXException {
		encoder.startDocument();
	}

	public void endDocument() throws SAXException {
		encoder.endDocument();
	}

	@Override
	public void startPrefixMapping(String prefix, String namespaceURI)
			throws SAXException {
		encoder.startPrefixMapping(prefix, namespaceURI);
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		encoder.endPrefixMapping(prefix);
	}

	@Override
	public void startElement(QName qname, AttrList attribs) throws SAXException {
		AttributesImpl attributes = null;
		if(attribs != null) {
			attributes = new AttributesImpl();
			for(int x=0; x < attribs.size; x++) {
				final QName attribQName = attribs.getQName(x);
				attributes.addAttribute(attribQName.getNamespaceURI(),
						attribQName.getLocalPart(),
						attribQName.getStringValue(),
						UNKNOWN_TYPE,
						attribs.getValue(x));
			}
		}
		encoder.startElement(qname.getNamespaceURI(), qname.getLocalPart(), null, attributes);
		
	}

	@Override
	public void endElement(QName qname) throws SAXException {
		encoder.endElement(qname.getNamespaceURI(), qname.getLocalPart(), null);
		
	}

	@Override
	public void characters(CharSequence seq) throws SAXException {
		final String sequence = seq.toString();
		encoder.characters(sequence.toCharArray(), 0, sequence.length());
	}

	@Override
	public void attribute(QName qname, String value) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void comment(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
	}

	@Override
	public void cdataSection(char[] ch, int start, int len) throws SAXException {
		// TODO Auto-generated method stub
	}

	@Override
	public void processingInstruction(String target, String data)
			throws SAXException {
		// TODO Auto-generated method stub
	}

	@Override
	public void documentType(String name, String publicId, String systemId)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void highlightText(CharSequence seq) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setCurrentNode(INodeHandle node) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Document getDocument() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes atts) throws SAXException {
		encoder.startElement(uri, localName, null, atts);
		
	}

	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		encoder.endElement(uri, localName, null);
		
	}

	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		encoder.characters(ch, start, length);
		
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length)
			throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		
	}
	
	void setEncoder(SAXEncoder encoder) {
		this.encoder = encoder;
	}

}
