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
import org.exist.util.Lock;
import org.exist.util.LockException;


/**
 * Implements the WebDAV COPY method.
 * 
 * @author wolf
 */
public class Copy implements WebDAVMethod {

    private BrokerPool pool;
    
    /**
     * 
     */
    public Copy(BrokerPool pool) {
        super();
        this.pool = pool;
    }

    /* (non-Javadoc)
     * @see org.exist.http.webdav.WebDAVMethod#process(org.exist.security.User, javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
     */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, Collection collection,
            DocumentImpl resource) throws ServletException, IOException {
        if(collection == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Resource or collection not found");
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
                        "Copying to a different server is not yet implemented");
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
        if(resource != null)
            copyResource(user, request, response, resource, path);
        else
            response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
                    "Copying collections is not yet implemented");
    }

    private void copyResource(User user, HttpServletRequest request, HttpServletResponse response, DocumentImpl resource, 
            String destination)
    throws ServletException, IOException {
        int p = destination.lastIndexOf('/');
        if(p < 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Bad destination: " + destination);
            return;
        }
        String newResourceName = destination.substring(p + 1);
        destination = destination.substring(0, p);
        boolean replaced = false;
        DBBroker broker = null;
        Collection destCollection = null;
        Collection sourceCollection = null;
        try {
            broker = pool.get(user);
            destCollection = broker.openCollection(destination, Lock.WRITE_LOCK);
            if(destCollection == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Destination collection not found");
                return;
            }
            String sourcePath = resource.getName();
            int pos = sourcePath.lastIndexOf('/');
    		String collName = sourcePath.substring(0, pos);
    		String docName = sourcePath.substring(pos + 1);
    		sourceCollection = broker.openCollection(collName, Lock.READ_LOCK);
    		
            DocumentImpl oldDoc = destCollection.getDocument(broker, newResourceName);
            if(oldDoc != null) {
                boolean overwrite = overwrite(request);
                if(!overwrite) {
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                            "Destination resource exists and overwrite is not allowed");
                    return;
                }
                replaced = true;
            }
            broker.copyResource(resource, destCollection, newResourceName);
            if(replaced)
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            else
                response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (LockException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
        	if(sourceCollection != null)
        		sourceCollection.release();
        	if(destCollection != null)
        		destCollection.release();
            pool.release(broker);
        }
    }
    
    private void copyCollection(User user, HttpServletRequest request, HttpServletResponse response, 
    		Collection collection, String destination)
    throws ServletException, IOException {
        int p = destination.lastIndexOf('/');
        if(p < 0) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Bad destination: " + destination);
            return;
        }
        String newCollectionName = destination.substring(p + 1);
        destination = destination.substring(0, p);
        boolean replaced = false;
        DBBroker broker = null;
        Collection destCollection = null;
        try {
            broker = pool.get(user);
            destCollection = broker.openCollection(destination, Lock.WRITE_LOCK);
            if(destCollection == null) {
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Destination collection not found");
                return;
            }
    		if(destCollection.hasChildCollection(newCollectionName)) {
    			boolean overwrite = overwrite(request);
    			if(!overwrite) {
    				response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
    						"Destination collection exists and overwrite is not allowed");
    				return;
    			}
    			replaced = true;
    		}
            
            broker.copyCollection(collection, destCollection, newCollectionName);
            if(replaced)
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            else
                response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (EXistException e) {
            throw new ServletException(e.getMessage(), e);
        } catch (PermissionDeniedException e) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (LockException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
        	if(destCollection != null)
        		destCollection.release();
            pool.release(broker);
        }
    }
    
    private boolean overwrite(HttpServletRequest request) {
        String header = request.getHeader("Overwrite");
        if(header == null)
            return false;
        return header.equals("T");
    }
}
