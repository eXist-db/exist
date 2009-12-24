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
import org.exist.xmldb.XmldbURI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

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
            HttpServletResponse response, XmldbURI path) throws ServletException, IOException {
    	DBBroker broker = null;
		Collection collection = null;
		DocumentImpl resource = null;
		try {
			broker = pool.get(user);
			collection = broker.openCollection(path, Lock.WRITE_LOCK);
			if(collection == null) {
            	XmldbURI docUri = path.lastSegment();
            	XmldbURI collUri = path.removeLastSegment();
				collection = broker.openCollection(collUri, Lock.WRITE_LOCK);
				if(collection == null) {
					LOG.debug("No resource or collection found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
				resource = collection.getDocumentWithLock(broker, docUri, Lock.WRITE_LOCK);
				if(resource == null) {
					LOG.debug("No resource found for path: " + path);
					response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
					return;
				}
			}
			//TODO : release collection lock here ?
			
	        String destination = request.getHeader("Destination");
	        XmldbURI destPath = null;
	        try {
	            URI uri = new URI(destination);
	            String host = uri.getHost();
	            int port = uri.getPort();
	            if(!(host.equals(request.getServerName()) && port == request.getServerPort())) {
	                response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED,
	                        "Copying to a different server is not yet implemented");
	                return;
	            }
	            
                //TODO: use XmldbURI for this stuff too
                String tempDestPath = uri.getPath();
                
                if(tempDestPath.startsWith(request.getContextPath()))
                	tempDestPath = tempDestPath.substring(request.getContextPath().length());
                
                if(tempDestPath.startsWith(request.getServletPath()))
                	tempDestPath = tempDestPath.substring(request.getServletPath().length());
                
                destPath = XmldbURI.create(tempDestPath);
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
				collection.release(Lock.WRITE_LOCK);
			if(resource != null)
				resource.getUpdateLock().release(Lock.WRITE_LOCK);
			pool.release(broker);
		}
    }

    private void moveCollection(User user, DBBroker broker, HttpServletRequest request, HttpServletResponse response, 
            Collection collection, XmldbURI destination) throws ServletException, IOException {
        if(collection.getURI().equals(destination)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN,
                    "Source and destination are the same");
            return;
        }
        if(destination.lastSegment()==null) {
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
                
            	XmldbURI parentURI = destination.removeLastSegment();
            	XmldbURI newCollURI = destination.lastSegment();
                 LOG.debug("parent = " + parentURI + "; new name = " + newCollURI);
                destCollection = broker.openCollection(parentURI, Lock.WRITE_LOCK);
                if(destCollection == null) {
                    response.sendError(HttpServletResponse.SC_CONFLICT,
                            "No parent collection: " + parentURI);
                    return;
                }
                broker.moveCollection(txn, collection, destCollection, newCollURI);
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
            		destCollection.release(Lock.WRITE_LOCK);
                transact.commit(txn);
            }
        } catch (TransactionException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
    
    private void moveResource(User user, DBBroker broker, HttpServletRequest request, HttpServletResponse response, 
    		DocumentImpl resource, XmldbURI destination)
    throws ServletException, IOException {
    	XmldbURI docUri = destination.lastSegment();
    	XmldbURI collUri = destination.removeLastSegment();
        if(docUri == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Bad destination: " + destination);
            return;
        }
        boolean replaced = false;
        Collection destCollection = null;
        TransactionManager transact = broker.getBrokerPool().getTransactionManager();
        Txn transaction = transact.beginTransaction();
        try {
            destCollection = broker.openCollection(collUri, Lock.WRITE_LOCK);
            if(destCollection == null) {
                transact.abort(transaction);
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Destination collection not found");
                return;
            }
    		
            DocumentImpl oldDoc = destCollection.getDocument(broker, docUri);
            //TODO : release collection lock here ?
            
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
            broker.moveResource(transaction, resource, destCollection, docUri);
            
            // [1509776] After a webDAV MOVE all locks need to be removed.
            resource.setUserLock(null);
            resource.getMetadata().setLockToken(null);
            
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
        		destCollection.release(Lock.WRITE_LOCK);
        }
    }
    
    private boolean overwrite(HttpServletRequest request) {
        String header = request.getHeader("Overwrite");
        if(header == null)
            return false;
        return header.equals("T");
    }
}
