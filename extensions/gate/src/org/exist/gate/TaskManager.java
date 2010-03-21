/*
 *  eXist's  Gate extension - REST client for automate document management
 *  form any browser in any desktop application on any client platform
 *  Copyright (C) 2010,  Evgeny V. Gazdovsky (gazdovsky@gmail.com)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.gate;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

public class TaskManager extends TimerTask{
	
	private List <Task> tasks = new ArrayList<Task>();
	
	boolean haveNewTask = false;

	private GateApplet gate;
	
	public TaskManager(GateApplet gate){
		super();
		this.gate = gate;
	}
	
	public void addTask(Task task){
		tasks.add(task);
		haveNewTask = true;
	}
	
	public void run(){
		if (haveNewTask){
			haveNewTask = false;
			tasks.get(tasks.size()-1).execute();
		}
	}
	
	
	public void load(){
		for(File meta: gate.getMeta().listFiles()){
			try {
				Properties prop = new Properties();
				FileInputStream in = new FileInputStream(meta);
				prop.loadFromXML(in);
				File tmp = new File(gate.getCache(), prop.getProperty("file"));
				if (tmp.exists()){
					String downloadFrom = prop.getProperty("download-from");
					String uploadTo = prop.getProperty("upload-to");
					long modified = new Long(prop.getProperty("modified")).longValue();
					Task task = new Task(downloadFrom, uploadTo, tmp, gate);
					tasks.add(task);
					Listener listener= new Listener(task, gate, modified);
					Timer timer = new Timer();
					timer.schedule(listener, GateApplet.PERIOD, GateApplet.PERIOD);
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
