/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011-2012 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.collections.triggers;

import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import org.exist.collections.Collection;
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
 *
 * @author aretter
 */
public class DocumentTriggersVisitor extends AbstractTriggersVisitor<DocumentTrigger, DocumentTriggerProxies> implements DocumentTrigger {

    protected Logger LOG = Logger.getLogger(getClass());
    
    public DocumentTriggersVisitor(DBBroker broker, DocumentTriggerProxies proxies) {
        super(broker, proxies);
    }
    
    
    @Override
    public void configure(DBBroker broker, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
        //ignore triggers are already configured by this stage!
    }

    @Override
    public void prepare(int event, DBBroker broker, Txn txn, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.prepare(event, broker, txn, documentPath, existingDocument);
        }
    }

    @Override
    public void finish(int event, DBBroker broker, Txn txn, XmldbURI documentPath, DocumentImpl document) {
        try {
            for(final DocumentTrigger trigger : getTriggers()) {
                trigger.finish(event, broker, txn, documentPath, document);
            }
        } catch (final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.beforeCreateDocument(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
    	for(final DocumentTrigger trigger : getTriggers()) {
            trigger.afterCreateDocument(broker, txn, document);
        }
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
        	trigger.beforeUpdateDocument(broker, txn, document);
        }
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.afterUpdateDocument(broker, txn, document);
        }
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.beforeCopyDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.afterCopyDocument(broker, txn, document, oldUri);
        }
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.beforeMoveDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.afterMoveDocument(broker, txn, document, oldUri);
        }
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.beforeDeleteDocument(broker, txn, document);
        }
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.afterDeleteDocument(broker, txn, uri);
        }
    }
    
    @Override
    public void startDocument() throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.skippedEntity(name);
        }
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endDTD();
        }
    }

    @Override
    public void startEntity(String name) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startEntity(name);
        }
    }

    @Override
    public void endEntity(String name) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.endCDATA();
        }
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        for(final DocumentTrigger trigger : getTriggers()) {
            trigger.comment(ch, start, length);
        }
    }
    
    @Override
    public void setDocumentLocator(Locator locator) {
        try {
            for(final DocumentTrigger trigger : getTriggers()) {
                trigger.setDocumentLocator(locator);
            }
        } catch(final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }
    
    private boolean validating = true;
    
    @Override
    public boolean isValidating() {
        return this.validating;
    }

    @Override
    public void setValidating(boolean validating) {
        this.validating = validating;
        try {
            for(final DocumentTrigger trigger : getTriggers()) {
                trigger.setValidating(validating);
            }
        } catch(final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }

    private ContentHandler outputHandler;
    
    @Override
    public ContentHandler getOutputHandler() {
        return outputHandler;
    }
            
    @Override
    public void setOutputHandler(ContentHandler outputHandler) {
        
        ContentHandler prevOutputHandler = outputHandler;
        
        try {
            for(final DocumentTrigger trigger : getTriggers()) {
                prevOutputHandler = new ContentHandlerWrapper(prevOutputHandler, trigger);
            }
        } catch(final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
        
        this.outputHandler = prevOutputHandler;
    }
    
    private LexicalHandler lexicalHandler;
    
    @Override
    public LexicalHandler getLexicalOutputHandler() {
        return lexicalHandler;
    }

    @Override
    public void setLexicalOutputHandler(LexicalHandler lexicalHandler) {
        LexicalHandler prevLexicalHandler = lexicalHandler;
        
        try {
            for(final DocumentTrigger trigger : getTriggers()) {
                prevLexicalHandler = new LexicalHandlerWrapper(prevLexicalHandler, trigger);
            }
        } catch(final TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
        
        this.lexicalHandler = prevLexicalHandler;
    }

    //TODO check this 
    /*** these should defer to the Handler methods invoked above ***/
    @Override
    public ContentHandler getInputHandler() {
        return this.outputHandler;
    }
    
    @Override
    public LexicalHandler getLexicalInputHandler() {
        return this.lexicalHandler;
    }

	@Override
	public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
        	trigger.beforeUpdateDocumentMetadata(broker, txn, document);
        }
	}

	@Override
	public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for(final DocumentTrigger trigger : getTriggers()) {
        	trigger.afterUpdateDocumentMetadata(broker, txn, document);
        }
	}
}