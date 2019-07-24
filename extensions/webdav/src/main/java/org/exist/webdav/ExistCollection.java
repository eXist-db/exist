/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.webdav;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.io.*;
import org.exist.webdav.exceptions.CollectionDoesNotExistException;
import org.exist.webdav.exceptions.CollectionExistsException;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class for accessing the Collection class of the exist-db native API.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistCollection extends ExistResource {

    /**
     * Constructor.
     *
     * @param uri  URI of document
     * @param pool Reference to brokerpool
     */
    public ExistCollection(XmldbURI uri, BrokerPool pool) {

        if (LOG.isTraceEnabled()) {
            LOG.trace(String.format("New collection object for %s", uri));
        }

        brokerPool = pool;
        this.xmldbUri = uri;
    }

    /**
     * Initialize Collection, authenticate() is required first
     */
    @Override
    public void initMetadata() {

        if (subject == null) {
            LOG.error("User not initialized yet");
            return;
        }

        // check if initialization is required
        if (isInitialized) {
            LOG.debug("Already initialized");
            return;
        }

        try (final DBBroker broker = brokerPool.get(Optional.of(subject));
                final Collection collection = broker.openCollection(xmldbUri, LockMode.READ_LOCK)) {

            if (collection == null) {
                LOG.error(String.format("Collection for %s cannot be opened for metadata", xmldbUri));
                return;
            }

            // Retrieve some meta data
            permissions = collection.getPermissionsNoLock();
            readAllowed = permissions.validate(subject, Permission.READ);
            writeAllowed = permissions.validate(subject, Permission.WRITE);
            executeAllowed = permissions.validate(subject, Permission.EXECUTE);

            creationTime = collection.getCreationTime();
            lastModified = creationTime; // Collection does not have more information.

            ownerUser = permissions.getOwner().getUsername();
            ownerGroup = permissions.getGroup().getName();

        } catch (final PermissionDeniedException | EXistException pde) {
            LOG.error(pde);
        }

        // Set flag
        isInitialized = true;
    }

    /**
     * Retrieve full URIs of all Collections in this collection.
     *
     * @return All collections URIs in the current collection.
     */
    public List<XmldbURI> getCollectionURIs() {
        final List<XmldbURI> collectionURIs = new ArrayList<>();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
            final Collection collection = broker.openCollection(xmldbUri, LockMode.READ_LOCK)) {
            // Get all collections
            final Iterator<XmldbURI> collections = collection.collectionIteratorNoLock(broker); // QQ: use collectionIterator ?
            while (collections.hasNext()) {
                collectionURIs.add(xmldbUri.append(collections.next()));

            }
        } catch (final EXistException | PermissionDeniedException e) {
            LOG.error(e);
            //return empty list
            return new ArrayList<>();
        }

        return collectionURIs;
    }

    /**
     * Retrieve full URIs of all Documents in the collection.
     *
     * @return  All document URIs in the current collection.
     */
    public List<XmldbURI> getDocumentURIs() {
        final List<XmldbURI> documentURIs = new ArrayList<>();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
                final Collection collection = broker.openCollection(xmldbUri, LockMode.READ_LOCK)) {

            // Get all documents
            final Iterator<DocumentImpl> documents = collection.iteratorNoLock(broker); // QQ: use 'iterator'
            while (documents.hasNext()) {
                documentURIs.add(documents.next().getURI());
            }
        } catch (final PermissionDeniedException | EXistException e) {
            LOG.error(e);
            //return empty list
            return new ArrayList<>();
        }

        return documentURIs;
    }

    /*
     * Delete document or collection.
     */
    void delete() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Deleting '%s'", xmldbUri));
        }

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
            final Txn txn = txnManager.beginTransaction();
            final Collection collection = broker.openCollection(xmldbUri, LockMode.WRITE_LOCK)) {

            // Open collection if possible, else abort
            if (collection == null) {
                txnManager.abort(txn);
                return;
            }

            // Remove collection
            broker.removeCollection(txn, collection);

            // Commit change
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Document deleted sucessfully");
            }
        } catch (EXistException | IOException | PermissionDeniedException | TriggerException e) {
            LOG.error(e);
        } finally {

            if(LOG.isDebugEnabled()) {
                LOG.debug("Finished delete");
            }
        }
    }

    public XmldbURI createCollection(String name) throws PermissionDeniedException, CollectionExistsException, EXistException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Create  '%s' in '%s'", name, xmldbUri));
        }

        XmldbURI newCollection = xmldbUri.append(name);

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
            final Txn txn = txnManager.beginTransaction();
            final Collection collection = broker.openCollection(newCollection, LockMode.WRITE_LOCK)) {

            // Check if collection exists. not likely to happen since availability is
            // checked by ResourceFactory

            if (collection != null) {
                final String msg = "Collection already exists";

                LOG.debug(msg);

                //XXX: double "abort" is bad thing!!!
                txnManager.abort(txn);
                throw new CollectionExistsException(msg);
            }

            // Create collection
            try (final Collection created = broker.getOrCreateCollection(txn, newCollection)) {
                broker.saveCollection(txn, created);
                broker.flush();

                // Commit change
                txnManager.commit(txn);

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Collection created sucessfully");
                }
            }
        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            throw e;

        } catch (Throwable e) {
            LOG.error(e);
            throw new EXistException(e);

        } finally {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Finished creation");
            }
        }

        return newCollection;
    }

    public XmldbURI createFile(String newName, InputStream is, Long length, String contentType)
            throws IOException, PermissionDeniedException, CollectionDoesNotExistException {

        if (LOG.isDebugEnabled())
            LOG.debug(String.format("Create '%s' in '%s'", newName, xmldbUri));

        XmldbURI newNameUri = XmldbURI.create(newName);

        // Get mime, or NULL when not available
        MimeType mime = MimeTable.getInstance().getContentTypeFor(newName);
        if (mime == null) {
            mime = MimeType.BINARY_TYPE;
        }

        // To support LockNullResource, a 0-byte XML document can be received. Since 0-byte
        // XML documents are not supported a small file will be created.

        if(mime.isXMLType() && length == 0) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Creating dummy XML file for null resource lock '%s'", newNameUri));
            }

            is = new FastByteArrayInputStream("<null_resource/>".getBytes(UTF_8));
        }

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
            final Txn txn = txnManager.beginTransaction();
            final Collection collection = broker.openCollection(xmldbUri, LockMode.WRITE_LOCK)) {

            // Check if collection exists. not likely to happen since availability is checked
            // by ResourceFactory
            if (collection == null) {
                LOG.debug(String.format("Collection %s does not exist", xmldbUri));
                txnManager.abort(txn);
                throw new CollectionDoesNotExistException(xmldbUri + "");
            }


            try(final FilterInputStreamCache cache = FilterInputStreamCacheFactory.getCacheInstance(() -> (String) brokerPool.getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY), is);
                    final InputStream cfis = new CachingFilterInputStream(cache)) {
                if (mime.isXMLType()) {
                    if (LOG.isDebugEnabled())
                        LOG.debug(String.format("Inserting XML document '%s'", mime.getName()));

                    // Stream into database
                    cfis.mark(Integer.MAX_VALUE);
                    final IndexInfo info = collection.validateXMLResource(txn, broker, newNameUri, new InputSource(cfis));
                    final DocumentImpl doc = info.getDocument();
                    doc.getMetadata().setMimeType(mime.getName());
                    cfis.reset();
                    collection.store(txn, broker, info, new InputSource(cfis));
                } else {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug(String.format("Inserting BINARY document '%s'", mime.getName()));
                    }

                    // Stream into database
                    collection.addBinaryResource(txn, broker, newNameUri, cfis, mime.getName(), length);
                }
            }

            // Commit change
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Document created sucessfully");
            }


        } catch (EXistException | SAXException e) {
            LOG.error(e);
            throw new IOException(e);

        } catch (LockException e) {
            LOG.error(e);
            throw new PermissionDeniedException(xmldbUri + "");

        } catch (IOException | PermissionDeniedException e) {
            LOG.error(e);
            throw e;

        } finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished creation");
            }
        }

        // Send the result back to the client
        XmldbURI newResource = xmldbUri.append(newName);

        return newResource;
    }

    void resourceCopyMove(XmldbURI destCollectionUri, String newName, Mode mode) throws EXistException {

        if (LOG.isDebugEnabled())
            LOG.debug(String.format("%s '%s' to '%s' named '%s'", mode, xmldbUri, destCollectionUri, newName));

        XmldbURI newNameUri = null;
        try {
            newNameUri = XmldbURI.xmldbUriFor(newName);

        } catch (URISyntaxException ex) {
            LOG.error(ex);
            throw new EXistException(ex.getMessage());
        }

        // This class contains already the URI of the resource that shall be moved/copied
	    XmldbURI srcCollectionUri = xmldbUri;
        // use WRITE_LOCK if moving or if src and dest collection are the same
        final LockMode srcCollectionLockMode = mode == Mode.MOVE
                || destCollectionUri.equals(srcCollectionUri) ? LockMode.WRITE_LOCK : LockMode.READ_LOCK;

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try (final DBBroker broker = brokerPool.get(Optional.ofNullable(subject));
                final Txn txn = txnManager.beginTransaction();
                final Collection srcCollection = broker.openCollection(srcCollectionUri, srcCollectionLockMode)) {

            // Open collection if possible, else abort
            if (srcCollection == null) {
                txnManager.abort(txn);
                return; // TODO throw
            }

            // Open collection if possible, else abort
            try(final Collection destCollection = broker.openCollection(destCollectionUri, LockMode.WRITE_LOCK)) {
                if (destCollection == null) {
                    LOG.debug(String.format("Destination collection %s does not exist.", xmldbUri));
                    txnManager.abort(txn);
                    return; // TODO throw?
                }

                // Perform actial move/copy
                if (mode == Mode.COPY) {
                    broker.copyCollection(txn, srcCollection, destCollection, newNameUri);

                } else {
                    broker.moveCollection(txn, srcCollection, destCollection, newNameUri);
                }

                // Commit change
                txnManager.commit(txn);

                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Collection %sd successfully", mode));
                }
            }

        } catch (LockException e) {
            LOG.error("Resource is locked.", e);
            throw new EXistException(e.getMessage());
        } catch (EXistException e) {
            LOG.error(e);
            throw e;
        } catch (IOException | PermissionDeniedException | TriggerException e) {
            LOG.error(e);
            throw new EXistException(e.getMessage());
        } finally {
            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Finished %s", mode));
            }
        }
    }
}
