package org.exist.indexing.sort;

import org.apache.log4j.Logger;
import org.exist.backup.RawDataBackup;
import org.exist.indexing.AbstractIndex;
import org.exist.indexing.IndexWorker;
import org.exist.indexing.RawBackupSupport;
import org.exist.storage.DBBroker;
import org.exist.storage.btree.DBException;
import org.exist.storage.index.BTreeStore;
import org.exist.storage.lock.Lock;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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
 *
 */
public class SortIndex extends AbstractIndex implements RawBackupSupport {

    protected static final Logger LOG = Logger.getLogger(SortIndex.class);

    public static final String ID = SortIndex.class.getName();
    public static final String FILE_NAME = "sort.dbx";
    public static final byte SORT_INDEX_ID = 0x10;
    public static final double DEFAULT_SORT_KEY_THRESHOLD = 0.01;

    protected BTreeStore btree;
    
    public SortIndex() {
    }
    
    public String getIndexId() {
    	return ID;
    }

    @Override
    public void open() throws DatabaseConfigurationException {
        File file = new File(getDataDir(), FILE_NAME);
        LOG.debug("Creating '" + file.getName() + "'...");
        try {
            btree = new BTreeStore(db, SORT_INDEX_ID, false,
                    file, db.getCacheManager(), DEFAULT_SORT_KEY_THRESHOLD);
        } catch (DBException e) {
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
        final Lock lock = btree.getLock();
        try {
            lock.acquire(Lock.WRITE_LOCK);
            btree.flush();
        } catch (LockException e) {
            LOG.warn("Failed to acquire lock for '" + btree.getFile().getName() + "'", e);
            //TODO : throw an exception ? -pb
        } catch (DBException e) {
            LOG.error(e.getMessage(), e);
            //TODO : throw an exception ? -pb
        } finally {
            lock.release(Lock.WRITE_LOCK);
        }
    }

    @Override
    public void remove() throws DBException {
        btree.closeAndRemove();
    }

    @Override
    public IndexWorker getWorker(DBBroker broker) {
        return new SortIndexWorker(this);
    }

    @Override
    public boolean checkIndex(DBBroker broker) {
        return false;
    }

	@Override
	public void backupToArchive(RawDataBackup backup) throws IOException {
        OutputStream os = backup.newEntry(btree.getFile().getName());
        btree.backupToStream(os);
        backup.closeEntry();
	}
	
}
