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
package org.exist.xmldb;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.FunctionE;
import org.exist.xmldb.function.LocalXmldbCollectionFunction;
import org.exist.xmldb.function.LocalXmldbFunction;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.util.Optional;
import java.util.function.Function;

/**
 * Base class for Local XMLDB classes
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class AbstractLocal {

    public final static String PROP_JOIN_TRANSACTION_IF_PRESENT = "exist.api.xmldb.local.join-transaction-if-present";

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


    protected XmldbURI getCollectionUri(final DBBroker broker, final Txn transaction, final Collection collection) throws XMLDBException {
        final String name;
        if(collection instanceof LocalCollection) {
            name = ((LocalCollection)collection).getName(broker, transaction);
        } else {
            name = collection.getName();
        }
        return XmldbURI.create(name);
    }

    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final XmldbURI collectionUri) throws XMLDBException {
        return readOp -> withDb((broker, transaction) -> this.<R>read(broker, transaction, collectionUri).apply(readOp));
    }

    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @param errorCode The error code to use in the XMLDBException if the collection does not exist, see {@link ErrorCodes}
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final XmldbURI collectionUri, final int errorCode) throws XMLDBException {
        return readOp -> withDb((broker, transaction) -> this.<R>read(broker, transaction, collectionUri, errorCode).apply(readOp));
    }

    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param broker The database broker to use when accessing the collection
     * @param transaction The transaction to use when accessing the collection
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return this.<R>with(LockMode.READ_LOCK, broker, transaction, collectionUri);
    }

    /**
     * Higher-order-function for performing read-only operations against a database collection
     *
     * @param broker The database broker to use when accessing the collection
     * @param transaction The transaction to use when accessing the collection
     * @param collectionUri The uri of the collection to perform read-only operations on
     * @param errorCode The error code to use in the XMLDBException if the collection does not exist, see {@link ErrorCodes}
     * @return A function to receive a read-only operation to perform against the collection
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri, final int errorCode) throws XMLDBException {
        return this.<R>with(LockMode.READ_LOCK, broker, transaction, collectionUri, errorCode);
    }

    /**
     * Higher-order-function for performing read/write operations against a database collection
     *
     * @param collectionUri The uri of the collection to perform read/write operations on
     * @return A function to receive a read/write operation to perform against the collection
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst modifying.
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
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst modifying.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> modify(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return this.<R>with(LockMode.WRITE_LOCK, broker, transaction, collectionUri);
    }

    /**
     * Higher-order function for performing lockable operations on a collection
     *
     * @param lockMode the lock mode
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param collectionUri the URI of the collection.
     *
     * @return A function to receive an operation to perform on the locked database collection
     *
     * @throws XMLDBException if the collection does not exist or the caller does not have permission to open
     * the collection. The error code of the XMLDBException will be either {@link ErrorCodes#INVALID_COLLECTION}
     * if the collection does not exist, or {@link ErrorCodes#PERMISSION_DENIED} if the caller does not have
     * permission to open the collection.
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs during the operation.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> with(final LockMode lockMode, final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws XMLDBException {
        return with(lockMode, broker, transaction, collectionUri, ErrorCodes.INVALID_COLLECTION);
    }

    /**
     * Higher-order function for performing lockable operations on a collection
     *
     * @param lockMode the lock mode
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @param collectionUri the URI of the collection.
     * @param errorCode The error code to use in the XMLDBException if the collection does not exist, see {@link ErrorCodes}
     *
     * @return A function to receive an operation to perform on the locked database collection
     *
     * @throws XMLDBException if the collection does not exist or the caller does not have permission to open
     * the collection. The error code of the XMLDBException will be either taken from the `errorCode` param
     * or set to {@link ErrorCodes#PERMISSION_DENIED}
     *
     * @param <R> the return type.
     */
    protected <R> FunctionE<LocalXmldbCollectionFunction<R>, R, XMLDBException> with(final LockMode lockMode, final DBBroker broker, final Txn transaction, final XmldbURI collectionUri, final int errorCode) throws XMLDBException {
        return collectionOp -> {
            try(org.exist.collections.Collection coll = broker.openCollection(collectionUri, lockMode)) {
                if (coll == null) {
                    throw new XMLDBException(errorCode, "Collection " + collectionUri.toString() + " not found");
                }

                final R result = collectionOp.apply(coll, broker, transaction);
                return result;
            } catch (final PermissionDeniedException e) {
                throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, e.getMessage(), e);
            }
        };
    }

    /**
     * Either begins a new transaction (default) or
     * attempts to join an existing transaction.
     *
     * If there is no existing transaction, a new transaction
     * will begin.
     *
     * Controlled by the System Property {@link AbstractLocal#PROP_JOIN_TRANSACTION_IF_PRESENT }
     *
     * @return A transaction
     *
     * @deprecated This function will be removed when {@link DBBroker#continueOrBeginTransaction()} is removed
     */
    @Deprecated
    private static Function<DBBroker, Txn> transaction() {
        final boolean joinTransactionIfPresent = System.getProperty(PROP_JOIN_TRANSACTION_IF_PRESENT, "true")
                .equalsIgnoreCase("true");
        if(joinTransactionIfPresent) {
            return DBBroker::continueOrBeginTransaction;
        } else {
            return (broker) -> broker.getBrokerPool().getTransactionManager().beginTransaction();
        }
    }

    /**
     * Higher-order-function for performing an XMLDB operation on
     * the database
     *
     * @param dbOperation The operation to perform on the database
     * @param <R> The return type of the operation
     *
     * @return the result of the operation
     *
     * @throws XMLDBException if an error occurs when executing the operation.
     */
    protected <R> R withDb(final LocalXmldbFunction<R> dbOperation) throws XMLDBException {
        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(user));
             final Txn transaction = transaction().apply(broker)) {
            try {
                final R result = dbOperation.apply(broker, transaction);
                transaction.commit();
                return result;
            } catch (final XMLDBException | EXistException e) {
                transaction.abort();
                throw e;
            }
        } catch (final EXistException e) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, e.getMessage(), e);
        }
    }
}
