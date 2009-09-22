/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.collections;

import org.exist.Indexer;
import org.exist.Namespaces;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.TriggerException;
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
	private DocumentTrigger trigger;
    private int event;
	private CollectionConfiguration collectionConfig;
    
    IndexInfo(Indexer indexer, CollectionConfiguration collectionConfig) {
		this.indexer = indexer;
        this.collectionConfig = collectionConfig;
    }
	
	public Indexer getIndexer() {
		return indexer;
	}
    
	int getEvent() {
		return event;
	}
	
	void setReader(XMLReader reader, EntityResolver entityResolver) throws SAXException {
		if(entityResolver != null)
			reader.setEntityResolver(entityResolver);
		LexicalHandler lexicalHandler = trigger == null ? indexer : trigger.getLexicalInputHandler();
		ContentHandler contentHandler = trigger == null ? indexer : trigger.getInputHandler();
		reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, lexicalHandler);
		reader.setContentHandler(contentHandler);
		reader.setErrorHandler(indexer);
	}
	
	void setDOMStreamer(DOMStreamer streamer) {
		this.streamer = streamer;
		if (trigger == null) {
			streamer.setContentHandler(indexer);
			streamer.setLexicalHandler(indexer);
		} else {
			streamer.setContentHandler(trigger.getInputHandler());
			streamer.setLexicalHandler(trigger.getLexicalInputHandler());
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

    void setTrigger(DocumentTrigger trigger, int event) {
		this.trigger = trigger;
		this.event = event;
	}
	
	DocumentTrigger getTrigger() {
		return trigger;
	}
	
	void prepareTrigger(DBBroker broker, Txn transaction, XmldbURI docUri, DocumentImpl doc) throws TriggerException {
		if (trigger == null) return;
		trigger.setOutputHandler(indexer);
		trigger.setLexicalOutputHandler(indexer);
		trigger.setValidating(true);
		trigger.prepare(event, broker, transaction, docUri, doc);
	}
	
	void postValidateTrigger() {
		if (trigger == null) return;
		trigger.setValidating(false);
	}
	
	void finishTrigger(DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl doc) {
		if (trigger == null) return;
		trigger.finish(event, broker, transaction, documentPath, doc);
	}
}
