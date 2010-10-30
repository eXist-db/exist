/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.synchro;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.TriggerException;
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
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class WatchDocument implements DocumentTrigger {

	private Communicator comm = null;

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.Trigger#configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)
	 */
	@Override
	public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws CollectionConfigurationException {
		List<?> objs = parameters.get(Communicator.COMMUNICATOR);
		if (objs != null)
			comm = (Communicator) objs.get(0);
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.Trigger#getLogger()
	 */
	@Override
	public Logger getLogger() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#setDocumentLocator(org.xml.sax.Locator)
	 */
	@Override
	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startDocument()
	 */
	@Override
	public void startDocument() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endDocument()
	 */
	@Override
	public void endDocument() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startPrefixMapping(java.lang.String, java.lang.String)
	 */
	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endPrefixMapping(java.lang.String)
	 */
	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#startElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#endElement(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#characters(char[], int, int)
	 */
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#ignorableWhitespace(char[], int, int)
	 */
	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#processingInstruction(java.lang.String, java.lang.String)
	 */
	@Override
	public void processingInstruction(String target, String data) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ContentHandler#skippedEntity(java.lang.String)
	 */
	@Override
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startDTD(java.lang.String, java.lang.String, java.lang.String)
	 */
	@Override
	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endDTD()
	 */
	@Override
	public void endDTD() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startEntity(java.lang.String)
	 */
	@Override
	public void startEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endEntity(java.lang.String)
	 */
	@Override
	public void endEntity(String name) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#startCDATA()
	 */
	@Override
	public void startCDATA() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#endCDATA()
	 */
	@Override
	public void endCDATA() throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.LexicalHandler#comment(char[], int, int)
	 */
	@Override
	public void comment(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#prepare(int, org.exist.storage.DBBroker, org.exist.storage.txn.Txn, org.exist.xmldb.XmldbURI, org.exist.dom.DocumentImpl)
	 */
	@Override
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
		if (comm == null) return;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#finish(int, org.exist.storage.DBBroker, org.exist.storage.txn.Txn, org.exist.xmldb.XmldbURI, org.exist.dom.DocumentImpl)
	 */
	@Override
	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
		if (comm == null) return;
		
		switch (event) {
		case STORE_DOCUMENT_EVENT:
			comm.createDocument(documentPath);
			break;

		case UPDATE_DOCUMENT_EVENT:
			comm.updateDocument(documentPath);
			break;

		case REMOVE_DOCUMENT_EVENT:
			comm.deleteDocument(documentPath);
			break;

		default:
			break;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#isValidating()
	 */
	@Override
	public boolean isValidating() {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#setValidating(boolean)
	 */
	@Override
	public void setValidating(boolean validating) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#setOutputHandler(org.xml.sax.ContentHandler)
	 */
	@Override
	public void setOutputHandler(ContentHandler handler) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#setLexicalOutputHandler(org.xml.sax.ext.LexicalHandler)
	 */
	@Override
	public void setLexicalOutputHandler(LexicalHandler handler) {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#getOutputHandler()
	 */
	@Override
	public ContentHandler getOutputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#getInputHandler()
	 */
	@Override
	public ContentHandler getInputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#getLexicalOutputHandler()
	 */
	@Override
	public LexicalHandler getLexicalOutputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.collections.triggers.DocumentTrigger#getLexicalInputHandler()
	 */
	@Override
	public LexicalHandler getLexicalInputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

}
