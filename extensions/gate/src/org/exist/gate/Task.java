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
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.methods.GetMethod;

public class Task implements Serializable{

	private static final long serialVersionUID = 2712574630003981814L;

	private GateApplet gate;
	
	private String downloadFrom;
	protected String uploadTo;
	
	protected String path;
	
	protected Long modified;
	
	public Task(String downloadFrom, String uploadTo, GateApplet gate){
		this.downloadFrom = downloadFrom;
		this.uploadTo = uploadTo;
		this.gate = gate;
	}

	public void execute(){
		
		GetMethod get = new GetMethod(downloadFrom); 
		
		try {
			
			HttpClient http = gate.getHttp();
			
			http.executeMethod(get);
			
			long contentLength = ((HttpMethodBase) get).getResponseContentLength();
			
	        if (contentLength < Integer.MAX_VALUE) {
	        	
	        	InputStream is = get.getResponseBodyAsStream();
	        	
	    		File tmp = gate.createFile(new File(downloadFrom).getName());
	    		OutputStream os = new FileOutputStream(tmp);
	    		path = tmp.getAbsolutePath();
	    		modified = tmp.lastModified();
	    		
	    		byte[] data = new byte[1024];
	    		int read = 0;
	    		
	    		while((read = is.read(data)) > -1) {
	    			os.write(data, 0, read);
	    		}
	    		
	    		os.flush();
	    		is.close();
	    		os.close();
	    		
	    		Listener listener = new Listener(tmp, uploadTo, gate);
	    		
	    		Timer timer = new Timer();
	    		timer.schedule(listener, GateApplet.PERIOD, GateApplet.PERIOD);
	    		
	    		gate.open(tmp);
	    		
	        }
	        
        } catch (HttpException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			get.releaseConnection();
		}
		
		 
		
	}
	
}
