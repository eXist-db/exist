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
 * $Id: XmlrpcDownload.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.net.URL;

import org.apache.log4j.Logger;
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
    
    private final static Logger LOG = Logger.getLogger(XmlrpcDownload.class);
    
    /**
     *  Write document referred by the URL to the output stream.
     * 
     * 
     * @param xmldbURL Document location in database.
     * @param os Stream to which the document is written.
     * @throws ExistIOException
     */
    public void stream(XmldbURL xmldbURL, OutputStream os) throws IOException {
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
            final Hashtable<String, String> options = new Hashtable<String, String>();
            options.put("indent", "no");
            options.put("encoding", "UTF-8");
            
            // Setup client parameters
            final Vector<Object> params = new Vector<Object>();
            params.addElement( xmldbURL.getCollectionPath() );
            params.addElement( options );
            
            // Shoot first method write data
            Hashtable ht = (Hashtable) client.execute("getDocumentData", params);
            int offset = ((Integer)ht.get("offset")).intValue();
            byte[]data= (byte[]) ht.get("data");
            final String handle = (String) ht.get("handle");
            os.write(data);
            
            // When there is more data to download
            while(offset!=0){
                // Clean and re-setup client parameters
                params.clear();
                params.addElement(handle);
                params.addElement(Integer.valueOf(offset));
                
                // Get and write next chunk
                ht = (Hashtable) client.execute("getNextChunk", params);
                data= (byte[]) ht.get("data");
                offset = ((Integer)ht.get("offset")).intValue();
                os.write(data);
            }
            
        } catch (final IOException ex) {
            LOG.error(ex);
            throw ex;
            
        } catch (final Exception ex) {
            LOG.error(ex);
            throw new IOException(ex.getMessage(), ex);
                       
        } finally {
            LOG.debug("Finished document download"); 

        }
    }
    
}
