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
import javax.management.*;
import org.exist.storage.BrokerPool;
import org.exist.storage.journal.Journal;
import org.exist.util.Configuration;

/**
 * Class DiskUsage
 * 
 * @author wessels
 */
public class DiskUsage implements DiskUsageMBean {
    
    private BrokerPool pool;
    private Configuration config;

    
    public DiskUsage(BrokerPool pool){
        this.pool=pool;
        config = pool.getConfiguration();
    }

    public long getDataDirectoryFreeDiskSpace() {
        //return (new File( getJournalDirectory() ).getUsableSpace());
        return -(1L);
    }

    public String getDataDirectory() {
        return (String) config.getProperty(BrokerPool.PROPERTY_DATA_DIR);
    }

    public String getJournalDirectory() {
        return (String) config.getProperty(Journal.PROPERTY_RECOVERY_JOURNAL_DIR);
    }

    public long getJournalDirectoryFreeSpace() {
        //return new File(getJournalDirectory()).getUsableSpace();
        return -(1L);
    }

    public long getDataDirectoryTotalSpace() {
        //return (new File( getJournalDirectory() ).getTotalSpace());
        return -(1L);
    }

    public long getJournalDirectoryTotalSpace() {
         //return new File(getJournalDirectory()).getTotalSpace();
        return -(1L);
    }
    
    
    
}

class DbxFilenameFiler implements FilenameFilter {

    public boolean accept(File directory, String name) {
        if(name.endsWith(".dbx")){
            return true;
        } else {
            return false;
        }
    }
    
}

class JournalFilenameFiler implements FilenameFilter {

    public boolean accept(File directory, String name) {
        if(name.endsWith(".log")){
            return true;
        } else {
            return false;
        }
    }
    
}


