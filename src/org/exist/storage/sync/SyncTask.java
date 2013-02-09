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

    private File dataDir;
    private long diskSpaceMin = 64 * 1024L * 1024L;

    @Override
    public boolean afterCheckpoint() {
        // a checkpoint is created by the MAJOR_SYNC event
        return false;
    }

    @Override
    public void configure(Configuration config, Properties properties)
            throws EXistException {
        final Integer min = (Integer) config.getProperty(BrokerPool.DISK_SPACE_MIN_PROPERTY);
        if (min != null)
            {diskSpaceMin = min * 1024L * 1024L;}

        // fixme! - Shouldn't it be data dir AND journal dir we check
        // rather than EXIST_HOME? /ljo
        dataDir = new File((String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR));
        LOG.info("Using DATA_DIR: " + dataDir.getAbsolutePath() + ". Minimal disk space required for database " +
                 "to continue operations: " + (diskSpaceMin / 1024 / 1024) + "mb");
        final long space = dataDir.getUsableSpace();
        LOG.info("Usable space on partition containing DATA_DIR: " + dataDir.getAbsolutePath() + ": " + (space / 1024 / 1024) + "mb");
    }

    @Override
    public void execute(DBBroker broker) throws EXistException {
        final BrokerPool pool = broker.getBrokerPool();
        if (!checkDiskSpace(pool)) {
            LOG.fatal("Partition containing DATA_DIR: " + dataDir.getAbsolutePath() + " is running out of disk space. " +
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
        final long space = dataDir.getUsableSpace();
        //LOG.info("Usable space on partition containing DATA_DIR: " + dataDir.getAbsolutePath() + ": " + (space / 1024 / 1024) + "mb");
        return space > diskSpaceMin;
    }
}
