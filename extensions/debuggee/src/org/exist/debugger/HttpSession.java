/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2011 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.debugger;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.PostMethod;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class HttpSession implements Runnable {
	
	private DebuggerImpl debugger;
	private String url;
	
	protected HttpSession(DebuggerImpl debugger, String url) {
		this.debugger = debugger;
		this.url = url;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod(url);

		NameValuePair[] postData = new NameValuePair[1];
        postData[0] = new NameValuePair("XDEBUG_SESSION", "default");
        
        method.addParameters(postData);

		try {
			System.out.println("sending http request with debugging flag");
			
			debugger.terminate(url, client.executeMethod(method));

			System.out.println("get http response");
		} catch (Exception e) {
		}
	}

}
