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

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateTimeValue;

/**
 * This collection trigger will save all old versions of documents before
 * they are overwritten or removed. The old versions are kept in the
 * 'history root' which is by default '<code>/db/history</code>', but can be 
 * changed with the parameter '<code>root</code>'.
 * You need to configure this trigger for every collection whose history you
 * want to preserve, by modifying '<code>collection.xconf</code>' such that it
 * resembles this:
 *
 * <pre>
 *   &lt;?xml version='1.0'?&gt;
 *   &lt;collection xmlns='http://exist-db.org/collection-config/1.0'&gt;
 *     &lt;triggers&gt;
 *       &lt;trigger 
 *         event='update'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       /&gt;
 *       &lt;trigger
 *         event='remove'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       /&gt;
 *     &lt;/triggers&gt;
 *   &lt;/collection&gt;
 * </pre>
 *
 * @author Mark Spanbroek
 * @see org.exist.collections.triggers.Trigger
 */
public class HistoryTrigger extends FilteringTrigger implements DocumentTrigger {

    public static final String PARAM_ROOT_NAME = "root";
    public static final XmldbURI DEFAULT_ROOT_PATH = XmldbURI.ROOT_COLLECTION_URI.append("history");

    private XmldbURI rootPath = DEFAULT_ROOT_PATH;

    @Override
    public void configure(final DBBroker broker, final Txn transaction, final Collection parent,
            final Map<String, List<?>> parameters) throws TriggerException {

        super.configure(broker, transaction, parent, parameters);

        if(parameters.containsKey(PARAM_ROOT_NAME)) {
            try {
                rootPath = XmldbURI.xmldbUriFor(parameters.get(PARAM_ROOT_NAME).getFirst().toString());
            } catch(final URISyntaxException e) {
                throw new TriggerException(e);
            }
        }
    }

    private void makeCopy(final DBBroker broker, final Txn txn, final DocumentImpl doc)
            throws TriggerException {
        if (doc == null) {
            return;
        }

        // construct the destination path
        final XmldbURI path = rootPath.append(doc.getURI());
        try {
            //construct the destination document name
            String dtValue = new DateTimeValue(new Date(doc.getLastModified())).getStringValue();
            dtValue = dtValue.replaceAll(":", "-"); // multiple ':' are not allowed in URI so use '-'
            dtValue = dtValue.replaceAll("\\.", "-"); // as we are using '-' instead of ':' do the same for '.'
            final XmldbURI name = XmldbURI.create(dtValue);

            // create the destination document
            //TODO : how is the transaction handled ? It holds the locks ! 
            final Collection destination = broker.getOrCreateCollection(txn, path);
            broker.saveCollection(txn, destination);
            broker.copyResource(txn, doc, destination, name);
        } catch(final XPathException | IOException | PermissionDeniedException | LockException | EXistException xpe) {
            throw new TriggerException(xpe);
        }
    }

    @Override
    public void beforeCreateDocument(final DBBroker broker, final Txn txn,
            final XmldbURI uri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterCreateDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void beforeUpdateDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        makeCopy(broker, txn, document);
    }

    @Override
    public void afterUpdateDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void beforeCopyDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterCopyDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void beforeDeleteDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        makeCopy(broker, txn, document);
    }

    @Override
    public void beforeMoveDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        makeCopy(broker, txn, document);
    }

    @Override
    public void afterMoveDocument(final DBBroker broker, final Txn txn,
            final DocumentImpl document, final XmldbURI newUri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterDeleteDocument(final DBBroker broker, final Txn txn,
            final XmldbURI uri) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void beforeUpdateDocumentMetadata(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        //Nothing to do
    }

    @Override
    public void afterUpdateDocumentMetadata(final DBBroker broker, final Txn txn,
            final DocumentImpl document) throws TriggerException {
        //Nothing to do
    }
}
