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
import java.io.IOException;
import java.util.TimerTask;

import org.apache.commons.httpclient.HttpException;

public class Listener extends TimerTask{
	
	private long lastModified;
	private Task task;
	private File file;
	private GateApplet gate;
	
	public Listener(Task task, GateApplet gate){
		this.gate = gate;
		this.task = task;
		this.file = task.getFile();
		this.lastModified = file.lastModified();
	}
	
	public Listener(Task task, GateApplet gate, long lastModified){
		this.gate = gate;
		this.task = task;
		this.file = task.getFile();
		this.lastModified = lastModified;
	}
	
	private boolean isModified(){
		return file.lastModified() > this.lastModified;
	}
	
	public void run() {
		if (isModified()){
			this.lastModified = file.lastModified();
			try {
				gate.upload(file, task.getUploadTo());
				task.store();
			} catch (HttpException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
}
