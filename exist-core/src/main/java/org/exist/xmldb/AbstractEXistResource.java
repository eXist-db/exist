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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.Permission;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import com.evolvedbinary.j8fu.function.FunctionE;
import com.evolvedbinary.j8fu.function.SupplierE;
import org.exist.xmldb.function.LocalXmldbDocumentFunction;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

import java.util.Date;

/**
 * Abstract base implementation of interface EXistResource.
 */
public abstract class AbstractEXistResource extends AbstractLocal implements EXistResource {
    protected final XmldbURI docId;
    private String mimeType = null;
    protected boolean isNewResource = false;

    protected Date datecreated = null;
    protected Date datemodified = null;
    private boolean closed;
    
    public AbstractEXistResource(final Subject user, final BrokerPool pool, final LocalCollection parent, final XmldbURI docId, final String mimeType) {
        super(user, pool, parent);
        this.docId = docId.lastSegment();
        this.mimeType = mimeType;
    }

    @Override
    public void setMimeType(final String mime) {
        this.mimeType = mime;
    }

    @Override
    public String getMimeType() throws XMLDBException {
        return getMimeType(() -> read((document, broker, transaction) -> document.getMetadata().getMimeType()));
    }

    /**
     * Similar to {@link org.exist.xmldb.EXistResource#getMimeType()}
     * but useful for operations within the XML:DB Local API
     * that are already working within a transaction
     */
    String getMimeType(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return getMimeType(() -> this.<String>read(broker, transaction).apply((document, broker1, transaction1) -> document.getMetadata().getMimeType()));
    }

    private String getMimeType(final SupplierE<String, XMLDBException> mimeTypeRead) throws XMLDBException {
        if (isNewResource) {
            return mimeType;
        } else {
            return mimeTypeRead.get();
        }
    }

    @Override
    public String getId() throws XMLDBException {
        return docId.toString();
    }

    @Override
    public Collection getParentCollection() throws XMLDBException {
        if (collection == null) {
            throw new XMLDBException(ErrorCodes.INVALID_COLLECTION, "collection parent is null");
        }
        return collection;
    }

    @Override
    public Date getCreationTime() throws XMLDBException {
        return read((document, broker, transaction) -> new Date(document.getMetadata().getCreated()));
    }

    @Override
    public Date getLastModificationTime() throws XMLDBException {
        return read((document, broker, transaction) -> new Date(document.getMetadata().getLastModified()));
    }

    @Override
    public void setLastModificationTime(final Date lastModificationTime) throws XMLDBException {
        if(lastModificationTime.before(getCreationTime())) {
            throw new XMLDBException(ErrorCodes.PERMISSION_DENIED, "Modification time must be after creation time.");
        }

        modify((document, broker, transaction) -> {
            if (document == null) {
                throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + docId + " not found");
            }

            document.getMetadata().setLastModified(lastModificationTime.getTime());
            return null;
        });
    }

    @Override
    public long getContentLength() throws XMLDBException {
        return read((document, broker, transaction) -> document.getContentLength());
    }

    @Override
    public Permission getPermissions() throws XMLDBException {
        return read((document, broker, transaction) -> document.getPermissions());
    }

    /**
     * Higher-order-function for performing read-only operations against this resource
     *
     * NOTE this read will occur using the database user set on the resource
     *
     * @param readOp The read-only operation to execute against the resource
     * @return The result of the read-only operation
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    protected <R> R read(final LocalXmldbDocumentFunction<R> readOp) throws XMLDBException {
        return withDb((broker, transaction) -> this.<R>read(broker, transaction).apply(readOp));
    }

    /**
     * Higher-order-function for performing read-only operations against this resource
     *
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @return A function to receive a read-only operation to perform against the resource
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst reading.
     */
    public <R> FunctionE<LocalXmldbDocumentFunction<R>, R, XMLDBException> read(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return with(LockMode.READ_LOCK, broker, transaction);
    }

    /**
     * Higher-order-function for performing read/write operations against this resource
     *
     * NOTE this operation will occur using the database user set on the resource
     *
     * @param op The read/write operation to execute against the resource
     * @return The result of the operation
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst modifying.
     */
    protected <R> R modify(final LocalXmldbDocumentFunction<R> op) throws XMLDBException {
        return withDb((broker, transaction) -> this.<R>modify(broker, transaction).apply(op));
    }

    /**
     * Higher-order-function for performing read/write operations against this resource
     *
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @return A function to receive an operation to perform against the resource
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs whilst modifying.
     */
    public <R> FunctionE<LocalXmldbDocumentFunction<R>, R, XMLDBException> modify(final DBBroker broker, final Txn transaction) throws XMLDBException {
        return writeOp -> this.<R>with(LockMode.WRITE_LOCK, broker, transaction).apply((document, broker1, transaction1) -> {
            final R result = writeOp.apply(document, broker1, transaction1);
            broker.storeXMLResource(transaction1, document);
            return result;
        });
    }

    /**
     * Higher-order function for performing lockable operations on this resource
     *
     * @param lockMode
     * @param broker The broker to use for the operation
     * @param transaction The transaction to use for the operation
     * @return A function to receive an operation to perform on the locked database resource
     *
     * @param <R> the return type.
     *
     * @throws XMLDBException if an error occurs during the operation.
     */
    private <R> FunctionE<LocalXmldbDocumentFunction<R>, R, XMLDBException> with(final LockMode lockMode, final DBBroker broker, final Txn transaction) throws XMLDBException {
        return documentOp ->
                collection.<R>with(lockMode, broker, transaction).apply((collection, broker1, transaction1) -> {
                    try(final LockedDocument lockedDoc = collection.getDocumentWithLock(broker1, docId, lockMode)) {

                        // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                        collection.close();

                        if(lockedDoc == null) {
                            throw new XMLDBException(ErrorCodes.INVALID_RESOURCE);
                        }
                        return documentOp.apply(lockedDoc.getDocument(), broker1, transaction1);
                    }
                });
    }

    @Override
    public final boolean isClosed() {
        return closed;
    }

    /**
     * Implement this in your sub-class if you need
     * to do cleanup.
     *
     * The method will only be called once, no matter
     * how many times the user calls {@link #close()}.
     *
     * @throws XMLDBException if an error occurs whilst closing.
     */
    protected void doClose() throws XMLDBException {
        //no-op
    }

    @Override
    public final void close() throws XMLDBException {
        if(!isClosed()) {
            try {
                doClose();
            } finally {
                closed = true;
            }
        }
    }

    @Override
    public final void freeResources() throws XMLDBException{
        close();
    }
}
