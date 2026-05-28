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
import org.exist.xquery.TerminatedException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

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
    /** {@code -1} means not yet initialized; use lazy BTree scan on first read. */
    private final AtomicLong entryCount = new AtomicLong(-1);

    public VectorStoreImpl(final BrokerPool pool, final Path dataDir) throws DBException {
        final Path file = dataDir.resolve(FILE_NAME);
        this.bfile = new BFile(pool, VECTOR_DBX_ID, FILE_FORMAT_VERSION_ID, true, file,
                pool.getCacheManager(), 1.25, 0.03);
    }

    /**
     * Returns the number of entries in the store, maintaining an incremental counter when possible.
     * The first call may scan the BTree ({@code O(n)}).
     */
    public long getEntryCount() throws IOException {
        final long cached = entryCount.get();
        if (cached >= 0) {
            return cached;
        }
        return initializeEntryCount();
    }

    /**
     * Clears the cached entry count so the next {@link #getEntryCount()} rescans the BTree.
     */
    public void resetEntryCountCache() {
        entryCount.set(-1);
    }

    Path getFilePath() {
        return bfile.getFile();
    }

    private long initializeEntryCount() throws IOException {
        try {
            final long count = bfile.getKeys().size();
            entryCount.compareAndSet(-1, count);
            return entryCount.get() >= 0 ? entryCount.get() : count;
        } catch (final BTreeException | TerminatedException e) {
            throw new IOException(e);
        }
    }

    private void adjustEntryCount(final long delta) {
        final long cached = entryCount.get();
        if (cached >= 0) {
            entryCount.addAndGet(delta);
        }
    }

    private int countKeysWithPrefix(final String docPath) throws IOException {
        try {
            final Value prefix = new Value(docPath + "!");
            final ArrayList<Value> keys = bfile.findKeys(new IndexQuery(IndexQuery.TRUNC_RIGHT, prefix));
            return keys.size();
        } catch (final BTreeException | TerminatedException e) {
            throw new IOException(e);
        }
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
        final boolean existed = bfile.get(k) != null;
        final FixedByteArray v = new FixedByteArray(vector, 0, vector.length);
        bfile.put(transaction, k, v, true);
        if (!existed) {
            adjustEntryCount(1);
        }
    }

    @Override
    public void remove(final Txn transaction, final String docPath, final NodeId nodeId) throws IOException {
        final Value k = key(docPath, nodeId);
        final boolean existed = bfile.get(k) != null;
        bfile.remove(transaction, k);
        if (existed) {
            adjustEntryCount(-1);
        }
    }

    @Override
    public void removeByDocument(final Txn transaction, final String docPath) throws IOException {
        try {
            final int removed = countKeysWithPrefix(docPath);
            final Value prefix = new Value(docPath + "!");
            bfile.removeAll(transaction, new IndexQuery(IndexQuery.TRUNC_RIGHT, prefix));
            if (removed > 0) {
                adjustEntryCount(-removed);
            }
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
