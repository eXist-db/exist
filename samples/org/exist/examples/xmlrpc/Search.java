package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;

/**
 * Execute a query using XMLRPC.
 */
public class Search {

    private static String encoding = "ISO-8859-1";

    public static void main(String args[]) throws Exception {
    	
        XmlRpcClient xmlrpc = new XmlRpcClient("http://localhost:8080/exist/xmlrpc");
        xmlrpc.setBasicAuthentication( "guest", "guest" );
        
        // execute query and retrieve an id for the generated result set
        Vector params = new Vector();
        params.addElement("document(*)//SPEECH[LINE &= 'king']");
		params.addElement("UTF-8");
		params.addElement(new Integer(10));
		params.addElement(new Integer(1));
		params.addElement(new Integer(1));
		params.addElement("/SPEAKER[1]");
        byte[] result = (byte[]) xmlrpc.execute("query", params);
		System.out.println(new String(result, "UTF-8"));
    }
}

