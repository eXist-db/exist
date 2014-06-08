/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 */
package org.exist.indexing;

import org.exist.collections.Collection;
import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.storage.MetaStorage;
import org.exist.storage.MetaStreamListener;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.exist.security.PermissionDeniedException;

/**
 * Internally used to dispatch an operation to each of the
 * registered indexes. An IndexController instance can be
 * retrieved via {@link org.exist.storage.DBBroker#getIndexController()}.
 * 
 */
public class IndexController {

    protected Map<String, IndexWorker> indexWorkers = new HashMap<String, IndexWorker>();

    protected DBBroker broker;
    protected StreamListener listener = null;    
    protected DocumentImpl currentDoc = null;
    protected int currentMode = StreamListener.UNKNOWN;
    protected XmldbURI currentURL = null;

    public IndexController(DBBroker broker) {
        this.broker = broker;
        final List<IndexWorker> workers = broker.getBrokerPool().getIndexManager().getWorkers(broker);
        for (final IndexWorker worker : workers) {
            indexWorkers.put(worker.getIndexId(), worker);
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
    public Map<String, Object> configure(NodeList configNodes, Map<String, String> namespaces) throws DatabaseConfigurationException {
        final Map<String, Object> map = new HashMap<String, Object>();
        Object conf;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            conf = indexWorker.configure(this, configNodes, namespaces);
            if (conf != null)
                {map.put(indexWorker.getIndexId(), conf);}
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
        return indexWorkers.get(indexId);
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
        for (final IndexWorker worker : indexWorkers.values()) {
            if (indexName.equals(worker.getIndexName()))
                {return worker;}
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
            {listener = null;}
        currentDoc = doc;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
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
            {listener = null;}
        currentMode = mode;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
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
    
    public void setURL(XmldbURI uri) {
        currentURL = uri;
    }

    /**
     * Flushes all index workers.
     */
    public void flush() {
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            indexWorker.flush();
        }
    }  

    /**
     * Remove all indexes defined on the specified collection.
     *
     * @param collection the collection to remove
     * @param broker the broker that will perform the operation
     */
    public void removeCollection(Collection collection, DBBroker broker, boolean reindex)
            throws PermissionDeniedException {
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            indexWorker.removeCollection(collection, broker, reindex);
        }
    }

    /**
     * Re-index all nodes below the specified root node, using the given mode.
     *
     * @param transaction the current transaction
     * @param reindexRoot the node from which reindexing should occur
     * @param mode the mode, one of {@link StreamListener#UNKNOWN}, {@link StreamListener#STORE},
     * {@link StreamListener#REMOVE_SOME_NODES} or {@link StreamListener#REMOVE_ALL_NODES}.
     */
    public void reindex(Txn transaction, StoredNode reindexRoot, int mode) {
        if (reindexRoot == null)
            {return;}
        reindexRoot = broker.objectWith(new NodeProxy(reindexRoot.getDocument(), reindexRoot.getNodeId()));
        setDocument(reindexRoot.getDocument(), mode);
        getStreamListener();
        IndexUtils.scanNode(broker, transaction, reindexRoot, listener);
        flush();
    }

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to re-index some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will return the top-most root.
     *
     * @param node the node to be modified.
     * @param path the NodePath of the node
     * @return the top-most root node to be re-indexed
     */
    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert) {
        return getReindexRoot(node, path, insert, false);
    }

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to re-index some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will return the top-most root.
     *
     * @param node the node to be modified.
     * @param path path the NodePath of the node
     * @param includeSelf if set to true, the current node itself will be included in the check
     * @return the top-most root node to be re-indexed
     */
    public StoredNode getReindexRoot(StoredNode node, NodePath path, boolean insert, boolean includeSelf) {
        StoredNode next, top = null;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            next = indexWorker.getReindexRoot(node, path, insert, includeSelf);
            if (next != null && (top == null || top.getNodeId().isDescendantOf(next.getNodeId()))) {
                top = next;
            }
        }
        if (top != null && top.getNodeId().equals(node.getNodeId()))
            {top = node;}
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
                // next.getWorker().setDocument(currentDoc, currentMode);
                next = next.getNextInChain();
            }
            return listener;
        }
        StreamListener first = null;
        StreamListener current, previous = null;
        for (final IndexWorker worker : indexWorkers.values()) {
            // wolf: setDocument() should have been called before
            //worker.setDocument(currentDoc, currentMode);
            current = worker.getListener();
            if (first == null) {
                first = current;
            } else {
                if (current != null)
                    {previous.setNextInChain(current);}
            }
            if (current != null)
                {previous = current;}
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
            case Node.CDATA_SECTION_NODE :
                listener.characters(transaction, (CharacterDataImpl) node, path);
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
           {listener.startElement(transaction, node, path);}
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
            {listener.endElement(transaction, node, path);}
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
            {listener.attribute(transaction, node, path);}
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
            {listener.characters(transaction, node, path);}
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
        for (final IndexWorker worker : indexWorkers.values()) {
            current = worker.getMatchListener(broker, proxy);
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

    public List<QueryRewriter> getQueryRewriters(XQueryContext context) {
        List<QueryRewriter> rewriters = new ArrayList<QueryRewriter>(5);
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            QueryRewriter rewriter = indexWorker.getQueryRewriter(context);
            if (rewriter != null) {
                rewriters.add(rewriter);
            }
        }
        return rewriters;
    }
    
    public void streamMetas(MetaStreamListener listener) {
        MetaStorage ms = broker.getDatabase().getMetaStorage();
        if (ms != null) {
                if (currentDoc != null)
                    ms.streamMetas(currentDoc.getURI(), listener);
                else if (currentURL != null)
                    ms.streamMetas(currentURL, listener);
        }
    }
    
    public void indexBinary(BinaryDocument doc) {
        setDocument(doc, StreamListener.STORE);
        //setMode(StreamListener.STORE);
        
        for (final IndexWorker worker : indexWorkers.values()) {
            worker.indexBinary(doc);
        }
    }
    

    public void removeIndex(BinaryDocument doc) {
        setDocument(doc, StreamListener.REMOVE_BINARY);
        //setMode(StreamListener.REMOVE_BINARY);
        
        for (final IndexWorker worker : indexWorkers.values()) {
            worker.removeIndex(doc.getURI());
        }
    }
}