/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-2014 The eXist Project
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
package org.exist.collections;

import org.exist.Indexer;
import org.exist.Namespaces;
import org.exist.collections.triggers.DocumentTriggers;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
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
    private DocumentTriggers docTriggers;
    private boolean creating = false;
    private Permission oldDocPermissions = null;
    private CollectionConfiguration collectionConfig;

    IndexInfo(Indexer indexer, CollectionConfiguration collectionConfig) {
        this.indexer = indexer;
        this.collectionConfig = collectionConfig;
    }

    public Indexer getIndexer() {
        return indexer;
    }

    //XXX: make protected
    public void setTriggers(DocumentTriggers triggersVisitor) {
        this.docTriggers = triggersVisitor;
    }

    //XXX: make protected
    public DocumentTriggers getTriggers() {
        return docTriggers;
    }

    public void setCreating(boolean creating) {
        this.creating = creating;
    }

    public boolean isCreating() {
        return creating;
    }
    
    public void setOldDocPermissions(final Permission oldDocPermissions) {
        this.oldDocPermissions = oldDocPermissions;
    }
    
    public Permission getOldDocPermissions() {
        return oldDocPermissions;
    }

    void setReader(XMLReader reader, EntityResolver entityResolver) throws SAXException {
        if(entityResolver != null) {
            reader.setEntityResolver(entityResolver);
        }
        final LexicalHandler lexicalHandler = docTriggers == null ? indexer : docTriggers;
        final ContentHandler contentHandler = docTriggers == null ? indexer : docTriggers;
        reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, lexicalHandler);
        reader.setContentHandler(contentHandler);
        reader.setErrorHandler(indexer);
    }

    void setDOMStreamer(DOMStreamer streamer) {
        this.streamer = streamer;
        if (docTriggers == null) {
            streamer.setContentHandler(indexer);
            streamer.setLexicalHandler(indexer);
        } else {
            streamer.setContentHandler(docTriggers);
            streamer.setLexicalHandler(docTriggers);
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
