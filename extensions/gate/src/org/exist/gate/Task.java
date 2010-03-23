/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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

package org.exist.gate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Properties;
import java.util.Timer;

import org.apache.commons.httpclient.HttpException;

/**
 * @author Evgeny Gazdovsky (gazdovsky@gmail.com)
 */
public class Task{

	private GateApplet gate;
	
	private String downloadFrom;	// URL of file download from
	private String uploadTo;		// URL for upload file
	private File tmp;				// Downladed file 

	/**
	 * Create a new task
	 * @param downloadFrom URL of file download from
	 * @param uploadTo URL for upload file after changes
	 * @param gate GateApplet
	 */
	public Task(String downloadFrom, String uploadTo, GateApplet gate){
		this.downloadFrom = downloadFrom;
		this.uploadTo = uploadTo;
		this.gate = gate;
	}
	
	/**
	 * Create a task, used for stored task
	 * @param downloadFrom URL of file download from
	 * @param uploadTo URL for upload file after changes
	 * @param tmp path of file stored in local cache
	 * @param gate GateApplet
	 */
	public Task(String downloadFrom, String uploadTo, File tmp, GateApplet gate){
		this.downloadFrom = downloadFrom;
		this.uploadTo = uploadTo;
		this.gate = gate;
		this.tmp = tmp;
	}

	/**
	 * Execute task: dowload file, store desrioption,
	 * then file changes listener and open one 
	 * in desktop application. 
	 */
	public void execute(){
		try {
    		tmp = gate.download(downloadFrom);
    		store();
    		Listener listener = new Listener(this, gate);
    		Timer timer = new Timer();
    		timer.schedule(listener, GateApplet.PERIOD, GateApplet.PERIOD);
    		gate.open(tmp);
        } catch (HttpException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public String getDownloadFrom(){
		return downloadFrom;
	}
	public String getUploadTo(){
		return uploadTo;
	}
	public File getFile(){
		return tmp;
	}
	
	/**
	 * Store task description into FS as XML doc
	 * @throws IOException
	 */
	public void store() throws IOException{
		Properties prop = new Properties();
		prop.put("download-from", downloadFrom);
		prop.put("upload-to", uploadTo);
		prop.put("file", tmp.getAbsolutePath());
		prop.put("modified", new Long(tmp.lastModified()).toString());
		File fld = gate.getMeta();
		if (!fld.isDirectory()){
			fld.mkdirs();
		}
		String name = Integer.toHexString(downloadFrom.hashCode()) + ".xml";
		File meta = new File(fld,  name);
		if (!meta.exists()){
			meta.createNewFile();
		}
		FileOutputStream os = new FileOutputStream(meta);
		prop.storeToXML(os, "Gate task description");
		os.close();
	}
	
}
