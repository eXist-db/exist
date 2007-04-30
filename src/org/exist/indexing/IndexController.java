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

    protected StreamListener listener = null;

    protected StoredNode reindexNode = null;
    
    protected int currentMode = StreamListener.UNKKNOWN;
    
    public IndexController(DBBroker broker) {
        IndexWorker[] workers = broker.getBrokerPool().getIndexManager().getWorkers();
        for (int i = 0; i < workers.length; i++) {
            indexWorkers.put(workers[i].getIndexId(), workers[i]);
        }
    }

    /**
     * Returns an {@link org.exist.indexing.IndexWorker} instance corresponding
     * to the specified type of index in indexId. The indexId should be the same one
     * as returned by {@link org.exist.indexing.IndexWorker#getIndexId()}.
     * 
     * @param indexId
     * @return instance of index worker
     */
    public IndexWorker getIndexWorkerById(String indexId) {
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
    public IndexWorker getIndexWorkerByName(String indexName) {
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
        	IndexWorker worker = (IndexWorker) i.next();
        	if (indexName.equals(worker.getIndexName()))
        		return worker;
        }
        return null;
    }
    
    /**
     * Returns a chain of {@link org.exist.indexing.StreamListener}, one
     * for each index configured.
     * Note that the chain is reinitialized when the operating mode changes.
     * That allows workers to return different {@link org.exist.indexing.StreamListener}
     * for each mode.  
     *
     * @param document
     * @param mode
     * @return chain of StreamListeners
     */
    public StreamListener getStreamListener(DocumentImpl document, int mode) {
        if (currentMode != mode) {
            currentMode = mode;
        } else if (listener != null) {
            StreamListener next = listener;
            while (next != null) {
                next.getWorker().setDocument(document, mode);
                next = next.getNextInChain();
            }
            return listener;
        }
        StreamListener first = null;
        StreamListener current, previous = null;
        IndexWorker worker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext();) {
            worker = (IndexWorker) i.next();
            worker.setDocument(document, mode);
            current = worker.getListener(mode, document);
            if (first == null) {
                first = current;
            } else {
                previous.setNextInChain(current);
            }
            previous = current;
        }
        listener = first;
        return listener;
    }

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

    /**
     * Configures all index modules registered with the db instance.
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
     * Flushes all index modules.
     */
    public void flush() {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.flush();
        }
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
     * Reindex all nodes below the specified root node, using the given mode. mode should be
     * one of {@link StreamListener#STORE} or {@link StreamListener#REMOVE_NODES}.
     *
     * @param transaction the current transaction
     * @param reindexRoot the root node to reindex
     * @param mode one of {@link StreamListener#STORE} or {@link StreamListener#REMOVE_NODES}.
     */
    public void reindex(Txn transaction, StoredNode reindexRoot, int mode) {
        if (reindexRoot == null)
            return;
        reindexRoot = reindexRoot.getDocument().getBroker().objectWith(new NodeProxy(reindexRoot.getDocument(), reindexRoot.getNodeId()));
        setDocument(reindexRoot.getDocument(), mode);
        getStreamListener(reindexRoot.getDocument(), mode);
        IndexUtils.scanNode(transaction, reindexRoot, listener);
        flush();
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
     * Helper method: dispatch a single endElement event to the specified listener.
     *
     * @param transaction the current transaction
     * @param node the node to index
     * @param path the node's NodePath
     * @param listener the StreamListener which receives index events
     */
    public void indexEndElement(Txn transaction, ElementImpl node, NodePath path, StreamListener listener) {
        if (listener != null)
            listener.endElement(transaction, node, path);
    }
    
    public void setDocument(DocumentImpl doc, int mode) {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.setDocument(doc, mode);
        }    	
    }

    /**
     * Remove all indexes defined on the specified collection.
     *
     * @param collection the collection to remove
     */
    public void removeCollection(Collection collection) {
        IndexWorker indexWorker;
        for (Iterator i = indexWorkers.values().iterator(); i.hasNext(); ) {
            indexWorker = (IndexWorker) i.next();
            indexWorker.removeCollection(collection);
        }
    }
}