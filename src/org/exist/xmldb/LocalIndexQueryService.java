/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmldb;

import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.sync.Sync;
import org.exist.util.LockException;
import org.exist.util.Occurrences;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;

public class LocalIndexQueryService extends AbstractLocalService implements IndexQueryService {

    public LocalIndexQueryService(final Subject user, final BrokerPool pool, final LocalCollection parent) {
        super(user, pool, parent);
    }

    @Override
    public String getName() throws XMLDBException {
        return "IndexQueryService";
    }

    @Override
    public String getVersion() throws XMLDBException {
        return "1.0";
    }

    @Override
    public void reindexCollection() throws XMLDBException {
        reindexCollection(collection.getURI().toCollectionPathURI());
    }

    @Override
    public void reindexCollection(final String collectionPath) throws XMLDBException {
    	try {
            reindexCollection(XmldbURI.xmldbUriFor(collectionPath));
    	} catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
    	}
    }

    @Override
    public void reindexCollection(final XmldbURI col) throws XMLDBException {
        final XmldbURI collectionPath = resolve(col);
        read(collectionPath).apply((collection, broker, transaction) -> {
            try {
                broker.reindexCollection(transaction, collectionPath);
                broker.sync(Sync.MAJOR);
                return null;
            } catch(final LockException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e);
            }
        });
    }

    @Override
    public void reindexDocument(final String name) throws XMLDBException {
        reindexDocument(collection.getURI().toCollectionPathURI(), name);
    }

    private void reindexDocument(final XmldbURI col, final String docName) throws XMLDBException {
        final XmldbURI collectionPath = resolve(col);
        withDb((broker, transaction) -> {
            try(final LockedDocument lockedDoc = broker.getXMLResource(collectionPath.append(docName), LockMode.READ_LOCK)) {
                if(lockedDoc != null) {
                    broker.reindexXMLResource(transaction, lockedDoc.getDocument(), DBBroker.IndexMode.STORE);
                    broker.sync(Sync.MAJOR);
                }
                return null;
            }
        });
    }

    @Override
    public void configureCollection(final String configData) throws XMLDBException {
        modify(collection.getPathURI()).apply((collection, broker, transaction) -> {
            final CollectionConfigurationManager mgr = brokerPool.getConfigurationManager();
            try {
                mgr.addConfiguration(transaction, broker, collection, configData);
                return null;
            } catch (final CollectionConfigurationException e) {
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
            }
        });
    }
	
    @Override
    public Occurrences[] getIndexedElements(final boolean inclusive) throws XMLDBException {
    	return this.<Occurrences[]>read(collection.getPathURI()).apply((collection, broker, transaction) -> broker.getElementIndex().scanIndexedElements(collection, inclusive));
    }

    @Override
    public String getProperty(final String name) throws XMLDBException {
        return null;
    }

    @Override
    public void setProperty(final String name, final String value) throws XMLDBException {
    }
}
