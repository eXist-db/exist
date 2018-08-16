/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
package org.exist.storage.serializers;

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeHandle;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * Utility implementation of interface {@link ChainOfReceivers} which forwards all
 * events to a second receiver. Subclass this class and overwrite the methods you are interested in.
 * After processing an event, call the corresponding super method to forward it to the next receiver
 * in the chain.
 */
public class AbstractChainOfReceivers implements ChainOfReceivers {

    protected Receiver next;
    protected NodeHandle currentNode = null;

    @Override
    public void setNextInChain(Receiver next) {
        this.next = next;
    }

    @Override
    public Receiver getNextInChain() {
        return next;
    }

    @Override
    public Receiver getLastInChain() {
        Receiver last = this;
        Receiver next = getNextInChain();
        while (next != null) {
            last = next;
            next = ((ChainOfReceivers)next).getNextInChain();
        }
        return last;
    }

    @Override
    public void setCurrentNode(final NodeHandle node) {
        this.currentNode = node;
        if (next != null) {
            getNextInChain().setCurrentNode(node);
        }
    }

    protected NodeHandle getCurrentNode() {
        return currentNode;
    }

    @Override
    public Document getDocument() {
        throw new IllegalAccessError("unimplemented");
    }

    @Override
    public void startDocument() throws SAXException {
        if (next != null) {
            next.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        if (next != null) {
            next.endDocument();
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (next != null) {
            next.startPrefixMapping(prefix, namespaceURI);
        }
    }

    @Override
    public void endPrefixMapping(String prefix) throws SAXException {
        if (next != null) {
            next.endPrefixMapping(prefix);
        }
    }

    @Override
    public void startElement(QName qname, AttrList atts) throws SAXException {
        if (next != null) {
            next.startElement(qname, atts);
        }
    }

    @Override
    public void endElement(QName qname) throws SAXException {
        if (next != null) {
            next.endElement(qname);
        }
    }

    @Override
    public void characters(CharSequence seq) throws SAXException {
        if (next != null) {
            next.characters(seq);
        }
    }

    @Override
    public void attribute(QName qname, String value) throws SAXException {
        if (next != null) {
            next.attribute(qname, value);
        }
    }

    @Override
    public void comment(char[] ch, int start, int length) throws SAXException {
        if (next != null) {
            next.comment(ch, start, length);
        }
    }

    @Override
    public void cdataSection(char[] ch, int start, int len) throws SAXException {
        if (next != null) {
            next.cdataSection(ch, start, len);
        }
    }

    @Override
    public void processingInstruction(String target, String data) throws SAXException {
        if (next != null) {
            next.processingInstruction(target, data);
        }
    }

    @Override
    public void documentType(String name, String publicId, String systemId) throws SAXException {
        if (next != null) {
            next.documentType(name, publicId, systemId);
        }
    }

    @Override
    public void highlightText(CharSequence seq) throws SAXException {
        if (next != null) {
            next.highlightText(seq);
        }
    }
}
