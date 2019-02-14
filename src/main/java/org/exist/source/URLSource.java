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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A source implementation reading from an URL.
 * 
 * @author wolf
 */
public class URLSource extends AbstractSource {

	private final static Pattern URL_PATTERN = Pattern.compile("(?i)\\bcharset=\\s*\"?([^\\s;\"]*)");

	private final static Logger LOG = LogManager.getLogger(URLSource.class);
	
	protected URL url;
	private URLConnection connection = null;
	private long lastModified = 0;
    private int responseCode = HttpURLConnection.HTTP_OK;

	protected URLSource() {   
    }
    
	public URLSource(URL url) {
		this.url = url;
	}

	@Override
	public String path() {
		final String protocol = url.getProtocol();
		final String host = url.getHost();
		if(protocol.equals("file") && (host == null || host.length() == 0 || "localhost".equals(host) || "127.0.0.1".equals(host)))
		{
			return url.getFile();
		}
		return url.toExternalForm();
	}

	@Override
	public String type() {
		final String protocol = url.getProtocol();
		final String host = url.getHost();
		if(protocol.equals("file") && (host == null || host.length() == 0 || "localhost".equals(host) || "127.0.0.1".equals(host)))
		{
			return "File";
		}
		return "URL";
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
				{connection = url.openConnection();}
			return connection.getLastModified();
		} catch (final IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			return 0;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.source.Source#getKey()
	 */
	public Object getKey() {
		return url;
	}

	@Override
	public Validity isValid(final DBBroker broker) {
		final long modified = getLastModification();
		if(modified == 0 && modified > lastModified) {
			return Validity.INVALID;
		} else {
			return Validity.VALID;
		}
	}

	@Override
	public Validity isValid(final Source other) {
		return Validity.INVALID;
	}

	public Charset getEncoding() throws IOException {
		if (connection == null) {
			connection = url.openConnection();
		}
		final String contentType = connection.getContentType();
		if (contentType != null) {
			final Matcher matcher = URL_PATTERN.matcher(contentType);
			if (matcher.find()) {
				try {
					return Charset.forName(matcher.group(1).trim().toUpperCase());
				} catch (IllegalArgumentException e) {
					// unknown or illegal charset
					return null;
				}
			} else if (contentType.startsWith("text/")) {
				return StandardCharsets.ISO_8859_1;
			}
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.source.Source#getReader()
	 */
	public Reader getReader() throws IOException {
		try {
			if(connection == null) {
				connection = url.openConnection();
                if (connection instanceof HttpURLConnection)
                    {responseCode = ((HttpURLConnection) connection).getResponseCode();}
            }
            Reader reader = null;
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND)
			    {reader = new InputStreamReader(connection.getInputStream(), "UTF-8");}
			connection = null;
			return reader;
		} catch (final IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			throw e;
		}
	}

    public InputStream getInputStream() throws IOException {
        try {
            if(connection == null) {
                connection = url.openConnection();
                if (connection instanceof HttpURLConnection)
                    {responseCode = ((HttpURLConnection) connection).getResponseCode();}
            }
            InputStream is = null;
            if (responseCode != HttpURLConnection.HTTP_NOT_FOUND)
                {is = connection.getInputStream();}
            connection = null;
            return is;
            
        } catch (final ConnectException e){
            LOG.warn("Unable to connect to URL: " + e.getMessage());
            throw e;

        } catch (final IOException e) {
            LOG.warn("URL could not be opened: " + e.getMessage(), e);
            throw e;
        }
    }

    /* (non-Javadoc)
	 * @see org.exist.source.Source#getContent()
	 */
	public String getContent() throws IOException {
		try {
			if(connection == null) {
				connection = url.openConnection();
                if (connection instanceof HttpURLConnection)
                    {responseCode = ((HttpURLConnection) connection).getResponseCode();}
            }
			final String content = connection.getContent().toString();
			connection = null;
			return content;
		} catch (final IOException e) {
			LOG.warn("URL could not be opened: " + e.getMessage(), e);
			return null;
		}
	}

    public int getResponseCode() {
        return responseCode;
    }

	public String toString() {
		if (url == null)
			{return "[not set]";}
		return url.toString();
	}

	@Override
	public void validate(Subject subject, int perm) throws PermissionDeniedException {
		// TODO protected?
	}

    @Override
    public QName isModule() throws IOException {
		try (final InputStream is = getInputStream()) {
			return getModuleDecl(is);
		}
    }
}
