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
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.dom.LockToken;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.StringValue;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Implements the WebDAV PUT method.
 *
 * @author wolf
 * @author dizzz
 */
public class Put extends AbstractWebDAVMethod {
    
    public Put(BrokerPool pool) {
        super(pool);
    }
    
        /* (non-Javadoc)
         * @see org.exist.http.webdav.WebDAVMethod#process(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse, org.exist.collections.Collection, org.exist.dom.DocumentImpl)
         */
    public void process(User user, HttpServletRequest request,
            HttpServletResponse response, XmldbURI path) throws ServletException, IOException {
        LOG.debug("PUT start");
        File tempFile = saveRequestContent(request);
       
        
        String url = tempFile.toURI().toASCIIString();
        String contentType = request.getContentType();
        DBBroker broker = null;
        Collection collection = null;
        
        boolean collectionLocked = true;
        TransactionManager transact = pool.getTransactionManager();
        Txn txn = transact.beginTransaction();
        try {
            broker = pool.get(user);
            
        	XmldbURI pathUri = path.lastSegment();
        	XmldbURI collUri = path.removeLastSegment();
            
            LOG.debug("collUri='"+collUri+"';  path="+pathUri+"';" );
            
            collection = broker.openCollection(collUri, Lock.READ_LOCK);
            // TODO check why not use WRITE_LOCK here?
            if(collection == null) {
                transact.abort(txn);
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Parent collection " + collUri + " not found");
                return;
            }
            if(collection.hasChildCollection(pathUri)) {
                transact.abort(txn);
                response.sendError(HttpServletResponse.SC_CONFLICT,
                        "Cannot overwrite an existing collection with a resource");
                return;
            }
                        
            MimeType mime;
            if(contentType == null) {
                mime = MimeTable.getInstance().getContentTypeFor(pathUri);
                if (mime != null)
                    contentType = mime.getName();
            } else {
                int p = contentType.indexOf(';');
                if (p > -1)
                    contentType = StringValue.trimWhitespace(contentType.substring(0, p));
                mime = MimeTable.getInstance().getContentType(contentType);
            }
            
            if (mime == null){
                mime = MimeType.BINARY_TYPE;
            }
            

            LOG.debug("Storing document " + pathUri + "; content-type='" + contentType+"'");
            
            DocumentImpl doc = null;
            if(mime.isXMLType()) {
                LOG.debug("Storing XML resource");           
                
                // 0 byte XML files cannot exist, create place colder
                if(tempFile.length()==0L){
                    LOG.debug("Create '0 byte' place for XML resource");
                    String txt="<!-- place holder for null byte sized XML document --><null/>";
                    
                    InputSource is = new InputSource(url);
                    IndexInfo info = collection.validateXMLResource(txn, broker, pathUri, txt);
                    doc = info.getDocument();
                    doc.getMetadata().setMimeType(contentType);
                    collection.store(txn, broker, info, txt, false);
                    
                } else {
                    InputSource is = new InputSource(url);
                    IndexInfo info = collection.validateXMLResource(txn, broker, pathUri, is);
                    doc = info.getDocument();
                    doc.getMetadata().setMimeType(contentType);
                    collection.store(txn, broker, info, is, false);
                }
                LOG.debug("done");
                
            } else {
                LOG.debug("Storing binary resource"); 
                FileInputStream is = new FileInputStream(tempFile);
                doc = collection.addBinaryResource(txn, broker, pathUri, is, contentType, (int) tempFile.length());
                is.close();
                LOG.debug("done");
            }
            
            // Remove Null Resource flag
            LockToken token = doc.getMetadata().getLockToken();
            if(token!=null){
                token.setResourceType(LockToken.RESOURCE_TYPE_NOT_SPECIFIED);
            } else {
                LOG.debug("token==null");
            }
            
            transact.commit(txn);
            
            LOG.debug("PUT ready");
        } catch (EXistException e) {
            transact.abort(txn);
            LOG.error(e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    e.getMessage());
            return;
            
        } catch (PermissionDeniedException e) {
            transact.abort(txn);
            LOG.debug(e);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
            
        } catch (TriggerException e) {
            transact.abort(txn);
            LOG.debug(e);
            response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            return;
            
        } catch (SAXException e) {
            transact.abort(txn);
            LOG.debug(e);
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
            
        } catch (LockException e) {
            transact.abort(txn);
            LOG.debug(e);
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
            return;
            
        } finally {
            pool.release(broker);
            if(collectionLocked && collection != null)
                collection.release(Lock.READ_LOCK);
            tempFile.delete();
        }
        response.setStatus(HttpServletResponse.SC_CREATED);
    }
    
    
    private File saveRequestContent(HttpServletRequest request) throws IOException {
        ServletInputStream is = request.getInputStream();
        int len = request.getContentLength();
        // put may send a lot of data, so save it
        // to a temporary file first.
        File tempFile = File.createTempFile("existSRC", ".tmp");
        FileOutputStream fos = new FileOutputStream(tempFile);
        BufferedOutputStream os = new BufferedOutputStream(fos);
        
        if(len!=0){
            byte[] buffer = new byte[4096];
            int count, l = 0;
            do {
                count = is.read(buffer);
                if (count > 0)
                    os.write(buffer, 0, count);
                l += count;
            } while (l < len);
            os.close();
            is.close(); // DIZ: sure about this?
        }
        
        return tempFile;
    }
}
