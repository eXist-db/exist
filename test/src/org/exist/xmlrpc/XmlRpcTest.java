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
package org.exist.xmlrpc;

import java.net.BindException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

import javax.xml.transform.OutputKeys;

import junit.textui.TestRunner;

import org.apache.xmlrpc.XmlRpc;
import org.apache.xmlrpc.XmlRpcClient;
import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.TestConstants;
import org.exist.xmldb.XmldbURI;
import org.mortbay.util.MultiException;

/**
 * JUnit test for XMLRPC interface methods.
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class XmlRpcTest extends XMLTestCase {    
	
	private static StandaloneServer server = null;
    private final static String URI = "http://localhost:8088/xmlrpc";
    
    private final static String XML_DATA =
    	"<test>" +
    	"<para>\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</para>" +
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
    
    private final static XmldbURI TARGET_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xmlrpc");
    
    private final static XmldbURI TARGET_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_XML_URI);
    
    private final static XmldbURI SPECIAL_COLLECTION = TARGET_COLLECTION.append(TestConstants.SPECIAL_NAME);
    
    private final static XmldbURI SPECIAL_RESOURCE = SPECIAL_COLLECTION.append(TestConstants.SPECIAL_XML_URI);
    
	public XmlRpcTest(String name) {
		super(name);
	}
	
	protected void setUp() {
		//Don't worry about closing the server : the shutdown hook will do the job
		initServer();		
	}
	
	private void initServer() {
		try {
			if (server == null) {
				server = new StandaloneServer();
				if (!server.isStarted()) {			
					try {				
						System.out.println("Starting standalone server...");
						String[] args = {};
						server.run(args);
						while (!server.isStarted()) {
							Thread.sleep(1000);
						}
					} catch (MultiException e) {
						boolean rethrow = true;
						Iterator i = e.getExceptions().iterator();
						while (i.hasNext()) {
							Exception e0 = (Exception)i.next();
							if (e0 instanceof BindException) {
								System.out.println("A server is running already !");
								rethrow = false;
								break;
							}
						}
						if (rethrow) throw e;
					}
				}
			}
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }
	}	
	
	public void testStore() {
		try {
			System.out.println("Creating collection " + TARGET_COLLECTION);
			XmlRpcClient xmlrpc = getClient();
			Vector params = new Vector();
			params.addElement(TARGET_COLLECTION.toString());
			Boolean result = (Boolean)xmlrpc.execute("createCollection", params);
			assertTrue(result.booleanValue());
			
			System.out.println("Storing document " + XML_DATA);
			params.clear();
			params.addElement(XML_DATA);
			params.addElement(TARGET_RESOURCE.toString());
			params.addElement(new Integer(1));
			
			result = (Boolean)xmlrpc.execute("parse", params);
			assertTrue(result.booleanValue());
			
			params.setElementAt(XSL_DATA, 0);
			params.setElementAt(TARGET_COLLECTION.append("test.xsl").toString(), 1);
			result = (Boolean)xmlrpc.execute("parse", params);
			assertTrue(result.booleanValue());
			
			System.out.println("Documents stored.");
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }			
	}
	
	public void testRetrieveDoc() {
		System.out.println("Retrieving document " + TARGET_RESOURCE);
		Hashtable options = new Hashtable();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        Vector params = new Vector();
        params.addElement( TARGET_RESOURCE.toString() ); 
        params.addElement( options );
        
        try {
	        // execute the call
			XmlRpcClient xmlrpc = getClient();
			byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
			System.out.println( new String(data, "UTF-8") );
			
			System.out.println("Retrieving document with stylesheet applied");
			options.put("stylesheet", "test.xsl");
			data = (byte[]) xmlrpc.execute( "getDocument", params );
			System.out.println( new String(data, "UTF-8") );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }			
	}
	
	public void testCharEncoding() {
		try {
			System.out.println("Testing charsets returned by query");
			Vector params = new Vector();
			String query = "distinct-values(//para)";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(new Hashtable());
			XmlRpcClient xmlrpc = getClient();
	        Hashtable result = (Hashtable) xmlrpc.execute( "queryP", params );
	        Vector resources = (Vector)result.get("results");
	        //TODO : check the number of resources before !
	        assertEquals(resources.size(), 2);
	        String value = (String)resources.elementAt(0);
	        assertEquals(value, "\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF");
	        System.out.println("Result1: " + value);
	        value = (String)resources.elementAt(1);
	        assertEquals(value, "\uC5F4\uB2E8\uACC4");
	        System.out.println("Result2: " + value);
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }	        
	}
	
	public void testQuery() {
		try {
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
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }	        
	}	
	
	public void testQueryWithStylesheet() {
		try {
			Hashtable options = new Hashtable();
			options.put(EXistOutputKeys.STYLESHEET, "test.xsl");
			options.put(EXistOutputKeys.STYLESHEET_PARAM + ".testparam", "Test");
			options.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
			//TODO : check the number of resources before !
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
	        assertXMLEqual("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
	        		"<p>Test: \u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</p>", out);
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }	        
	}
	
	public void testExecuteQuery() {
		try {
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
        
	        params.addElement(new Integer(0));
	        params.addElement(new Hashtable());
	        byte[] item = (byte[]) xmlrpc.execute( "retrieve", params );
	        System.out.println(new String(item, "UTF-8"));
	        
	        params.clear();
	        params.addElement(handle);
	        params.addElement(new Integer(1));
	        params.addElement(new Hashtable());
	        item = (byte[]) xmlrpc.execute( "retrieve", params );
	        System.out.println(new String(item, "UTF-8"));
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }	        
	}
	
	public void testCollectionWithAccentsAndSpaces() {
		try {
			System.out.println("Creating collection with accents and spaces in name ...");
			Vector params = new Vector();
			params.addElement(SPECIAL_COLLECTION.toString());
			XmlRpcClient xmlrpc = getClient();
			xmlrpc.execute( "createCollection", params );
			
			System.out.println("Storing document " + XML_DATA);
			params.clear();
			params.addElement(XML_DATA);
			params.addElement(SPECIAL_RESOURCE.toString());
			params.addElement(new Integer(1));
			
			Boolean result = (Boolean)xmlrpc.execute("parse", params);
			assertTrue(result.booleanValue());
			
			params.clear();
			params.addElement(SPECIAL_COLLECTION.removeLastSegment().toString());
	
			Hashtable collection = (Hashtable) xmlrpc.execute("describeCollection", params);
			Vector collections = (Vector) collection.get("collections");
			boolean foundMatch=false;
			String targetCollectionName = SPECIAL_COLLECTION.lastSegment().toString();
			for (int i = 0; i < collections.size(); i++) {
				String childName = (String) collections.elementAt(i);
				System.out.println("Child collection: " + childName);
				if(childName.equals(targetCollectionName)) {
					foundMatch=true;
					break;
				}
			}
			assertTrue("added collection not found", foundMatch);
			
			System.out.println("Retrieving document '" + SPECIAL_RESOURCE.toString() + "'");
			Hashtable options = new Hashtable();
	        options.put("indent", "yes");
	        options.put("encoding", "UTF-8");
	        options.put("expand-xincludes", "yes");
	        options.put("process-xsl-pi", "no");
	        
	        params.clear();
	        params.addElement( SPECIAL_RESOURCE.toString() ); 
	        params.addElement( options );
	        
	        // execute the call
			byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
			System.out.println( new String(data, "UTF-8") );
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }			
	}
	
	protected XmlRpcClient getClient() {
		try {
			XmlRpc.setEncoding("UTF-8");
			XmlRpcClient xmlrpc = new XmlRpcClient(URI);
			xmlrpc.setBasicAuthentication("admin", "");
			return xmlrpc;
	    } catch (Exception e) {            
	        fail(e.getMessage());  
	    }
	    return null;
	}

	public static void main(String[] args) {
		TestRunner.run(XmlRpcTest.class);
		//Explicit shutdown for the shutdown hook
		System.exit(0);		
	}	
}
