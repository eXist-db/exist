/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 * 
 *  $Id$
 */
package org.exist.indexing;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.dom.AttrImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Internally used to dispatch an operation to each of the
 * registered indexes. An IndexController instance can be
 * retrieved via {@link org.exist.storage.DBBroker#getIndexController()}.
 * 
 */
public class IndexController {

    protected Map indexWorkers = new HashMap();

    protected DBBroker broker;
    protected StreamListener listener = null;    
    protected DocumentImpl currentDoc = null;
    protected int currentMode = StreamListener.UNKNOWN;
    
    public IndexController(DBBroker broker) {
    	this.broker = broker;
        IndexWorker[] workers = broker.getBrokerPool().getIndexManager().getWorkers(broker);
        for (int i = 0; i < workers.length; i++) {
            indexWorkers.put(workers[i].getIndexId(), workers[i]);
        }
    }

    /**
     * TODO: temporary method to plug in fulltext index.
     * Remove once new fulltext index module is ready.
     * 
     * @param worker
     */
    public void addIndexWorker(IndexWorker worker) {
        indexWorkers.put(worker.getIndexId(), worker);
    }

    /**
     * Configures all index workers registered with the db instance.
     * 
     * @param configNodes lists the top-level child nodes below the &lt;index&gt; element in collection.xconf
     * @param namespaces the active prefix/namespace map
     * @return an arbitrary configuration object to be kept for this index in the collection configuration
     * @throws DatabaseConfigurationException if a configuration error occurs
     */
    public Map configure(NodeList configNodes, Map namespaces) throws DatabaseConfigurationException {
        Map map = new HashMap();
        IndexWorker indexWorker;
        Object conf;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            conf = indexWorker.configure(this, configNodes, namespaces);
            if (conf != null)
                map.put(indexWorker.getIndexId(), conf);
        }
        return map;
    }    

    /**
     * Returns an {@link org.exist.indexing.IndexWorker} instance corresponding
     * to the specified type of index in indexId. The indexId should be the same one
     * as returned by {@link org.exist.indexing.IndexWorker#getIndexId()}.
     * 
     * @param indexId
     * @return instance of index worker
     */
    public IndexWorker getWorkerByIndexId(String indexId) {
        return (IndexWorker) indexWorkers.get(indexId);
    }

    /**
     * Returns an {@link org.exist.indexing.IndexWorker} instance corresponding
     * to the specified index named by indexName. The indexName should be the same one
     * as returned by {@link org.exist.indexing.IndexWorker#getIndexName()}.
     * 
     * @param indexName
     * @return instance of index worker
     */    
    public IndexWorker getWorkerByIndexName(String indexName) {
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
        	IndexWorker worker = (IndexWorker) i.next();
        	if (indexName.equals(worker.getIndexName()))
        		return worker;
        }
        return null;
    }
    
    /**
     * Sets the document for the next operation.
     * 
     * @param doc the document
     */    
    public void setDocument(DocumentImpl doc) {
        if (currentDoc != doc)
        	//Reset listener
        	listener = null;
        currentDoc = doc;
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.setDocument(currentDoc);
        }    	
    }

    /**
     * Sets the the mode for the next operation.
     * 
     * @param mode the mode, one of {@link StreamListener#UNKNOWN}, {@link StreamListener#STORE}, 
     * {@link StreamListener#REMOVE_SOME_NODES} or {@link StreamListener#REMOVE_ALL_NODES}.
     */
    public void setMode(int mode) {
        if (currentMode != mode)
        	//Reset listener
        	listener = null;
        currentMode = mode;
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.setMode(currentMode);
        }
    }
    
    /**
     * Returns the document for the next operation.
     * 
     * @return the document
     */
    public DocumentImpl getDocument() {
    	return currentDoc;
    }
    
    /**
     * Returns the mode for the next operation.
     * 
     * @return the document
     */
    public int getMode() {
    	return currentMode;
    }    
    
    /**
     * Sets the document and the mode for the next operation.
     * 
     * @param doc the document
     * @param mode the mode, one of {@link StreamListener#UNKNOWN}, {@link StreamListener#STORE}, 
     * {@link StreamListener#REMOVE_SOME_NODES} or {@link StreamListener#REMOVE_ALL_NODES}.
     */
    public void setDocument(DocumentImpl doc, int mode) {
    	setDocument(doc);
    	setMode(mode);
    }
    
    /**
     * Flushes all index workers.
     */
    public void flush() {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.flush();
        }
    }  
    
    /**
     * Remove all indexes defined on the specified collection.
     *
     * @param collection the collection to remove
     * @param broker the broker that will perform the operation
     */
    public void removeCollection(Collection collection, DBBroker broker) {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.removeCollection(collection, broker);
        }
    }    

    /**
     * Reindex all nodes below the specified root node, using the given mode.
     *
     * @param transaction the current transaction
     * @param reindexRoot the node from which reindexing should occur
     * @param mode the mode, one of {@link StreamListener#UNKNOWN}, {@link StreamListener#STORE}, 
     * {@link StreamListener#REMOVE_SOME_NODES} or {@link StreamListener#REMOVE_ALL_NODES}.
     */
    public void reindex(Txn transaction, StoredNode reindexRoot, int mode) {
        if (reindexRoot == null)
            return;
        reindexRoot = reindexRoot.getDocument().getBroker().objectWith(new NodeProxy(reindexRoot.getDocument(), reindexRoot.getNodeId()));
        setDocument(reindexRoot.getDocument(), mode);
        getStreamListener();
        IndexUtils.scanNode(transaction, reindexRoot, listener);
        flush();
    }
    
    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to reindex some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will call
     * {@link IndexWorker#getReindexRoot(org.exist.dom.StoredNode, org.exist.storage.NodePath, boolean)}
     * on each configured index. It will then return the top-most root.
     *
     * @param node the node to be modified.
     * @param path the NodePath of the node
     * @return the top-most root node to be reindexed
     */
    public StoredNode getReindexRoot(StoredNode node, NodePath path) {
        return getReindexRoot(node, path, false);
    }

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to reindex some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will call
     * {@link IndexWorker#getReindexRoot(org.exist.dom.StoredNode, org.exist.storage.NodePath, boolean)}
     * on each configured index. It will then return the top-most root.
     *
     * @param node the node to be modified.
     * @param path path the NodePath of the node
     * @param includeSelf if set to true, the current node itself will be included in the check
     * @return the top-most root node to be reindexed
     */
    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf) {
        IndexWorker indexWorker;
        StoredNode next, top = null;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            next = indexWorker.getReindexRoot(node, path, includeSelf);
            if (next != null && (top == null || top.getNodeId().isDescendantOf(next.getNodeId())))
                top = next;
        }
        if (top != null && top.getNodeId().equals(node.getNodeId()))
            top = node;
        return top;
    }
    
    /**
     * Returns a chain of {@link org.exist.indexing.StreamListener}, one
     * for each index configured on the current document for the current mode.
     * Note that the chain is reinitialized when the operating mode changes.
     * That allows workers to return different {@link org.exist.indexing.StreamListener}
     * for each mode.  
     *
     * @return the first listener in the chain of StreamListeners
     */
    public StreamListener getStreamListener() {
        if (listener != null) {
            StreamListener next = listener;
            while (next != null) {
                // wolf: setDocument() should have been called before
//                next.getWorker().setDocument(currentDoc, currentMode);
                next = next.getNextInChain();
            }
            return listener;
        }
        StreamListener first = null;
        StreamListener current, previous = null;
        IndexWorker worker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext();) {
            worker = (IndexWorker) i.next();
            // wolf: setDocument() should have been called before
//            worker.setDocument(currentDoc, currentMode);
            current = worker.getListener();
            if (first == null) {
                first = current;
            } else {
            	if (current != null)
            		previous.setNextInChain(current);
            }
            if (current != null)
            	previous = current;
        }
        listener = first;
        return listener;
    }    
    
    /**
     * Helper method: index a single node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */
    public void indexNode(Txn transaction, StoredNode node, NodePath path, StreamListener listener) {
        if (listener != null) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    listener.startElement(transaction, (ElementImpl) node, path);
                    break;
                case Node.TEXT_NODE :
                    listener.characters(transaction, (TextImpl) node, path);
                    break;
                case Node.ATTRIBUTE_NODE :
                    listener.attribute(transaction, (AttrImpl) node, path);
                    break;
            }
        }
    }
    
    /**
     * Helper method: index a single element node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */
    public void startElement(Txn transaction, ElementImpl node, NodePath path, StreamListener listener) {
        if (listener != null)
           listener.startElement(transaction, node, path);
    } 
    
    /**
     * Helper method: dispatch a single endElement event to the specified listener.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives index events
     */
    public void endElement(Txn transaction, ElementImpl node, NodePath path, StreamListener listener) {
        if (listener != null)
            listener.endElement(transaction, node, path);
    }    
    
    /**
     * Helper method: index a single attribute node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */     
    public void attribute(Txn transaction, AttrImpl node, NodePath path, StreamListener listener) {
        if (listener != null)
        	listener.attribute(transaction, node, path);
    }
    
    /**
     * Helper method: index a single text node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */    
    public void characters(Txn transaction, TextImpl node, NodePath path, StreamListener listener) {
        if (listener != null)
            listener.characters(transaction, node, path);       
    }
   
    /**
     * Returns the match listener for this node.
     * 
     * @param proxy a proxy to the node.
     * @return the MatchListener 
     */
    public MatchListener getMatchListener(NodeProxy proxy) {
        MatchListener first = null;
        MatchListener current, previous = null;
        IndexWorker worker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            worker = (IndexWorker) i.next();
            current = worker.getMatchListener(proxy);
            if (current != null) {
                if (first == null) {
                    first = current;
                } else {
                    previous.setNextInChain(current);
                }
                previous = current;
            }
        }
        return first;
    }    

}