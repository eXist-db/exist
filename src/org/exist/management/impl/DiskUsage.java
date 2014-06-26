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
import java.io.IOException;
import org.apache.commons.lang3.StringUtils;

import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;

/**
 * Class DiskUsage. Retrieves data from the java File object
 *
 * @author dizzzz@exist-db.org
 */
public class DiskUsage implements DiskUsageMBean {

    private File journalDir;
    private File dataDir;

    public DiskUsage(BrokerPool pool) {

        Configuration config = pool.getConfiguration();

        String journalDirValue = (String) config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR);
        if (StringUtils.isNotBlank(journalDirValue)) {
            File tmpDir = new File(journalDirValue);
            if (tmpDir.isDirectory()) {
                journalDir = tmpDir;
            }
        }

        String dataDirValue = (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
        if (StringUtils.isNotBlank(dataDirValue)) {
            File tmpDir = new File(dataDirValue);
            if (tmpDir.isDirectory()) {
                dataDir = tmpDir;
            }
        }

    }

    @Override
    public String getDataDirectory() {
        if (dataDir != null) {
            try {
                return dataDir.getCanonicalPath();
            } catch (IOException ex) {
                return dataDir.getAbsolutePath();
            }
        }
        return NOT_CONFIGURED;
    }

    @Override
    public String getJournalDirectory() {
        if (journalDir != null) {
            try {
                return journalDir.getCanonicalPath();
            } catch (IOException ex) {
                return journalDir.getAbsolutePath();
            }
        }
        return NOT_CONFIGURED;
    }

    @Override
    public long getDataDirectoryTotalSpace() {
        if (dataDir != null) {
            return dataDir.getTotalSpace();
        }

        return NO_VALUE;
    }

    @Override
    public long getDataDirectoryUsableSpace() {
        if (dataDir != null) {
            return dataDir.getUsableSpace();
        }
        return NO_VALUE;
    }

    @Override
    public long getJournalDirectoryTotalSpace() {
        if (journalDir != null) {
            return journalDir.getTotalSpace();
        }
        return NO_VALUE;
    }

    @Override
    public long getJournalDirectoryUsableSpace() {
        if (journalDir != null) {
            return journalDir.getUsableSpace();
        }
        return NO_VALUE;
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
