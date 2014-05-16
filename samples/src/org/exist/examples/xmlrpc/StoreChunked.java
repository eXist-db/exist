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
 * $Id$
 */
package org.exist.examples.xmlrpc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.xmldb.XmldbURI;

/**
 *  Example code for demonstrating XMLRPC methods upload and parseLocal.
 * 
 * Execute: bin\run.bat org.exist.examples.xmlrpc.StoreChunked 
 *
 * @author dizzzz
 */
public class StoreChunked {
    
    
    public static void main(String args[])  {
        
        // Upload file to this uri:
        String xmldbUri = "xmldb:exist://guest:guest@localhost:8080/exist/xmlrpc/db/admin2.png";
        XmldbURI uri = XmldbURI.create(xmldbUri);
        
        // Construct url for xmlrpc, without collections / document
        // username/password yet hardcoded, need to update XmldbUri fir this
        String url = "http://guest:guest@" + uri.getAuthority() + uri.getContext();
        String path =uri.getCollectionPath();
        
        // TODO: Filename hardcoded
        String filename="webapp/resources/admin2.png";
        try {
            InputStream fis = new FileInputStream(filename);
            
            // Setup xmlrpc client
            XmlRpcClient client = new XmlRpcClient();
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(url));
            config.setBasicUserName("guest");
            config.setBasicPassword("guest");
            client.setConfig(config);

            // Initialize xmlrpc parameters
            Vector<Object> params = new Vector<Object>();
            String handle=null;
            
            // Copy data from inputstream to database
            byte[] buf = new byte[4096];
            int len;
            while ((len = fis.read(buf)) > 0) {
                params.clear();
                if(handle!=null){
                    params.addElement(handle);
                }
                params.addElement(buf);
                params.addElement(new Integer(len));
                handle = (String)client.execute("upload", params);
            }
            fis.close();
            
            // All data transported, parse data on server
            params.clear();
            params.addElement(handle);
            params.addElement(path);
            params.addElement(new Boolean(true));
            params.addElement("image/png");
            Boolean result =(Boolean)client.execute("parseLocal", params); // exceptions
            
            // Check result
            if(result.booleanValue())
                System.out.println("document stored.");
            else
                System.out.println("could not store document.");
            
        } catch (FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        } catch (XmlRpcException ex) {
            ex.printStackTrace();
        }
    }
}
