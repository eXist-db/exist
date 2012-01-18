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
    public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.prepare(event, broker, transaction, documentPath, existingDocument);
        }
    }

    @Override
    public void finish(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl document) {
        try {
            for(DocumentTrigger trigger : getTriggers()) {
                trigger.finish(event, broker, transaction, documentPath, document);
            }
        } catch (TriggerException te) {
            LOG.error(te.getMessage(), te);
        }
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.beforeCreateDocument(broker, transaction, uri);
        }
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.afterCreateDocument(broker, transaction, document);
        }
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
       for(DocumentTrigger trigger : getTriggers()) {
           trigger.beforeUpdateDocument(broker, transaction, document);
       }
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.afterUpdateDocument(broker, transaction, document);
        }
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.beforeCopyDocument(broker, transaction, document, newUri);
        }
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.afterCopyDocument(broker, transaction, document, oldUri);
        }
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.beforeMoveDocument(broker, transaction, document, newUri);
        }
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.afterMoveDocument(broker, transaction, document, oldUri);
        }
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.beforeDeleteDocument(broker, transaction, document);
        }
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.afterDeleteDocument(broker, transaction, uri);
        }
    }
    
    @Override
    public void startDocument() throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startPrefixMapping(prefix, uri);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startElement(uri, localName, qName, atts);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endElement(uri, localName, qName);
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.characters(ch, start, length);
        }
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.ignorableWhitespace(ch, start, length);
        }
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.processingInstruction(target, data);
        }
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.skippedEntity(name);
        }
    }

    @Override
    public void startDTD(String name, String publicId, String systemId) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endDTD();
        }
    }

    @Override
    public void startEntity(String name) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startEntity(name);
        }
    }

    @Override
    public void endEntity(String name) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.endCDATA();
        }
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        for(DocumentTrigger trigger : getTriggers()) {
            trigger.comment(ch, start, length);
        }
    }
    
    @Override
    public void setDocumentLocator(Locator locator) {
        try {
            for(DocumentTrigger trigger : getTriggers()) {
                trigger.setDocumentLocator(locator);
            }
        } catch(TriggerException te) {
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
            for(DocumentTrigger trigger : getTriggers()) {
                trigger.setValidating(validating);
            }
        } catch(TriggerException te) {
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
            for(DocumentTrigger trigger : getTriggers()) {

                prevOutputHandler = new ContentHandlerWrapper(prevOutputHandler, trigger);
            }
        } catch(TriggerException te) {
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
            for(DocumentTrigger trigger : getTriggers()) {

                prevLexicalHandler = new LexicalHandlerWrapper(prevLexicalHandler, trigger);
            }
        } catch(TriggerException te) {
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
}