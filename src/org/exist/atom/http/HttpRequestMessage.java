/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
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
package org.exist.atom.http;

import org.exist.atom.IncomingMessage;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

/**
 * 
 * @author R. Alexander Milowski
 */
public class HttpRequestMessage implements IncomingMessage {

	String path;
	String base;
	HttpServletRequest request;

	/** Creates a new instance of HttpRequestMIMEMessage */
	public HttpRequestMessage(HttpServletRequest request, String path,
			String base) {
		this.request = request;
		this.path = path;
		this.base = base;
	}

	public String getMethod() {
		return request.getMethod();
	}

	public String getPath() {
		return path;
	}

	public String getParameter(String name) {
		return request.getParameter(name);
	}

	public String getHeader(String key) {
		return request.getHeader(key);
	}

	public long getContentLength() {
		long len = request.getContentLength();
		final String lenstr = request.getHeader("Content-Length");
		if(lenstr!=null)
			{len = Long.parseLong(lenstr);}
		return len;
	}

	public InputStream getInputStream() throws IOException {
		return request.getInputStream();
	}

	public Reader getReader() throws IOException {
		return request.getReader();
	}

	public String getModuleBase() {
		return base;
	}

	public HttpServletRequest getRequest() {
		return request;
	}
}