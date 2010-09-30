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

import java.io.File;
import java.io.FileOutputStream;
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
import org.exist.dom.DocumentImpl;
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
import org.exist.webdav.exceptions.CollectionDoesNotExistException;
import org.exist.webdav.exceptions.CollectionExistsException;
import org.exist.xmldb.XmldbURI;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Class for accessing the Collection class of the exist-db native API.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistCollection extends ExistResource {

    public ExistCollection(XmldbURI uri, BrokerPool pool) {
        LOG.debug("New collection object for " + uri);
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
                LOG.error("Collection for " + xmldbUri + " cannot be opened for  metadata");
                return;
            }

            // Retrieve some meta data
            permissions = collection.getPermissions();
            readAllowed = permissions.validate(subject, Permission.READ);
            writeAllowed = permissions.validate(subject, Permission.WRITE);
            updateAllowed = permissions.validate(subject, Permission.UPDATE);

            creationTime = collection.getCreationTime();
            lastModified = creationTime; // Collection does not have more information.

            ownerUser = permissions.getOwner().getUsername();
            ownerGroup = permissions.getOwnerGroup().getName();


        } catch (EXistException e) {
            LOG.error(e);

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


        List<XmldbURI> collectionURIs = new ArrayList<XmldbURI>();

        DBBroker broker = null;
        Collection collection = null;
        try {
            // Try to read as specified subject
            broker = brokerPool.get(subject);
            collection = broker.openCollection(xmldbUri, Lock.READ_LOCK);

            // Get all collections
            Iterator<XmldbURI> collections = collection.collectionIteratorNoLock(); // QQ: use collectionIterator ?
            while (collections.hasNext()) {
                collectionURIs.add(xmldbUri.append(collections.next()));

            }

        } catch (EXistException e) {
            LOG.error(e);
            collectionURIs = null;

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

        List<XmldbURI> documentURIs = new ArrayList<XmldbURI>();

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

        } catch (EXistException e) {
            LOG.error(e);
            documentURIs = null;

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

        LOG.debug("Deleting '" + xmldbUri + "'");

        DBBroker broker = null;
        Collection collection = null;

        TransactionManager transact = brokerPool.getTransactionManager();
        Txn txn = transact.beginTransaction();

        try {
            broker = brokerPool.get(subject);


            // Open collection if possible, else abort
            collection = broker.openCollection(xmldbUri, Lock.WRITE_LOCK);
            if (collection == null) {
                transact.abort(txn);
                return;
            }

            // Remove collection
            broker.removeCollection(txn, collection);

            // Commit change
            transact.commit(txn);

            LOG.debug("Document deleted sucessfully");


        } catch (EXistException e) {
            LOG.error(e);
            transact.abort(txn);

        } catch (IOException e) {
            LOG.error(e);
            transact.abort(txn);

        } catch (PermissionDeniedException e) {
            LOG.error(e);
            transact.abort(txn);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);

            LOG.debug("Finished delete");
        }
    }

    public XmldbURI createCollection(String name) throws PermissionDeniedException, CollectionExistsException, EXistException {
        LOG.debug("Create  '" + name + "' in '" + xmldbUri + "'");

        XmldbURI newCollection = xmldbUri.append(name);


        DBBroker broker = null;
        Collection collection = null;

        TransactionManager transact = brokerPool.getTransactionManager();
        Txn txn = transact.beginTransaction();

        try {
            broker = brokerPool.get(subject);

            // Check if collection exists. not likely to happen since availability is
            // checked by ResourceFactory
            collection = broker.openCollection(newCollection, Lock.WRITE_LOCK);
            if (collection != null) {
                LOG.debug("Collection already exists");
                transact.abort(txn);
                throw new CollectionExistsException("Collection already exists");
            }

            // Create collection
            Collection created = broker.getOrCreateCollection(txn, newCollection);
            broker.saveCollection(txn, created);
            broker.flush();

            // Commit change
            transact.commit(txn);

            LOG.debug("Collection created sucessfully");


        } catch (EXistException e) {
            LOG.error(e);
            transact.abort(txn);
            throw e;

        } catch (IOException e) {
            LOG.error(e);
            transact.abort(txn);

        } catch (PermissionDeniedException e) {
            LOG.error(e);
            transact.abort(txn);
            throw e;

        } catch (Throwable e) {
            LOG.error(e);
            transact.abort(txn);
            throw new EXistException(e);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);

            LOG.debug("Finished creation");
        }

        return newCollection;
    }

    public XmldbURI createFile(String newName, InputStream dis, Long length, String contentType)
            throws IOException, PermissionDeniedException, CollectionDoesNotExistException {
        LOG.debug("Create '" + newName + "' in '" + xmldbUri + "'");
     
        XmldbURI newNameUri = XmldbURI.create(newName);

        DBBroker broker = null;
        Collection collection = null;
        File tmpFile = null;

        // Start transaction
        TransactionManager transact = brokerPool.getTransactionManager();
        Txn txn = transact.beginTransaction();

        try {
            broker = brokerPool.get(subject);

            // Check if collection exists. not likely to happen since availability is checked
            // by ResourceFactory
            collection = broker.openCollection(xmldbUri, Lock.WRITE_LOCK);
            if (collection == null) {
                LOG.debug("Collection does not exist");
                transact.abort(txn);
                throw new CollectionDoesNotExistException(xmldbUri + "");
            }
            

            // Get mime, or NULL when not available
            MimeType mime = MimeTable.getInstance().getContentTypeFor(newName);
            if (mime == null) {
                mime = MimeType.BINARY_TYPE;
            }


            if (mime.isXMLType()) {
                LOG.debug("Inserting XML document '" + mime.getName() + "'");

                // create temp file and store. Existdb needs to read twice from a stream.
                tmpFile = File.createTempFile("Miltonwebdavtmpxml", "tmp");
                FileOutputStream fos = new FileOutputStream(tmpFile);
                IOUtils.copy(dis, fos);
                dis.close();

                String url = tmpFile.toURI().toASCIIString();
                InputSource is = new InputSource(url);
                IndexInfo info = collection.validateXMLResource(txn, broker, newNameUri, is);
                DocumentImpl doc = info.getDocument();
                doc.getMetadata().setMimeType(contentType);
                collection.store(txn, broker, info, is, false);

            } else {
                LOG.debug("Inserting BINARY document '" + mime.getName() + "'");
                DocumentImpl doc = collection.addBinaryResource(txn, broker, newNameUri, dis,
                        contentType, length.intValue());
                dis.close();
            }

            // Commit change
            transact.commit(txn);

            LOG.debug("Document created sucessfully");


        } catch (EXistException e) {
            LOG.error(e);
            transact.abort(txn);
            throw new IOException(e);

        } catch (TriggerException e) {
            LOG.error(e);
            transact.abort(txn);
            throw new IOException(e);

        } catch (SAXException e) {
            LOG.error(e);
            transact.abort(txn);
            throw new IOException(e);

        } catch (LockException e) {
            LOG.error(e);
            transact.abort(txn);
            throw new PermissionDeniedException(xmldbUri + "");

        } catch (IOException e) {
            LOG.error(e);
            transact.abort(txn);
            throw e;

        } catch (PermissionDeniedException e) {
            LOG.error(e);
            transact.abort(txn);
            throw e;

        } finally {

            if(tmpFile!=null){
                tmpFile.delete();
            }

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);

            LOG.debug("Finished creation");
        }

        XmldbURI newResource = xmldbUri.append(newName);
        return newResource;
    }

    void resourceCopyMove(XmldbURI destCollectionUri, String newName, Mode mode) throws EXistException {
        LOG.debug(mode + " '" + xmldbUri + "' to '" + destCollectionUri + "' named '" + newName + "'");

        XmldbURI newNameUri = null;
        try {
            newNameUri = XmldbURI.xmldbUriFor(newName);
        } catch (URISyntaxException ex) {
            LOG.error(ex);
            throw new EXistException(ex.getMessage());
        }

        DBBroker broker = null;
        Collection srcCollection = null;
        Collection destCollection = null;


        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            broker = brokerPool.get(subject);

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
                LOG.debug("Destination collection " + xmldbUri + " does not exist.");
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

            LOG.debug("Collection " + mode + "d sucessfully");

        } catch (LockException e) {
            LOG.error("Resource is locked.", e);
            txnManager.abort(txn);
            throw new EXistException(e.getMessage());

        } catch (EXistException e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw e;

        } catch (IOException e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new EXistException(e.getMessage());

        } catch (PermissionDeniedException e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new EXistException(e.getMessage());

        } finally {

            if (destCollection != null) {
                destCollection.release(Lock.WRITE_LOCK);
            }

            if (srcCollection != null) {
                srcCollection.release(Lock.WRITE_LOCK);
            }


            brokerPool.release(broker);

            LOG.debug("Finished " + mode);
        }
    }
}
