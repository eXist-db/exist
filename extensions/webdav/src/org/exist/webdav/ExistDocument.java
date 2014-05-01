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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.URISyntaxException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.LockToken;
import org.exist.security.Account;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.VirtualTempFile;
import org.exist.webdav.exceptions.DocumentAlreadyLockedException;
import org.exist.webdav.exceptions.DocumentNotLockedException;
import org.exist.xmldb.XmldbURI;

import org.xml.sax.SAXException;

/**
 * Class for accessing the Collection class of the exist-db native API.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class ExistDocument extends ExistResource {

    /**
     *  Constructor.
     * 
     * @param uri   URI of document
     * @param pool  Reference to brokerpool
     */
    public ExistDocument(XmldbURI uri, BrokerPool pool) {

        if (LOG.isTraceEnabled()) {
            LOG.trace(String.format("New document object for %s", uri));
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
        DocumentImpl document = null;
        try {
            broker = brokerPool.get(subject);

            // If it is not a collection, check if it is a document
            document = broker.getXMLResource(xmldbUri, Lock.READ_LOCK);

            if (document.getResourceType() == DocumentImpl.XML_FILE) {
                isXmlDocument = true;
            }

            // Get meta data         
            creationTime = document.getMetadata().getCreated();
            lastModified = document.getMetadata().getLastModified();
            mimeType = document.getMetadata().getMimeType();

            // Retrieve perssions
            permissions = document.getPermissions();
            readAllowed = permissions.validate(subject, Permission.READ);
            writeAllowed = permissions.validate(subject, Permission.WRITE);
            executeAllowed = permissions.validate(subject, Permission.EXECUTE);


            ownerUser = permissions.getOwner().getUsername();
            ownerGroup = permissions.getGroup().getName();

            // Get (estimated) file size
            contentLength = document.getContentLength();

        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);

        } finally {

            // Cleanup resources
            if (document != null) {
                document.getUpdateLock().release(Lock.READ_LOCK);
            }

            if(broker != null) {
                brokerPool.release(broker);
            }

            isInitialized = true;
        }
    }
    private String mimeType;

    public String getMimeType() {
        return mimeType;
    }
    
    private long contentLength = 0;

    public long getContentLength() {
        return contentLength;
    }
    
    private boolean isXmlDocument = false;

    public boolean isXmlDocument() {
        return isXmlDocument;
    }

    /**
     * Stream document to framework.
     */
    public void stream(OutputStream os) throws IOException, PermissionDeniedException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Stream started");
        }

        long startTime = System.currentTimeMillis();

        DBBroker broker = null;
        DocumentImpl document = null;
        try {
            broker = brokerPool.get(subject);

            // If it is not a collection, check if it is a document
            document = broker.getXMLResource(xmldbUri, Lock.READ_LOCK);

            if (document.getResourceType() == DocumentImpl.XML_FILE) {
                // Stream XML document
                Serializer serializer = broker.getSerializer();
                serializer.reset();
                try {
                    // Set serialization options
                    serializer.setProperties(configuration);

                    // Serialize document
                    try (Writer w = new OutputStreamWriter(os, "UTF-8")) {
                        serializer.serialize(document, w);
                        w.flush();
                    }

                    // don;t flush
                    if (!(os instanceof VirtualTempFile)) {
                        os.flush();
                    }

                } catch (SAXException e) {
                    LOG.error(e);
                    throw new IOException(String.format("Error while serializing XML document: %s", e.getMessage()), e);
                }

            } else {
                // Stream NON-XML document
                broker.readBinaryResource((BinaryDocument) document, os);
                os.flush();
            }

        } catch (EXistException e) {
            LOG.error(e);
            throw new IOException(e.getMessage());

        } catch (PermissionDeniedException e) {
            LOG.error(e);
            throw e;

        } finally {

            if (document != null) {
                document.getUpdateLock().release(Lock.READ_LOCK);
            }

            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Stream stopped, duration %s msec.", System.currentTimeMillis() - startTime));
            }
        }

    }

    /**
     * Remove document from database.
     */
    void delete() {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("Deleting %s", xmldbUri));
        }

        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            broker = brokerPool.get(subject);

            // Need to split path into collection and document name
            XmldbURI collName = xmldbUri.removeLastSegment();
            XmldbURI docName = xmldbUri.lastSegment();

            // Open collection if possible, else abort
            collection = broker.openCollection(collName, Lock.WRITE_LOCK);
            if (collection == null) {
                LOG.debug("Collection does not exist");
                txnManager.abort(txn);
                return;
            }

            // Open document if possible, else abort
            resource = collection.getDocument(broker, docName);
            if (resource == null) {
                LOG.debug(String.format("No resource found for path: %s", xmldbUri));
                txnManager.abort(txn);
                return;
            }

            if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                collection.removeBinaryResource(txn, broker, resource.getFileURI());

            } else {
                collection.removeXMLResource(txn, broker, resource.getFileURI());
            }

            // Commit change
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Document deleted sucessfully");
            }

        } catch (LockException e) {
            LOG.error("Resource is locked.", e);
            txnManager.abort(txn);

        } catch (EXistException | TriggerException | PermissionDeniedException e) {
            LOG.error(e);
            txnManager.abort(txn);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }
            txnManager.close(txn);
            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished delete");
            }
        }
    }

    /**
     * Get lock token from database.
     */
    public LockToken getCurrentLock() {

        if (LOG.isDebugEnabled()) {
            LOG.debug("Get current lock " + xmldbUri);
        }

        DBBroker broker = null;
        DocumentImpl document = null;

        try {
            broker = brokerPool.get(subject);

            // If it is not a collection, check if it is a document
            document = broker.getXMLResource(xmldbUri, Lock.READ_LOCK);

            if (document == null) {
                LOG.debug("No resource found for path: " + xmldbUri);
                return null;
            }

            // TODO consider. A Webdav lock can be set without subject lock.
            Account lock = document.getUserLock();
            if (lock == null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Document " + xmldbUri + " does not contain userlock");
                }
                return null;
            }

            // Retrieve Locktoken from document metadata
            org.exist.dom.LockToken token = document.getMetadata().getLockToken();
            if (token == null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Document meta data does not contain a LockToken");
                }
                return null;
            }


            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully retrieved token");
            }

            return token;


        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            return null;

        } finally {

            if (document != null) {
                document.getUpdateLock().release(Lock.READ_LOCK);
            }

            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished probe lock");
            }
        }
    }

    /**
     * Lock document.
     */
    public LockToken lock(LockToken inputToken) throws PermissionDeniedException,
            DocumentAlreadyLockedException, EXistException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("create lock " + xmldbUri);
        }

        DBBroker broker = null;
        DocumentImpl document = null;
        TransactionManager txnManager = null;
        Txn txn = null;

        try {
            broker = brokerPool.get(subject);

            // Try to get document (add catch?)
            document = broker.getXMLResource(xmldbUri, Lock.WRITE_LOCK);

            if (document == null) {

                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("No resource found for path: %s", xmldbUri));
                }
                //return null; // throw exception?
                throw new EXistException("No resource found.");
            }

            // Get current userlock
            Account userLock = document.getUserLock();

            // Check if Resource is already locked. @@ToDo
            if (userLock != null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Resource was already locked, ignored.");
                }
            }

            if ( userLock != null && userLock.getName() != null 
                    && !userLock.getName().equals(subject.getName())
                    && !subject.hasDbaRole() ) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Resource is locked by user %s.", userLock.getName()));
                }
                throw new PermissionDeniedException(userLock.getName());
            }

            // Check for request for shared lock. @@TODO
            if (inputToken.getScope() == LockToken.LOCK_SCOPE_SHARED) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Shared locks are not implemented.");
                }
                throw new EXistException("Shared locks are not implemented.");
            }

            // Update locktoken
            inputToken.setOwner(subject.getName());
            inputToken.createOpaqueLockToken();
            //inputToken.setTimeOut(inputToken.getTimeOut());
            inputToken.setTimeOut(LockToken.LOCK_TIMEOUT_INFINITE);

            // Update document
            document.getMetadata().setLockToken(inputToken);
            document.setUserLock(subject);

            // Make token persistant
            txnManager = brokerPool.getTransactionManager();
            txn = txnManager.beginTransaction();
            broker.storeMetadata(txn, document);
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully retrieved token");
            }
            
            return inputToken;


        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            if (txnManager != null) {
                txnManager.abort(txn);
            }
            throw e;

        } catch (TriggerException e) {
            LOG.error(e);
            //dead code, remove?
            if (txnManager != null) {
                txnManager.abort(txn);
            }
            //-----------------------
            throw new EXistException(e);

		} finally {

            // TODO: check if can be done earlier
            if (document != null) {
                document.getUpdateLock().release(Lock.WRITE_LOCK);
            }

            if (txnManager != null) {
                txnManager.close(txn);
            }
            
            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished create lock");
            }
        }
    }

    /**
     * Unlock document in database.
     */
    void unlock() throws PermissionDeniedException, DocumentNotLockedException, EXistException {

        if (LOG.isDebugEnabled()) {
            LOG.debug("unlock " + xmldbUri);
        }

        DBBroker broker = null;
        DocumentImpl document = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            broker = brokerPool.get(subject);


            // Try to get document (add catch?)
            document = broker.getXMLResource(xmldbUri, Lock.WRITE_LOCK);

            if (document == null) {
                final String msg = String.format("No resource found for path: %s", xmldbUri);
                LOG.debug(msg);
                throw new EXistException(msg);
            }

            // Get current userlock
            Account lock = document.getUserLock();

            // Check if Resource is already locked.
            if (lock == null) {
                LOG.debug(String.format("Resource %s is not locked.", xmldbUri));
                throw new DocumentNotLockedException("" + xmldbUri);
            }

            // Check if Resource is from subject
            if (!lock.getName().equals(subject.getName()) && !subject.hasDbaRole() ) {
                LOG.debug(String.format("Resource lock is from user %s", lock.getName()));
                throw new PermissionDeniedException(lock.getName());
            }

            // Update document
            document.setUserLock(null);
            document.getMetadata().setLockToken(null);

            // Make it persistant
            broker.storeMetadata(txn, document);
            txnManager.commit(txn);

        } catch (EXistException | PermissionDeniedException e) {
            txnManager.abort(txn);
            LOG.error(e);
            throw e;

        } catch (TriggerException e) {
            txnManager.abort(txn);
            LOG.error(e);
            throw new EXistException(e);

		} finally {

            if (document != null) {
                document.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            txnManager.close(txn);
            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished create lock");
            }
        }
    }

    /**
     * Copy document or collection in database.
     */
    void resourceCopyMove(XmldbURI destCollectionUri, String newName, Mode mode) throws EXistException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s %s to %s named %s", mode, xmldbUri, destCollectionUri, newName));
        }

        XmldbURI newNameUri = null;
        try {
            newNameUri = XmldbURI.xmldbUriFor(newName);
        } catch (URISyntaxException ex) {
            LOG.error(ex);
            throw new EXistException(ex.getMessage());
        }

        DBBroker broker = null;
        Collection srcCollection = null;
        DocumentImpl srcDocument = null;

        Collection destCollection = null;


        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            broker = brokerPool.get(subject);

            // Need to split path into collection and document name
            XmldbURI srcCollectionUri = xmldbUri.removeLastSegment();
            XmldbURI srdDocumentUri = xmldbUri.lastSegment();

            // Open collection if possible, else abort
            srcCollection = broker.openCollection(srcCollectionUri, Lock.WRITE_LOCK);
            if (srcCollection == null) {
                txnManager.abort(txn);
                return; // TODO throw
            }

            // Open document if possible, else abort
            srcDocument = srcCollection.getDocument(broker, srdDocumentUri);
            if (srcDocument == null) {
                LOG.debug(String.format("No resource found for path: %s", xmldbUri));
                txnManager.abort(txn);
                return;
            }

            // Open collection if possible, else abort
            destCollection = broker.openCollection(destCollectionUri, Lock.WRITE_LOCK);
            if (destCollection == null) {
                LOG.debug(String.format("Destination collection %s does not exist.", xmldbUri));
                txnManager.abort(txn);
                return;
            }


            // Perform actial move/copy
            if (mode == Mode.COPY) {
                broker.copyResource(txn, srcDocument, destCollection, newNameUri);

            } else {
                broker.moveResource(txn, srcDocument, destCollection, newNameUri);
            }


            // Commit change
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug(String.format("Document %sd sucessfully", mode));
            }

        } catch (LockException e) {
            LOG.error("Resource is locked.", e);
            txnManager.abort(txn);
            throw new EXistException(e.getMessage());

        } catch (EXistException e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw e;

        } catch (IOException | PermissionDeniedException | TriggerException e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new EXistException(e.getMessage());

        } finally {

            // TODO: check if can be done earlier
            if (destCollection != null) {
                destCollection.release(Lock.WRITE_LOCK);
            }

            if (srcCollection != null) {
                srcCollection.release(Lock.WRITE_LOCK);
            }

            txnManager.close(txn);
            brokerPool.release(broker);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished " + mode);
            }
        }
    }

    public LockToken refreshLock(String token) throws PermissionDeniedException,
            DocumentAlreadyLockedException, EXistException, DocumentNotLockedException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("refresh lock %s  lock=%s", xmldbUri, token));
        }

        DBBroker broker = null;
        DocumentImpl document = null;

        if (token == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("token is null");
            }
            throw new EXistException("token is null");
        }

        // Prepare transaction
        TransactionManager txnManager = null;
        Txn txn = null;

        try {
            broker = brokerPool.get(subject);

            // Try to get document (add catch?)
            document = broker.getXMLResource(xmldbUri, Lock.WRITE_LOCK);

            if (document == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("No resource found for path: %s", xmldbUri));
                }
                //return null; // throw exception?
                throw new EXistException("No resource found.");
            }

            // Get current userlock
            Account userLock = document.getUserLock();

            // Check if Resource is already locked. 
            if (userLock == null) {
                final String msg = "Resource was not locked.";
                if (LOG.isDebugEnabled()) {
                    LOG.debug(msg);
                }
                throw new DocumentNotLockedException(msg);
            }

            if (userLock.getName() != null && !userLock.getName().equals(subject.getName()) 
                    && !subject.hasDbaRole()) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(String.format("Resource is locked by %s", userLock.getName()));
                }
                throw new PermissionDeniedException(userLock.getName());
            }

            LockToken lockToken = document.getMetadata().getLockToken();

            if (!token.equals(lockToken.getOpaqueLockToken())) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Token does not match");
                }
                throw new PermissionDeniedException(String.format("Token %s does not match %s", token, lockToken.getOpaqueLockToken()));
            }

            lockToken.setTimeOut(LockToken.LOCK_TIMEOUT_INFINITE);

            // Make token persistant
            txnManager = brokerPool.getTransactionManager();
            txn = txnManager.beginTransaction();
            broker.storeXMLResource(txn, document);
            txnManager.commit(txn);

            if (LOG.isDebugEnabled()) {
                LOG.debug("Successfully retrieved token");
            }
            return lockToken;


        } catch (EXistException | PermissionDeniedException e) {
            LOG.error(e);
            if (txnManager != null) {
                txnManager.abort(txn);
            }
            throw e;

        } finally {

            // TODO: check if can be done earlier
            if (document != null) {
                document.getUpdateLock().release(Lock.WRITE_LOCK);
            }
            
            if (txnManager != null) {
                txnManager.close(txn);
            }
            
            if(broker != null) {
                brokerPool.release(broker);
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Finished create lock");
            }
        }
    }
}
