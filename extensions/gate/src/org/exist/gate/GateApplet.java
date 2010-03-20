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
import java.io.IOException;
import java.util.Timer;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.ProxyHost;

public class GateApplet extends Applet {
	
	private static final long serialVersionUID = -8952536002584984227L;
	
	private HttpClient http = new HttpClient();
	
	private TaskManager manager = new TaskManager();
	
	private String opencmd;
	
	private File home = new File(System.getProperty("user.home"));
	private File etc  = new File(home, ".eXist");
	private File gate = new File(etc, "gate");
	
	public final static long PERIOD = 1000; 
	
	public GateApplet(){
		super();
		
		// Setup HTTP proxy
		String proxyHost = System.getProperty( "http.proxyHost"); 
        if (proxyHost != null && !proxyHost.equals("")) {
        	ProxyHost proxy = new ProxyHost(proxyHost, Integer.parseInt(System.getProperty("http.proxyPort")));
            http.getHostConfiguration().setProxyHost(proxy);
        }
        
        // Create gate folder
        if (!gate.isDirectory()){
        	gate.mkdirs();
        }
        
        // Detect OS open file command 
        String os = System.getProperty("os.name").toLowerCase();
        if ( os.indexOf("windows") != -1 || os.indexOf("nt") != -1){
            opencmd = "cmd /c start";
        } else if ( os.indexOf("mac") != -1 ) {
            opencmd = "open";
        } else {
        	opencmd = "xdg-open";
        }
        
		Timer timer = new Timer();
		timer.schedule(manager, PERIOD, PERIOD);
	}
	
	public HttpClient getHttp() {
		return http;
	}
	
	public void manage(String downloadFrom, String uploadTo){
		manager.addTask(new Task(downloadFrom, uploadTo, this));
	}
	
	public void open(File file) throws IOException{
    	String cmd = String.format(opencmd + " '%s'", file.getAbsolutePath());
    	Runtime.getRuntime().exec(cmd);
    }
	
	public File createFile(String name) throws IOException{
		File tmp = new File(gate, name);
		tmp.createNewFile();
		return tmp;
	}
	
}
