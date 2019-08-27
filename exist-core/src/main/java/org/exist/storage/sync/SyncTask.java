/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011-2013 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.storage.sync;

import java.nio.file.FileStore;
import java.nio.file.Path;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.scheduler.JobDescription;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.SystemTask;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;

public class SyncTask implements SystemTask {

    private final static Logger LOG = LogManager.getLogger(SyncTask.class);

    private final static String JOB_NAME = "Sync";

    public static String getJobName() {
        return JOB_NAME;
    }

    public static String getJobGroup() {
        return JobDescription.EXIST_INTERNAL_GROUP;
    }

    private Path dataDir;
    private long diskSpaceMin;

    @Override
    public boolean afterCheckpoint() {
        // a checkpoint is created by the MAJOR_SYNC event
        return false;
    }

    @Override
    public String getName() {
        return getJobName();
    }

    @Override
    public void configure(final Configuration config, final Properties properties) throws EXistException {
        this.diskSpaceMin = 1024l * 1024l * config.getProperty(BrokerPool.DISK_SPACE_MIN_PROPERTY, BrokerPool.DEFAULT_DISK_SPACE_MIN);

        // fixme! - Shouldn't it be data dir AND journal dir we check
        // rather than EXIST_HOME? /ljo
        dataDir = (Path) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        LOG.info("Using DATA_DIR: " + dataDir.toAbsolutePath().toString() + ". Minimal disk space required for database " +
                 "to continue operations: " + (diskSpaceMin / 1024 / 1024) + "mb");
        final long space = FileUtils.measureFileStore(dataDir, FileStore::getUsableSpace);
        LOG.info("Usable space on partition containing DATA_DIR: " + dataDir.toAbsolutePath().toString() + ": " + (space / 1024 / 1024) + "mb");
    }

    @Override
    public void execute(final DBBroker broker, final Txn transaction) throws EXistException {
        final BrokerPool pool = broker.getBrokerPool();
        if (!checkDiskSpace()) {
            LOG.fatal("Partition containing DATA_DIR: " + dataDir.toAbsolutePath().toString() + " is running out of disk space. " +
                "Switching eXist-db to read only to prevent data loss!");
            pool.setReadOnly();
        }

        if(System.currentTimeMillis() - pool.getLastMajorSync() >
                pool.getMajorSyncPeriod()) {
            pool.sync(broker, Sync.MAJOR);
        } else {
            pool.sync(broker, Sync.MINOR);
        }
    }

    /**
     * @return true if there is enough disk space, false otherwis
     */
    private boolean checkDiskSpace() {
        final long space = FileUtils.measureFileStore(dataDir, FileStore::getUsableSpace);
        //LOG.info("Usable space on partition containing DATA_DIR: " + dataDir.getAbsolutePath() + ": " + (space / 1024 / 1024) + "mb");
        return space > diskSpaceMin || space == -1;
    }
}
