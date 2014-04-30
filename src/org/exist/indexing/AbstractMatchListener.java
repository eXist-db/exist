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

    @Override
    public void setNextInChain(Receiver next) {
        this.nextListener = next;
    }

    @Override
    public Receiver getNextInChain() {
        return nextListener;
    }

    @Override
    public Receiver getLastInChain() {
        Receiver last = this, next = getNextInChain();
        while (next != null) {
            last = next;
            next = ((MatchListener)next).getNextInChain();
        }
        return last;
    }

    @Override
    public void setCurrentNode(StoredNode node) {
        this.currentNode = node;
        if (nextListener != null)
            {getNextInChain().setCurrentNode(node);}
    }

    protected StoredNode getCurrentNode() {
        return currentNode;
    }

    @Override
    public Document getDocument() {
        //TODO return currentNode.getDocument() ?
        return null;
    }

    @Override
    public void startDocument() throws SAXException {
        if (nextListener != null) {nextListener.startDocument();}
    }

    @Override
    public void endDocument() throws SAXException {
        if (nextListener != null) {nextListener.endDocument();}
    }

    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (nextListener != null) {nextListener.startPrefixMapping(prefix, namespaceURI);}
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if (nextListener != null) {nextListener.endPrefixMapping(prefix);}
    }

    @Override
    public void startElement(QName qname, AttrList attribs) throws SAXException {
        if (nextListener != null) {nextListener.startElement(qname, attribs);}
    }

    @Override
    public void endElement(QName qname) throws SAXException {
        if (nextListener != null) {nextListener.endElement(qname);}
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {
        if (nextListener != null) {nextListener.characters(seq);}
    }

    @Override
    public void attribute(QName qname, String value) throws SAXException {
        if (nextListener != null) {nextListener.attribute(qname, value);}
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (nextListener != null) {nextListener.comment(ch, start, length);}
    }

    @Override
    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        if (nextListener != null) {nextListener.cdataSection(ch, start, len);}
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (nextListener != null) {nextListener.processingInstruction(target, data);}
    }

    @Override
    public void documentType(String name, String publicId, String systemId) throws SAXException {
        if (nextListener != null) {nextListener.documentType(name, publicId, systemId);}
    }

    @Override
    public void highlightText(CharSequence seq) throws SAXException {
        if (nextListener != null) {
            //Nothing to do
        }
    }
}
