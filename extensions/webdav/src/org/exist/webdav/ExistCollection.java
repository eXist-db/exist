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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.io.IOUtils;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.util.VirtualTempFileInputSource;

import org.exist.webdav.exceptions.CollectionDoesNotExistException;
import org.exist.webdav.exceptions.CollectionExistsException;
import org.exist.xmldb.XmldbURI;

import org.xml.sax.SAXException;

/**
 * Class for accessing the Collection class of the exist-db native API.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistCollection extends ExistResource {
	
    /**
     *  Constructor.
     * 
     * @param uri   URI of document
     * @param pool  Reference to brokerpool
     */
    public ExistCollection(XmldbURI uri, BrokerPool pool) {

        if(LOG.isTraceEnabled()) {
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

        DBBroker broker = null;
        Collection collection = null;
        try {
            // Get access to collection
            broker = brokerPool.get(subject);
            collection = broker.openCollection(xmldbUri, Lock.READ_LOCK);

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

        } catch (PermissionDeniedException | EXistException pde) {
            LOG.error(pde);

        } finally {

            // Clean up collection
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }

            // Return broker
            brokerPool.release(broker);

            // Set flag
            isInitialized = true;
        }
    }

    /**
     * Retrieve full URIs of all Collections in this collection.
     */
    public List<XmldbURI> getCollectionURIs() {


        List<XmldbURI> collectionURIs = new ArrayList<>();

        DBBroker broker = null;
        Collection collection = null;
        try {
            // Try to read as specified subject
            broker = brokerPool.get(subject);
            collection = broker.openCollection(xmldbUri, Lock.READ_LOCK);

            // Get all collections
            Iterator<XmldbURI> collections = collection.collectionIteratorNoLock(broker); // QQ: use collectionIterator ?
            while (collections.hasNext()) {
                collectionURIs.add(xmldbUri.append(collections.next()));

            }

        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            //return empty list
            return new ArrayList<>();

        } finally {

            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }

            brokerPool.release(broker);
        }

        return collectionURIs;
    }

    /**
     * Retrieve full URIs of all Documents in the collection.
     */
    public List<XmldbURI> getDocumentURIs() {

        List<XmldbURI> documentURIs = new ArrayList<>();

        DBBroker broker = null;
        Collection collection = null;
        try {
            // Try to read as specified subject
            broker = brokerPool.get(subject);
            collection = broker.openCollection(xmldbUri, Lock.READ_LOCK);

            // Get all documents
            Iterator<DocumentImpl> documents = collection.iteratorNoLock(broker); // QQ: use 'iterator'
            while (documents.hasNext()) {
                documentURIs.add(documents.next().getURI());
            }

        } catch (PermissionDeniedException | EXistException e) {
            LOG.error(e);
            //return empty list
            return new ArrayList<>();
            
        } finally {
            // Clean up resources
            if (collection != null) {
                collection.release(Lock.READ_LOCK);
            }

            brokerPool.release(broker);
        }

        return documentURIs;
    }

    /*
     * Delete document or collection.
     */
    void delete() {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Deleting '%s'", xmldbUri));
        }

        Collection collection = null;

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try(final DBBroker broker = brokerPool.get(subject);
            final Txn txn = txnManager.beginTransaction()) {

            // Open collection if possible, else abort
            collection = broker.openCollection(xmldbUri, Lock.WRITE_LOCK);
            if (collection == null) {
                txnManager.abort(txn);
                return;
            }

            // Remove collection
            broker.removeCollection(txn, collection);

            // Commit change
            txnManager.commit(txn);

            if(LOG.isDebugEnabled()) {
                LOG.debug("Document deleted sucessfully");
            }
        } catch (EXistException | IOException | PermissionDeniedException | TriggerException e) {
            LOG.error(e);
        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Finished delete");
            }
        }
    }

    public XmldbURI createCollection(String name) throws PermissionDeniedException, CollectionExistsException, EXistException {

        if(LOG.isDebugEnabled()) {
            LOG.debug(String.format("Create  '%s' in '%s'", name, xmldbUri));
        }

        XmldbURI newCollection = xmldbUri.append(name);
        Collection collection = null;

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try(final DBBroker broker = brokerPool.get(subject);
            final Txn txn = txnManager.beginTransaction()) {

            // Check if collection exists. not likely to happen since availability is
            // checked by ResourceFactory
            collection = broker.openCollection(newCollection, Lock.WRITE_LOCK);
            if (collection != null) {
                final String msg = "Collection already exists";

                LOG.debug(msg);
                
                //XXX: double "abort" is bad thing!!!
                txnManager.abort(txn);
                throw new CollectionExistsException(msg);
            }

            // Create collection
            Collection created = broker.getOrCreateCollection(txn, newCollection);
            broker.saveCollection(txn, created);
            broker.flush();

            // Commit change
            txnManager.commit(txn);

            if(LOG.isDebugEnabled()) {
                LOG.debug("Collection created sucessfully");
            }
        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            throw e;

        } catch (IOException e) {
            LOG.error(e);

        } catch (Throwable e) {
            LOG.error(e);
            throw new EXistException(e);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Finished creation");
            }
        }

        return newCollection;
    }

    public XmldbURI createFile(String newName, InputStream is, Long length, String contentType)
            throws IOException, PermissionDeniedException, CollectionDoesNotExistException {

        if(LOG.isDebugEnabled())
            LOG.debug(String.format("Create '%s' in '%s'", newName, xmldbUri));

        XmldbURI newNameUri = XmldbURI.create(newName);

        // Get mime, or NULL when not available
        MimeType mime = MimeTable.getInstance().getContentTypeFor(newName);
        if (mime == null) {
            mime = MimeType.BINARY_TYPE;
        }

        // References to the database
        Collection collection = null;

        // create temp file and store. Existdb needs to read twice from a stream.
        VirtualTempFile vtf = new VirtualTempFile();
        try(final BufferedInputStream bis = new BufferedInputStream(is);
            final BufferedOutputStream bos = new BufferedOutputStream(vtf)) {

            // Perform actual copy
            IOUtils.copy(bis, bos);
        }
        vtf.close();

        // To support LockNullResource, a 0-byte XML document can received. Since 0-byte
        // XML documents are not supported a small file will be created.
        if (mime.isXMLType() && vtf.length() == 0L) {

            if(LOG.isDebugEnabled())
                LOG.debug(String.format("Creating dummy XML file for null resource lock '%s'", newNameUri));

            vtf = new VirtualTempFile();
            IOUtils.write("<null_resource/>", vtf);
            vtf.close();
        }

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try(final DBBroker broker = brokerPool.get(subject);
            final Txn txn = txnManager.beginTransaction()) {

            // Check if collection exists. not likely to happen since availability is checked
            // by ResourceFactory
            collection = broker.openCollection(xmldbUri, Lock.WRITE_LOCK);
            if (collection == null) {
                LOG.debug(String.format("Collection %s does not exist", xmldbUri));
                txnManager.abort(txn);
                throw new CollectionDoesNotExistException(xmldbUri + "");
            }


            if (mime.isXMLType()) {

                if(LOG.isDebugEnabled())
                    LOG.debug(String.format("Inserting XML document '%s'", mime.getName()));

                // Stream into database
                VirtualTempFileInputSource vtfis = new VirtualTempFileInputSource(vtf);
                IndexInfo info = collection.validateXMLResource(txn, broker, newNameUri, vtfis);
                DocumentImpl doc = info.getDocument();
                doc.getMetadata().setMimeType(mime.getName());
                collection.store(txn, broker, info, vtfis, false);

            } else {

                if(LOG.isDebugEnabled())
                    LOG.debug(String.format("Inserting BINARY document '%s'", mime.getName()));

                // Stream into database
                try(final InputStream fis = vtf.getByteStream();
                    final InputStream bis = new BufferedInputStream(fis)) {
                    collection.addBinaryResource(txn, broker, newNameUri, bis, mime.getName(), length);
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

            if (vtf != null) {
                vtf.delete();
            }

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            if(LOG.isDebugEnabled()) {
                LOG.debug("Finished creation");
            }
        }

        // Send the result back to the client
        XmldbURI newResource = xmldbUri.append(newName);

        return newResource;
    }

    void resourceCopyMove(XmldbURI destCollectionUri, String newName, Mode mode) throws EXistException {

        if(LOG.isDebugEnabled())
            LOG.debug(String.format("%s '%s' to '%s' named '%s'", mode, xmldbUri, destCollectionUri, newName));

        XmldbURI newNameUri = null;
        try {
            newNameUri = XmldbURI.xmldbUriFor(newName);
            
        } catch (URISyntaxException ex) {
            LOG.error(ex);
            throw new EXistException(ex.getMessage());
        }

        Collection srcCollection = null;
        Collection destCollection = null;

        final TransactionManager txnManager = brokerPool.getTransactionManager();

        try(final DBBroker broker = brokerPool.get(subject);
            final Txn txn = txnManager.beginTransaction()) {

            // This class contains already the URI of the resource that shall be moved/copied
            XmldbURI srcCollectionUri = xmldbUri;

            // Open collection if possible, else abort
            srcCollection = broker.openCollection(srcCollectionUri, Lock.WRITE_LOCK);
            if (srcCollection == null) {
                txnManager.abort(txn);
                return; // TODO throw
            }


            // Open collection if possible, else abort
            destCollection = broker.openCollection(destCollectionUri, Lock.WRITE_LOCK);
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

            if(LOG.isDebugEnabled()) {
                LOG.debug(String.format("Collection %sd sucessfully", mode));
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

            if (destCollection != null) {
                destCollection.release(Lock.WRITE_LOCK);
            }

            if (srcCollection != null) {
                srcCollection.release(Lock.WRITE_LOCK);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Finished %s", mode));
            }
        }
    }
}
