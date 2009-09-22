/*
 *  FilteringTrigger.java - eXist Open Source Native XML Database
 *  Copyright (C) 2003 Wolfgang M. Meier
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
 * $Id$
 *
 */
package org.exist.collections.triggers;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

/**
 * Abstract default implementation of a Trigger. This implementation just forwards
 * all SAX events to the output content handler.
 *  
 * @author wolf
 */
public abstract class FilteringTrigger implements DocumentTrigger {

	protected Logger LOG = Logger.getLogger(getClass());
	
	// The output handlers to which SAX events should be
	// forwarded
	protected ContentHandler outputHandler = null;
	protected LexicalHandler lexicalOutputHandler = null;
	protected Collection collection = null;
	protected boolean validating = true;
	
	/**
	 * Configure the trigger. The default implementation just stores the parent collection
	 * reference into the field {@link #collection collection}. Use method {@link #getCollection() getCollection}
	 * to later retrieve the collection. 
	 */
	public void configure(DBBroker broker, Collection parent, Map parameters) 
    throws CollectionConfigurationException {
    	this.collection = parent;
    }
    
	public void setValidating(boolean validating) {
		this.validating = validating;
	}
	
	public boolean isValidating() {
		return validating;
	}
	
	public Collection getCollection() {
		return collection;
	}
	
	public ContentHandler getInputHandler() {
		return this;
	}
	
	public LexicalHandler getLexicalInputHandler() {
		return this;
	}

	public ContentHandler getOutputHandler() {
		return outputHandler;
	}
	
	public LexicalHandler getLexicalOutputHandler() {
		return lexicalOutputHandler;
	}
	
	public void setOutputHandler(ContentHandler handler) {
		outputHandler = handler;
	}

	public void setLexicalOutputHandler(LexicalHandler handler) {
		lexicalOutputHandler = handler;
	}
	
	public Logger getLogger() {
		return LOG;
	}
	
	public void setDocumentLocator(Locator locator) {
		outputHandler.setDocumentLocator(locator);
	}

	public void startDocument() throws SAXException {
		outputHandler.startDocument();
	}

	public void endDocument() throws SAXException {
		outputHandler.endDocument();
	}

	public void startPrefixMapping(String prefix, String namespaceURI)
		throws SAXException {
		outputHandler.startPrefixMapping(prefix, namespaceURI);
	}

	public void endPrefixMapping(String prefix) throws SAXException {
		outputHandler.endPrefixMapping(prefix);
	}

	public void startElement(
		String namespaceURI,
		String localName,
		String qname,
		Attributes attributes)
		throws SAXException {
		outputHandler.startElement(namespaceURI, localName, qname, attributes);
	}

	public void endElement(String namespaceURI, String localName, String qname)
		throws SAXException {
		outputHandler.endElement(namespaceURI, localName, qname);
	}

	public void characters(char[] ch, int start, int length)
		throws SAXException {
		outputHandler.characters(ch, start, length);
	}

	public void ignorableWhitespace(char[] ch, int start, int length)
		throws SAXException {
		outputHandler.ignorableWhitespace(ch, start, length);
	}

	public void processingInstruction(String target, String data)
		throws SAXException {
		outputHandler.processingInstruction(target, data);
	}

	public void skippedEntity(String arg0) throws SAXException {
		outputHandler.skippedEntity(arg0);
	}

	public void startDTD(String name, String publicId, String systemId)
		throws SAXException {
		lexicalOutputHandler.startDTD(name, publicId, systemId);
	}

	public void endDTD() throws SAXException {
		lexicalOutputHandler.endDTD();
	}

	public void startEntity(String arg0) throws SAXException {
		lexicalOutputHandler.startEntity(arg0);
	}

	public void endEntity(String arg0) throws SAXException {
		lexicalOutputHandler.endEntity(arg0);
	}

	public void startCDATA() throws SAXException {
		lexicalOutputHandler.startCDATA();
	}

	public void endCDATA() throws SAXException {
		lexicalOutputHandler.endCDATA();
	}

	public void comment(char[] ch, int start, int length) throws SAXException {
		lexicalOutputHandler.comment(ch, start, length);
	}

}
