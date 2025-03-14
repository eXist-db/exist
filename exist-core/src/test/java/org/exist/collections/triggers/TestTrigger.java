/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.collections.triggers;

import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Test trigger to check if trigger configuration is working properly.
 */
public class TestTrigger extends SAXTrigger implements DocumentTrigger {

    protected Logger LOG = LogManager.getLogger(getClass());
    
    private final static String TEMPLATE = "<?xml version=\"1.0\"?><events></events>";

    private DocumentImpl doc;

    public void configure(DBBroker broker, Txn transaction, org.exist.collections.Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, transaction, parent, parameters);
        XmldbURI docPath = XmldbURI.create("messages.xml");
        final boolean triggersEnabled = broker.isTriggersEnabled();
        try {
            this.doc = parent.getDocument(broker, docPath);
            if (this.doc == null) {
                LOG.debug("creating new file for collection contents");

                // IMPORTANT: temporarily disable triggers on the collection.
                // We would end up in infinite recursion if we don't do that
                broker.setTriggersEnabled(false);
                broker.storeDocument(transaction, docPath, new StringInputSource(TEMPLATE), MimeType.XML_TYPE, parent);
                this.doc = parent.getDocument(broker, docPath);
            }

        } catch (Exception e) {
            throw new TriggerException(e.getMessage(), e);
        } finally {
            // restore triggers enabled setting
            broker.setTriggersEnabled(triggersEnabled);
        }
    }

	private void addRecord(DBBroker broker, Txn transaction, String xupdate) throws TriggerException {
        MutableDocumentSet docs = new DefaultDocumentSet();
        docs.add(doc);
        final boolean triggersEnabled = broker.isTriggersEnabled();
        try {
            // IMPORTANT: temporarily disable triggers on the collection.
            // We would end up in infinite recursion if we don't do that
            broker.setTriggersEnabled(false);
            // create the XUpdate processor
            XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
            // process the XUpdate
            Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            for (Modification modification : modifications) {
                modification.process(transaction);
            }

            broker.flush();
        } catch (Exception e) {
            throw new TriggerException(e.getMessage(), e);
        } finally {
            // IMPORTANT: reenable trigger processing for the collection.
            broker.setTriggersEnabled(triggersEnabled);
        }

	}

	@Override
	public void beforeCreateDocument(DBBroker broker, Txn transaction, XmldbURI uri) throws TriggerException {
        String xupdate = "<?xml version=\"1.0\"?>" +
        "<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
        "   <xu:append select='/events'>" +
        "       <xu:element name='event'>" +
        "           <xu:attribute name='id'>STORE-DOCUMENT</xu:attribute>" +
        "           <xu:attribute name='collection'>" + doc.getCollection().getURI() + "</xu:attribute>" +
        "       </xu:element>" +
        "   </xu:append>" +
        "</xu:modifications>";

        addRecord(broker, transaction, xupdate);
	}

	@Override
	public void afterCreateDocument(DBBroker broker, Txn transaction, DocumentImpl document) {
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
        String xupdate = "<?xml version=\"1.0\"?>" +
        "<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
        "   <xu:append select='/events'>" +
        "       <xu:element name='event'>" +
        "           <xu:attribute name='id'>REMOVE-DOCUMENT</xu:attribute>" +
        "           <xu:attribute name='collection'>" + doc.getCollection().getURI() + "</xu:attribute>" +
        "       </xu:element>" +
        "   </xu:append>" +
        "</xu:modifications>";
        
        addRecord(broker, transaction, xupdate);
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
}
