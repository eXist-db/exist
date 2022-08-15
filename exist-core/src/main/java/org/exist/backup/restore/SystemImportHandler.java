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
package org.exist.backup.restore;

import org.exist.Namespaces;
import org.exist.backup.BackupDescriptor;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * Handler for parsing __contents__.xml files during
 * restoration of a db backup
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class SystemImportHandler extends AbstractRestoreHandler {
    
    private final org.exist.backup.RestoreHandler rh;

    /**
     * @param broker the database broker
     * @param transaction the transaction to use for the entire restore,
     *                    or null if restoring each collection/resource
     *                    should occur in its own transaction
     * @param descriptor the backup descriptor to start restoring from
     * @param listener the listener to report restore events to
     */
    public SystemImportHandler(final DBBroker broker, @Nullable final Txn transaction, final BackupDescriptor descriptor,
            final RestoreListener listener) {
        super(broker, transaction, descriptor, listener, null);
        this.rh = broker.getDatabase().getPluginsManager().getRestoreHandler();
    }

    @Override
    public void startDocument() throws SAXException {
        super.startDocument();
        rh.startDocument();
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
        if (Namespaces.EXIST_NS.equals(namespaceURI) &&
                (COLLECTION_ELEMENT_NAME.equals(localName) ||
                        RESOURCE_ELEMENT_NAME.equals(localName) ||
                        SUBCOLLECTION_ELEMENT_NAME.equals(localName) ||
                        DELETED_ELEMENT_NAME.equals(localName) ||
                        ACE_ELEMENT_NAME.equals(localName))) {
            super.startElement(namespaceURI, localName, qName, atts);
        } else {
            rh.startElement(namespaceURI, localName, qName, atts);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        if (Namespaces.EXIST_NS.equals(namespaceURI) &&
                (COLLECTION_ELEMENT_NAME.equals(localName) ||
                        RESOURCE_ELEMENT_NAME.equals(localName) ||
                        SUBCOLLECTION_ELEMENT_NAME.equals(localName) ||
                        DELETED_ELEMENT_NAME.equals(localName) ||
                        ACE_ELEMENT_NAME.equals(localName))) {
            super.endElement(namespaceURI, localName, qName);
        } else {
            rh.endElement(namespaceURI, localName, qName);
        }
    }

    @Override
    protected AbstractRestoreHandler newSelf(final DBBroker broker, final @Nullable Txn transaction,
            final BackupDescriptor descriptor, final RestoreListener listener,
            @Nullable final Set<String> pathsToIgnore) {
        return new SystemImportHandler(broker, transaction, descriptor, listener);
    }

    @Override
    protected void notifyStartCollectionRestore(final XmldbURI collectionUri, final Attributes attributes) throws PermissionDeniedException {
        try (final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            rh.startCollectionRestore(collection, attributes);
        }
    }

    @Override
    protected void notifyEndCollectionRestore(final XmldbURI collectionUri) throws PermissionDeniedException {
        try (final Collection collection = broker.openCollection(collectionUri, Lock.LockMode.WRITE_LOCK)) {
            rh.endCollectionRestore(collection);
        }
    }

    @Override
    protected void notifyStartDocumentRestore(final XmldbURI documentUri, final Attributes attributes) throws PermissionDeniedException {
        try (final LockedDocument lockedDocument = broker.getXMLResource(documentUri, Lock.LockMode.WRITE_LOCK)) {
            rh.startDocumentRestore(lockedDocument.getDocument(), attributes);
        }
    }

    @Override
    protected void notifyEndDocumentRestore(final XmldbURI documentUri) throws PermissionDeniedException {
        try (final LockedDocument lockedDocument = broker.getXMLResource(documentUri, Lock.LockMode.WRITE_LOCK)) {
            rh.endDocumentRestore(lockedDocument.getDocument());
        }
    }
}
