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

import org.exist.atom.OutgoingMessage;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;

/**
 * 
 * @author R. Alexander Milowski
 */
public class HttpResponseMessage implements OutgoingMessage {

	HttpServletResponse response;

	/** Creates a new instance of HttpRequestMIMEMessage */
	public HttpResponseMessage(HttpServletResponse response) {
		this.response = response;
	}

	public void setStatusCode(int code) {
		response.setStatus(code);
	}

	public void setContentType(String value) {
		response.setContentType(value);
	}

	public void setHeader(String key, String value) {
		response.setHeader(key, value);
	}

	public OutputStream getOutputStream() throws IOException {
		return response.getOutputStream();
	}

	public Writer getWriter() throws IOException {
		return response.getWriter();
	}

	public HttpServletResponse getResponse() {
		return response;
	}
}