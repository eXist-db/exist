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
package org.exist.storage.structural;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.dom.persistent.SymbolTable;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BTreeStore;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.w3c.dom.Element;

public class NativeStructuralIndex extends AbstractIndex implements RawBackupSupport {

    protected static final Logger LOG = LogManager.getLogger(NativeStructuralIndex.class);

    public final static String ID = NativeStructuralIndex.class.getName();
    public static final String FILE_NAME = "structure.dbx";
    public final static short FILE_FORMAT_VERSION_ID = 3;

    public static final byte STRUCTURAL_INDEX_ID = 1;

    /** The datastore for this node index */
    protected BTreeStore btree;

    protected LockManager lockManager;
    protected SymbolTable symbols;

    public NativeStructuralIndex() {
        //Nothing to do
    }

    @Override
    public void configure(BrokerPool pool, Path dataDir, Element config) throws DatabaseConfigurationException {
        super.configure(pool, dataDir, config);
        lockManager = pool.getLockManager();
        symbols = pool.getSymbols();
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        final Path file = getDataDir().resolve(FILE_NAME);
        LOG.debug("Creating '" + FileUtils.fileName(file) + "'...");
        try {
            btree = new BTreeStore(pool, STRUCTURAL_INDEX_ID, FILE_FORMAT_VERSION_ID, false,
                    file, pool.getCacheManager());
        } catch (final DBException e) {
            LOG.error("Failed to initialize structural index: " + e.getMessage(), e);
            throw new DatabaseConfigurationException(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws DBException {
        btree.close();
        btree = null;
    }

    @Override
    public void sync() throws DBException {
        if (btree == null) {
            return;
        }
        try(final ManagedLock<ReentrantLock> bfileLock = lockManager.acquireBtreeWriteLock(btree.getLockName())) {
            btree.flush();
        } catch (final LockException e) {
            LOG.warn("Failed to acquire lock for '" + FileUtils.fileName(btree.getFile()) + "'", e);
            //TODO : throw an exception ? -pb
        } catch (final DBException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        }
    }

    @Override
    public void remove() throws DBException {
        btree.closeAndRemove();
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new NativeStructuralIndexWorker(this);
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return false;
    }

	@Override
	public void backupToArchive(final RawDataBackup backup) throws IOException {
        // do not use try-with-resources here, closing the OutputStream will close the entire backup
//        try(final OutputStream os = backup.newEntry(FileUtils.fileName(btree.getFile()))) {
        try {
            final OutputStream os = backup.newEntry(FileUtils.fileName(btree.getFile()));
            btree.backupToStream(os);
        } finally {
            backup.closeEntry();
        }
	}
}
