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

import javax.xml.transform.OutputKeys;

import junit.framework.TestCase;

import org.apache.xmlrpc.WebServer;
import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.exist.storage.serializers.EXistOutputKeys;

/**
 * JUnit test for XMLRPC interface methods.
 * 
 * This test assumes that the XMLRPC server is running at port 8081
 * of the local host. The server is normally started from Ant before calling
 * the test.
 * 
 * @author wolf
 */
public class XmlRpcTest extends TestCase {
    
    private final static String URI = "http://localhost:8081";
    
    private final static String XML_DATA =
    	"<test>" +
    	"<para>ääööüüÄÄÖÖÜÜßß</para>" +
		"<para>\uC5F4\uB2E8\uACC4</para>" +
    	"</test>";
    
    private final static String XSL_DATA =
    	"<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" " +
    	"version=\"1.0\">" +
		"<xsl:param name=\"testparam\"/>" +
		"<xsl:template match=\"test\"><test><xsl:apply-templates/></test></xsl:template>" +
		"<xsl:template match=\"para\">" +
		"<p><xsl:value-of select=\"$testparam\"/>: <xsl:apply-templates/></p></xsl:template>" +
		"</xsl:stylesheet>";
    
    private final static String TARGET_COLLECTION = "/db/xmlrpc/";
 
	public static void main(String[] args) {
		junit.textui.TestRunner.run(new XmlRpcTest("XmlRpcTest"));
	}

	private WebServer webServer = null;
	
	public XmlRpcTest(String name) {
		super(name);
	}
	
	public void testStore() throws Exception {
		System.out.println("Creating collection " + TARGET_COLLECTION);
		XmlRpcClient xmlrpc = getClient();
		Vector params = new Vector();
		params.addElement(TARGET_COLLECTION);
		Boolean result = (Boolean)xmlrpc.execute("createCollection", params);
		assertTrue(result.booleanValue());
		
		System.out.println("Storing document " + XML_DATA);
		params.clear();
		params.addElement(XML_DATA);
		params.addElement(TARGET_COLLECTION + "test.xml");
		params.addElement(new Integer(1));
		
		result = (Boolean)xmlrpc.execute("parse", params);
		assertTrue(result.booleanValue());
		
		params.setElementAt(XSL_DATA, 0);
		params.setElementAt(TARGET_COLLECTION + "test.xsl", 1);
		result = (Boolean)xmlrpc.execute("parse", params);
		assertTrue(result.booleanValue());
		
		System.out.println("Documents stored.");
	}
	
	public void testRetrieveDoc() throws Exception {
		System.out.println("Retrieving document " + TARGET_COLLECTION + "test.xml");
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
		byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
		System.out.println( new String(data, "UTF-8") );
		
		System.out.println("Retrieving document with stylesheet applied");
		options.put("stylesheet", "test.xsl");
		data = (byte[]) xmlrpc.execute( "getDocument", params );
		System.out.println( new String(data, "UTF-8") );
	}
	
	public void testCharEncoding() throws Exception {
		System.out.println("Testing charsets returned by query");
		Vector params = new Vector();
		String query = "distinct-values(//para)";
		params.addElement(query.getBytes("UTF-8"));
		params.addElement(new Hashtable());
		XmlRpcClient xmlrpc = getClient();
        Hashtable result = (Hashtable) xmlrpc.execute( "queryP", params );
        Vector resources = (Vector)result.get("results");
        assertEquals(resources.size(), 2);
        String value = (String)resources.elementAt(0);
        assertEquals(value, "ääööüüÄÄÖÖÜÜßß");
        System.out.println("Result1: " + value);
        value = (String)resources.elementAt(1);
        assertEquals(value, "\uC5F4\uB2E8\uACC4");
        System.out.println("Result2: " + value);
	}
	
	public void testQuery() throws Exception {
		Vector params = new Vector();
		String query = 
			"(::pragma exist:serialize indent=no::) //para";
		params.addElement(query.getBytes("UTF-8"));
		params.addElement(new Integer(10));
		params.addElement(new Integer(1));
		params.addElement(new Hashtable());
		XmlRpcClient xmlrpc = getClient();
        byte[] result = (byte[]) xmlrpc.execute( "query", params );
        assertNotNull(result);
        assertTrue(result.length > 0);
        System.out.println(new String(result, "UTF-8"));
	}
	
	public void testQueryWithStylesheet() throws Exception {
		Hashtable options = new Hashtable();
		options.put(EXistOutputKeys.STYLESHEET, "test.xsl");
		options.put(EXistOutputKeys.STYLESHEET_PARAM + ".testparam", "Test");
		options.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
		
		Vector params = new Vector();
		String query = "//para[1]";
		params.addElement(query.getBytes("UTF-8"));
		params.addElement(options);
		XmlRpcClient xmlrpc = getClient();
        Integer handle = (Integer) xmlrpc.execute( "executeQuery", params );
        assertNotNull(handle);
        
        params.clear();
        params.addElement(handle);
        params.addElement(new Integer(0));
        params.addElement(options);
        byte[] item = (byte[]) xmlrpc.execute( "retrieve", params );
        assertNotNull(item);
        assertTrue(item.length > 0);
        String out = new String(item, "UTF-8");
        System.out.println("Received: " + out);
        assertEquals("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
        		"<p>Test: ääööüüÄÄÖÖÜÜßß</p>", out);
	}
	
	public void testExecuteQuery() throws Exception {
		Vector params = new Vector();
		String query = "distinct-values(//para)";
		params.addElement(query.getBytes("UTF-8"));
		params.addElement(new Hashtable());
		XmlRpcClient xmlrpc = getClient();
		System.out.println("Executing query: " + query);
        Integer handle = (Integer) xmlrpc.execute( "executeQuery", params );
        assertNotNull(handle);
        
        params.clear();
        params.addElement(handle);
        Integer hits = (Integer) xmlrpc.execute( "getHits", params );
        assertNotNull(hits);
        System.out.println("Found: " + hits.intValue());
        assertEquals(hits.intValue(), 2);
        
        params.addElement(new Integer(1));
        params.addElement(new Hashtable());
        byte[] item = (byte[]) xmlrpc.execute( "retrieve", params );
        System.out.println(new String(item, "UTF-8"));
	}
	
	public void testCollectionWithAccents() throws Exception {
		System.out.println("Creating collection with accents in name ...");
		Vector params = new Vector();
		params.addElement("/db/Città");
		XmlRpcClient xmlrpc = getClient();
		xmlrpc.execute( "createCollection", params );
		
		System.out.println("Storing document " + XML_DATA);
		params.clear();
		params.addElement(XML_DATA);
		params.addElement("/db/Città/test.xml");
		params.addElement(new Integer(1));
		
		Boolean result = (Boolean)xmlrpc.execute("parse", params);
		assertTrue(result.booleanValue());
		
		params.clear();
		params.addElement("/db");

		Hashtable collection = (Hashtable) xmlrpc.execute("describeCollection", params);
		Vector collections = (Vector) collection.get("collections");
		String colWithAccent = null;
		for (int i = 0; i < collections.size(); i++) {
			String childName = (String) collections.elementAt(i);
			if(childName.equals("Città"))
				colWithAccent = childName;
			System.out.println("Child collection: " + childName);
		}
		assertNotNull("added collection not found", colWithAccent);
		
		System.out.println("Retrieving document /db/Città/test.xml");
		Hashtable options = new Hashtable();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        params.clear();
        params.addElement( "/db/" + colWithAccent + "/test.xml" ); 
        params.addElement( options );
        
        // execute the call
		byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
		System.out.println( new String(data, "UTF-8") );
	}
	
	protected XmlRpcClient getClient() throws MalformedURLException {
		XmlRpc.setEncoding("UTF-8");
		XmlRpcClient xmlrpc = new XmlRpcClient(URI);
		xmlrpc.setBasicAuthentication("admin", "");
		return xmlrpc;
	}
}
