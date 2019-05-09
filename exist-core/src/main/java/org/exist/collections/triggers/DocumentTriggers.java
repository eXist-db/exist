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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.exist.Indexer;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfiguration;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.ext.LexicalHandler;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DocumentTriggers implements DocumentTrigger, ContentHandler, LexicalHandler, ErrorHandler {
    
    private Indexer indexer;
    
    private ContentHandler contentHandler;
    private LexicalHandler lexicalHandler;
    private ErrorHandler errorHandler;
    
    private SAXTrigger last = null;
    
    private final List<DocumentTrigger> triggers;
    
    public DocumentTriggers(DBBroker broker, Txn transaction) throws TriggerException {
        this(broker, transaction, null, null, null);
    }
    
    public DocumentTriggers(DBBroker broker, Txn transaction, Collection collection) throws TriggerException {
        this(broker, transaction, null, collection, broker.isTriggersEnabled() ? collection.getConfiguration(broker) : null);
    }

    public DocumentTriggers(DBBroker broker, Txn transaction, Indexer indexer, Collection collection, CollectionConfiguration config) throws TriggerException {
        
        List<TriggerProxy<? extends DocumentTrigger>> docTriggers = null;
        if (config != null) {
            docTriggers = config.documentTriggers();
        }
        
        java.util.Collection<TriggerProxy<? extends DocumentTrigger>> masterTriggers = broker.getDatabase().getDocumentTriggers();
        
        triggers = new ArrayList<DocumentTrigger>( masterTriggers.size() + (docTriggers == null ? 0 : docTriggers.size()) );
        
        for (TriggerProxy<? extends DocumentTrigger> docTrigger : masterTriggers) {
            
            DocumentTrigger instance = docTrigger.newInstance(broker, transaction, collection);

            register(instance);
        }
        
        if (docTriggers != null) {
            for (TriggerProxy<? extends DocumentTrigger> docTrigger : docTriggers) {
                
                DocumentTrigger instance = docTrigger.newInstance(broker, transaction, collection);
                
                register(instance);
            }
        }
        
        if (indexer != null) {
            finishPreparation(indexer);
        }
        
        last = null;
    }
    
    private void finishPreparation(Indexer indexer) {
        if (last == null) {
            contentHandler = indexer;
            lexicalHandler = indexer;
            errorHandler = indexer;
        } else {
            last.next( indexer );
        }
        
        this.indexer = indexer;
    }

    private void register(DocumentTrigger trigger) {
        if (trigger instanceof SAXTrigger) {
            
            SAXTrigger filteringTrigger = (SAXTrigger) trigger;
            
            if (last == null) {
                contentHandler = filteringTrigger;
                lexicalHandler = filteringTrigger;
                errorHandler = filteringTrigger;

            } else {
                last.next( filteringTrigger );
            }
            
            last = filteringTrigger;
        }

        triggers.add(trigger);
    }

    @Override
    public void configure(DBBroker broker, Txn txn, Collection parent, Map<String, List<? extends Object>> parameters) throws TriggerException {
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        contentHandler.setDocumentLocator(locator);
    }

    @Override
    public void startDocument() throws SAXException {
        contentHandler.startDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        contentHandler.endDocument();
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        contentHandler.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        contentHandler.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        contentHandler.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        contentHandler.endElement(uri, localName, qName);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        contentHandler.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        contentHandler.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        contentHandler.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        contentHandler.skippedEntity(name);
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        lexicalHandler.startDTD(name, publicId, systemId);
    }

    @Override
    public void endDTD() throws SAXException {
        lexicalHandler.endDTD();
    }

    @Override
    public void startEntity(String name) throws SAXException {
        lexicalHandler.startEntity(name);
    }

    @Override
    public void endEntity(String name) throws SAXException {
        lexicalHandler.endEntity(name);
    }

    @Override
    public void startCDATA() throws SAXException {
        lexicalHandler.startCDATA();
    }

    @Override
    public void endCDATA() throws SAXException {
        lexicalHandler.endCDATA();
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        lexicalHandler.comment(ch, start, length);
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeCreateDocument(broker, txn, uri);
        }
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterCreateDocument(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeUpdateDocument(broker, txn, document);
        }
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterUpdateDocument(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeUpdateDocumentMetadata(broker, txn, document);
        }
    }

    @Override
    public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterUpdateDocumentMetadata(broker, txn, document);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeCopyDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterCopyDocument(broker, txn, document, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeMoveDocument(broker, txn, document, newUri);
        }
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterMoveDocument(broker, txn, document, oldUri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        for (DocumentTrigger trigger : triggers) {
            trigger.beforeDeleteDocument(broker, txn, document);
        }
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) {
        for (DocumentTrigger trigger : triggers) {
            try {
                trigger.afterDeleteDocument(broker, txn, uri);
            } catch (Exception e) {
                Trigger.LOG.error(e.getMessage(), e);
            }
        }
    }

    @Override
    public boolean isValidating() {
        return false;
    }

    @Override
    public void setValidating(boolean validating) {
        for (DocumentTrigger trigger : triggers) {
            trigger.setValidating(validating);
        }
        
        indexer.setValidating(validating);
    }

    @Override
    public void warning(SAXParseException exception) throws SAXException {
        if (errorHandler != null)
            errorHandler.warning(exception);
    }

    @Override
    public void error(SAXParseException exception) throws SAXException {
        if (errorHandler != null)
            errorHandler.error(exception);
    }

    @Override
    public void fatalError(SAXParseException exception) throws SAXException {
        if (errorHandler != null)
            errorHandler.fatalError(exception);
    }
}
