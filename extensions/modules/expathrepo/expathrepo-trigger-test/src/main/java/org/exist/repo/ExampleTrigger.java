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
package org.exist.repo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.SAXTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;

public class ExampleTrigger extends SAXTrigger implements DocumentTrigger, CollectionTrigger {

    private final static Logger LOG = LogManager.getLogger(ExampleTrigger.class);

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterCreateCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI newUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn txn, Collection collection, XmldbURI oldUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn txn, Collection collection) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        LOG.info("Create document {}", uri.toString());
    }

    @Override
    public void afterCreateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {


        if (document.getFileURI().toString().contains("copied")) {
            LOG.info("Prevent recreation of document {}", document.getFileURI().toString());
            return;
        }

        LOG.info("Created document {}", document.getDocumentURI());


        final byte[] data = "<a>dummy data</a>".getBytes();
        final XmldbURI newDocumentURI = XmldbURI.create(document.getFileURI().toString() + "-copied.xml");


        try (final Collection collection = broker.openCollection(document.getCollection().getURI(), Lock.LockMode.WRITE_LOCK)) {

            // Stream into database
            broker.storeDocument(txn, newDocumentURI, new StringInputSource(data), MimeType.XML_TYPE, collection);

        } catch (Exception e) {
            LOG.error(e);
            throw new TriggerException(e);
        }


    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        LOG.info("Update document {}", document.getDocumentURI());
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        LOG.info("Updated document {}", document.getDocumentURI());
    }

    @Override
    public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI newUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn txn, DocumentImpl document, XmldbURI oldUri) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        LOG.debug("Not implemented");
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn txn, XmldbURI uri) throws TriggerException {
        LOG.debug("Not implemented");
    }
}
