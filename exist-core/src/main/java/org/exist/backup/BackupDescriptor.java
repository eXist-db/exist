/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.backup;

import org.exist.util.EXistInputSource;
import org.exist.util.XMLReaderPool;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;


public interface BackupDescriptor {

    String COLLECTION_DESCRIPTOR = "__contents__.xml";

    String BACKUP_PROPERTIES = "backup.properties";

    String PREVIOUS_PROP_NAME = "previous";
    String NUMBER_IN_SEQUENCE_PROP_NAME = "nr-in-sequence";
    String INCREMENTAL_PROP_NAME = "incremental";
    String DATE_PROP_NAME = "date";

    EXistInputSource getInputSource();

    EXistInputSource getInputSource(String describedItem);

    EXistInputSource getBlobInputSource(String blobId);
    BackupDescriptor getChildBackupDescriptor(String describedItem);

    BackupDescriptor getBackupDescriptor(String describedItem);

    String getName();

    String getSymbolicPath();

    String getSymbolicPath(String describedItem, boolean isChildDescriptor);

    /**
     * Returns general properties of the backup, normally including the creation date or if it is an incremental backup.
     *
     * @return a Properties object or null if no properties were found
     * @throws IOException if there was an error in the properties file
     */
    Properties getProperties() throws IOException;

    Path getParentDir();

    Date getDate();

    boolean before(long timestamp);

    void parse(XMLReaderPool parserPool, ContentHandler handler)
            throws IOException, SAXException;

    Path getRepoBackup() throws IOException;

    long getNumberOfFiles();
}
