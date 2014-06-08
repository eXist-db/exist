package org.exist.storage.index;

import org.exist.Database;
import org.exist.storage.CacheManager;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ReentrantReadWriteLock;

import java.io.File;

public class BTreeStore extends BTree {

    public final static short FILE_FORMAT_VERSION_ID = 2;

    protected Lock lock = null;

    public BTreeStore(Database db, byte fileId, boolean transactional, File file, CacheManager cacheManager, double growthThreshold) throws DBException {
        super(db, fileId, transactional, cacheManager, file, growthThreshold);
        lock = new ReentrantReadWriteLock(file.getName());

        if(exists()) {
            open(FILE_FORMAT_VERSION_ID);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating data file: " + getFile().getName());
            }
            create((short)-1);
        }
        setSplitFactor(0.7);
    }

    @Override
    public Lock getLock() {
        return lock;
    }

    public short getFileVersion() {
        return FILE_FORMAT_VERSION_ID;
    }
}
