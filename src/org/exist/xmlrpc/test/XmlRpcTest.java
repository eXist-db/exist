/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmlrpc.test;

import java.net.MalformedURLException;
import java.util.Hashtable;
import java.util.Vector;

import junit.framework.TestCase;

import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xmlrpc.AuthenticatedHandler;

/**
 * JUnit test for XMLRPC interface methods.
 * 
 * @author wolf
 */
public class XmlRpcTest extends TestCase {

    private final static int PORT = 8081;
    
    private final static String URI = "http://localhost:8081";
    
    private final static String XML_DATA =
    	"<test><para>ääööüüÄÄÖÖÜÜ</para></test>";
    
    private final static String TARGET_COLLECTION = "/db/test/";
    
    private WebServer webServer = null;
 
	public static void main(String[] args) {
		junit.textui.TestRunner.run(XmlRpcTest.class);
	}

	public void testStore() throws Exception {
		System.out.println("Storing document " + XML_DATA);
		// set parameters for XML-RPC call
		Vector params = new Vector();
		params.addElement(XML_DATA);
		params.addElement(TARGET_COLLECTION + "test.xml");
		params.addElement(new Integer(1));
		
		// execute the call
		XmlRpcClient xmlrpc = getClient();
		Boolean result = (Boolean)xmlrpc.execute("parse", params);
		assertTrue(result.booleanValue());
		System.out.println("Document stored.");
	}
	
	public void testRetrieveDoc() throws Exception {
		Hashtable options = new Hashtable();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        Vector params = new Vector();
        params.addElement( TARGET_COLLECTION + "test.xml" ); 
        params.addElement( options );
        
        // execute the call
		XmlRpcClient xmlrpc = getClient();
		byte[] data = (byte[])
        xmlrpc.execute( "getDocument", params );
		System.out.println( new String(data, "UTF-8") );
	}
	
	/*
	 * @see TestCase#setUp()
	 */
	/* (non-Javadoc)
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		if(webServer == null)
			startServer();
	}
	
	protected void startServer() throws Exception {
		System.out.println("Starting database ...");
		String pathSep = System.getProperty( "file.separator", "/" );
        String home = System.getProperty( "exist.home" );
        if ( home == null )
            home = System.getProperty( "user.dir" );
        System.out.println( "loading configuration from " + home +
            pathSep + "conf.xml" );
        Configuration config = new Configuration( "conf.xml", home );
        BrokerPool.configure( 1, 5, config );
        
        System.out.println( "starting XMLRPC listener at port " + PORT );
        XmlRpc.setEncoding( "UTF-8" );
        webServer = new WebServer( PORT );
        AuthenticatedHandler handler = new AuthenticatedHandler( config );
        webServer.addHandler( "$default", handler );
        webServer.start();
        System.err.println( "waiting for connections ..." );
	}
	
	protected XmlRpcClient getClient() throws MalformedURLException {
		XmlRpc.setEncoding("UTF-8");
		XmlRpcClient xmlrpc = new XmlRpcClient(URI);
		xmlrpc.setBasicAuthentication("admin", "");
		return xmlrpc;
	}
}
