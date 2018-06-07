/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;

import org.exist.Indexer;
import org.exist.collections.Collection;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;

/**
 * Abstract default implementation of a Trigger. This implementation just
 * forwards all SAX events to the output content handler.
 * 
 * @author wolf
 */
public abstract class SAXTrigger implements DocumentTrigger, ContentHandler, LexicalHandler, ErrorHandler {

    // The output handlers to which SAX events should be forwarded
    private ContentHandler nextContentHandler = null;
    private LexicalHandler nextLexicalHandler = null;
    private ErrorHandler nextErrorHandler = null;
    
    private Collection collection = null;
    private boolean validating = true;

    protected Collection getCollection() {
        return collection;
    }

    /**
     * Configure the trigger. The default implementation just stores the parent
     * collection reference into the field {@link #collection collection}. Use
     * method {@link #getCollection() getCollection} to later retrieve the
     * collection.
     */
    @Override
    public void configure(DBBroker broker, Txn Transaction, Collection collection, Map<String, List<?>> parameters) throws TriggerException {
        this.collection = collection;
    }

    @Override
    public void setValidating(boolean validating) {
        this.validating = validating;
    }

    @Override
    public boolean isValidating() {
        return validating;
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        if (nextContentHandler != null)
            nextContentHandler.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.endDocument();
    }

    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.startPrefixMapping(prefix, namespaceURI);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(String namespaceURI, String localName, String qname, Attributes attributes) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.startElement(namespaceURI, localName, qname, attributes);
    }

    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.endElement(namespaceURI, localName, qname);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(String arg0) throws SAXException {
        if (nextContentHandler != null)
            nextContentHandler.skippedEntity(arg0);
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.startDTD(name, publicId, systemId);
    }

    @Override
    public void endDTD() throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.endDTD();
    }

    @Override
    public void startEntity(String arg0) throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.startEntity(arg0);
    }

    @Override
    public void endEntity(String arg0) throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.endEntity(arg0);
    }

    @Override
    public void startCDATA() throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.startCDATA();
    }

    @Override
    public void endCDATA() throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.endCDATA();
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (nextLexicalHandler != null)
            nextLexicalHandler.comment(ch, start, length);
    }
    
    public void warning(SAXParseException exception) throws SAXException {
        if (nextErrorHandler != null)
            nextErrorHandler.warning(exception);
    }

    public void error(SAXParseException exception) throws SAXException {
        if (nextErrorHandler != null)
            nextErrorHandler.error(exception);
    }
    
    public void fatalError(SAXParseException exception) throws SAXException {
        if (nextErrorHandler != null)
            nextErrorHandler.fatalError(exception);
    }

    protected void next(SAXTrigger nextTrigger) {
        nextContentHandler = nextTrigger;
        nextLexicalHandler = nextTrigger;
        nextErrorHandler = nextTrigger;
    }

    protected void next(Indexer indexer) {
        nextContentHandler = indexer;
        nextLexicalHandler = indexer;
        nextErrorHandler = indexer;
    }
}