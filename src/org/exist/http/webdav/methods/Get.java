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
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.EXistException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAV;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.Lock;
import org.xml.sax.SAXException;

/**
 * Implements the WebDAV GET method.
 * 
 * @author wolf
 */
public class Get extends AbstractWebDAVMethod {
	
	private final static String SERIALIZE_ERROR = "Error while serializing document: ";
	
	public Get(BrokerPool pool) {
		super(pool);
	}
	
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, String path) throws ServletException, IOException {
		DBBroker broker = null;
		byte[] contentData = null;
		DocumentImpl resource = null;
		try {
			broker = pool.get();
			resource = broker.openDocument(path, Lock.READ_LOCK);
			if(resource == null) {
				// GET is not available on collections
				response.sendError(HttpServletResponse.SC_FORBIDDEN, "GET is not available on collections");
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
			response.addDateHeader("Last-Modified", resource.getLastModified());
			
			if(resource.getResourceType() == DocumentImpl.XML_FILE) {
				Serializer serializer = broker.getSerializer();
				serializer.reset();
				try {
					serializer.setProperties(WebDAV.OUTPUT_PROPERTIES);
					String content = serializer.serialize(resource);
					contentData = content.getBytes("UTF-8");
				} catch (SAXException e) {
					throw new ServletException(SERIALIZE_ERROR + e.getMessage(), e);
				}
			} else
				contentData = broker.getBinaryResourceData((BinaryDocument)resource);
		} catch (EXistException e) {
			throw new ServletException(SERIALIZE_ERROR + e.getMessage(), e);
		} catch (PermissionDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, READ_PERMISSION_DENIED);
		} finally {
			if(resource != null)
				resource.getUpdateLock().release(Lock.READ_LOCK);
			pool.release(broker);
		}
		if(contentData == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		response.setContentLength(contentData.length);
		ServletOutputStream os = response.getOutputStream();
		os.write(contentData);
		os.flush();
	}
}
