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

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Hashtable;
import java.util.Vector;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.apache.xmlrpc.XmlRpcException;

/**
 *  Example code for demonstrating XMLRPC methods getDocumentData
 * and getNextChunk. Please run 'admin-examples setup' first, this will
 * download the required mondial.xml document.
 *
 * @author dizzzz
 */
public class RetrieveChuncked {
    
    /**
     * @param args ignored command line arguments
     */
    public static void main(String[] args) {
        String uri = "http://guest:guest@localhost:8080/exist/xmlrpc";
        String path ="/db/mondial/mondial.xml";
        String filename="mondial.xml";
        
        try {
            // Setup xmlrpc client
            XmlRpc.setEncoding("UTF-8");
            XmlRpcClient xmlrpc = new XmlRpcClient(uri);
            
            // Setup xml serializer
            Hashtable options = new Hashtable();
            options.put("indent", "no");
            options.put("encoding", "UTF-8");
            
            // Setup xmlrpc parameters
            Vector params = new Vector();
            params.addElement( path );
            params.addElement( options );
            
            // Setup output stream
            FileOutputStream fos = new FileOutputStream(filename);
            
            // Shoot first method write data
            Hashtable ht = (Hashtable) xmlrpc.execute("getDocumentData", params);
            int offset = ((Integer)ht.get("offset")).intValue();
            byte[]data= (byte[]) ht.get("data");
            String handle = (String) ht.get("handle");
            fos.write(data);
            
            // When there is more data to download
            while(offset!=0){
                // Clean and re-setup xmlrpc parameters
                params.clear();
                params.addElement(handle);
                params.addElement(new Integer(offset));
                
                // Get and write next chunk
                ht = (Hashtable) xmlrpc.execute("getNextChunk", params);
                data= (byte[]) ht.get("data");
                offset = ((Integer)ht.get("offset")).intValue();
                fos.write(data);
            }
            
            // Finish transport
            fos.close();
            
        } catch (MalformedURLException ex) {
            ex.printStackTrace();
        } catch (XmlRpcException ex) {
            ex.printStackTrace();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
    
}
