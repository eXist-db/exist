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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.httpclient;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.apache.commons.httpclient.methods.multipart.PartSource;
import org.exist.xmldb.XmldbURI;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class DBFile implements PartSource {
	
	private XmldbURI uri;

    private URLConnection connection = null;
	
	public DBFile(String uri) {
		this.uri = XmldbURI.create(uri);
	}

	private URLConnection getConnection() throws IOException {
    	if (connection == null) {
			try {
				URL url = new URL(uri.toString());
				connection = url.openConnection();
			} catch (IllegalArgumentException e) {
				throw new IOException(e.getMessage()); 
			} catch (MalformedURLException e) {
				throw new IOException(e.getMessage()); 
			}
    	}
    	return connection;
	}
	
	/* (non-Javadoc)
	 * @see org.apache.commons.httpclient.methods.multipart.PartSource#createInputStream()
	 */
	public InputStream createInputStream() throws IOException {
    	return getConnection().getInputStream();
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.httpclient.methods.multipart.PartSource#getFileName()
	 */
	public String getFileName() {
		return uri.lastSegment().toString();
	}

	/* (non-Javadoc)
	 * @see org.apache.commons.httpclient.methods.multipart.PartSource#getLength()
	 */
	public long getLength() {
		try {
			return getConnection().getContentLength();
		} catch (IOException e) {
			return 0;
		}
	}
}