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
 * Implements the WebDAV UNLOCK method.
 *
 * @author Dannes Wessels
 */
public class Unlock extends AbstractWebDAVMethod {
    
    
    /** Creates a new instance of Unlock */
    public Unlock(BrokerPool pool) {
        super(pool);
    }
    
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, XmldbURI path)
            throws ServletException, IOException {
        
        DBBroker broker = null;
        Collection collection = null;
        DocumentImpl resource = null;
        
        try {
            
            broker = pool.get(user);
            
            try {
	            try {
	                resource = broker.getXMLResource(path, org.exist.storage.lock.Lock.READ_LOCK);
	                
	            } catch (PermissionDeniedException ex) {
	                response.sendError(HttpServletResponse.SC_FORBIDDEN, ex.getMessage());
	                LOG.error(ex);
	                return;
	            }
	            
	            if(resource==null){
	                // No document found, maybe a collection
	                collection = broker.openCollection(path, org.exist.storage.lock.Lock.READ_LOCK);
	                if(collection!=null){
	                    LOG.info("Lock on collections not supported yet");
	                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED,
	                            "Lock on collections not supported yet");
	                    
	                    return;
	                } else  {
	                    LOG.info(NOT_FOUND_ERR + " " + path);
	                    response.sendError(HttpServletResponse.SC_NOT_FOUND,
	                            NOT_FOUND_ERR + " " + path);
	                    return;
	                }
	                //TODO : release collection lock here ?
	                //It is used below though...
	            }
	            
	            User lock = resource.getUserLock();
	            if(lock==null){
	                // No lock found, can not be unlocked
	                LOG.debug("No lock found");
	                
	                //clean up
	                resource.getMetadata().setLockToken(null);
	                response.sendError(HttpServletResponse.SC_NOT_FOUND);
	                return;
	                
	            } else {
	                // Resource is locked.
	                LOG.info("Unlocking resource.");
	                
	                boolean isNullResource=false;
	                if(resource.getMetadata().getLockToken().isNullResource() ){
	                    isNullResource=true;
	                }
	                
	                
	                // Make it persistant
	                TransactionManager transact = pool.getTransactionManager();
	                Txn transaction = transact.beginTransaction();
	                
	                // Remove if NullResource
	                if(isNullResource){
	                    LOG.debug("Unlock NullResource");
	                    try {
                                XmldbURI collUri = path.removeLastSegment();
                                collection = broker.openCollection(collUri, Lock.READ_LOCK);
                            
	                    	//TODO : if the collection lock has been released
	                    	//Reacquire one here
	                        if(resource.getResourceType() == DocumentImpl.BINARY_FILE)
	                            collection.removeBinaryResource(transaction, broker, resource.getFileURI());
	                        else
	                            collection.removeXMLResource(transaction, broker, resource.getFileURI());
	                    } catch (LockException ex) {
	                        LOG.error(ex);
	                    } catch (TriggerException ex) {
	                        LOG.error(ex);
	                    } catch (PermissionDeniedException ex) {
	                        LOG.error(ex);
	                    }
	                    
	                } else {
	                    LOG.debug("Unlock resource");
	                    resource.setUserLock(null);
	                    resource.getMetadata().setLockToken(null);
	                    broker.storeXMLResource(transaction, resource);
	                }
	                
	                //Moved to the finally clause
	                //resource.getUpdateLock().release(Lock.READ_LOCK);
	                
	                transact.commit(transaction);
	                
	                LOG.debug("Sucessfully unlocked '"+path+"'.");
	                
	                // Say OK
	                response.sendError(SC_UNLOCK_SUCCESSFULL, "Unlocked "+path);
	            }
            } finally {
            	if (resource != null)
            		resource.getUpdateLock().release(Lock.READ_LOCK);
            }
            
        } catch (EXistException e) {
            LOG.error(e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            throw new ServletException(e);
            
        } finally {
            
            if(collection != null){
                collection.release(Lock.READ_LOCK);
            }
            
            if(pool != null){
                pool.release(broker);
            }
        }
    }
}
