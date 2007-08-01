/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * $Id: EmbeddedUpload.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.io.ExistIOException;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.InputSource;

/**
 *   Read a document from a (input)stream and write it into database.
 *
 * @author Dannes Wessels
 */
public class EmbeddedUpload {
    
    private final static Logger LOG = Logger.getLogger(EmbeddedUpload.class);
    
    /**
     *   Read document from stream and write data to database.
     *
     * @param xmldbURL Location in database.
     * @param is Stream containing document.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, InputStream is) throws IOException {
        stream(xmldbURL, is, null);
    }
    
    /**
     *  Read document from stream and write data to database with specified user.
     *
     * @param user Effective user for operation. If NULL the user information
     * is distilled from the URL.
     * @param xmldbURL Location in database.
     * @param is Stream containing document.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, InputStream is, User user) throws IOException {
        File tmp =null;
        try{
            tmp = File.createTempFile("EMBEDDED", "tmp");
            FileOutputStream fos = new FileOutputStream(tmp);
            
            try{
                // Transfer bytes from in to out
                byte[] buf = new byte[1024];
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                }
            } finally {
                fos.close();
            }
            
            // Let database read file
            stream(xmldbURL, tmp, user);
            
        } catch(IOException ex){
            //ex.printStackTrace();
            LOG.error(ex);
            throw ex;
            
        } finally {
            if(tmp!=null){
                tmp.delete();
            }
        }
    }
    
    /**
     *  Read document and write data to database.
     *
     * @param xmldbURL Location in database.
     * @param tmp Document that is inserted.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, File tmp) throws IOException {
        stream(xmldbURL, tmp, null);
    }
    
    /**
     *  Read document and write data to database.
     *
     * @param user  Effective user for operation. If NULL the user information
     * is distilled from the URL.
     * @param xmldbURL Location in database.
     * @param tmp Document that is inserted.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, File tmp, User user) throws IOException {
        LOG.debug("Begin document upload");
        
        Collection collection = null;
        BrokerPool pool =null;
        DBBroker broker =null;
        TransactionManager transact = null;
        Txn txn = null;
        
        boolean collectionLocked = true;
        
        
        try {
            pool = BrokerPool.getInstance();
            
            if(user==null) {
                if(xmldbURL.hasUserInfo()){
                    user=EmbeddedUser.authenticate(xmldbURL, pool);
                    if(user==null){
                        LOG.debug("Unauthorized user "+xmldbURL.getUsername());
                        throw new ExistIOException("Unauthorized user "+xmldbURL.getUsername());
                    }
                } else {
                    user=EmbeddedUser.getUserGuest(pool);
                }
            }
            
            broker = pool.get(user);
            
            transact = pool.getTransactionManager();
            txn = transact.beginTransaction();
            
            XmldbURI collectionUri = XmldbURI.create(xmldbURL.getCollection());
            XmldbURI documentUri = XmldbURI.create(xmldbURL.getDocumentName());
            
            collection = broker.openCollection(collectionUri, Lock.READ_LOCK);
            
            if(collection == null) {
                transact.abort(txn);
                throw new ExistIOException("Resource "+collectionUri.toString()+" is not a collection.");
            }
            
            if(collection.hasChildCollection(documentUri)) {
                transact.abort(txn);
                throw new ExistIOException("Resource "+documentUri.toString()+" is a collection.");
            }
            
            MimeType mime = MimeTable.getInstance().getContentTypeFor(documentUri);
            String contentType=null;
            if (mime != null){
                contentType = mime.getName();
            } else {
                mime = MimeType.BINARY_TYPE;
            }
            
            if(mime.isXMLType()) {
                LOG.debug("storing XML resource");
                InputSource inputsource = new InputSource(tmp.toURI().toASCIIString());
                IndexInfo info = collection.validateXMLResource(txn, broker, documentUri, inputsource);
                DocumentImpl doc = info.getDocument();
                doc.getMetadata().setMimeType(contentType);
                collection.release(Lock.READ_LOCK);
                collectionLocked = false;
                collection.store(txn, broker, info, inputsource, false);
                LOG.debug("done");
                
            } else {
                LOG.debug("storing Binary resource");
                InputStream is = new FileInputStream(tmp);
                DocumentImpl doc = collection.addBinaryResource(txn, broker, documentUri, is, contentType, (int) tmp.length());
                is.close();
                LOG.debug("done");
            }
            
            LOG.debug("commit");
            transact.commit(txn);
            
        } catch (IOException ex) {
            try { 
                // Throws an exception when the user is unknown!
                transact.abort(txn);
            } catch (Exception abex) {
                LOG.debug(abex);
            }
            //ex.printStackTrace();
            LOG.debug(ex);
            throw ex;
            
        } catch (Exception ex) {
            try { 
                // Throws an exception when the user is unknown!
                transact.abort(txn);
            } catch (Exception abex) {
                LOG.debug(abex);
            }
            //ex.printStackTrace();
            LOG.debug(ex);
            throw new ExistIOException(ex.getMessage(), ex);
            
        } finally {
            LOG.debug("Done.");
            if(collectionLocked && collection != null){
                collection.release(Lock.READ_LOCK);
            }
            
            pool.release(broker);
        }
        
    }
    
}
