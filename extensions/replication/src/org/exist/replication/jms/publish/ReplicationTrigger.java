/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
package org.exist.replication.jms.publish;

import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;

import org.exist.collections.Collection;
import org.exist.collections.triggers.CollectionTrigger;
import org.exist.collections.triggers.DocumentTrigger;
import org.exist.collections.triggers.FilteringTrigger;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.replication.shared.MessageHelper;
import org.exist.replication.shared.TransportException;
import org.exist.replication.shared.eXistMessage;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;

/**
 * Trigger for detecting document and collection changes to have the changes
 * propagated to remote eXist-db instances.
 *
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class ReplicationTrigger extends FilteringTrigger implements DocumentTrigger, CollectionTrigger {

    private final static Logger LOG = Logger.getLogger(ReplicationTrigger.class);
    private Map<String, List<?>> parameters;

    //
    // Document Triggers
    //
    private void afterUpdateCreateDocument(DBBroker broker, Txn transaction,
            DocumentImpl document, eXistMessage.ResourceOperation operation) /* throws TriggerException */ {

        if (LOG.isDebugEnabled()) {
            LOG.debug(document.getURI().toString());
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.DOCUMENT);
        msg.setResourceOperation(operation);
        msg.setResourcePath(document.getURI().toString());

        // Retrieve Metadata
        Map<String, Object> md = msg.getMetadata();
        MessageHelper.retrieveDocMetadata(md, document.getMetadata());
        MessageHelper.retrieveFromDocument(md, document);
        MessageHelper.retrievePermission(md, document.getPermissions());

        
        // The content is always gzip-ped
        md.put(MessageHelper.EXIST_MESSAGE_CONTENTENCODING, "gzip");

        // Serialize document
        try {
            msg.setPayload(MessageHelper.gzipSerialize(broker, document));

        } catch (Throwable ex) {
            LOG.error(String.format("Problem while serializing document (contentLength=%s) to compressed message:%s", document.getContentLength(), ex.getMessage()), ex);
            //throw new TriggerException("Unable to retrieve message payload: " + ex.getMessage());
        }

        // Send Message   
        sendMessage(msg);
    }
    
    @Override
    public void afterCreateDocument(DBBroker broker, Txn transaction,
            DocumentImpl document) throws TriggerException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(document.getURI().toString());
        }

        this.afterUpdateCreateDocument(broker, transaction, document, eXistMessage.ResourceOperation.CREATE);
    }

    @Override
    public void afterUpdateDocument(DBBroker broker, Txn transaction,
            DocumentImpl document) throws TriggerException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(document.getURI().toString());
        }

        this.afterUpdateCreateDocument(broker, transaction, document, eXistMessage.ResourceOperation.UPDATE);
    }

    @Override
    public void afterCopyDocument(DBBroker broker, Txn transaction,
            DocumentImpl document, XmldbURI oldUri) throws TriggerException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s %s", document.getURI().toString(), oldUri.toString()));
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.DOCUMENT);
        msg.setResourceOperation(eXistMessage.ResourceOperation.COPY);
        msg.setResourcePath(oldUri.toString());
        msg.setDestinationPath(document.getURI().toString());

        // Send Message   
        sendMessage(msg);
    }

    @Override
    public void afterMoveDocument(DBBroker broker, Txn transaction,
            DocumentImpl document, XmldbURI oldUri) throws TriggerException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s %s", document.getURI().toString(), oldUri.toString()));
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.DOCUMENT);
        msg.setResourceOperation(eXistMessage.ResourceOperation.MOVE);
        msg.setResourcePath(oldUri.toString());
        msg.setDestinationPath(document.getURI().toString());

        // Send Message   
        sendMessage(msg);
    }

    @Override
    public void afterDeleteDocument(DBBroker broker, Txn transaction,
            XmldbURI uri) throws TriggerException {

        if (LOG.isDebugEnabled()) {
            LOG.debug(uri.toString());
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.DOCUMENT);
        msg.setResourceOperation(eXistMessage.ResourceOperation.DELETE);
        msg.setResourcePath(uri.toString());

        // Send Message   
        sendMessage(msg);
    }

    //
    // Collection Triggers
    //
    @Override
    public void afterCreateCollection(DBBroker broker, Txn transaction,
            Collection collection) throws TriggerException {
        
        if (LOG.isDebugEnabled()) {
            LOG.debug(collection.getURI().toString());
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.COLLECTION);
        msg.setResourceOperation(eXistMessage.ResourceOperation.CREATE);
        msg.setResourcePath(collection.getURI().toString());

        Map<String, Object> md = msg.getMetadata();
        MessageHelper.retrievePermission(md, collection.getPermissions());

        // Send Message   
        sendMessage(msg);
    }

    @Override
    public void afterCopyCollection(DBBroker broker, Txn transaction, Collection collection,
            XmldbURI oldUri) throws TriggerException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s %s", collection.getURI().toString(), oldUri.toString()));
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.COLLECTION);
        msg.setResourceOperation(eXistMessage.ResourceOperation.COPY);
        msg.setResourcePath(oldUri.toString());
        msg.setDestinationPath(collection.getURI().toString());

        // Send Message   
        sendMessage(msg);
    }

    @Override
    public void afterMoveCollection(DBBroker broker, Txn transaction, Collection collection,
            XmldbURI oldUri) throws TriggerException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(String.format("%s %s", collection.getURI().toString(), oldUri.toString()));
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.COLLECTION);
        msg.setResourceOperation(eXistMessage.ResourceOperation.MOVE);
        msg.setResourcePath(oldUri.toString());
        msg.setDestinationPath(collection.getURI().toString());

        // Send Message   
        sendMessage(msg);
    }

    @Override
    public void afterDeleteCollection(DBBroker broker, Txn transaction,
            XmldbURI uri) throws TriggerException {
        if (LOG.isDebugEnabled()) {
            LOG.debug(uri.toString());
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.COLLECTION);
        msg.setResourceOperation(eXistMessage.ResourceOperation.DELETE);
        msg.setResourcePath(uri.toString());

        // Send Message   
        sendMessage(msg);
    }
    
    // 
    // Metadata triggers
    //    

    @Override
    public void afterUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        
        if (LOG.isDebugEnabled()) {
            LOG.debug(document.getURI().toString());
        }

        // Create Message
        eXistMessage msg = new eXistMessage();
        msg.setResourceType(eXistMessage.ResourceType.DOCUMENT);
        msg.setResourceOperation(eXistMessage.ResourceOperation.METADATA);
        msg.setResourcePath(document.getURI().toString());

        // Retrieve Metadata
        Map<String, Object> md = msg.getMetadata();
        MessageHelper.retrieveDocMetadata(md, document.getMetadata());
        MessageHelper.retrieveFromDocument(md, document);
        MessageHelper.retrievePermission(md, document.getPermissions());

        // Send Message   
        sendMessage(msg);
    }
    
    //
    // Misc         
    //
    @Override
    public void configure(DBBroker broker, Collection parent, Map<String, List<?>> parameters) throws TriggerException {
        super.configure(broker, parent, parameters);
        this.parameters = parameters;

    }

    /**
     * Send 'trigger' message with parameters set using
     * {@link #configure(org.exist.storage.DBBroker, org.exist.collections.Collection, java.util.Map)}
     */
    private void sendMessage(eXistMessage msg) /* throws TriggerException  */ {
        // Send Message   
        JMSMessageSender sender = new JMSMessageSender(parameters);
        try {
            sender.sendMessage(msg);

        } catch (TransportException ex) {
            LOG.error(ex.getMessage(), ex);
            //throw new TriggerException(ex.getMessage(), ex);
            
        } catch (Throwable ex) {
            LOG.error(ex.getMessage(), ex);
            //throw new TriggerException(ex.getMessage(), ex);
        }
    }

    /*
     * ****** unused methods follow ******
     */
    @Override
    @Deprecated
    public void prepare(int event, DBBroker broker, Txn transaction,
            XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
        // Ignored
    }

    @Override
    @Deprecated
    public void finish(int event, DBBroker broker, Txn transaction,
            XmldbURI documentPath, DocumentImpl document) {
        // Ignored
    }

    @Override
    public void beforeCreateDocument(DBBroker broker, Txn transaction,
            XmldbURI uri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeUpdateDocument(DBBroker broker, Txn transaction,
            DocumentImpl document) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeCopyDocument(DBBroker broker, Txn transaction,
            DocumentImpl document, XmldbURI newUri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeMoveDocument(DBBroker broker, Txn transaction,
            DocumentImpl document, XmldbURI newUri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeDeleteDocument(DBBroker broker, Txn transaction,
            DocumentImpl document) throws TriggerException {
        // Ignored
    }
    
    @Override
    public void beforeUpdateDocumentMetadata(DBBroker broker, Txn txn, DocumentImpl document) throws TriggerException {
        // Ignored
    }

    @Override
    @Deprecated
    public void prepare(int event, DBBroker broker, Txn transaction, Collection collection,
            Collection newCollection) throws TriggerException {
        // Ignored
    }

    @Override
    @Deprecated
    public void finish(int event, DBBroker broker, Txn transaction, Collection collection,
            Collection newCollection) {
        // Ignored
    }

    @Override
    public void beforeCreateCollection(DBBroker broker, Txn transaction,
            XmldbURI uri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeCopyCollection(DBBroker broker, Txn transaction, Collection collection,
            XmldbURI newUri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeMoveCollection(DBBroker broker, Txn transaction, Collection collection,
            XmldbURI newUri) throws TriggerException {
        // Ignored
    }

    @Override
    public void beforeDeleteCollection(DBBroker broker, Txn transaction,
            Collection collection) throws TriggerException {
        // Ignored
    }
    
    

}
