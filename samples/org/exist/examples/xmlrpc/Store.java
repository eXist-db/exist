package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;
import java.io.*;

/**
 * Store a document to the database using
 * XML-RPC.
 */
public class Store {

	protected final static String uri = "http://localhost:8080/exist/xmlrpc";

	protected static void usage() {
		System.out.println("usage: org.exist.examples.xmlrpc.Store xmlFile [ docName ]");
		System.exit(0);
	}

	public static void main(String args[]) throws Exception {
		if(args.length < 1)
			usage();
		String docName = (args.length == 2) ? args[1] : args[0];

		XmlRpc.setEncoding("UTF-8");
		XmlRpcClient xmlrpc = new XmlRpcClient(uri);
		xmlrpc.setBasicAuthentication("admin", "");
		
		// read the file into a string
		BufferedReader f = new BufferedReader(new FileReader(args[0]));
		String line;
		StringBuffer xml = new StringBuffer();
		while((line = f.readLine()) != null)
			xml.append(line);
		f.close();

		// set parameters for XML-RPC call
		Vector params = new Vector();
		params.addElement(xml.toString());
		params.addElement(args[1]);
		params.addElement(new Integer(0));

		// execute the call
		Boolean result = (Boolean)xmlrpc.execute("parse", params);

		// check result
		if(result.booleanValue())
			System.out.println("document stored.");
		else
			System.out.println("could not store document.");
	}
}
