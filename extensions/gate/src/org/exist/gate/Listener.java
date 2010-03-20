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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimerTask;

import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

public class Listener extends TimerTask{
	
	private long lastModified;
	private String uploadTo;
	private File file;
	private GateApplet gate;
	
	public Listener(File file, String uploadTo, GateApplet gate){
		this.gate = gate;
		this.file = file;
		this.uploadTo = uploadTo;
		this.lastModified = file.lastModified();
	}
	
	private boolean isModified(){
		return file.lastModified() > this.lastModified;
	}
	
	public void run() {
		
		if (isModified()){
			
			this.lastModified = file.lastModified();
			
			PutMethod put = new PutMethod(uploadTo);
			
			try {
				
				InputStream is = new FileInputStream(file);
				RequestEntity entity = new InputStreamRequestEntity(is);
				put.setRequestEntity(entity);
				gate.getHttp().executeMethod(put);
				is.close();
				
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				put.releaseConnection();
			}
			
		}
		
	}
	
}
