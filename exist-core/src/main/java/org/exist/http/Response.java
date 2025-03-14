/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http;

import org.exist.util.MimeType;

import jakarta.servlet.http.HttpServletResponse;
import java.io.DataOutputStream;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Response {
	
	private final static String stdHeaders = 
		"Allow: POST GET PUT DELETE\n" +
		"Server: eXist\n" +
		"Cache-control: no-cache\n";
	
	private int code = HttpServletResponse.SC_OK;
	private String statusDesc = null;
	private String content = null;
	private byte[] binaryContent = null;
	private String encoding = UTF_8.name();
	private String contentType = MimeType.XML_TYPE.getName();
	
	public Response() {
	}
	
	public Response(int code, String message) {
		this.code = code;
		this.statusDesc = message;
	}
	
	public Response(String content) {
		this.content = content;
	}
	
	public void setResponseCode(int code) {
		this.code = code;
	}
	
	public int getResponseCode() {
		return this.code;
	}
	
	public void setEncoding(String encoding) {
		this.encoding = encoding;
	}
	
	public String getEncoding() {
		return this.encoding;
	}
	
	public void setDescription(String description) {
		this.statusDesc = description;
	}
	
	public String getDescription() {
		return this.statusDesc;
	}
	
	public void setContent(String content) {
		this.content = content;
	}
	
	public void setContent(byte[] content) {
		this.binaryContent =content;
	}
	
	public byte[] getContent() throws IOException {
		return content == null ? (binaryContent != null ? binaryContent : null) : content.getBytes(encoding);
	}
	
	public void setContentType(String type) {
		this.contentType = type;
	}
	
	public String getContentType() {
		return this.contentType;
	}
	
	public void write(DataOutputStream os) throws IOException {
		final byte[] contentData = content == null ? (binaryContent != null ? binaryContent : null) : content.getBytes(encoding);
		final StringBuilder buf = new StringBuilder();
		buf.append("HTTP/1.0 ");
		buf.append(code);
		if(statusDesc != null) {
			buf.append(' ');
			buf.append(statusDesc);
		} else
			{buf.append(" OK");}
		buf.append('\n');
		buf.append(stdHeaders);
		buf.append("Content-Type: ");
		buf.append(contentType);
		buf.append("\nContent-Length: ");
		buf.append(contentData == null ? 0 : contentData.length);
		buf.append("\n\n");
		
		os.writeBytes(buf.toString());
		if(contentData != null)
			{os.write(contentData, 0, contentData.length);}
		os.flush();
		os.close();
	}
}