/**
 * 
 */
package org.exist.xupdate;

import java.util.Map;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

class NullDocumentTrigger implements DocumentTrigger {

	public void prepare(int event, DBBroker broker, Txn transaction, String documentPath, DocumentImpl existingDocument) throws TriggerException {
	}

	public void finish(int event, DBBroker broker, Txn transaction, DocumentImpl document) {
	}

	public boolean isValidating() {	
		return false;
	}

	public void setValidating(boolean validating) {
	}

	public void setOutputHandler(ContentHandler handler) {
	}

	public void setLexicalOutputHandler(LexicalHandler handler) {
	}

	public ContentHandler getOutputHandler() {
		return null;
	}

	public ContentHandler getInputHandler() {
		return null;
	}

	public LexicalHandler getLexicalOutputHandler() {
		return null;
	}

	public LexicalHandler getLexicalInputHandler() {
		return null;
	}

	public void configure(DBBroker broker, Collection parent, Map parameters) throws CollectionConfigurationException {
	}

	public Logger getLogger() {
		return null;
	}

	public void setDocumentLocator(Locator locator) {
	}

	public void startDocument() throws SAXException {
	}

	public void endDocument() throws SAXException {
	}

	public void startPrefixMapping(String prefix, String uri) throws SAXException {
	}

	public void endPrefixMapping(String prefix) throws SAXException {
	}

	public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
	}

	public void endElement(String uri, String localName, String qName) throws SAXException {
	}

	public void characters(char[] ch, int start, int length) throws SAXException {
	}

	public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
	}

	public void processingInstruction(String target, String data) throws SAXException {
	}

	public void skippedEntity(String name) throws SAXException {
	}

	public void startDTD(String name, String publicId, String systemId) throws SAXException {	
	}

	public void endDTD() throws SAXException {
	}

	public void startEntity(String name) throws SAXException {
	}

	public void endEntity(String name) throws SAXException {	
	}

	public void startCDATA() throws SAXException {
	}

	public void endCDATA() throws SAXException {			
	}

	public void comment(char[] ch, int start, int length) throws SAXException {
	}
}