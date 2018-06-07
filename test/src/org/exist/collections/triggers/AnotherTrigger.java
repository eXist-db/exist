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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.List;
import java.util.Map;

/**
 * Test trigger to check if trigger configuration is working properly.
 */
public class AnotherTrigger extends SAXTrigger {
    
    protected static StringBuilder sb = null;

    protected static int count = 0;
    protected static byte createDocumentEvents = 0;

    public void configure(DBBroker broker, Txn transaction, org.exist.collections.Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, transaction, parent, parameters);
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        createDocumentEvents |= 1;
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) {
        createDocumentEvents |= 2;
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn transaction, DocumentImpl document) {
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) {
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) throws TriggerException {
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn transaction, DocumentImpl document, XmldbURI newUri) {
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn transaction, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
    }

    @Override
    public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
    }

    @Override
    public void startDocument() throws SAXException {
        sb = new StringBuilder();
        
        super.startDocument();
    }
    
    public void startElement(String namespaceURI, String localName, String qname, Attributes attributes) throws SAXException {
        count++;
        
        sb.append("<").append(qname);
        
        for (int i = 0; i < attributes.getLength(); i++) {
            sb.append(" ").append(attributes.getQName(i)).append("='").append(attributes.getValue(i)).append("'");
        }
        
        sb.append(">");
        
        super.startElement(namespaceURI, localName, qname, attributes);
    }
    
    @Override
    public void endElement(String namespaceURI, String localName, String qname) throws SAXException {
        
        sb.append("</").append(qname).append(">");

        super.endElement(namespaceURI, localName, qname);
    }
    
    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        
        for (int i = 0; i < length; i++) {
            sb.append(ch[start + i]);
        }
        
        super.characters(ch, start, length);
    }
}
