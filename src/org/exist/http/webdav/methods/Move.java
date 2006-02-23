/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.Constants;

/**
 * Implements the WebDAV move method.
 * @author wolf
 */
public class Move extends AbstractWebDAVMethod {

    
    public Move(BrokerPool pool) {
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
			broker = pool.get(user);
			collection = broker.openCollection(path, Lock.WRITE_LOCK);
			if(collection == null) {
                                ///TODO : use dedicated function in XmldbURI
				int pos = path.lastIndexOf("/");
				String collName = path.substring(0, pos);
				String docName = path.substring(pos + 1);
				collection = broker.openCollection(collName, Lock.WRITE_LOCK);
				if(collection == null) {
					LOG.debug("No resource or collection found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
				resource = collection.getDocumentWithLock(broker, docName, Lock.WRITE_LOCK);
				if(resource == null) {
					LOG.debug("No resource found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
			}
	        String destination = request.getHeader("Destination");
	        String destPath = null;
	        try {
	            URI uri = new URI(destination);
	            String host = uri.getHost();
	            int port = uri.getPort();
	            if(!(host.equals(request.getServerName()) && port == request.getServerPort())) {
	                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
	                        "Copying to a different server is not yet implemented");
	                return;
	            }
	            destPath = uri.getPath();
	            if(destPath.startsWith(request.getContextPath()))
	                destPath = destPath.substring(request.getContextPath().length());
	            if(destPath.startsWith(request.getServletPath()))
	                destPath = destPath.substring(request.getServletPath().length());
	        } catch (URISyntaxException e) {
	            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Malformed URL in destination header");
	        }
	        if(resource != null)
	            moveResource(user, broker, request, response, resource, destPath);
	        else
	            moveCollection(user, broker, request, response, collection, destPath);
		} catch (EXistException e) {
			throw new ServletException("Failed to copy: " + e.getMessage(), e);
		} catch (LockException e) {
			throw new ServletException("Failed to copy: " + e.getMessage(), e);
		} finally {
			if(collection != null)
				collection.release();
			if(resource != null)
				resource.getUpdateLock().release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
    }

    private void moveCollection(User user, DBBroker broker, HttpServletRequest request, HttpServletResponse response, 
            Collection collection, String destination) throws ServletException, IOException {
        //TODO : use dedicated function in XmldbURI
        if(collection.getName().equals(destination)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Source and destination are the same");
            return;
        }
        int p = destination.lastIndexOf("/");
        if(p == Constants.STRING_NOT_FOUND) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Bad destination: " + destination);
            return;
        }
        Collection destCollection = null;
        TransactionManager transact = pool.getTransactionManager();
        try {
            Txn txn = transact.beginTransaction();
            try {
                
                boolean replaced = false;
                destCollection = broker.openCollection(destination, Lock.WRITE_LOCK);
        		if(destCollection != null) {
        			boolean overwrite = overwrite(request);
        			if(!overwrite) {
        				response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
        						"Destination collection exists and overwrite is not allowed");
        				return;
        			}
        			broker.removeCollection(txn, destCollection);
        			replaced = true;
        		}
                
                String parentPath = destination.substring(0, p);
                String newCollectionName = destination.substring(p + 1);
                LOG.debug("parent = " + parentPath + "; new name = " + newCollectionName);
                destCollection = broker.openCollection(parentPath, Lock.WRITE_LOCK);
                if(destCollection == null) {
                    response.sendError(HttpServletResponse.SC_CONFLICT,
                            "No parent collection: " + parentPath);
                    return;
                }
                broker.moveCollection(txn, collection, destCollection, newCollectionName);
                if(replaced)
                    response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                else
                    response.setStatus(HttpServletResponse.SC_CREATED);
            } catch (PermissionDeniedException e) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            } catch (LockException e) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } finally {
            	if(destCollection != null)
            		destCollection.release();
                transact.commit(txn);
                pool.release(broker);
            }
        } catch (TransactionException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    private void moveResource(User user, DBBroker broker, HttpServletRequest request, HttpServletResponse response, 
    		DocumentImpl resource, String destination)
    throws ServletException, IOException {
        //TODO : use dedicated function in XmldbURI
    	int p = destination.lastIndexOf("/");
        if(p == Constants.STRING_NOT_FOUND) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Bad destination: " + destination);
            return;
        }
        String newResourceName = destination.substring(p + 1);
        destination = destination.substring(0, p);
        boolean replaced = false;
        Collection destCollection = null;
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            destCollection = broker.openCollection(destination, Lock.WRITE_LOCK);
            if(destCollection == null) {
                transact.abort(transaction);
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Destination collection not found");
                return;
            }
    		
            DocumentImpl oldDoc = destCollection.getDocument(broker, newResourceName);
            if(oldDoc != null) {
                boolean overwrite = overwrite(request);
                if(!overwrite) {
                    transact.abort(transaction);
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED,
                            "Destination resource exists and overwrite is not allowed");
                    return;
                }
                replaced = true;
            }
            broker.moveXMLResource(transaction, resource, destCollection, newResourceName);
            transact.commit(transaction);
            if(replaced)
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            else
                response.setStatus(HttpServletResponse.SC_CREATED);
        } catch (PermissionDeniedException e) {
            transact.abort(transaction);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (LockException e) {
            transact.abort(transaction);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (TransactionException e) {
            transact.abort(transaction);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } finally {
        	if(destCollection != null)
        		destCollection.release();
        }
    }
    
    private boolean overwrite(HttpServletRequest request) {
        String header = request.getHeader("Overwrite");
        if(header == null)
            return false;
        return header.equals("T");
    }
}
