/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.source;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.net.URLConnection;

import org.apache.log4j.Logger;
import org.exist.storage.DBBroker;

/**
 * A source implementation reading from an URL.
 * 
 * @author wolf
 */
public class URLSource extends AbstractSource {

	private final static Logger LOG = Logger.getLogger(URLSource.class);
	
	private URL url;
	private URLConnection connection = null;
	private long lastModified = 0;
	
	protected URLSource() {   
    }
    
	public URLSource(URL url) {
		this.url = url;
	}
	
    protected void setURL(URL url) {
        this.url = url;
    }
    
    public URL getURL() {
    	return url;
    }

    
	private long getLastModification() {
		try {
			if(connection == null)
				connection = url.openConnection();
			return connection.getLastModified();
		} catch (IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			return 0;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.source.Source#getKey()
	 */
	public Object getKey() {
		return url.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.source.Source#isValid()
	 */
	public int isValid(DBBroker broker) {
		long modified = getLastModification();
		if(modified == 0 && modified > lastModified)
			return INVALID;
		else
			return VALID;
	}

	/* (non-Javadoc)
	 * @see org.exist.source.Source#isValid(org.exist.source.Source)
	 */
	public int isValid(Source other) {
		return INVALID;
	}

	/* (non-Javadoc)
	 * @see org.exist.source.Source#getReader()
	 */
	public Reader getReader() throws IOException {
		try {
			if(connection == null)
				connection = url.openConnection();
			Reader reader = new InputStreamReader(connection.getInputStream(), "UTF-8");
			connection = null;
			return reader;
		} catch (IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			throw e;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.source.Source#getContent()
	 */
	public String getContent() throws IOException {
		try {
			if(connection == null)
				connection = url.openConnection();
			String content = connection.getContent().toString();
			connection = null;
			return content;
		} catch (IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			return null;
		}
	}
	
	public String toString() {
		if (url == null)
			return "[not set]";
		return url.toString();
	}
}
