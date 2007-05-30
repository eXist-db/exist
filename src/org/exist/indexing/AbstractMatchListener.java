package org.exist.indexing;

import org.exist.dom.QName;
import org.exist.dom.StoredNode;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Utility implementation of interface {@link org.exist.indexing.MatchListener} which forwards all
 * events to a second receiver. Subclass this class and overwrite the methods you are interested in.
 * After processing an event, call the corresponding super method to forward it to the next receiver
 * in the chain.
 */
public class AbstractMatchListener implements MatchListener {

    protected Receiver nextListener;
    protected StoredNode currentNode = null;

    public void setNextInChain(Receiver next) {
        this.nextListener = next;
    }

    public Receiver getNextInChain() {
        return nextListener;
    }

    public Receiver getLastInChain() {
        Receiver last = this, next = getNextInChain();
        while (next != null) {
            last = next;
            next = getNextInChain();
        }
        return last;
    }

    public void setCurrentNode(StoredNode node) {
        this.currentNode = node;
    }

    protected StoredNode getCurrentNode() {
        return currentNode;
    }
    
    public Document getDocument() {
    	//TODO return currentNode.getDocument() ?
    	return null;
    }

    public void startDocument() throws SAXException {
        if (nextListener != null) nextListener.startDocument();
    }

    public void endDocument() throws SAXException {
        if (nextListener != null) nextListener.endDocument();
    }

    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (nextListener != null) nextListener.startPrefixMapping(prefix, namespaceURI);
    }

    public void endPrefixMapping(String prefix) throws SAXException {
        if (nextListener != null) nextListener.endPrefixMapping(prefix);
    }

    public void startElement(QName qname, AttrList attribs) throws SAXException {
        if (nextListener != null) nextListener.startElement(qname, attribs);
    }

    public void endElement(QName qname) throws SAXException {
        if (nextListener != null) nextListener.endElement(qname);
    }

    public void characters(CharSequence seq) throws SAXException {
        if (nextListener != null) nextListener.characters(seq);
    }

    public void attribute(QName qname, String value) throws SAXException {
        if (nextListener != null) nextListener.attribute(qname, value);
    }

    public void comment(char[] ch, int start, int length) throws SAXException {
        if (nextListener != null) nextListener.comment(ch, start, length);
    }

    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        if (nextListener != null) nextListener.cdataSection(ch, start, len);
    }

    public void processingInstruction(String target, String data) throws SAXException {
        if (nextListener != null) nextListener.processingInstruction(target, data);
    }

    public void documentType(String name, String publicId, String systemId) throws SAXException {
        if (nextListener != null) nextListener.documentType(name, publicId, systemId);
    }

    public void highlightText(CharSequence seq) throws SAXException {
        if (nextListener != null) {
            
        }
    }
}
