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
import java.net.URI;
import java.net.URISyntaxException;

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
import org.exist.util.LockException;

/**
 * Implements the WebDAV move method.
 * @author wolf
 */
public class Move implements WebDAVMethod {

    private BrokerPool pool;
    
    public Move(BrokerPool pool) {
        this.pool = pool;
    }
    
    /* (non-Javadoc)
     * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
     */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, Collection collection,
            DocumentImpl resource) throws ServletException, IOException {
        if(resource != null) {
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                    "Move is not yet implemented for resources");
            return;
        }
        String destination = request.getHeader("Destination");
        String path = null;
        try {
            URI uri = new URI(destination);
            String host = uri.getHost();
            int port = uri.getPort();
            if(!(host.equals(request.getServerName()) && port == request.getServerPort())) {
                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                        "Moving to a different server is not yet implemented");
                return;
            }
            path = uri.getPath();
            if(path.startsWith(request.getContextPath()))
                path = path.substring(request.getContextPath().length());
            if(path.startsWith(request.getServletPath()))
                path = path.substring(request.getServletPath().length());
        } catch (URISyntaxException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed URL in destination header");
        }
        LOG.debug("Moving " + collection.getName() + " to " + path);
        moveCollection(user, response, collection, path);
    }

    private void moveCollection(User user, HttpServletResponse response, 
            Collection collection, String destination) throws ServletException, IOException {
        if(collection.getName().equals(destination)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Source and destination are the same");
            return;
        }
        DBBroker broker = null;
        try {
            broker = pool.get(user);
            Collection destCollection = broker.getCollection(destination);
            if(destCollection != null) {
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                        "Destination collection exists");
                return;
            }
            int p = destination.lastIndexOf('/');
            if(p < 0) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Bad destination: " + destination);
                return;
            }
            String parentPath = destination.substring(0, p);
            String newCollectionName = destination.substring(p + 1);
            LOG.debug("parent = " + parentPath + "; new name = " + newCollectionName);
            Collection parent = broker.getCollection(parentPath);
            if(parent == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "No parent collection: " + parentPath);
                return;
            }
            broker.moveCollection(collection, parent, newCollectionName);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (LockException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
}
