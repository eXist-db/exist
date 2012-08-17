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
package org.exist.replication.jms.subscribe;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import javax.jms.BytesMessage;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.replication.shared.MessageHelper;
import org.exist.replication.shared.eXistMessage;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.Permission;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.util.VirtualTempFileInputSource;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;

/**
 *
 * @author Dannes Wessels
 */
public class JMSMessageListener implements MessageListener {

    private final static Logger LOG = Logger.getLogger(JMSMessageListener.class);
    private BrokerPool brokerPool = null;
    private org.exist.security.SecurityManager securityManager = null;

    /**
     * Constructor
     *
     * @param brokerpool Reference to database brokerpool
     */
    public JMSMessageListener(BrokerPool brokerpool) {
        brokerPool = brokerpool;
        securityManager = brokerpool.getSecurityManager();
    }

    /**
     * Convert JMS ByteMessage into an eXist-db specific message.
     *
     * @param bm The original message
     * @return The converted message
     */
    private eXistMessage convertMessage(BytesMessage bm) {
        eXistMessage em = new eXistMessage();

        try {
            String value = bm.getStringProperty(eXistMessage.EXIST_RESOURCE_TYPE);
            eXistMessage.ResourceType resourceType = eXistMessage.ResourceType.valueOf(value);
            em.setResourceType(resourceType);

            value = bm.getStringProperty(eXistMessage.EXIST_RESOURCE_OPERATION);
            eXistMessage.ResourceOperation changeType = eXistMessage.ResourceOperation.valueOf(value);
            em.setResourceOperation(changeType);

            value = bm.getStringProperty(eXistMessage.EXIST_SOURCE_PATH);
            em.setResourcePath(value);

            value = bm.getStringProperty(eXistMessage.EXIST_DESTINATION_PATH);
            em.setDestinationPath(value);


            // This is potentially memory intensive
            long size = bm.getBodyLength();
            byte[] payload = new byte[(int) size];
            bm.readBytes(payload);
            em.setPayload(payload);

        } catch (JMSException ex) {
            String errorMessage = "Unable to convert incoming message. ("
                    + ex.getErrorCode() + "):  " + ex.getMessage();
            LOG.error(errorMessage, ex);
            throw new MessageReceiveException(errorMessage);
        }

        return em;

    }

    @Override
    public void onMessage(Message msg) {

        try {

            if (msg instanceof BytesMessage) {

                // Prepare received message
                eXistMessage em = convertMessage((BytesMessage) msg);
                

                // Report some details into logging
                if (LOG.isDebugEnabled()) {
                    String txt = em.getResourceOperation() + " : " + em.getResourceType()
                            + " from " + em.getResourcePath();
                    if (em.getDestinationPath() != null) {
                        txt = txt + " to " + em.getDestinationPath();
                    }
                    LOG.debug(txt);
                }

                Enumeration e = msg.getPropertyNames();
                while(e.hasMoreElements()){
                    Object next = e.nextElement();
                    if(next instanceof String){
                        em.getMetadata().put( (String) next, msg.getObjectProperty( (String) next) );
                    }
                }
                

                // First step: distinct between update for documents and messsages
                switch (em.getResourceType()) {
                    case DOCUMENT:
                        handleDocument(em);
                        break;
                    case COLLECTION:
                        handleCollection(em);
                        break;
                    default:
                        String errorMessage = "Unknown resource type " + em.getResourceType();
                        LOG.error(errorMessage);
                        throw new MessageReceiveException(errorMessage);

                }
                
                

            } else {
                // Only ByteMessage objects supported. 
                throw new MessageReceiveException("Could not handle message type "
                        + msg.getClass().getSimpleName());
            }

        } catch (MessageReceiveException ex) {
            // Thrown by local code. Just make it pass
            LOG.error("Could not handle received message: " + ex.getMessage(), ex);
            throw ex;

        } catch (Throwable t) {
            // Something really unexpected happened. Report
            LOG.error(t.getMessage(), t);
            throw new MessageReceiveException("Could not handle received message: "
                    + t.getMessage(), t);
        }
    }

    /**
     * Handle operation on documents
     *
     * @param em Message containing information about documents
     */
    private void handleDocument(eXistMessage em) {

        switch (em.getResourceOperation()) {
            case CREATE_UPDATE:
                createDocument(em);
                break;

            case DELETE:
                deleteDocument(em);
                break;

            case MOVE:
                relocateDocument(em, false);
                break;

            case COPY:
                relocateDocument(em, true);
                break;

            default:
                String errorMessage = "Unknown resource type " + em.getResourceOperation();
                LOG.error(errorMessage);
                throw new MessageReceiveException(errorMessage);
        }
    }

    /**
     * Handle operation on collections
     *
     * @param em Message containing information about collections
     */
    private void handleCollection(eXistMessage em) {


        switch (em.getResourceOperation()) {
            case CREATE_UPDATE:
                createCollection(em);
                break;

            case DELETE:
                deleteCollection(em);
                break;

            case MOVE:
                relocateCollection(em, false);
                break;

            case COPY:
                relocateCollection(em, true);
                break;

            default:
                String errorMessage = "Unknown change type";
                LOG.error(errorMessage);
                throw new MessageReceiveException(errorMessage);
        }
    }

    /**
     * Created document in database
     */
    private void createDocument(eXistMessage em) {

        Map<String, Object> props = em.getMetadata();


        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();

        // References to the database
        DBBroker broker = null;
        Collection collection = null;

        // Get mime, or NULL when not available
        MimeType mime = MimeTable.getInstance().getContentTypeFor(docURI.toString());
        if (mime == null) {
            mime = MimeType.BINARY_TYPE;
        }





        // Get OWNER
        String userName = null;
        Object prop = props.get(MessageHelper.EXIST_RESOURCE_OWNER);
        if (prop != null && prop instanceof String) {
            userName = (String) prop;
        }

        Account account = securityManager.getAccount(userName);
        if (account == null) {
            String errorText = "Username " + userName + " does not exist.";
            LOG.error(errorText);
            throw new MessageReceiveException(errorText);
        }

        // Get GROUP
        String groupName = null;
        prop = props.get(MessageHelper.EXIST_RESOURCE_GROUP);
        if (prop != null && prop instanceof String) {
            groupName = (String) prop;
        }

        Group group = securityManager.getGroup(groupName);
        if (group == null) {
            String errorText = "Group " + groupName + " does not exist.";
            LOG.error(errorText);
            throw new MessageReceiveException(errorText);
        }

        // Get MIME_TYPE
        MimeTable mimeTable = MimeTable.getInstance();
        String mimeType = null;
        prop = props.get(MessageHelper.EXIST_RESOURCE_MIMETYPE);
        if (prop != null && prop instanceof String) {
            MimeType mT = mimeTable.getContentTypeFor((String) prop);
            if (mT != null) {
                mimeType = mT.getName();
            }

        }

        // Fallback based on filename
        if (mimeType == null) {
            MimeType mT = mimeTable.getContentTypeFor(sourcePath);

            if (mT == null) {
                throw new MessageReceiveException("Unable to determine mimetype");
            }
            mimeType = mT.getName();
        }



        // Get/Set permissions
        Integer mode = null;
        prop = props.get(MessageHelper.EXIST_RESOURCE_MODE);
        if (prop != null && prop instanceof Integer) {
            mode = (Integer) prop;
        }




        // Start transaction
        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());

            // Check if collection exists. not likely to happen since availability is checked
            // by ResourceFactory
            collection = broker.openCollection(colURI, Lock.WRITE_LOCK);
//            collection.setTriggersEnabled(false);
            if (collection == null) {
                String errorMessage = "Collection " + colURI + " does not exist";
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }

            DocumentImpl doc = null;
            if (mime.isXMLType()) {

                // Stream into database
                VirtualTempFile vtf = new VirtualTempFile(em.getPayload());
                VirtualTempFileInputSource vt = new VirtualTempFileInputSource(vtf);
                InputStream bis = vt.getByteStream();

                GZIPInputStream gis = new GZIPInputStream(bis);
                InputSource is = new InputSource(gis);

                IndexInfo info = collection.validateXMLResource(txn, broker, docURI, is);
                doc = info.getDocument();
                doc.getMetadata().setMimeType(mimeType);

                // reconstruct gzip input stream
                bis.reset();
                gis = new GZIPInputStream(bis);
                is = new InputSource(gis);

                collection.store(txn, broker, info, is, false);
                is.getByteStream().close();

            } else {

                // Stream into database
                byte[] payload = em.getPayload();
                ByteArrayInputStream bais = new ByteArrayInputStream(payload);
                GZIPInputStream gis = new GZIPInputStream(bais);
                BufferedInputStream bis = new BufferedInputStream(gis);
                doc = collection.addBinaryResource(txn, broker, docURI, bis, mimeType, payload.length);
                bis.close();
            }

            // Set owner,group and permissions
            Permission permission = doc.getPermissions();
            if (userName != null) {
                permission.setOwner(userName);
            }
            if (groupName != null) {
                permission.setGroup(groupName);
            }
            if (mode != null) {
                permission.setMode(mode);
            }


            // Commit change
            txnManager.commit(txn);

        } catch (Throwable ex) {
            LOG.error(ex.getMessage(), ex);
            txnManager.abort(txn);
            throw new MessageReceiveException("Unable to write document into database: " + ex.getMessage());

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
                //collection.setTriggersEnabled(true);
            }

            brokerPool.release(broker);

        }
    }

    /**
     * Remove document from database
     */
    private void deleteDocument(eXistMessage em) {

        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();

        // References to the database
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());

            // Open collection if possible, else abort
            collection = broker.openCollection(colURI, Lock.WRITE_LOCK);
            if (collection == null) {
                String errorText = "Collection does not exist " + colURI;
                LOG.debug(errorText);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorText);
            }

            // Open document if possible, else abort
            resource = collection.getDocument(broker, docURI);
            if (resource == null) {
                String errorText = "No resource found for path: " + sourcePath;
                LOG.debug(errorText);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorText);
            }

            if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                collection.removeBinaryResource(txn, broker, resource.getFileURI());

            } else {
                collection.removeXMLResource(txn, broker, resource.getFileURI());
            }

            // Commit change
            txnManager.commit(txn);

        } catch (Throwable e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new MessageReceiveException(e.getMessage(), e);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);

        }
    }

    /**
     * Remove collection from database
     */
    private void deleteCollection(eXistMessage em) {

        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();


        DBBroker broker = null;
        Collection collection = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());


            // Open collection if possible, else abort
            collection = broker.openCollection(colURI, Lock.WRITE_LOCK);
            if (collection == null) {
                txnManager.abort(txn);
                return;
            }

            // Remove collection
            broker.removeCollection(txn, collection);

            // Commit change
            txnManager.commit(txn);


        } catch (Throwable e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new MessageReceiveException(e.getMessage());

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);

        }
    }

    /**
     * Created collection in database
     */
    private void createCollection(eXistMessage em) {

        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI colURI = sourcePath.removeLastSegment();
        XmldbURI docURI = sourcePath.lastSegment();
        
        Map<String, Object> props = em.getMetadata();
        
        
        // Get OWNER
        String userName = null;
        Object prop = props.get(MessageHelper.EXIST_RESOURCE_OWNER);
        if (prop != null && prop instanceof String) {
            userName = (String) prop;
        }

        Account account = securityManager.getAccount(userName);
        if (account == null) {
            String errorText = "Username " + userName + " does not exist.";
            LOG.error(errorText);
            throw new MessageReceiveException(errorText);
        }

        // Get GROUP
        String groupName = null;
        prop = props.get(MessageHelper.EXIST_RESOURCE_GROUP);
        if (prop != null && prop instanceof String) {
            groupName = (String) prop;
        }
        
        // Get/Set permissions
        Integer mode = null;
        prop = props.get(MessageHelper.EXIST_RESOURCE_MODE);
        if (prop != null && prop instanceof Integer) {
            mode = (Integer) prop;
        }


        DBBroker broker = null;
        Collection collection = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());

            // TODO ... consider to swallow situation transparently
            collection = broker.openCollection(sourcePath, Lock.WRITE_LOCK);
            if (collection != null) {
                String errorText = "Collection " + sourcePath + " already exists";
                LOG.debug(errorText);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorText);
            }

            // Create collection
            Collection newCollection = broker.getOrCreateCollection(txn, sourcePath);
            
            Permission perm = newCollection.getPermissions();
            
            // Set owner,group and permissions
            Permission permission = newCollection.getPermissions();
            if (userName != null) {
                permission.setOwner(userName);
            }
            if (groupName != null) {
                permission.setGroup(groupName);
            }
            if (mode != null) {
                permission.setMode(mode);
            }
            
            
            broker.saveCollection(txn, newCollection);
            broker.flush();

            // Commit change
            txnManager.commit(txn);

        } catch (Throwable e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new MessageReceiveException(e.getMessage(), e);

        } finally {

            // TODO: check if can be done earlier
            if (collection != null) {
                collection.release(Lock.WRITE_LOCK);
            }

            brokerPool.release(broker);
        }
    }

    private void relocateDocument(eXistMessage em, boolean keepDocument) {

        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI sourceColURI = sourcePath.removeLastSegment();
        XmldbURI sourceDocURI = sourcePath.lastSegment();

        XmldbURI destPath = XmldbURI.create(em.getDestinationPath());
        XmldbURI destColURI = destPath.removeLastSegment();
        XmldbURI destDocURI = destPath.lastSegment();


        DBBroker broker = null;

        Collection srcCollection = null;
        DocumentImpl srcDocument = null;

        Collection destCollection = null;

        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());

            // Open collection if possible, else abort
            srcCollection = broker.openCollection(sourceColURI, Lock.WRITE_LOCK);
            if (srcCollection == null) {
                String errorMessage = "Collection not found: " + sourceColURI;
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }

            // Open document if possible, else abort
            srcDocument = srcCollection.getDocument(broker, sourceDocURI);
            if (srcDocument == null) {
                String errorMessage = "No resource found for path: " + sourcePath;
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }

            // Open collection if possible, else abort
            destCollection = broker.openCollection(destColURI, Lock.WRITE_LOCK);
            if (destCollection == null) {
                String errorMessage = "Destination collection " + destColURI + " does not exist.";
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }


            // Perform actial move/copy
            if (keepDocument) {
                broker.copyResource(txn, srcDocument, destCollection, destDocURI);

            } else {
                broker.moveResource(txn, srcDocument, destCollection, destDocURI);
            }


            // Commit change
            txnManager.commit(txn);



        } catch (Throwable e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new MessageReceiveException(e.getMessage(), e);

        } finally {

            // TODO: check if can be done earlier
            if (destCollection != null) {
                destCollection.release(Lock.WRITE_LOCK);
            }

            if (srcCollection != null) {
                srcCollection.release(Lock.WRITE_LOCK);
            }


            brokerPool.release(broker);


        }
    }

    private void relocateCollection(eXistMessage em, boolean keepCollection) {

        XmldbURI sourcePath = XmldbURI.create(em.getResourcePath());
        XmldbURI sourceColURI = sourcePath.removeLastSegment();
        XmldbURI sourceDocURI = sourcePath.lastSegment();

        XmldbURI destPath = XmldbURI.create(em.getDestinationPath());
        XmldbURI destColURI = destPath.removeLastSegment();
        XmldbURI destDocURI = destPath.lastSegment();

        DBBroker broker = null;
        Collection srcCollection = null;
        Collection destCollection = null;


        TransactionManager txnManager = brokerPool.getTransactionManager();
        Txn txn = txnManager.beginTransaction();

        try {
            // TODO get user
            broker = brokerPool.get(securityManager.getSystemSubject());

            // Open collection if possible, else abort
            srcCollection = broker.openCollection(sourcePath, Lock.WRITE_LOCK);
            if (srcCollection == null) {
                String errorMessage = "Collection " + sourcePath + " does not exist.";
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }


            // Open collection if possible, else abort
            destCollection = broker.openCollection(destColURI, Lock.WRITE_LOCK);
            if (destCollection == null) {
                String errorMessage = "Destination collection " + destColURI + " does not exist.";
                LOG.debug(errorMessage);
                txnManager.abort(txn);
                throw new MessageReceiveException(errorMessage);
            }

            // Perform actual move/copy
            if (keepCollection) {
                broker.copyCollection(txn, srcCollection, destCollection, destDocURI);

            } else {
                broker.moveCollection(txn, srcCollection, destCollection, destDocURI);
            }

            // Commit change
            txnManager.commit(txn);




        } catch (Throwable e) {
            LOG.error(e);
            txnManager.abort(txn);
            throw new MessageReceiveException(e.getMessage());

        } finally {

            if (destCollection != null) {
                destCollection.release(Lock.WRITE_LOCK);
            }

            if (srcCollection != null) {
                srcCollection.release(Lock.WRITE_LOCK);
            }


            brokerPool.release(broker);


        }
    }
}
