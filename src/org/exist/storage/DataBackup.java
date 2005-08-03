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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.util.Configuration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Properties;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry; 
import java.io.File;
import java.io.*;

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
            dest = (String)config.getProperty("db-connection.data-dir") +
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
		Configuration config = broker.getConfiguration();
		
		String dataDir = (String) config.getProperty("db-connection.data-dir");
		LOG.debug("Backing up data files ...");

		File dir = new File(dataDir);
		 FilenameFilter filter = new FilenameFilter() {
	        public boolean accept(File dir, String name) {
	            return name.endsWith(".dbx");
	        }
	    };
	    String[] filenames = dir.list(filter);
		try {
			compressFiles(dataDir + File.separatorChar,filenames);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	 void compressFiles(String datadir, String[] filenames) throws IOException {
	     String creationDate = creationDateFormat.format(new Date());
         String outFilename = dest + File.separatorChar + creationDate + ".zip";
         
	    // Create a buffer for reading the files
	    byte[] buf = new byte[1024];
	    
	    try {
	        // Create the ZIP file
            LOG.debug("Archiving data files into: " + outFilename);
            
	        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
	    
	        // Compress the files
	        for (int i=0; i<filenames.length; i++) {
	            FileInputStream in = new FileInputStream(datadir+filenames[i]);
	    
	            // Add ZIP entry to output stream.
	            out.putNextEntry(new ZipEntry(filenames[i]));
	    
	            // Transfer bytes from the file to the ZIP file
	            int len;
	            while ((len = in.read(buf)) > 0) {
	                out.write(buf, 0, len);
	            }
	    
	            // Complete the entry
	            out.closeEntry();
	            in.close();
	        }
	    
	        // Complete the ZIP file
	        out.close();
	    } catch (IOException e) {
	    }
	 }
}

