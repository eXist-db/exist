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

import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
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

    public void configure(DBBroker broker, org.exist.collections.Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, parent, parameters);
        XmldbURI docPath = XmldbURI.create("messages.xml");
        TransactionManager transactMgr = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transactMgr.beginTransaction();
        try {
            this.doc = parent.getDocument(broker, docPath);
            if (this.doc == null) {
                LOG.debug("creating new file for collection contents");

                // IMPORTANT: temporarily disable triggers on the collection.
                // We would end up in infinite recursion if we don't do that
                parent.setTriggersEnabled(false);
                IndexInfo info = parent.validateXMLResource(transaction, broker, docPath, TEMPLATE);
                //TODO : unlock the collection here ?
                parent.store(transaction, broker, info, TEMPLATE);
                this.doc = info.getDocument();
            }
            transactMgr.commit(transaction);

        } catch (Exception e) {
            transactMgr.abort(transaction);
            throw new TriggerException(e.getMessage(), e);
        } finally {
            transaction.close();
            parent.setTriggersEnabled(true);
        }
    }

	private void addRecord(DBBroker broker, String xupdate) throws TriggerException {
        MutableDocumentSet docs = new DefaultDocumentSet();
        docs.add(doc);
        TransactionManager transactMgr = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transactMgr.beginTransaction();
        try {
            // IMPORTANT: temporarily disable triggers on the collection.
            // We would end up in infinite recursion if we don't do that
            getCollection().setTriggersEnabled(false);
            // create the XUpdate processor
            XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
            // process the XUpdate
            Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            for (int i = 0; i < modifications.length; i++) {
                modifications[i].process(transaction);
            }
            transactMgr.commit(transaction);

            broker.flush();
        } catch (Exception e) {
            transactMgr.abort(transaction);
            e.printStackTrace();
            throw new TriggerException(e.getMessage(), e);
        } finally {
            transaction.close();
            // IMPORTANT: reenable trigger processing for the collection.
            getCollection().setTriggersEnabled(true);
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

        addRecord(broker, xupdate);
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
        
        addRecord(broker, xupdate);
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
