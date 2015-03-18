/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */

package org.exist.storage;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.backup.RawDataBackup;
import org.exist.util.Configuration;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Properties;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class DataBackup implements SystemTask {

    private final static Logger LOG = LogManager.getLogger(DataBackup.class);
    
    public final static SimpleDateFormat creationDateFormat = new SimpleDateFormat("yyyyMMddHHmmssS");
    
	private String dest;
	
    public DataBackup() {
    }
    
    public DataBackup(String destination) {
        dest = destination;
    }
    
    @Override
    public boolean afterCheckpoint() {
    	return true;
    }
    
    /* (non-Javadoc)
     * @see org.exist.storage.SystemTask#configure(java.util.Properties)
     */
    public void configure(Configuration config, Properties properties) throws EXistException { 
        dest = properties.getProperty("output-dir", "backup");
        File f = new File(dest);
        if (!f.isAbsolute()) {
            dest = (String)config.getProperty(BrokerPool.PROPERTY_DATA_DIR) +
                File.separatorChar + dest;
            f = new File(dest);
        }
        if (f.exists() && !(f.canWrite() && f.isDirectory()))
            {throw new EXistException("Cannot write backup files to " + f.getAbsolutePath() +
                    ". It should be a writable directory.");}
        else
            {f.mkdirs();}
        dest = f.getAbsolutePath();
        LOG.debug("Setting backup data directory: " + dest);
    }
    
	public void execute(DBBroker broker) throws EXistException {
		if (!(broker instanceof NativeBroker))
			{throw new EXistException("DataBackup system task can only be used " +
					"with the native storage backend");}
//		NativeBroker nbroker = (NativeBroker) broker;
		
		LOG.debug("Backing up data files ...");
		
		final String creationDate = creationDateFormat.format(Calendar.getInstance().getTime());
        final String outFilename = dest + File.separatorChar + creationDate + ".zip";
        
        // Create the ZIP file
        LOG.debug("Archiving data files into: " + outFilename);
        
        try {
			final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
            out.setLevel(Deflater.NO_COMPRESSION);
            final Callback cb = new Callback(out);
            broker.backupToArchive(cb);
            // close the zip file
			out.close();
		} catch (final IOException e) {
			LOG.warn("An IO error occurred while backing up data files: " + e.getMessage(), e);
		}
	}

    private class Callback implements RawDataBackup {

        private ZipOutputStream zout;

        private Callback(ZipOutputStream out) {
            zout = out;
        }

        public OutputStream newEntry(String name) throws IOException {
            zout.putNextEntry(new ZipEntry(name));
            return zout;
        }

        public void closeEntry() throws IOException {
            zout.closeEntry();
        }
    }
}


