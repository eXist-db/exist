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
package org.exist.storage.vector;

import org.exist.backup.RawDataBackup;
import org.exist.storage.txn.Txn;

import javax.annotation.Nullable;
import java.io.Closeable;
import java.io.IOException;

/**
 * Store for dense float vectors keyed by (collectionPath, docName, nodeId).
 * Used for reindex efficiency: vectors can be read from here instead of parsing XML.
 */
public interface VectorStore extends Closeable {

    /**
     * Gets a vector by document path and node id.
     *
     * @param docPath document URI (e.g. /db/collection/doc.xml)
     * @param nodeId  node id within the document
     * @return float32 bytes, or null if not found
     */
    @Nullable
    byte[] get(String docPath, org.exist.numbering.NodeId nodeId) throws IOException;

    /**
     * Stores a vector.
     *
     * @param transaction the current transaction
     * @param docPath     document URI
     * @param nodeId      node id
     * @param vector      float32 bytes (raw, little-endian)
     */
    void put(Txn transaction, String docPath, org.exist.numbering.NodeId nodeId, byte[] vector) throws IOException;

    /**
     * Removes a vector.
     *
     * @param transaction the current transaction
     * @param docPath     document URI
     * @param nodeId      node id
     */
    void remove(Txn transaction, String docPath, org.exist.numbering.NodeId nodeId) throws IOException;

    /**
     * Removes all vectors for a document.
     *
     * @param transaction the current transaction
     * @param docPath     document URI
     */
    void removeByDocument(Txn transaction, String docPath) throws IOException;

    /**
     * Backs up the store to the given backup.
     */
    void backupToArchive(RawDataBackup backup) throws IOException;
}
