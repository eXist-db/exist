/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2016 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xmldb.txn.bridge;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.*;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Service;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Avoids overlapping transactions on Collections
 * when the XML:DB Local API executes XQuery that then
 * calls the XMLDB XQuery Module which then tries
 * to use the XML:DB Local API
 *
 * @author Adam Retter
 */
public class InTxnLocalCollection extends LocalCollection {
    public InTxnLocalCollection(final Subject user, final BrokerPool brokerPool, final LocalCollection parent, final XmldbURI name) throws XMLDBException {
        super(user, brokerPool, parent, name);
    }

    @Override
    protected <R> R withDb(final LocalXmldbFunction<R> dbOperation) throws XMLDBException {
        return withDb(brokerPool, user, dbOperation);
    }

    static <R> R withDb(final BrokerPool brokerPool, final Subject user, final LocalXmldbFunction<R> dbOperation) throws XMLDBException {
        try (final DBBroker broker = brokerPool.get(Optional.of(user));
             final Txn transaction = broker.continueOrBeginTransaction()) {
            final R result = dbOperation.apply(broker, transaction);
            transaction.commit();
            return result;
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }

    @Override
    public Service getService(final String name, final String version) throws XMLDBException {
        final Service service;
        switch(name) {
            case "XPathQueryService":
            case "XQueryService":
                service = new InTxnLocalXPathQueryService(user, brokerPool, this);
                break;

            case "CollectionManagementService":
            case "CollectionManager":
                service = new InTxnLocalCollectionManagementService(user, brokerPool, this);
                break;

            case "UserManagementService":
                service = new InTxnLocalUserManagementService(user, brokerPool, this);
                break;

            case "DatabaseInstanceManager":
                service = new LocalDatabaseInstanceManager(user, brokerPool);
                break;

            case "XUpdateQueryService":
                service = new InTxnLocalXUpdateQueryService(user, brokerPool, this);
                break;

            case "IndexQueryService":
                service = new InTxnLocalIndexQueryService(user, brokerPool, this);
                break;

            default:
                throw new XMLDBException(ErrorCodes.NO_SUCH_SERVICE);
        }
        return service;
    }

    @Override
    public Service[] getServices() throws XMLDBException {
        final Service[] services = {
                new InTxnLocalXPathQueryService(user, brokerPool, this),
                new InTxnLocalCollectionManagementService(user, brokerPool, this),
                new InTxnLocalUserManagementService(user, brokerPool, this),
                new LocalDatabaseInstanceManager(user, brokerPool),
                new InTxnLocalXUpdateQueryService(user, brokerPool, this),
                new InTxnLocalIndexQueryService(user, brokerPool, this)
        };
        return services;
    }

    @Override
    public org.xmldb.api.base.Collection getParentCollection() throws XMLDBException {
        if(getName().equals(XmldbURI.ROOT_COLLECTION)) {
            return null;
        }

        if(collection == null) {
            final XmldbURI parentUri = this.<XmldbURI>read().apply((collection, broker, transaction) -> collection.getParentURI());
            this.collection = new InTxnLocalCollection(user, brokerPool, null, parentUri);
        }
        return collection;
    }

    @Override
    public org.xmldb.api.base.Collection getChildCollection(final String name) throws XMLDBException {

        final XmldbURI childURI;
        try {
            childURI = XmldbURI.xmldbUriFor(name);
        } catch(final URISyntaxException e) {
            throw new XMLDBException(ErrorCodes.INVALID_URI,e);
        }

        final XmldbURI nameUri = this.<XmldbURI>read().apply((collection, broker, transaction) -> {
            XmldbURI childName = null;
            if (collection.hasChildCollection(broker, childURI)) {
                childName = getPathURI().append(childURI);
            }
            return childName;
        });

        if(nameUri != null) {
            return new InTxnLocalCollection(user, brokerPool, this, nameUri);
        } else {
            return null;
        }
    }
}
