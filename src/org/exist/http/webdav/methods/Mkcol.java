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
import org.exist.dom.DocumentImpl;
import org.exist.http.webdav.WebDAVMethod;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;


/**
 * @author wolf
 */
public class Mkcol implements WebDAVMethod {

    private BrokerPool pool;
    
    /**
     * 
     */
    public Mkcol(BrokerPool pool) {
        super();
        this.pool = pool;
    }

    /* (non-Javadoc)
     * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
     */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, Collection collection,
            DocumentImpl resource) throws ServletException, IOException {
        if(collection != null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "collection " + request.getPathInfo() + " already exists");
            return;
        }
        if(resource != null) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "path conflicts with an existing resource");
            return;
        }
        String path = request.getPathInfo();
        if(path == null || path.equals("")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "no path specified");
            return;
        }
        int p = path.lastIndexOf('/');
        String parentPath = -1 < p ? path.substring(0, p) : "/db";
        String newCollection = -1 < p ? path.substring(p + 1) : path;
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            Collection parent = broker.getCollection(parentPath);
            if(parent == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "parent collection not found");
                return;
            }
            Collection created = broker.getOrCreateCollection(path);
            broker.saveCollection(created);
            broker.flush();
            LOG.debug("created collection " + path);
            response.sendError(HttpServletResponse.SC_CREATED);
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
