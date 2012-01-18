package org.exist.collections.triggers;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 *
 * @author aretter
 */
public class ContentHandlerWrapper implements ContentHandler {

    private final ContentHandler output;
    
    public ContentHandlerWrapper(ContentHandler output, DocumentTrigger trigger) {
        this.output = output;
        trigger.setOutputHandler(output);
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        output.characters(ch, start, length);
    }

    @Override
    public void endDocument() throws SAXException {
        output.endDocument();
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        output.endElement(uri, localName, qName);
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        output.endPrefixMapping(prefix);
    }

    @Override
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        output.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        output.processingInstruction(target, data);
    }

    @Override
    public void setDocumentLocator(Locator locator) {
        output.setDocumentLocator(locator);
    }

    @Override
    public void skippedEntity(String name) throws SAXException {
        output.skippedEntity(name);
    }

    @Override
    public void startDocument() throws SAXException {
        output.startDocument();
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
        output.startElement(uri, localName, qName, atts);
    }

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
        output.startPrefixMapping(prefix, uri);
    }
}