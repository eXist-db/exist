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
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

/**
 * @author wolf
 */
public class Delete extends AbstractWebDAVMethod {
    
    public Delete(BrokerPool pool) {
        super(pool);
    }
    
        /* (non-Javadoc)
         * @see org.exist.http.webdav.WebDAVMethod#process(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
         */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, XmldbURI path) throws ServletException, IOException {
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(user);
            collection = broker.openCollection(path, Lock.WRITE_LOCK);
            if(collection == null) {
                XmldbURI collName = path.removeLastSegment();
                XmldbURI docName = path.lastSegment();
                LOG.debug("collection = " + collName + "; doc = " + docName);
                collection = broker.openCollection(collName, Lock.WRITE_LOCK);
                if(collection == null) {
                    transact.abort(txn);
                    LOG.debug("No resource or collection found for path: " + path);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
                    return;
                }
                resource = collection.getDocument(broker, docName);
                if(resource == null) {
                    LOG.debug("No resource found for path: " + path);
                    transact.abort(txn);
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, NOT_FOUND_ERR);
                    return;
                }
            }
            if(!collection.getPermissions().validate(user, Permission.READ)) {
                LOG.debug("Permission denied to read collection");
                transact.abort(txn);
                response.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
            //TODO : release collection lock here ?
            
            if(resource == null) {
                broker.removeCollection(txn, collection);
            } else {
                if(resource.getResourceType() == DocumentImpl.BINARY_FILE)
                    collection.removeBinaryResource(txn, broker, resource.getFileURI());
                else
                    collection.removeXMLResource(txn, broker, resource.getFileURI());
            }
            transact.commit(txn);
        } catch (EXistException e) {
            transact.abort(txn);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (PermissionDeniedException e) {
            transact.abort(txn);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        } catch (LockException e) {
            transact.abort(txn);
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        } catch (TriggerException e) {
            transact.abort(txn);
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        } finally {
            if(collection != null)
                collection.release(Lock.WRITE_LOCK);
            pool.release(broker);
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }
}
