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

import org.w3c.dom.NodeList;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.StoredNode;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.Occurrences;
import org.exist.collections.Collection;
import org.exist.storage.NodePath;

import java.util.Map;

/**
 * Provide concurrent access to the index structure. Implements the core operations on the index.
 * The methods in this class are used in a multi-threaded environment. Every thread accessing the
 * database will have exactly one IndexWorker for every index. {@link org.exist.indexing.Index#getWorker()}
 * should thus return a new IndexWorker whenever it is  called. Implementations of IndexWorker have
 * to take care of synchronizing access to shared resources.
 */
public interface IndexWorker {

    /**
     * Returns an ID which uniquely identifies this index. This will usually be the class name.
     * @return a unique ID identifying this index.
     */
    String getIndexId();

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
     * @param configNodes lists the top-level child nodes below the &lt;index&gt; element in collection.xconf
     * @param namespaces the active prefix/namespace map
     * @return an arbitrary configuration object to be kept for this index in the collection configuration
     * @throws DatabaseConfigurationException if a configuration error occurs
     */
    Object configure(IndexController controller, NodeList configNodes, Map namespaces) throws DatabaseConfigurationException;

    /**
     * Flush the index. This method will be called when indexing a document. The implementation should
     * immediately process all data it has buffered (if there is any), release as many memory resources
     * as it can and prepare for being reused for a different job.
     */
    void flush();

    /**
     * Return a stream listener to index the specified document. There will never be more than one
     * StreamListener being used per thread, so it is safe for the implementation to reuse a
     * single StreamListener.
     *
     * Parameter mode specifies the type of the current operation. 
     *
     * @param mode one of {@link StreamListener#STORE} or {@link StreamListener#REMOVE}
     * @param document the document to be indexed.
     * @return a StreamListener
     */
    StreamListener getListener(int mode, DocumentImpl document);

    /**
     * Remove all indexes for the given collection, its subcollections and
     * all resources..
     *
     * @param collection
     */
    void removeCollection(Collection collection);

    Occurrences[] scanIndex(DocumentSet docs);

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
    StoredNode getReindexRoot(StoredNode node, NodePath path, boolean includeSelf);
}