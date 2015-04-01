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

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * Store a document to the database using XML-RPC.
 *
 * Execute bin\run.bat org.exist.examples.xmlrpc.Store <localfilename>
 * <remotedocname>
 */
public class Store {

    private final static String uri = "http://localhost:8080/exist/xmlrpc";

    protected static void usage() {
        System.out.println("usage: org.exist.examples.xmlrpc.Store xmlFile [docName]");
        System.exit(0);
    }

    public static void main(final String args[]) throws Exception {
        if (args.length < 1) {
            usage();
        }
        
        final String docName = (args.length == 2) ? args[1] : args[0];

        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(uri));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);

        // read the file into a string
        final StringBuilder xml = new StringBuilder();
        try (final BufferedReader f = new BufferedReader(new FileReader(args[0]))) {
            String line;
            while ((line = f.readLine()) != null) {
                xml.append(line);
            }
        }

        // set parameters for XML-RPC call
        final List<Object> params = new ArrayList<>();
        params.add(xml.toString());
        params.add(docName);
        params.add(0);

        // execute the call
        final Boolean result = (Boolean) client.execute("parse", params);

        // check result
        if (result) {
            System.out.println("document stored.");
        } else {
            System.out.println("could not store document.");
        }
    }
}
