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
import org.jgroups.blocks.MethodCall;
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

	@Override
	public Logger getLogger() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void setDocumentLocator(Locator locator) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startDocument() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endDocument() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startPrefixMapping(String prefix, String uri) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endPrefixMapping(String prefix) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void processingInstruction(String target, String data) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void skippedEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startDTD(String name, String publicId, String systemId) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endDTD() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endEntity(String name) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void startCDATA() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void endCDATA() throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void comment(char[] ch, int start, int length) throws SAXException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
	}

	@Override
	public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_CREATE_DOCUMENT, comm.getChannel().getName(), uri) );
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_CREATE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_UPDATE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_UPDATE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_COPY_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_COPY_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_MOVE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_MOVE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.BEFORE_DELETE_DOCUMENT, comm.getChannel().getName(), document.getURI()) );
	}

	@Override
	public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
		if (comm == null) return;
		comm.callRemoteMethods( 
				new MethodCall(Communicator.AFTER_DELETE_DOCUMENT, comm.getChannel().getName(), uri) );
	}

	@Override
	public boolean isValidating() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void setValidating(boolean validating) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setOutputHandler(ContentHandler handler) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setLexicalOutputHandler(LexicalHandler handler) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public ContentHandler getOutputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public ContentHandler getInputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LexicalHandler getLexicalOutputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public LexicalHandler getLexicalInputHandler() {
		// TODO Auto-generated method stub
		return null;
	}

}
