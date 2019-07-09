/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist-db Project
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

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.TextImpl;
import org.exist.dom.persistent.AbstractCharacterData;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.collections.Collection;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
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
 */
public class IndexController {

    private final Map<String, IndexWorker> indexWorkers = new HashMap<>();

    private final DBBroker broker;
    private StreamListener listener = null;
    private DocumentImpl currentDoc = null;
    private ReindexMode currentMode = ReindexMode.UNKNOWN;
    private boolean reindexing;

    public IndexController(final DBBroker broker) {
        this.broker = broker;
        final List<IndexWorker> workers = broker.getBrokerPool().getIndexManager().getWorkers(broker);
        for (final IndexWorker worker : workers) {
            indexWorkers.put(worker.getIndexId(), worker);
        }
    }

    /**
     * Configures all index workers registered with the db instance.
     * 
     * @param configNodes lists the top-level child nodes below the &lt;index&gt; element in collection.xconf
     * @param namespaces the active prefix/namespace map
     * @return an arbitrary configuration object to be kept for this index in the collection configuration
     * @throws DatabaseConfigurationException if a configuration error occurs
     */
    public Map<String, Object> configure(final NodeList configNodes, final Map<String, String> namespaces) throws DatabaseConfigurationException {
        final Map<String, Object> map = new HashMap<>();
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            final Object conf = indexWorker.configure(this, configNodes, namespaces);
            if (conf != null) {
                map.put(indexWorker.getIndexId(), conf);
            }
        }
        return map;
    }

    /**
     * Returns an {@link org.exist.indexing.IndexWorker} instance corresponding
     * to the specified type of index in indexId. The indexId should be the same one
     * as returned by {@link org.exist.indexing.IndexWorker#getIndexId()}.
     * 
     * @param indexId The id of the index
     * @return instance of index worker
     */
    public IndexWorker getWorkerByIndexId(final String indexId) {
        return indexWorkers.get(indexId);
    }

    /**
     * Returns an {@link org.exist.indexing.IndexWorker} instance corresponding
     * to the specified index named by indexName. The indexName should be the same one
     * as returned by {@link org.exist.indexing.IndexWorker#getIndexName()}.
     * 
     * @param indexName The name of the index
     * @return instance of index worker
     */    
    public IndexWorker getWorkerByIndexName(final String indexName) {
        for (final IndexWorker worker : indexWorkers.values()) {
            if (indexName.equals(worker.getIndexName())) {
                return worker;
            }
        }
        return null;
    }

    /**
     * Sets the document for the next operation.
     * 
     * @param doc the document
     *
     * @deprecated use getStreamListener(DocumentImpl, ReindexMode)
     */
    @Deprecated
    public void setDocument(final DocumentImpl doc) {
        if (currentDoc != doc) {
            //Reset listener
            listener = null;
        }
        currentDoc = doc;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            indexWorker.setDocument(currentDoc);
        }
    }

    /**
     * Sets the the mode for the next operation.
     * 
     * @param mode the mode, one of {@link ReindexMode#UNKNOWN}, {@link ReindexMode#STORE},
     * {@link ReindexMode#REMOVE_SOME_NODES} or {@link ReindexMode#REMOVE_ALL_NODES}.
     *
     * @deprecated use getStreamListener(DocumentImpl, ReindexMode)
     */
    @Deprecated
    public void setMode(final ReindexMode mode) {
        if (currentMode != mode) {
            //Reset listener
            listener = null;
        }
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
    public ReindexMode getMode() {
        return currentMode;
    }

    /**
     * Sets the document and the mode for the next operation.
     * 
     * @param doc the document
     * @param mode the mode, one of {@link ReindexMode#UNKNOWN}, {@link ReindexMode#STORE},
     * {@link ReindexMode#REMOVE_SOME_NODES} or {@link ReindexMode#REMOVE_ALL_NODES}.
     *
     * @deprecated use getStreamListener(DocumentImpl, ReindexMode)
     */
    @Deprecated
    public void setDocument(final DocumentImpl doc, final ReindexMode mode) {
        setDocument(doc);
        setMode(mode);
    }

    /**
     * Flushes all index workers.
     */
    public void flush() {
        indexWorkers.values().forEach(IndexWorker::flush);
    }  

    /**
     * Remove all indexes defined on the specified collection.
     *
     * @param collection the collection to remove
     * @param broker the broker that will perform the operation
     * @param reindex enable or disable reindexing after removal
     * @throws PermissionDeniedException in case user does not have sufficient rights
     */
    public void removeCollection(final Collection collection, final DBBroker broker, final boolean reindex)
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
     * @param mode the mode, one of {@link ReindexMode#UNKNOWN}, {@link ReindexMode#STORE},
     * {@link ReindexMode#REMOVE_SOME_NODES} or {@link ReindexMode#REMOVE_ALL_NODES}.
     */
    public void reindex(final Txn transaction, final IStoredNode<? extends IStoredNode> reindexRoot, final ReindexMode mode) {
        if (reindexRoot == null) {
            return;
        }

        setReindexing(true);
        try {
            final IStoredNode<? extends IStoredNode> node = broker.objectWith(new NodeProxy(reindexRoot.getOwnerDocument(), reindexRoot.getNodeId()));
            listener = getStreamListener(node.getOwnerDocument(), mode);
            listener.startIndexDocument(transaction);
            try {
                IndexUtils.scanNode(broker, transaction, node, listener);
            } finally {
                listener.endIndexDocument(transaction);
            }
            flush();
        } finally {
            setReindexing(false);
        }
    }

    public boolean isReindexing() {
        return reindexing;
    }

    private void setReindexing(final boolean reindexing) {
        this.reindexing = reindexing;
    }

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to re-index some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will return the top-most root.
     *
     * @param node the node to be modified.
     * @param path the NodePath of the node
     * @param insert TODO: document
     * @return the top-most root node to be re-indexed
     */
    public IStoredNode getReindexRoot(final IStoredNode node, final NodePath path, final boolean insert) {
        return getReindexRoot(node, path, insert, false);
    }

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to re-index some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. This method will return the top-most root.
     *
     * @param node the node to be modified.
     * @param path path the NodePath of the node
     * @param insert TODO: document
     * @param includeSelf if set to true, the current node itself will be included in the check
     * @return the top-most root node to be re-indexed
     */
    public IStoredNode getReindexRoot(final IStoredNode node, final NodePath path, final boolean insert, final boolean includeSelf) {
        IStoredNode next;
        IStoredNode top = null;
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            next = indexWorker.getReindexRoot(node, path, insert, includeSelf);
            if (next != null && (top == null || top.getNodeId().isDescendantOf(next.getNodeId()))) {
                top = next;
            }
        }
        if (top != null && top.getNodeId().equals(node.getNodeId())) {
            top = node;
        }
        return top;
    }

    public StreamListener getStreamListener(final DocumentImpl doc, final ReindexMode mode) {
        setDocument(doc);
        setMode(mode);
        return getStreamListener();
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
        StreamListener previous = null;
        for (final IndexWorker worker : indexWorkers.values()) {
            // wolf: setDocument() should have been called before
            //worker.setDocument(currentDoc, currentMode);
            final StreamListener current = worker.getListener();
            if (first == null) {
                first = current;
            } else {
                if (current != null) {
                    previous.setNextInChain(current);
                }
            }
            if (current != null) {
                previous = current;
            }
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
    public void indexNode(final Txn transaction, final IStoredNode node, final NodePath path, final StreamListener listener) {
        if (listener != null) {
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    listener.startElement(transaction, (ElementImpl) node, path);
                    break;
                case Node.TEXT_NODE :
                case Node.CDATA_SECTION_NODE :
                    listener.characters(transaction, (AbstractCharacterData) node, path);
                    break;
                case Node.ATTRIBUTE_NODE :
                    listener.attribute(transaction, (AttrImpl) node, path);
                    break;
            }
        }
    }

    /**
     * Helper method: indexing is starting for a document
     *
     * @param transaction the current transaction
     * @param listener the StreamListener which receives the index events
     */
    public void startIndexDocument(final Txn transaction, final StreamListener listener) {
        if (listener != null) {
            listener.startIndexDocument(transaction);
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
    public void startElement(final Txn transaction, final ElementImpl node, final NodePath path, final StreamListener listener) {
        if (listener != null) {
            listener.startElement(transaction, node, path);
        }
    }

    /**
     * Helper method: dispatch a single endElement event to the specified listener.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives index events
     */
    public void endElement(final Txn transaction, final ElementImpl node, final NodePath path, final StreamListener listener) {
        if (listener != null) {
            listener.endElement(transaction, node, path);
        }
    }

    /**
     * Helper method: index a single attribute node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */     
    public void attribute(final Txn transaction, final AttrImpl node, final NodePath path, final StreamListener listener) {
        if (listener != null) {
            listener.attribute(transaction, node, path);
        }
    }

    /**
     * Helper method: index a single text node which has been added during an XUpdate or XQuery update expression.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives the index events
     */    
    public void characters(final Txn transaction, final TextImpl node, final NodePath path, final StreamListener listener) {
        if (listener != null) {
            listener.characters(transaction, node, path);
        }
    }

    /**
     * Helper method: indexing has finished for a document
     *
     * @param transaction the current transaction
     * @param listener the StreamListener which receives the index events
     */
    public void endIndexDocument(final Txn transaction, final StreamListener listener) {
        if (listener != null) {
            listener.endIndexDocument(transaction);
        }
    }

    /**
     * Returns the match listener for this node.
     * 
     * @param proxy a proxy to the node.
     * @return the MatchListener 
     */
    public MatchListener getMatchListener(final NodeProxy proxy) {
        MatchListener first = null;
        MatchListener previous = null;
        for (final IndexWorker worker : indexWorkers.values()) {
            final MatchListener current = worker.getMatchListener(broker, proxy);
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

    public List<QueryRewriter> getQueryRewriters(final XQueryContext context) {
        final List<QueryRewriter> rewriters = new ArrayList<>(5);
        for (final IndexWorker indexWorker : indexWorkers.values()) {
            final QueryRewriter rewriter = indexWorker.getQueryRewriter(context);
            if (rewriter != null) {
                rewriters.add(rewriter);
            }
        }
        return rewriters;
    }
}
