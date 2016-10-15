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

import java.applet.Applet;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.ProxyHost;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.InputStreamRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

/**
 * Applet is make possible to edit documents,
 * stored in the eXist directly in desktop applications 
 * via REST.
 * @author Evgeny Gazdovsky (gazdovsky@gmail.com)
 */
public class NetEditApplet extends Applet {
	
	private static final long serialVersionUID = -8952536002584984227L;
	
	private HttpClient http = new HttpClient();
	
	// Since we use applet's methods from unsigned javascript, 
	// we must have a trusted thread for operations in local file system  
	private TaskManager manager = new TaskManager(this);
	
	private String sessionid;
	private String opencmd;
	
	private Path user;   // user home folder
	private Path exist;  // eXist's folder
	private Path etc;    // Gate's folder
	private Path meta;   // Task's meta storage folder
	private Path cache;  // Cache folder

	
	public final static long PERIOD = 1000; // Default period/delay for different operations
	
	public void init(){
		
		sessionid = getParameter("sessionid");
		
		user  = Paths.get(System.getProperty("user.home"));
		exist = user.resolve(".eXist");
		etc   = exist.resolve("gate");

		String host = getParameter("host");
		if (host != null) {
			etc = etc.resolve(host);
		}
		
		meta  = etc.resolve("meta");
		cache = etc.resolve("cache");
		
		// Setup HTTP proxy
		String proxyHost = System.getProperty( "http.proxyHost"); 
        if (proxyHost != null && !proxyHost.equals("")) {
        	ProxyHost proxy = new ProxyHost(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort")));
            http.getHostConfiguration().setProxyHost(proxy);
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
        
        // Load tasks of old applet's sessions
		try {
			manager.load();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		// Start main trusted thread
        Timer timer = new Timer();
		timer.schedule(manager, PERIOD, PERIOD);
	}
	
	public HttpClient getHttp() {
		return http;
	}
	
	/**
	 * Add task to manage the remote doc 
	 * @param downloadFrom URL of remote doc for download
	 * @param uploadTo URL of remote doc for upload back after doc will be changing
	 */
	public void manage(String downloadFrom, String uploadTo){
		manager.addTask(new Task(downloadFrom, uploadTo, this));
	}
	
	private void useCurrentSession(HttpMethodBase method){
		if (sessionid != null){
			method.setRequestHeader("Cookie", "JSESSIONID=" + sessionid);
		}
	}
	
	/**
	 * Download remote doc
	 * @param downloadFrom URL of remote doc for download * @return downloaded file
	 * @throws IOException
	 */
	public Path download(String downloadFrom) throws IOException{
		Path file = null;
		GetMethod get = new GetMethod(downloadFrom);
		try {
			useCurrentSession(get);
			http.executeMethod(get);
			long contentLength = get.getResponseContentLength();

			if (contentLength < Integer.MAX_VALUE) {
				InputStream is = get.getResponseBodyAsStream();
				file = createFile(get.getPath());
				Files.copy(is, file);
			}
		} finally {
			get.releaseConnection();
		}
        return file;
	}
	
	/**
	 * Upload file to server 
	 * @param file uploaded file
	 * @param uploadTo URL of remote doc to upload
	 * @throws HttpException
	 * @throws IOException
	 */
	public void upload(Path file, String uploadTo) throws HttpException, IOException{
		PutMethod put = new PutMethod(uploadTo);
		useCurrentSession(put);
		try(final InputStream is = Files.newInputStream(file)) {
			RequestEntity entity = new InputStreamRequestEntity(is);
			put.setRequestEntity(entity);
			http.executeMethod(put);
		} finally {
			put.releaseConnection();
		}
	}
	
	/**
	 * Open file on application, registered for type of file in current Desktop 
	 * @param file opened file
	 * @throws IOException
	 */
	public void open(Path file) throws IOException{
    	String cmd = String.format(opencmd, file.toUri().toURL());
    	Runtime.getRuntime().exec(cmd);
    }
	
	/**
	 * Create file in local cache
	 * @param path name of file
	 * @return file in cache
	 * @throws IOException
	 */
	public Path createFile(String path) throws IOException{
		Path tmp = cache.resolve(path);
		Path fld = tmp.getParent();
		if (!Files.isDirectory(fld)){
			Files.createDirectories(fld);
		}
		Files.createFile(tmp);
		return tmp;
	}
	
	/**
	 * @return task manager
	 */
	public TaskManager getTaskManager(){
		return manager;
	}
	
	/**
	 * @return folder of GateApplet in local FS
	 */
	public Path getEtc(){
		return etc;
	}
	
	/**
	 * @return "meta" folder of in local FS
	 */
	public Path getMeta(){
		return meta;
	}
	
	/**
	 * @return "cache" folder in local FS
	 */
	public Path getCache(){
		return cache;
	}

}
