/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.dom;

import org.w3c.dom.Node;

/**
 * Decides which nodes of a document get an entry in the DOM B-tree ({@code dom.dbx}), which maps the id of a node to the
 * address of its record.
 *
 * <p>A node without an entry is found by walking the document from its nearest ancestor that has one, so what has an
 * entry sets the cost of finding a node by its id. Up to {@code index-depth} levels every element has one; text,
 * attribute and CDATA records never do.</p>
 *
 * <p>The rule is asked in two places that do not agree about every node. Storing a node gives an entry to everything
 * but text, attributes and CDATA up to the index depth, so comments and processing instructions get one. Rebuilding the
 * entries of a document (a reindex that stores, and nodes inserted by an update) gives one to every element up to the
 * index depth and to anything at level 1, so comments and processing instructions below the root level lose theirs.
 * Both rules are kept as they were, in one place; whether they should be one is a separate question.</p>
 *
 * <p>With an index depth of 0 nothing gets an entry when storing, but the root level still does when rebuilding.</p>
 */
public final class EntryPolicy {

    private final int indexDepth;

    /**
     * Creates the policy for an index depth.
     *
     * @param indexDepth the number of levels, counted from the root element at level 1, whose elements have an entry
     */
    public EntryPolicy(final int indexDepth) {
        this.indexDepth = indexDepth;
    }

    /**
     * Returns the index depth.
     *
     * @return the number of levels whose elements have an entry
     */
    public int getIndexDepth() {
        return indexDepth;
    }

    /**
     * Tells whether a node gets an entry when it is stored.
     *
     * @param nodeType the DOM node type of the node
     * @param level the level of the node in the document, 1 for the root element
     * @return true if the record of the node is stored with an entry
     */
    public boolean entryOnStore(final short nodeType, final int level) {
        if (nodeType == Node.TEXT_NODE || nodeType == Node.ATTRIBUTE_NODE || nodeType == Node.CDATA_SECTION_NODE) {
            return false;
        }
        return level <= indexDepth;
    }

    /**
     * Tells whether a node gets an entry when the entries of its document are rebuilt, or when it is inserted by an
     * update.
     *
     * <p>All nodes at level 1 get one, see https://github.com/eXist-db/exist/issues/1691, and only elements below.</p>
     *
     * @param nodeType the DOM node type of the node
     * @param level the level of the node in the document, 1 for the root element
     * @return true if an entry is added for the node
     */
    public boolean entryOnReindex(final short nodeType, final int level) {
        return level == 1 || (nodeType == Node.ELEMENT_NODE && level <= indexDepth);
    }
}
