package org.exist.storage.index;

import org.exist.storage.BrokerPool;
import org.exist.storage.DefaultCacheManager;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.DBException;
import org.exist.util.FileUtils;

import java.nio.file.Path;

public class BTreeStore extends BTree {

    public BTreeStore(final BrokerPool pool, final byte fileId, final short fileVersion, final boolean recoverEnabled, final Path file, final DefaultCacheManager cacheManager) throws DBException {
        super(pool, fileId, fileVersion, recoverEnabled, cacheManager, file);

        if(exists()) {
            open(fileVersion);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Creating data file: " + FileUtils.fileName(getFile()));
            }
            create((short)-1);
        }
        setSplitFactor(0.7);
    }

    @Override
    public String getLockName() {
        return FileUtils.fileName(getFile());
    }
}
