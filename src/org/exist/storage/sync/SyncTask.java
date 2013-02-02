package org.exist.storage.sync;

import java.io.File;
import java.util.Properties;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.util.Configuration;

public class SyncTask implements SystemTask {

    private final static String JOB_GROUP = "eXist.internal";
    private final static String JOB_NAME = "Sync";

    public static String getJobName() {
        return JOB_NAME;
    }

    public static String getJobGroup() {
        return JOB_GROUP;
    }

    private File partition;
    private long diskSpaceMin = 64 * 1024L * 1024L;

    @Override
    public boolean afterCheckpoint() {
        // a checkpoint is created by the MAJOR_SYNC event
        return false;
    }

    @Override
    public void configure(Configuration config, Properties properties)
            throws EXistException {
        Integer min = (Integer) config.getProperty(BrokerPool.DISK_SPACE_MIN_PROPERTY);
        if (min != null)
            diskSpaceMin = min * 1024L * 1024L;

        File homeDir = config.getExistHome();
        partition = homeDir;
        File parent = homeDir.getParentFile();
        while (parent != null) {
            partition = parent;
            parent = parent.getParentFile();
        }
        LOG.info("Using Partition: " + partition.getAbsolutePath() + ". Minimal disk space required for database " +
            "to continue operations: " + diskSpaceMin);
    }

    @Override
    public void execute(DBBroker broker) throws EXistException {
        BrokerPool pool = broker.getBrokerPool();
        if (!checkDiskSpace(pool)) {
            LOG.fatal("Partition " + partition.getAbsolutePath() + " is running out of disk space. " +
                "Switching eXist-db to read only to prevent data loss!");
            pool.setReadOnly();
        }
        if(System.currentTimeMillis() - pool.getLastMajorSync() >
                pool.getMajorSyncPeriod()) {
            pool.sync(broker, Sync.MAJOR_SYNC);
        } else {
            pool.sync(broker, Sync.MINOR_SYNC);
        }
    }

    private boolean checkDiskSpace(BrokerPool pool) {
        long space = partition.getUsableSpace();
        //LOG.info("Usable space on partition " + partition.getAbsolutePath() + ": " + (space / 1024 / 1024) + "mb");
        return space > diskSpaceMin;
    }
}