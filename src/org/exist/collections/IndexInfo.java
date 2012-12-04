/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-2012 The eXist Project
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
package org.exist.collections;

import org.exist.Indexer;
import org.exist.Namespaces;
import org.exist.collections.triggers.DocumentTriggersVisitor;
import org.exist.dom.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.serializer.DOMStreamer;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

/**
 * Internal class used to track required fields between calls to
 * {@link org.exist.collections.Collection#validateXMLResource(Txn, DBBroker, XmldbURI, InputSource)} and
 * {@link org.exist.collections.Collection#store(Txn, DBBroker, IndexInfo, InputSource, boolean)}.
 * 
 * @author wolf
 */
public class IndexInfo {

    private Indexer indexer;
    private DOMStreamer streamer;
    private DocumentTriggersVisitor triggersVisitor;
    private boolean creating = false;
    private CollectionConfiguration collectionConfig;

    IndexInfo(Indexer indexer, CollectionConfiguration collectionConfig) {
        this.indexer = indexer;
        this.collectionConfig = collectionConfig;
    }

    public Indexer getIndexer() {
        return indexer;
    }

    public void setTriggersVisitor(DocumentTriggersVisitor triggersVisitor) {
        this.triggersVisitor = triggersVisitor;
    }

    public DocumentTriggersVisitor getTriggersVisitor() {
        return triggersVisitor;
    }

    public void setCreating(boolean creating) {
        this.creating = creating;
    }

    public boolean isCreating() {
        return creating;
    }

    void setReader(XMLReader reader, EntityResolver entityResolver) throws SAXException {
        if(entityResolver != null) {
            reader.setEntityResolver(entityResolver);
        }
        LexicalHandler lexicalHandler = triggersVisitor == null ?
            indexer : triggersVisitor.getLexicalInputHandler();
        ContentHandler contentHandler = triggersVisitor == null ?
            indexer : triggersVisitor.getInputHandler();
        reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, lexicalHandler);
        reader.setContentHandler(contentHandler);
        reader.setErrorHandler(indexer);
    }

    void setDOMStreamer(DOMStreamer streamer) {
        this.streamer = streamer;
        if (triggersVisitor == null) {
            streamer.setContentHandler(indexer);
            streamer.setLexicalHandler(indexer);
        } else {
            streamer.setContentHandler(triggersVisitor.getInputHandler());
            streamer.setLexicalHandler(triggersVisitor.getLexicalInputHandler());
        }
    }

    public DOMStreamer getDOMStreamer() {
        return this.streamer;
    }

    public DocumentImpl getDocument() {
        return indexer.getDocument();
    }

    public CollectionConfiguration getCollectionConfig() {
        return collectionConfig;
    }
}
