/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmldb;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.function.FunctionE;
import org.exist.xmldb.function.LocalXmldbCollectionFunction;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * Base class for Local XMLDB classes
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public abstract class AbstractLocal {
    protected final BrokerPool brokerPool;
    protected final Subject user;
    protected LocalCollection collection;

    AbstractLocal(final Subject user, final BrokerPool brokerPool, final LocalCollection collection) {
        this.user = user;
        this.brokerPool = brokerPool;
        this.collection = collection;
    }

    protected XmldbURI resolve(final XmldbURI name) {
        if (collection != null) {
            return collection.getPathURI().resolveCollectionPath(name);
        } else {
            return name;
        }
    }

    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @return A function to receive a read-only operation to perform against the collection
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final XmldbURI collectionUri) throws XMLDBException {
        return readOp -> withDb((broker, transaction) -> this.<R>read(broker, transaction, collectionUri).apply(readOp));
    }
    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param broker The database broker to use when accessing the collection
     * @param transaction The transaction to use when accessing the collection
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @return A function to receive a read-only operation to perform against the collection
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return this.<R>with(Lock.READ_LOCK, broker, transaction, collectionUri);
    }

    /**
     * Higher-order-function for performing read/write operations against a database collection
     *
     * @param collectionUri The uri of the collection to perform read/write operations on
     * @return A function to receive a read/write operation to perform against the collection
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> modify(final XmldbURI collectionUri) throws XMLDBException {
        return modifyOp -> withDb((broker, transaction) -> this.<R>modify(broker, transaction, collectionUri).apply(modifyOp));
    }

    /**
     * Higher-order-function for performing read/write operations against a database collection
     *
     * @param broker The database broker to use when accessing the collection
     * @param transaction The transaction to use when accessing the collection
     * @param collectionUri The uri of the collection to perform read/write operations on
     * @return A function to receive a read/write operation to perform against the collection
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> modify(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return this.<R>with(Lock.WRITE_LOCK, broker, transaction, collectionUri);
    }

    /**
     * Higher-order function for performing lockable operations on a collection
     *
     * @param lockMode
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @return A function to receive an operation to perform on the locked database collection
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> with(final int lockMode, final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return collectionOp -> {
            org.exist.collections.Collection coll = null;
            try {
                coll = broker.openCollection(collectionUri, lockMode);
                if (coll == null) {
                    throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "Collection " + collectionUri.toString() + " not found");
                }

                final R result = collectionOp.apply(coll, broker, transaction);
                return result;
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            } finally {
                if (coll != null) {
                    coll.release(lockMode);
                }
            }
        };
    }

    /**
     * Higher-order-function for performing an XMLDB operation on
     * the database
     *
     * @param dbOperation The operation to perform on the database
     * @param <R>         The return type of the operation
     * @throws org.xmldb.api.base.XMLDBException
     */
    <R> R withDb(final LocalXmldbFunction<R> dbOperation) throws XMLDBException {
        try (final DBBroker broker = brokerPool.get(user);
             final Txn transaction = brokerPool.getTransactionManager().beginTransaction()) {
            final R result = dbOperation.apply(broker, transaction);
            transaction.commit();
            return result;
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
}
