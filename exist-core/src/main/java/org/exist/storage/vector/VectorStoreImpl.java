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
import org.exist.numbering.NodeId;
import org.exist.storage.BrokerPool;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.DBException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.txn.Txn;
import org.exist.util.FileUtils;
import org.exist.util.FixedByteArray;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * BFile-backed implementation of VectorStore.
 * Key: docPath + "!" + nodeId.toString()
 * Value: raw float32 bytes (little-endian).
 */
public class VectorStoreImpl implements VectorStore {

    public static final String FILE_NAME = "vector.dbx";
    public static final short FILE_FORMAT_VERSION_ID = 1;
    public static final byte VECTOR_DBX_ID = 0x20;

    private final BFile bfile;

    public VectorStoreImpl(final BrokerPool pool, final Path dataDir) throws DBException {
        final Path file = dataDir.resolve(FILE_NAME);
        this.bfile = new BFile(pool, VECTOR_DBX_ID, FILE_FORMAT_VERSION_ID, true, file,
                pool.getCacheManager(), 1.25, 0.03);
    }

    private static Value key(final String docPath, final NodeId nodeId) {
        return new Value(docPath + "!" + nodeId.toString());
    }

    @Override
    @Nullable
    public byte[] get(final String docPath, final NodeId nodeId) throws IOException {
        final Value key = key(docPath, nodeId);
        final Value val = bfile.get(key);
        return val != null ? val.getData() : null;
    }

    @Override
    public void put(final Txn transaction, final String docPath, final NodeId nodeId, final byte[] vector) throws IOException {
        final Value k = key(docPath, nodeId);
        final FixedByteArray v = new FixedByteArray(vector, 0, vector.length);
        bfile.put(transaction, k, v, true);
    }

    @Override
    public void remove(final Txn transaction, final String docPath, final NodeId nodeId) throws IOException {
        bfile.remove(transaction, key(docPath, nodeId));
    }

    @Override
    public void removeByDocument(final Txn transaction, final String docPath) throws IOException {
        try {
            final Value prefix = new Value(docPath + "!");
            bfile.removeAll(transaction, new IndexQuery(IndexQuery.TRUNC_RIGHT, prefix));
        } catch (final BTreeException e) {
            throw new IOException(e);
        }
    }

    /**
     * Backs up vector.dbx to the archive. Do not use try-with-resources on the stream returned by
     * {@link RawDataBackup#newEntry(String)}—it is the underlying archive stream and closing it
     * would close the entire backup.
     */
    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException {
        try {
            final OutputStream os = backup.newEntry(FileUtils.fileName(bfile.getFile()));
            bfile.backupToStream(os);
        } finally {
            backup.closeEntry();
        }
    }

    @Override
    public void close() throws IOException {
        try {
            bfile.close();
        } catch (final DBException e) {
            throw new IOException(e);
        }
    }
}
