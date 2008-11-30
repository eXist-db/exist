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
 * $Id: XmlrpcUpload.java 223 2007-04-21 22:13:05Z dizzzz $
 */

package org.exist.protocolhandler.xmlrpc;

import java.io.IOException;
import java.io.InputStream;
import java.util.Vector;
import java.util.ArrayList;
import java.util.List;
import java.net.URL;

import org.apache.log4j.Logger;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.protocolhandler.xmldb.XmldbURL;
import org.exist.storage.io.ExistIOException;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;

/**
 * Write document using XMLRPC to remote database and read the data 
 * from an input stream.
 * 
 * Sends a document to an eXist-db server using XMLRPC. The document can be
 * either XML or non-XML (binary). Chunked means that the document is send 
 * as smaller parts to the server, the servler glues the parts together. There
 * is no limitation on the size of the documents that can be transported.
 *
 * @author Dannes Wessels
 */
public class XmlrpcUpload {
    
    private final static Logger LOG = Logger.getLogger(XmlrpcUpload.class);
    
    /**
     * Write data from a (input)stream to the specified XMLRPC url and leave
     * the input stream open.
     * 
     * @param xmldbURL URL pointing to location on eXist-db server.
     * @param is Document stream
     * @throws Exception When something is wrong.
     */
    public void stream(XmldbURL xmldbURL, InputStream is)
    throws IOException {
        LOG.debug("Begin document upload");
        try {
            // Setup xmlrpc client
            XmlRpcClient client = new XmlRpcClient();
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEncoding("UTF-8");
            config.setEnabledForExtensions(true);
            config.setServerURL(new URL(xmldbURL.getXmlRpcURL()));

            if(xmldbURL.hasUserInfo()) {
                config.setBasicUserName(xmldbURL.getUsername());
                config.setBasicPassword(xmldbURL.getPassword());
            }
            client.setConfig(config);

            String contentType=MimeType.BINARY_TYPE.getName();
            MimeType mime
                    = MimeTable.getInstance().getContentTypeFor(xmldbURL.getDocumentName());
            if (mime != null){
                contentType = mime.getName();
            }
            
            // Initialize xmlrpc parameters
            List params = new ArrayList(5);
            String handle=null;
            
            // Copy data from inputstream to database
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) {
                params.clear();
                if(handle!=null){
                    params.add(handle);
                }
                params.add(buf);
                params.add(new Integer(len));
                handle = (String)client.execute("upload", params);
            }
            
            // All data transported, now parse data on server
            params.clear();
            params.add(handle);
            params.add(xmldbURL.getCollectionPath() );
            params.add(Boolean.valueOf(true));
            params.add(contentType);
            Boolean result =(Boolean)client.execute("parseLocal", params);
            
            // Check XMLRPC result
            if(result.booleanValue()){
                LOG.debug("Document stored.");
            } else {
                LOG.debug("Could not store document.");
                throw new ExistIOException("Could not store document.");
            }
            
        } catch (IOException ex) {
            LOG.debug(ex);
            throw ex;
            
        } catch (Exception ex) {
            LOG.debug(ex);
            throw new ExistIOException(ex.getMessage(), ex);
            
        } finally {
           LOG.debug("Finished document upload");
        }
    }

}
