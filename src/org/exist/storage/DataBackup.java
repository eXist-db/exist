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

import org.exist.EXistException;
import org.exist.util.Configuration;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipEntry; 
import java.io.File;
import java.io.*;

public class DataBackup implements SystemTask {

	String dest;
    public DataBackup(String destination) {
	    this.dest = destination; 
		}
	
	public void execute(DBBroker broker) throws EXistException {
		Configuration config = broker.getConfiguration();
		
		String dataDir = (String) config.getProperty("db-connection.data-dir");
		System.out.println("Backup the data file into " + this.dest);

		String[] filenames = new String[]{dataDir + File.separatorChar + "dom.dbx",
				dataDir + File.separatorChar +"symbols.dbx",
				dataDir + File.separatorChar + "collections.dbx",
				dataDir + File.separatorChar +"elements.dbx",
				dataDir + File.separatorChar +"words.dbx"
				};
		try {
			compressFiles(filenames, this.dest);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	 void compressFiles(String[] filenames, String filename) throws IOException {
	    
	    // Create a buffer for reading the files
	    byte[] buf = new byte[1024];
	    
	    try {
	        // Create the ZIP file
	        String outFilename = filename;
	        ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outFilename));
	    
	        // Compress the files
	        for (int i=0; i<filenames.length; i++) {
	            FileInputStream in = new FileInputStream(filenames[i]);
	    
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

