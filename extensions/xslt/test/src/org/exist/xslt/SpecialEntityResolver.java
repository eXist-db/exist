/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009-2010 The eXist Project
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
package org.exist.xslt;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SpecialEntityResolver implements EntityResolver2 {
	
	private String rootURL;
	
	public SpecialEntityResolver(String rootURL) {
		this.rootURL = rootURL;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.EntityResolver2#getExternalSubset(java.lang.String, java.lang.String)
	 */
	public InputSource getExternalSubset(String name, String baseURI)
			throws SAXException, IOException {

		if (baseURI != null)
			return resolveInputSource(baseURI);
		
		return null;
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.ext.EntityResolver2#resolveEntity(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	 */
	public InputSource resolveEntity(String name, String publicId,
			String baseURI, String systemId) throws SAXException, IOException {

		return resolveInputSource(systemId);
	}

	/* (non-Javadoc)
	 * @see org.xml.sax.EntityResolver#resolveEntity(java.lang.String, java.lang.String)
	 */
	public InputSource resolveEntity(String publicId, String systemId)
			throws SAXException, IOException {

		return resolveInputSource(systemId);
	}

    private InputSource resolveInputSource(String path) throws IOException {

		try {
			InputSource inputsource = new InputSource();

			URI url = new URI(path);
    	
			InputStream is;
			if (url.isAbsolute())
				is = new URL(path).openStream();
			else {
				File file = new File(rootURL+path);
				is = new FileInputStream(file);
			}
        
			inputsource.setByteStream(is);
			inputsource.setSystemId(path);

        	return inputsource;
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		
    }
}
