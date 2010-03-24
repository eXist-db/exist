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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author Evgeny Gazdovsky (gazdovsky@gmail.com)
 */
public class TaskManager extends TimerTask{
	
	private List <Task> tasks = new ArrayList<Task>();
	
	boolean haveNewTask = false;

	private NetEditApplet netEdit;
	
	public TaskManager(NetEditApplet netEdit){
		super();
		this.netEdit = netEdit;
	}
	
	/**
	 * Add a task to execute
	 * @param task added task
	 */
	public void addTask(Task task){
		tasks.add(task);
		haveNewTask = true;
	}
	
	/**
	 * Start a new task added into list
	 */
	public void run(){
		if (haveNewTask){
			haveNewTask = false;
			tasks.get(tasks.size()-1).execute();
		}
	}
	
	/**
	 * Load tasks from local file system
	 * and start listeners for them
	 */
	public void load(){
		File metaFolder = netEdit.getMeta();
		if (metaFolder.isDirectory()){
			for(File meta: metaFolder.listFiles()){
				try {
					Properties prop = new Properties();
					FileInputStream in = new FileInputStream(meta);
					prop.loadFromXML(in);
					File tmp = new File(prop.getProperty("file"));
					if (tmp.exists()){
						String downloadFrom = prop.getProperty("download-from");
						String uploadTo = prop.getProperty("upload-to");
						long modified = new Long(prop.getProperty("modified")).longValue();
						Task task = new Task(downloadFrom, uploadTo, tmp, netEdit);
						tasks.add(task);
						Listener listener= new Listener(task, netEdit, modified);
						Timer timer = new Timer();
						timer.schedule(listener, NetEditApplet.PERIOD, NetEditApplet.PERIOD);
						in.close();
					} else {
						meta.delete();
					}
				} catch (InvalidPropertiesFormatException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}
	
}
