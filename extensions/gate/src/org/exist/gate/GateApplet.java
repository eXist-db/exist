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

import java.applet.Applet;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.ProxyHost;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

public class GateApplet extends Applet {
	
	private static final long serialVersionUID = -8952536002584984227L;
	
	private HttpClient http = new HttpClient();
	
	private TaskManager manager = new TaskManager(this);
	
	private String opencmd;
	
	private File user  = new File(System.getProperty("user.home"));	// user home folder
	private File exist = new File(user, ".eXist");					// .eXist's folder
	private File etc   = new File(exist, "gate");					// Gate's folder
	private File meta  = new File(etc, "meta");						// Store info about task here
	private File cache = new File(etc, "cache");					// Store opened files here 
	
	public final static long PERIOD = 1000; 						// Default period/delay for different operations
	
	public GateApplet(){
		super();
		
		// Setup HTTP proxy
		String proxyHost = System.getProperty( "http.proxyHost"); 
        if (proxyHost != null && !proxyHost.equals("")) {
        	ProxyHost proxy = new ProxyHost(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort")));
            http.getHostConfiguration().setProxyHost(proxy);
        }
        
        // Create gate folder
        if (!cache.isDirectory()){
        	cache.mkdirs();
        }
        
        // Create meta folder
        if (!meta.isDirectory()){
        	meta.mkdirs();
        }
        
        // Detect OS open file command 
        String os = System.getProperty("os.name").toLowerCase();
        if ( os.indexOf("windows") != -1 || os.indexOf("nt") != -1){
            opencmd = "cmd /c \"start %s\"";
        } else if ( os.indexOf("mac") != -1 ) {
            opencmd = "open %s";
        } else {
        	opencmd = "xdg-open %s";
        }
        
        manager.load();
        
		Timer timer = new Timer();
		timer.schedule(manager, PERIOD, PERIOD);
	}
	
	public HttpClient getHttp() {
		return http;
	}
	
	public void manage(String downloadFrom, String uploadTo){
		manager.addTask(new Task(downloadFrom, uploadTo, this));
	}
	
	public File download(String downloadFrom) throws IOException{
		File file = null;
		GetMethod get = new GetMethod(downloadFrom); 
		http.executeMethod(get);
		long contentLength = ((HttpMethodBase) get).getResponseContentLength();
		
        if (contentLength < Integer.MAX_VALUE) {
        	InputStream is = get.getResponseBodyAsStream();
    		file = createFile(new File(downloadFrom).getName());
    		OutputStream os = new FileOutputStream(file);
    		byte[] data = new byte[1024];
    		int read = 0;
    		while((read = is.read(data)) > -1) {
    			os.write(data, 0, read);
    		}
    		os.flush();
    		is.close();
    		os.close();
        }
		get.releaseConnection();
        return file;
	}
	
	public void upload(File file, String uploadTo) throws HttpException, IOException{
		PutMethod put = new PutMethod(uploadTo);
		InputStream is = new FileInputStream(file);
		RequestEntity entity = new InputStreamRequestEntity(is);
		put.setRequestEntity(entity);
		http.executeMethod(put);
		is.close();
		put.releaseConnection();
	}
	
	public void open(File file) throws IOException{
    	String cmd = String.format(opencmd, file.toURI().toURL());
    	Runtime.getRuntime().exec(cmd);
    }
	
	public File createFile(String name) throws IOException{
		File tmp = new File(cache, name);
		tmp.createNewFile();
		return tmp;
	}
	
	public TaskManager getTaskManager(){
		return manager;
	}
	
	public File getEtc(){
		return etc;
	}
	
	public File getMeta(){
		return meta;
	}
	
	public File getCache(){
		return cache;
	}
	
}
