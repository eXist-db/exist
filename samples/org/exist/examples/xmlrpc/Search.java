package org.exist.examples.xmlrpc;

import org.apache.xmlrpc.*;
import java.util.Vector;
import java.util.Hashtable;

/**
 * Execute a query using XMLRPC.
 */
public class Search {

	private final static Hashtable options = new Hashtable();
	static {
		options.put("encoding", "UTF-8");
		options.put("indent", "yes");
	}
	
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
		System.out.println("Executing " + params.elementAt(0));
        Vector result = (Vector) xmlrpc.execute("query", params);
        System.out.println("found: " + result.size());
		for(int i = 0; i < result.size(); i++) {
			System.out.println(result.elementAt(i));
			Vector current = (Vector)result.elementAt(i);
			params = new Vector();
			params.addElement(current.elementAt(0));
			params.addElement(current.elementAt(1));
			params.addElement(options);
			byte[] data = (byte[])xmlrpc.execute("retrieve", params);
			System.out.println(new String(data, "UTF-8"));
		}
    }
}
