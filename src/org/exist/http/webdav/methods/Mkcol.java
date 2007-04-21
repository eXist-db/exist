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

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.xmldb.XmldbURI;


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
            HttpServletResponse response, XmldbURI path) throws ServletException, IOException {
    	//String origPath = request.getPathInfo();
    	if(path == null || path.equals("")) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
                    "no path specified");
            return;
        }
    	DBBroker broker = null;
		Collection collection = null;
		try {
			broker = pool.get(user);
			try {
				collection = broker.openCollection(path, Lock.READ_LOCK);
				if(collection != null) {
					response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
		                    "collection " + request.getPathInfo() + " already exists");
		            return;
				}
			} finally {
				if (collection != null)
					collection.release(Lock.READ_LOCK);
			}
			
            XmldbURI parentURI = path.removeLastSegment();
            if(parentURI==null)
            	parentURI = XmldbURI.ROOT_COLLECTION_URI;
            XmldbURI collURI = path.lastSegment();
            try {
		        collection = broker.openCollection(parentURI, Lock.WRITE_LOCK);
		        if(collection == null) {
	                LOG.debug("Parent collection " + parentURI + " not found");
	                response.sendError(HttpServletResponse.SC_CONFLICT,
	                        "Parent collection not found");
	                return;
	            }
		        if(collection.hasDocument(collURI)) {
		        	response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
		        		"path conflicts with an existing resource");
		        	return;
		        }
            } finally {
            	if (collection != null)
            		collection.release(Lock.WRITE_LOCK);
            }
            
            TransactionManager transact = pool.getTransactionManager();
            Txn txn = transact.beginTransaction();
            try {
                Collection created = broker.getOrCreateCollection(txn, path);
                broker.saveCollection(txn, created);
                broker.flush();
                transact.commit(txn);
            } catch(PermissionDeniedException e) {
                transact.abort(txn);
                response.sendError(HttpServletResponse.SC_FORBIDDEN,
                        e.getMessage());
            }
            LOG.debug("Created collection " + path);
            response.setStatus(HttpServletResponse.SC_CREATED);
        } catch(EXistException e) {
            throw new ServletException("Database error: " + e.getMessage(), e);
        } finally {
            pool.release(broker);
        }
    }

}
