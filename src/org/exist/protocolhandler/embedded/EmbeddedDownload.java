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
 * $Id: EmbeddedDownload.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.embedded;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.io.ExistIOException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xmldb.XmldbURI;

/**
 *   Read document from an embedded database and write the data into an
 * output stream.
 *
 * @author Dannes Wessels
 */
public class EmbeddedDownload {
    
    private final static Logger LOG = Logger.getLogger(EmbeddedDownload.class);
    
    /**
     *   Write document referred by URL to an (output)stream.
     *
     * @param xmldbURL Document location in database.
     * @param os Stream to which the document is written.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, OutputStream os) throws IOException {
        stream(xmldbURL, os, null);
    }
    
    /**
     *   Write document referred by URL to an (output)stream as specified user.
     *
     * @param user Effective user for operation. If NULL the user information
     * is distilled from the URL.
     * @param xmldbURL Document location in database.
     * @param os Stream to which the document is written.
     * @throws IOException
     */
    public void stream(XmldbURL xmldbURL, OutputStream os, User user) throws IOException {
        LOG.debug("Begin document download");
        
        DocumentImpl resource = null;
        Collection collection = null;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
            XmldbURI path = XmldbURI.create(xmldbURL.getPath());
            pool = BrokerPool.getInstance();
            
            if(user==null){
                if(xmldbURL.hasUserInfo()){
                    user=EmbeddedUser.authenticate(xmldbURL, pool);
                    if(user==null){
                        throw new ExistIOException("Unauthorized user "+xmldbURL.getUsername());
                    }
                } else {
                    user=EmbeddedUser.getUserGuest(pool);
                }
            }
            broker = pool.get(user);
            
            resource = broker.getXMLResource(path, Lock.READ_LOCK);
            
            if(resource == null) {
                // Test for collection
                collection = broker.openCollection(path, Lock.READ_LOCK);
                if(collection == null){
                    // No collection, no document
                    throw new ExistIOException("Resource "+xmldbURL.getPath()+" not found.");
                    
                } else {
                    // Collection
                    throw new ExistIOException("Resource "+xmldbURL.getPath()+" is a collection.");
                }
                
            } else {
                if(resource.getResourceType() == DocumentImpl.XML_FILE) {
                    Serializer serializer = broker.getSerializer();
                    serializer.reset();
                    
                    // Preserve doctype
                    serializer.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "yes");
                    Writer w = new OutputStreamWriter(os,"UTF-8");
                    serializer.serialize(resource,w);
                    w.close();
                    
                } else {
                    broker.readBinaryResource((BinaryDocument) resource, os);
                }
            }
        } catch (IOException ex) {
            //ex.printStackTrace();
            LOG.error(ex);
            throw ex;
            
        } catch (Exception ex) {
            //ex.printStackTrace();
            LOG.error(ex);
            throw new ExistIOException(ex.getMessage(), ex);
            
        } finally {
            if(resource != null){
                resource.getUpdateLock().release(Lock.READ_LOCK);
            }
            
            if(collection != null){
                collection.release(Lock.READ_LOCK);
            }
            
            pool.release(broker);
            
            LOG.debug("End document download");
        }
    }
}
