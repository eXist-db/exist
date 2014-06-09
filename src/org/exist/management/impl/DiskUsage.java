/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.management.impl;

import java.io.File;
import java.io.FilenameFilter;

import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;


/**
 * Class DiskUsage
 * 
 * @author dizzzz@exist-db.org
 */
public class DiskUsage implements DiskUsageMBean {

    private final File journalDir;
    private final File dataDir;

    public DiskUsage(BrokerPool pool) {

        Configuration config = pool.getConfiguration();

        String journalDirValue = (String) config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR, "NOT DEFINED");
        journalDir = new File(journalDirValue);

        String dataDirValue = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR, "NOT DEFINED");
        dataDir = new File(dataDirValue);
    }

    @Override
    public String getDataDirectory() {
        return dataDir.getAbsolutePath();
    }

    @Override
    public String getJournalDirectory() {
        return journalDir.getAbsolutePath();
    }

    @Override
    public long getDataDirectoryTotalSpace() {
        return dataDir.getTotalSpace();
    }

    @Override
    public long getDataDirectoryFreeSpace() {
        return dataDir.getUsableSpace();
    }

    @Override
    public long getJournalDirectoryTotalSpace() {
        return journalDir.getTotalSpace();
    }

    @Override
    public long getJournalDirectoryFreeSpace() {
        return journalDir.getUsableSpace();
    }

    @Override
    public long getDataDirectoryUsedSpace() {

        long totalSize = 0;

        final File dir = new File(getDataDirectory());
        final File[] files = dir.listFiles(new DbxFilenameFilter());
        for (final File file : files) {
            totalSize += file.length();
        }

        return totalSize;
    }

    @Override
    public long getJournalDirectoryUsedSpace() {
        long totalSize = 0;

        final File dir = new File(getJournalDirectory());
        final File[] files = dir.listFiles(new JournalFilenameFilter());
        for (final File file : files) {
            totalSize += file.length();
        }

        return totalSize;
    }

    @Override
    public int getJournalDirectoryNumberOfFiles() {
       final File dir = new File(getJournalDirectory());
       final File[] files = dir.listFiles(new JournalFilenameFilter());
       return files.length;
    }
}

class DbxFilenameFilter implements FilenameFilter {

    @Override
    public boolean accept(File directory, String name) {
        return name.endsWith(".dbx");
    }
}

class JournalFilenameFilter implements FilenameFilter {

    @Override
    public boolean accept(File directory, String name) {
        return name.endsWith(".log");
    }
}


