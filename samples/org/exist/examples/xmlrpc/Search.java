package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;
import java.util.Hashtable;

/**
 * Execute a query using XMLRPC.
 */
public class Search {

    public static void main(String args[]) throws Exception {
    	
    	String query = "document()//character[.//reading &= 'チョウ']";
    	if(args.length > 0)
    		query = args[0];
    		
		XmlRpc.setEncoding("UTF8");
		XmlRpcClient xmlrpc = new XmlRpcClient("http://localhost:8080/exist/xmlrpc");
        xmlrpc.setBasicAuthentication( "guest", "guest" );
        
        // execute query and retrieve an id for the generated result set
        Vector params = new Vector();
        params.addElement(query);
		params.addElement(new Integer(10));
		params.addElement(new Integer(1));
		params.addElement(new Integer(1));
		params.addElement(new Hashtable());
		System.out.println("Executing query " + params.elementAt(0));
        String result = (String) xmlrpc.execute("query", params);
		System.out.println(result);
    }
}
