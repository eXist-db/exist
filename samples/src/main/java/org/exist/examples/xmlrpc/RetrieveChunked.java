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
package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.xmldb.XmldbURI;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example code for demonstrating XMLRPC methods getDocumentData and
 * getNextChunk. Please run 'admin-examples setup' first, this will download the
 * required macbeth.xml document.
 *
 * @author Dannes Wessels
 */
public class RetrieveChunked {

    /**
     * @param args ignored command line arguments
     */
    public static void main(final String[] args) {

        // Download file using xmldb url
        final String xmldbUri = "xmldb:exist://localhost:8080/exist/xmlrpc/db/shakespeare/plays/macbeth.xml";
        final XmldbURI uri = XmldbURI.create(xmldbUri);

        // Construct url for xmlrpc, without collections / document
        final String url = "http://" + uri.getAuthority() + uri.getContext();
        final String path = uri.getCollectionPath();

        // TODO file is hardcoded
        final String filename = "macbeth.xml";

        try {
            // Setup xmlrpc client
            final XmlRpcClient client = new XmlRpcClient();
            final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setServerURL(new URL(url));
            config.setBasicUserName("guest");
            config.setBasicPassword("guest");
            client.setConfig(config);

            // Setup xml serializer
            final Map<String, String> options = new HashMap<>();
            options.put("indent", "no");
            options.put("encoding", "UTF-8");

            // Setup xmlrpc parameters
            final List<Object> params = new ArrayList<>();
            params.add(path);
            params.add(options);

            // Setup output stream
            try(final FileOutputStream fos = new FileOutputStream(filename)) {

                // Shoot first method write data
                Map ht = (Map) client.execute("getDocumentData", params);
                int offset = (int) ht.get("offset");
                byte[] data = (byte[]) ht.get("data");
                final String handle = (String) ht.get("handle");
                fos.write(data);

                // When there is more data to download
                while (offset != 0) {
                    // Clean and re-setup xmlrpc parameters
                    params.clear();
                    params.add(handle);
                    params.add(offset);

                    // Get and write next chunk
                    ht = (Map) client.execute("getNextChunk", params);
                    data = (byte[]) ht.get("data");
                    offset = (int) ht.get("offset");
                    fos.write(data);
                }
            }

        } catch (final XmlRpcException | IOException ex) {
            ex.printStackTrace();
        }
    }
}
