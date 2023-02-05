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
package org.exist.xmldb.txn.bridge;

import org.exist.EXistException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.*;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.ErrorCodes;
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
