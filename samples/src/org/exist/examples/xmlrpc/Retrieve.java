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

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.exist.util.SSLHelper;

/**
 * Retrieve a document from the database using XMLRPC.
 *
 * Execute bin\run.bat org.exist.examples.xmlrpc.Retrieve <remotedoc>
 *
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 * created August 1, 2002
 */
public class Retrieve {

    private final static String uri = "http://localhost:8080/exist/xmlrpc";

    private static void usage() {
        System.out.println("usage: org.exist.examples.xmlrpc.Retrieve path-to-document");
        System.exit(0);
    }

    public static void main(final String args[]) throws Exception {
        if (args.length < 1) {
            usage();
        }

        // Initialize HTTPS connection to accept selfsigned certificates
        // and the Hostname is not validated 
        SSLHelper.initialize();

        final XmlRpcClient client = new XmlRpcClient();
        final XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(uri));
        config.setBasicUserName("guest");
        config.setBasicPassword("guest");
        client.setConfig(config);

        final Map<String, String> options = new HashMap<>();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");

        final List<Object> params = new ArrayList<>();
        params.add(args[0]);
        params.add(options);
        final String xml = (String) client.execute("getDocumentAsString", params);
        System.out.println(xml);
    }
}
