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
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.TimerTask;

import org.apache.commons.httpclient.HttpException;
import org.exist.util.FileUtils;

/**
 * @author Evgeny Gazdovsky (gazdovsky@gmail.com)
 */
public class Listener extends TimerTask{
	
	private long lastModified;	// time of last file change
	private Task task;			// listened task 
	private Path file;			// listened file
	private NetEditApplet netEdit;	
	
	/**
	 * Create a listener for task
	 * @param task listened task
	 * @param netEdit NetEditApplet
	 */
	public Listener(Task task, NetEditApplet netEdit){
		this.netEdit = netEdit;
		this.task = task;
		this.file = task.getFile();
		this.lastModified = FileUtils.lastModifiedQuietly(file).map(FileTime::toMillis).getOrElse(-1l);
	}
	
	/**
	 * Create a listener for task, used for task loaded from FS
	 * @param task listened task
	 * @param netEdit NetEditApplet
	 * @param lastModified time of last task modification, stored in XML description of task 
	 */
	public Listener(Task task, NetEditApplet netEdit, long lastModified){
		this.netEdit = netEdit;
		this.task = task;
		this.file = task.getFile();
		this.lastModified = lastModified;
	}
	
	private boolean isModified(){
		return FileUtils.lastModifiedQuietly(file).map(FileTime::toMillis).getOrElse(-1l) > this.lastModified;
	}
	
	/**
	 * Listen task's file changes, upload file to server 
	 * and store task description on local FS   
	 */
	public void run() {
		if (isModified()){
			this.lastModified = FileUtils.lastModifiedQuietly(file).map(FileTime::toMillis).getOrElse(-1l);
			try {
				netEdit.upload(file, task.getUploadTo());
				task.store();
			} catch (HttpException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
