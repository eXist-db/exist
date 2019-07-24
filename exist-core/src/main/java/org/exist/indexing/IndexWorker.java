/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.xquery.QueryRewriter;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.NodeList;

import java.util.Map;
import org.exist.security.PermissionDeniedException;

/**
 * Provide concurrent access to the index structure. Implements the core operations on the index.
 * The methods in this class are used in a multi-threaded environment. Every thread accessing the
 * database will have exactly one IndexWorker for every index. {@link org.exist.indexing.Index#getWorker(DBBroker)}
 * should thus return a new IndexWorker whenever it is  called. Implementations of IndexWorker have
 * to take care of synchronizing access to shared resources.
 */
public interface IndexWorker {

    /**
     * A key to a QName {@link java.util.List} "hint" to be used when the index scans its index entries
     */	
    public static final String VALUE_COUNT = "value_count";

    /**
     * Returns an ID which uniquely identifies this worker's index.
     * @return a unique name identifying this worker's index.
     */
    public String getIndexId();

    /**
     * Returns a name which uniquely identifies this worker's index.
     * @return a unique name identifying this worker's index.
     */
    public String getIndexName();

    /**
     * Read an index configuration from an collection.xconf configuration document.
     *
     * This method is called by the {@link org.exist.collections.CollectionConfiguration} while
     * reading the collection.xconf configuration file for a given collection. The configNodes
     * parameter lists all top-level child nodes below the &lt;index&gt; element in the
     * collection.xconf. The IndexWorker should scan this list and handle those elements
     * it understands.
     *
     * The returned Object will be stored in the collection configuration structure associated
     * with each collection. It can later be retrieved from the collection configuration, e.g. to
     * check if a given node should be indexed or not.
     *
     * @param controller the IndexController
     * @param configNodes lists the top-level child nodes below the &lt;index&gt; element in collection.xconf
     * @param namespaces the active prefix/namespace map
     * @return an arbitrary configuration object to be kept for this index in the collection configuration
     * @throws DatabaseConfigurationException if a configuration error occurs
     */
    Object configure(IndexController controller, NodeList configNodes,
        Map<String, String> namespaces) throws DatabaseConfigurationException;

    /**
     * Notify this worker to operate on the specified document.
     *
     * @param doc the document which is processed
     */
    void setDocument(DocumentImpl doc);

    /**
     * Notify this worker to operate on the specified document, using the mode
     * given. Mode will be one of {@link ReindexMode#UNKNOWN}, {@link ReindexMode#STORE},
     * {@link ReindexMode#REMOVE_SOME_NODES} or {@link ReindexMode#REMOVE_ALL_NODES}.
     *
     * @param doc the document which is processed
     * @param mode the current operation mode
     */
    void setDocument(DocumentImpl doc, ReindexMode mode);

    /**
     * Notify this worker to operate using the mode
     * given. Mode will be one of {@link ReindexMode#UNKNOWN}, {@link ReindexMode#STORE},
     * {@link ReindexMode#REMOVE_SOME_NODES} or {@link ReindexMode#REMOVE_ALL_NODES}.
     *
     * @param mode the current operation mode
     */
    void setMode(final ReindexMode mode);

    /**
     * Returns the document for the next operation.
     * 
     * @return the document
     */
    DocumentImpl getDocument();

    /**
     * Returns the mode for the next operation.
     * 
     * @return the document
     */
    ReindexMode getMode();

    /**
     * When adding or removing nodes to or from the document tree, it might become
     * necessary to re-index some parts of the tree, in particular if indexes are defined
     * on mixed content nodes. It will then return the top-most root.
     *
     * @param node the node to be modified.
     * @param path path the NodePath of the node
     * @param insert true if a node is being inserted or appended. In this case, the method
     *               will be called with the parent node as first argument. Usually a reindex is
     *               not required unless the index is defined on the parent node or an ancestor of it.
     * @param includeSelf if set to true, the current node itself will be included in the check
     * @param <T> class of the node returned
     * @return the top-most root node to be reindexed
     */
    <T extends IStoredNode> IStoredNode getReindexRoot(IStoredNode<T> node, NodePath path, boolean insert, boolean includeSelf);

    /**
     * Return a stream listener to index the current document in the current mode.
     * There will never be more than one StreamListener being used per thread, so it is safe
     * for the implementation to reuse a single StreamListener.
     *
     * Parameter mode specifies the type of the current operation.
     *
     * @return a StreamListener
     */
    StreamListener getListener();

    /**
     * Returns a {@link org.exist.indexing.MatchListener}, which can be used to filter
     * (and manipulate) the XML output generated by the serializer when serializing
     * query results. The method should return null if the implementation is not interested
     * in receiving serialization events.
     *
     * @param broker The broker that will perform the operation
     * @param proxy the NodeProxy which is being serialized
     * @return a MatchListener or null if the implementation does not want to receive
     * serialization events
     */
    MatchListener getMatchListener(DBBroker broker, NodeProxy proxy);

    /**
     * Flush the index. This method will be called when indexing a document. The implementation should
     * immediately process all data it has buffered (if there is any), release as many memory resources
     * as it can and prepare for being reused for a different job.
     */
    void flush();

    /**
     * Remove all indexes for the given collection, its subcollections and
     * all resources..
     *
     * @param collection The collection to remove
     * @param broker The broker that will perform the operation
     * @param reindex enable or disable reindex
     * @throws PermissionDeniedException in case user does not have sufficient rights
     */
    void removeCollection(Collection collection, DBBroker broker, boolean reindex) throws PermissionDeniedException;

    /** 
     * Checking index could be delegated to a worker. Use this method to do so.
     * @param broker The broker that will perform the operation
     * @return Whether or not the index if in a suitable state
     */
    boolean checkIndex(DBBroker broker);

    /** 
     * Return <strong>aggregated</strong> (on a document count basis) 
     * index entries for the specified document set. Aggregation can only occur if
     * the index entries can be compared, i.e. if the index implements 
     * {@link org.exist.indexing.OrderedValuesIndex}, otherwise each entry will be considered
     * as a single occurrence.
     * @param context the XQuery context
     * @param docs The documents to which the index entries belong
     * @param contextSet the contextSet to opereate on
     * @param hints Some "hints" for retrieving the index entries. See such hints in
     * {@link org.exist.indexing.OrderedValuesIndex} and {@link org.exist.indexing.QNamedKeysIndex}.
     * @return Occurrences objects that contain :
     * <ol>
     * <li>a <strong>string</strong> representation of the index entry. This may change in the future.</li>
     * <li>the number of occurrences for the index entry over all the documents</li>
     * <li>the list of the documents in which the index entry is</li>
     * </ol> 
     */
    public Occurrences[] scanIndex(XQueryContext context, DocumentSet docs, NodeSet contextSet, Map<?,?> hints);

    /**
     * Returns a {@link QueryRewriter} to be called by the query optimizer.
     *
     * @param context the current XQuery context
     * @return the query rewriter or null if the index does no rewriting
     */
    QueryRewriter getQueryRewriter(XQueryContext context);

    //TODO : a scanIndex() method that would return an unaggregated list of index entries ?

}
