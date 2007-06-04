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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.btree.Paged;
import org.exist.util.Configuration;

public class DataBackup implements SystemTask {

    private final static Logger LOG = Logger.getLogger(DataBackup.class);
    
    private final static SimpleDateFormat creationDateFormat =
        new SimpleDateFormat("yyMMdd-HHmmss", Locale.US);
    
	private String dest;
	
    public DataBackup() {
    }
    
    public DataBackup(String destination) {
        dest = destination;
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
            throw new EXistException("Cannot write backup files to " + f.getAbsolutePath() +
                    ". It should be a writable directory.");
        else
            f.mkdirs();
        dest = f.getAbsolutePath();
        LOG.debug("Setting backup data directory: " + dest);
    }
    
	public void execute(DBBroker broker) throws EXistException {
		if (!(broker instanceof NativeBroker))
			throw new EXistException("DataBackup system task can only be used " +
					"with the native storage backend");
		NativeBroker nbroker = (NativeBroker) broker;
		
		LOG.debug("Backing up data files ...");
		
		String creationDate = creationDateFormat.format(new Date());
        String outFilename = dest + File.separatorChar + creationDate + ".zip";
        
        // Create the ZIP file
        LOG.debug("Archiving data files into: " + outFilename);
        
        try {
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
			byte[] fileIds = nbroker.getStorageFileIds();
			for (int i = 0; i < fileIds.length; i++) {
				Paged paged = nbroker.getStorage(fileIds[i]);
				
				// create a new entry and copy the paged file contents to it
				out.putNextEntry(new ZipEntry(paged.getFile().getName()));
				paged.backupToStream(out);
				
				// Complete the entry
	            out.closeEntry();
			}
			
			//TODO : could we mutualize there ?
			// backup the symbols.dbx file (not included above)
			out.putNextEntry(new ZipEntry(broker.getSymbols().getFile().getName()));
			broker.getSymbols().backupSymbolsTo(out);
			out.closeEntry();
			
			// close the zip file
			out.close();
		} catch (IOException e) {
			LOG.warn("An IO error occurred while backing up data files: " + e.getMessage(), e);
		}
	}
}


