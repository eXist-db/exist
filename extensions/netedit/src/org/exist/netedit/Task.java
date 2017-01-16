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

package org.exist.netedit;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Properties;
import java.util.Timer;

import org.apache.commons.httpclient.HttpException;
import org.exist.util.FileUtils;

/**
 * @author Evgeny Gazdovsky (gazdovsky@gmail.com)
 */
public class Task{

	private NetEditApplet netEdit;
	
	private String downloadFrom;	// URL of file download from
	private String uploadTo;		// URL for upload file
	private Path tmp;				// Downladed file

	/**
	 * Create a new task
	 * @param downloadFrom URL of file download from
	 * @param uploadTo URL for upload file after changes
	 * @param netEdit NetEditApplet
	 */
	public Task(String downloadFrom, String uploadTo, NetEditApplet netEdit){
		this.downloadFrom = downloadFrom;
		this.uploadTo = uploadTo;
		this.netEdit = netEdit;
	}
	
	/**
	 * Create a task, used for stored task
	 * @param downloadFrom URL of file download from
	 * @param uploadTo URL for upload file after changes
	 * @param tmp path of file stored in local cache
	 * @param netEdit NetEditApplet
	 */
	public Task(String downloadFrom, String uploadTo, Path tmp, NetEditApplet netEdit){
		this.downloadFrom = downloadFrom;
		this.uploadTo = uploadTo;
		this.netEdit = netEdit;
		this.tmp = tmp;
	}

	/**
	 * Execute task: dowload file, store desrioption,
	 * then file changes listener and open one 
	 * in desktop application. 
	 */
	public void execute(){
		try {
    		tmp = netEdit.download(downloadFrom);
    		store();
    		Listener listener = new Listener(this, netEdit);
    		Timer timer = new Timer();
    		timer.schedule(listener, NetEditApplet.PERIOD, NetEditApplet.PERIOD);
    		netEdit.open(tmp);
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
	public Path getFile(){
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
		prop.put("file", tmp.toAbsolutePath().toString());
		prop.put("modified", Long.toString(FileUtils.lastModifiedQuietly(tmp).map(FileTime::toMillis).getOrElse(-1l)));
		Path fld = netEdit.getMeta();
		if (!Files.isDirectory(fld)){
			Files.createDirectories(fld);
		}
		String name = Integer.toHexString(downloadFrom.hashCode()) + ".xml";
		Path meta = fld.resolve(name);
		if (!Files.exists(meta)){
			Files.createFile(meta);
		}
		try(final OutputStream os = Files.newOutputStream(meta)) {
			prop.storeToXML(os, "net-edit task description");
		}
	}
	
}
