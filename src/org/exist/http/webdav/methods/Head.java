/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.http.webdav.methods;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAV;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.security.User;

/**
 * @author wolf
 */
public class Head implements WebDAVMethod {
	
	/* (non-Javadoc)
	 * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
	 */
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, Collection collection,
			DocumentImpl resource) throws ServletException, IOException {
		if(resource == null) {
			// GET is not available on collections
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "GET is not available on collections");
			return;
		}
		String contentType;
		if(resource.getResourceType() == DocumentImpl.XML_FILE)
			contentType = WebDAV.XML_CONTENT;
		else
			contentType = WebDAV.BINARY_CONTENT;
		response.setContentType(contentType);
		response.addDateHeader("Last-Modified", resource.getLastModified());
	}
}
