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
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Lock;


/**
 * Implements the WebDAV MKCOL method.
 * 
 * @author wolf
 */
public class Mkcol extends AbstractWebDAVMethod {

    public Mkcol(BrokerPool pool) {
        super(pool);
    }

    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, String path) throws ServletException, IOException {
    	String origPath = request.getPathInfo();
    	if(path == null || path.equals("")) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "no path specified");
            return;
        }
    	DBBroker broker = null;
		Collection collection = null;
		try {
			broker = pool.get(user);
			collection = broker.openCollection(path, Lock.READ_LOCK);
			if(collection != null) {
				collection.release();
				response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
	                    "collection " + request.getPathInfo() + " already exists");
	            return;
			}
			int p = path.lastIndexOf('/');
	        String parentPath = -1 < p ? path.substring(0, p) : "/db";
	        String newCollection = -1 < p ? path.substring(p + 1) : path;
	        collection = broker.openCollection(parentPath, Lock.WRITE_LOCK);
	        if(collection == null) {
                LOG.debug("Parent collection " + parentPath + " not found");
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Parent collection not found");
                return;
            }
	        if(collection.hasDocument(newCollection)) {
	        	collection.release();
	        	response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
	        		"path conflicts with an existing resource");
	        	return;
	        }
	        // TODO: releasing the lock here is dangerous, but we may get into deadlocks otherwise.
	        collection.release();
            Collection created = broker.getOrCreateCollection(path);
            broker.saveCollection(created);
            broker.flush();
            LOG.debug("Created collection " + path);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } catch(EXistException e) {
            throw new ServletException("Database error: " + e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    e.getMessage());
        } finally {
            pool.release(broker);
        }
    }

}
