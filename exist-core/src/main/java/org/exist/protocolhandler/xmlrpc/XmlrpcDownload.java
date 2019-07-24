/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

import org.exist.protocolhandler.xmldb.XmldbURL;


/**
 *  Read document from using XMLRPC from remote database and write the data 
 * into an output stream.
 *
 * @author Dannes Wessels
 */
public class XmlrpcDownload {
    
    private final static Logger LOG = LogManager.getLogger(XmlrpcDownload.class);
    
    /**
     * Write document referred by the URL to the output stream.
     *
     * @param xmldbURL Document location in database.
     * @param os Stream to which the document is written.
     * @throws IOException An IO error occurred.
     */
    public void stream(final XmldbURL xmldbURL, final OutputStream os) throws IOException {
        LOG.debug("Begin document download");
        try {
            final XmlRpcClient client = new XmlRpcClient();
            final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEncoding("UTF-8");
            config.setEnabledForExtensions(true);
            config.setServerURL(new URL(xmldbURL.getXmlRpcURL()));

            // Setup client client
            if(xmldbURL.hasUserInfo()) {
                config.setBasicUserName(xmldbURL.getUsername());
                config.setBasicPassword(xmldbURL.getPassword());
            }
            client.setConfig(config);

            // Setup xml serializer
            final Map<String, String> options = new HashMap<>();
            options.put("indent", "no");
            options.put("encoding", "UTF-8");
            
            // Setup client parameters
            final List<Object> params = new ArrayList<>();
            params.add( xmldbURL.getCollectionPath() );
            params.add( options );
            
            // Shoot first method write data
            Map ht = (Map) client.execute("getDocumentData", params);
            int offset = (int)ht.get("offset");
            byte[]data= (byte[]) ht.get("data");
            final String handle = (String) ht.get("handle");
            os.write(data);
            
            // When there is more data to download
            while(offset!=0){
                // Clean and re-setup client parameters
                params.clear();
                params.add(handle);
                params.add(offset);
                
                // Get and write next chunk
                ht = (Map) client.execute("getNextChunk", params);
                data= (byte[]) ht.get("data");
                offset = (int)ht.get("offset");
                os.write(data);
            }
            
        } catch (final XmlRpcException ex) {
            LOG.error(ex);
            throw new IOException(ex.getMessage(), ex);
        } catch(final IOException ex) {
            LOG.error(ex);
            throw ex;     
        } finally {
            LOG.debug("Finished document download"); 
        }
    }
    
}
