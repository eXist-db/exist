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
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;

/**
 * @author wolf
 */
public class Delete implements WebDAVMethod {
	
	private BrokerPool pool;
	
	public Delete(BrokerPool pool) {
		this.pool = pool;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.webdav.WebDAVMethod#process(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
	 */
	public void process(User user, HttpServletRequest request,
			HttpServletResponse response, Collection collection,
			DocumentImpl resource) throws ServletException, IOException {
		if(collection == null) {
			response.sendError(HttpServletResponse.SC_NOT_FOUND);
			return;
		}
		DBBroker broker = null;
		try {
			broker = pool.get(user);
			if(resource == null) {
				broker.removeCollection(collection.getName());
			} else {
				if(resource.getResourceType() == DocumentImpl.BINARY_FILE)
					resource.getCollection().removeBinaryResource(broker, resource.getFileName());
				else
					resource.getCollection().removeDocument(broker, resource.getFileName());
			}
		} catch (EXistException e) {
			response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
		} catch (PermissionDeniedException e) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
		} catch (LockException e) {
			response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} catch (TriggerException e) {
			response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
		} finally {
			pool.release(broker);
		}
		response.setStatus(HttpServletResponse.SC_NO_CONTENT);
	}
}
