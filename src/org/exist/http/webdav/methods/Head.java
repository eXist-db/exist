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

import org.exist.EXistException;
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAV;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Lock;
import org.exist.collections.Collection;

/**
 * @author wolf
 */
public class Head extends AbstractWebDAVMethod {
	
	public Head(BrokerPool pool) {
		super(pool);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
	 */
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException, IOException {
		DBBroker broker = null;
		Collection collection = null;
		DocumentImpl resource = null;
		try {
			broker = pool.get();
			collection = broker.openCollection(path, Lock.READ_LOCK);
			if(collection != null) {
				response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "GET is not available on collections");
				return;
			}
			resource = broker.openDocument(path, Lock.READ_LOCK);
			if(resource == null) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource not found");
				return;
			}
			if(!resource.getPermissions().validate(user, Permission.READ)) {
				response.sendError(HttpServletResponse.SC_FORBIDDEN, READ_PERMISSION_DENIED);
				return;
			}
			String contentType;
			if(resource.getResourceType() == DocumentImpl.XML_FILE)
				contentType = WebDAV.XML_CONTENT;
			else
				contentType = WebDAV.BINARY_CONTENT;
			response.setContentType(contentType);
			response.setContentLength(resource.getContentLength());
			response.addDateHeader("Last-Modified", resource.getLastModified());
		} catch (EXistException e) {
			throw new ServletException(e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, READ_PERMISSION_DENIED);
		} finally {
			if(collection != null)
				collection.release();
			if(resource != null)
				resource.getUpdateLock().release(Lock.READ_LOCK);
			pool.release(broker);
		}
	}
}
