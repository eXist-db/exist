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
		try {			
			copy(dataDir + File.separatorChar + "dom.dbx",
					this.dest + File.separatorChar + "dom.dbx");
			copy(dataDir + File.separatorChar +"symbols.dbx",
					this.dest + File.separatorChar +"symbols.dbx");
			copy(dataDir + File.separatorChar + "collections.dbx",
					this.dest + File.separatorChar +"collections.dbx");
			copy(dataDir + File.separatorChar +"elements.dbx",
					this.dest + File.separatorChar +"elements.dbx");
			copy(dataDir + File.separatorChar +"words.dbx",
					this.dest + File.separatorChar +"words.dbx");			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	 boolean copy(String src, String dst) throws IOException {
		File src1 = new File(src);
		File dst1 = new File(dst);
		try {
			InputStream fis = new FileInputStream(src);
			OutputStream fos = new FileOutputStream(dst);
			byte[] buffer = new byte[1000];
			int len = 0;
			while ((len = fis.read(buffer)) > 0) {
				fos.write(buffer, 0, len);
			}
			fis.close();
			fos.close();
		} catch (FileNotFoundException fnf) {
			System.out.println("file not found" + fnf.getMessage());
		}
		return true;
	}

	
}

