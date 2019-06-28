package org.exist.indexing.sort;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BTreeStore;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.concurrent.locks.ReentrantLock;

/**
 * SortIndex helps to improve the performance of 'order by' expressions in XQuery.
 * The index simply maps node ids to an integer index, which corresponds to the position
 * of the node in the pre-ordered set.
 *
 * The creation and maintenance of the index is handled by the user. XQuery functions
 * are provided to create, delete and query an index.
 *
 * Every sort index has an id by which it is identified and distinguished from other indexes
 * on the same node set.
 */
public class SortIndex extends AbstractIndex implements RawBackupSupport {

    public static final String ID = SortIndex.class.getName();
    public static final String FILE_NAME = "sort.dbx";
    public final static short FILE_FORMAT_VERSION_ID = 3;
    public static final byte SORT_INDEX_ID = 0x10;
    protected static final Logger LOG = LogManager.getLogger(SortIndex.class);
    protected BTreeStore btree;

    @Override
    public void open() throws DatabaseConfigurationException {
        final Path file = getDataDir().resolve(FILE_NAME);
        LOG.debug("Creating '" + FileUtils.fileName(file) + "'...");
        try {
            btree = new BTreeStore(pool, SORT_INDEX_ID, FILE_FORMAT_VERSION_ID, false,
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
        if (btree == null)
            return;
        final LockManager lockManager = pool.getLockManager();
        try(final ManagedLock<ReentrantLock> btreeLock = lockManager.acquireBtreeWriteLock(btree.getLockName())) {
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
    public IndexWorker getWorker(final DBBroker broker) {
        return new SortIndexWorker(this);
    }

    @Override
    public boolean checkIndex(final DBBroker broker) {
        return false;
    }

    @Override
    public void backupToArchive(final RawDataBackup backup) throws IOException {
        try (final OutputStream os = backup.newEntry(FileUtils.fileName(btree.getFile()))) {
            btree.backupToStream(os);
        } finally {
            backup.closeEntry();
        }
    }

}
