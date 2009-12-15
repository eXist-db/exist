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
 * $Id$
 */
package org.exist.examples.xmlrpc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.net.URL;
import java.util.Vector;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;

/**
 * Store a document to the database using XML-RPC.
 *
 * Execute bin\run.bat org.exist.examples.xmlrpc.Store <localfilename> <remotedocname>
 */
public class Store {

	protected final static String uri = "http://localhost:8080/exist/xmlrpc";

	protected static void usage() {
		System.out.println("usage: org.exist.examples.xmlrpc.Store xmlFile [docName]");
		System.exit(0);
	}

	public static void main(String args[]) throws Exception {
		if(args.length < 1)
			usage();
		String docName = (args.length == 2) ? args[1] : args[0];

        XmlRpcClient client = new XmlRpcClient();
        XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
        config.setServerURL(new URL(uri));
        config.setBasicUserName("admin");
        config.setBasicPassword("");
        client.setConfig(config);

		// read the file into a string
		BufferedReader f = new BufferedReader(new FileReader(args[0]));
		String line;
		StringBuffer xml = new StringBuffer();
		while((line = f.readLine()) != null)
			xml.append(line);
		f.close();

		// set parameters for XML-RPC call
		Vector<Object> params = new Vector<Object>();
		params.addElement(xml.toString());
		params.addElement(docName);
		params.addElement(new Integer(0));

		// execute the call
		Boolean result = (Boolean)client.execute("parse", params);

		// check result
		if(result.booleanValue())
			System.out.println("document stored.");
		else
			System.out.println("could not store document.");
	}
}
