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
import java.lang.reflect.Method;

import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;


/**
 * Class DiskUsage
 * 
 * @author dizzzz@exist-db.org
 */
public class DiskUsage implements DiskUsageMBean {

    private BrokerPool pool;
    private Configuration config;

    public DiskUsage(BrokerPool pool) {
        this.pool = pool;
        config = pool.getConfiguration();
    }

    private long getSpace(File dir, String method) {
        try {
            Class cls = dir.getClass();
            Method m = cls.getMethod(method, new Class[0]);
            Long a = (Long) m.invoke(dir, new Object[0]);
            return a;
        } catch (NoSuchMethodException ex) {
            // method not 
        } catch (Exception ex) {
            ex.printStackTrace();
        }

        return -1;
    }

    public String getDataDirectory() {
        return (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
    }

    public String getJournalDirectory() {
        return (String) config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR);
    }

    public long getDataDirectoryTotalSpace() {
        File dir = new File(getDataDirectory());
        return getSpace(dir, "getTotalSpace");
    }

    public long getDataDirectoryFreeDiskSpace() {
        File dir = new File(getDataDirectory());
        return getSpace(dir, "getUsableSpace");
    }

    public long getJournalDirectoryTotalSpace() {
        File dir = new File(getJournalDirectory());
        return getSpace(dir, "getTotalSpace");
    }

    public long getJournalDirectoryFreeSpace() {
        File dir = new File(getJournalDirectory());
        return getSpace(dir, "getUsableSpace");
    }

    public long getDataDirectoryUsedSpace() {

        long totalSize = 0;

        File dir = new File(getDataDirectory());
        File[] files = dir.listFiles(new DbxFilenameFilter());
        for (File file : files) {
            totalSize += file.length();
        }

        return totalSize;
    }

    public long getJournalDirectoryUsedSpace() {
        long totalSize = 0;

        File dir = new File(getJournalDirectory());
        File[] files = dir.listFiles(new JournalFilenameFilter());
        for (File file : files) {
            totalSize += file.length();
        }

        return totalSize;
    }

    public int getJournalDirectoryNumberOfFiles() {
       File dir = new File(getJournalDirectory());
       File[] files = dir.listFiles(new JournalFilenameFilter());
       return files.length;
    }
}
class DbxFilenameFilter implements FilenameFilter {

    public boolean accept(File directory, String name) {
        if (name.endsWith(".dbx")) {
            return true;
        } else {
            return false;
        }
    }
}

class JournalFilenameFilter implements FilenameFilter {

    public boolean accept(File directory, String name) {
        if (name.endsWith(".log")) {
            return true;
        } else {
            return false;
        }
    }
}


