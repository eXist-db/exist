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
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.util.Properties;
import java.util.Date;
import java.io.IOException;
import java.io.File;

public interface BackupDescriptor {
	public final static String COLLECTION_DESCRIPTOR="__contents__.xml";

    public final static String BACKUP_PROPERTIES = "backup.properties";

    public final static String PREVIOUS_PROP_NAME = "previous";
    public final static String NUMBER_IN_SEQUENCE_PROP_NAME = "nr-in-sequence";
    public final static String INCREMENTAL_PROP_NAME = "incremental";
    public final static String DATE_PROP_NAME = "date";

	public EXistInputSource getInputSource();
	
	public EXistInputSource getInputSource(String describedItem);
	
	public BackupDescriptor getChildBackupDescriptor(String describedItem);

    public BackupDescriptor getBackupDescriptor(String describedItem);
    
    public String getName();
    
	public String getSymbolicPath();
	
	public String getSymbolicPath(String describedItem,boolean isChildDescriptor);

    /**
     * Returns general properties of the backup, normally including the creation date
     * or if it is an incremental backup.
     *
     * @return a Properties object or null if no properties were found
     * @throws IOException if there was an error in the properties file
     */
    public Properties getProperties() throws IOException;

    public File getParentDir();

    public Date getDate();

    public boolean before(long timestamp);

    public void parse(ContentHandler handler) throws IOException, SAXException, ParserConfigurationException; 
}
