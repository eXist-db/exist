/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.xmlrpc;

import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.custommonkey.xmlunit.XMLAssert;
import org.exist.jetty.JettyStart;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.security.MessageDigester;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.test.TestConstants;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.xml.transform.OutputKeys;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import org.exist.security.Permission;

/**
 * JUnit test for XMLRPC interface methods.
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author ljo
 */
public class XmlRpcTest {    
	
    private static JettyStart server = null;
    // jetty.port.standalone
    private final static String URI = "http://localhost:" + System.getProperty("jetty.port", "8088") + "/xmlrpc";
        
    private final static XmldbURI TARGET_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xmlrpc");
    
    private final static XmldbURI TARGET_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_XML_URI);
    
    public final static XmldbURI MODULE_RESOURCE = TARGET_COLLECTION.append(TestConstants.TEST_MODULE_URI);
    
    private final static XmldbURI SPECIAL_COLLECTION = TARGET_COLLECTION.append(TestConstants.SPECIAL_NAME);
    
    private final static XmldbURI SPECIAL_RESOURCE = SPECIAL_COLLECTION.append(TestConstants.SPECIAL_XML_URI);
    
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
    
    public final static String MODULE_DATA =
    	"module namespace tm = \"http://exist-db.org/test/module\"; " +
    	"declare variable $tm:imported-external-string as xs:string external;";
    
    public final static String QUERY_MODULE_DATA =
    	"xquery version \"1.0\";" +
    	"declare namespace tm-query = \"http://exist-db.org/test/module/query\";" +
    	"import module namespace tm = \"http://exist-db.org/test/module\" " +
        "at \"xmldb:exist://" + MODULE_RESOURCE.toString() + "\"; " +
        "declare variable $tm-query:local-external-string as xs:string external;" +
        "($tm:imported-external-string, $tm-query:local-external-string)";
    
    
	public XmlRpcTest() {
	}
	
    @BeforeClass
    public static void setUp() {
        System.out.println("==================================");
		//Don't worry about closing the server : the shutdownDB hook will do the job
		initServer();		
	}

    @AfterClass
    public static void stopServer() {
		server.shutdown();
        server = null;
        System.gc();
        try {
            Thread.sleep(500);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
        }
    }

    protected void tearDown() {
        XmlRpcClient xmlrpc = getClient();
        try {
            List<String> params = new ArrayList<String>(1);
            params.add(TARGET_COLLECTION.toString());
            @SuppressWarnings("unused")
			Boolean b = (Boolean) xmlrpc.execute("removeCollection", params);
        } catch (XmlRpcException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

	private static void initServer() {
		try {
			if (server == null) {
				server = new JettyStart();
                System.out.println("Starting standalone server...");
                server.run();
            }
	    } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());  
	    }
    }

	@Test
	public void testStoreAndRetrieve() {
        System.out.println("---testStoreAndRetrieve");
		try {
			System.out.println("Creating collection " + TARGET_COLLECTION);
			XmlRpcClient xmlrpc = getClient();
			Vector<Object> params = new Vector<Object>();
			params.addElement(TARGET_COLLECTION.toString());
			Boolean result = (Boolean)xmlrpc.execute("createCollection", params);
			Assert.assertTrue(result.booleanValue());

			System.out.println("Storing document " + XML_DATA);
			params.clear();
			params.addElement(XML_DATA);
			params.addElement(TARGET_RESOURCE.toString());
			params.addElement(new Integer(1));

			result = (Boolean)xmlrpc.execute("parse", params);
			Assert.assertTrue(result.booleanValue());

			System.out.println("Documents stored.");

            HashMap<String, String> options = new HashMap<String, String>();
            params.clear();
            params.addElement(TARGET_RESOURCE.toString());
            params.addElement(options);

			byte[] data = (byte[]) xmlrpc.execute( "getDocument", params );
			System.out.println( new String(data, "UTF-8") );
            Assert.assertNotNull(data);

            params.clear();
            params.addElement(TARGET_RESOURCE.toString());
            params.addElement("UTF-8");
            params.addElement(0);

            data = (byte[]) xmlrpc.execute( "getDocument", params );
            System.out.println( new String(data, "UTF-8") );
            Assert.assertNotNull(data);

            params.clear();
            params.addElement(TARGET_RESOURCE.toString());
            params.addElement(0);
            String sdata = (String) xmlrpc.execute( "getDocumentAsString", params );
            System.out.println(sdata);
            Assert.assertNotNull(data);

            params.clear();
            params.addElement(TARGET_RESOURCE.toString());
            params.addElement(options);
            HashMap<?,?> table = (HashMap<?,?>) xmlrpc.execute("getDocumentData", params);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            int offset = ((Integer)table.get("offset")).intValue();
            data = (byte[])table.get("data");
            os.write(data);
            while(offset > 0) {
                params.clear();
                params.add(table.get("handle"));
                params.add(Integer.valueOf(offset));
                table = (HashMap<?,?>) xmlrpc.execute("getNextChunk", params);
                offset = ((Integer)table.get("offset")).intValue();
                data = (byte[])table.get("data");
                os.write(data);
            }
            data = os.toByteArray();
            Assert.assertTrue(data.length > 0);
            System.out.println(new String(data, "UTF-8"));

            params.clear();
            params.addElement(TARGET_RESOURCE.toString());
            Boolean b = (Boolean) xmlrpc.execute( "hasDocument", params );
            Assert.assertTrue(b.booleanValue());
	    } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
	    }
	}

	private void storeData() {
        System.out.println("---storeData");
		try {
			System.out.println("Creating collection " + TARGET_COLLECTION);
			XmlRpcClient xmlrpc = getClient();
			Vector<Object> params = new Vector<Object>();
			params.addElement(TARGET_COLLECTION.toString());
			Boolean result = (Boolean)xmlrpc.execute("createCollection", params);
			Assert.assertTrue(result.booleanValue());
			
			System.out.println("Storing document " + XML_DATA);
			params.clear();
			params.addElement(XML_DATA);
			params.addElement(TARGET_RESOURCE.toString());
			params.addElement(Integer.valueOf(1));
			
			result = (Boolean)xmlrpc.execute("parse", params);
			Assert.assertTrue(result.booleanValue());
			
			System.out.println("Storing resource " + XSL_DATA);
			params.setElementAt(XSL_DATA, 0);
			params.setElementAt(TARGET_COLLECTION.append("test.xsl").toString(), 1);
			result = (Boolean)xmlrpc.execute("parse", params);
			Assert.assertTrue(result.booleanValue());
			
			System.out.println("Storing resource " + MODULE_DATA);
			params.setElementAt(MODULE_DATA.getBytes("UTF-8"), 0);
			params.setElementAt(MODULE_RESOURCE.toString(), 1);
			params.setElementAt(MimeType.XQUERY_TYPE.getName(), 2);
			params.addElement(Boolean.TRUE);
			result = (Boolean)xmlrpc.execute("storeBinary", params);
			Assert.assertTrue(result.booleanValue());
			
			System.out.println("Documents stored.");
	    } catch (Exception e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());  
	    }			
	}

	@Test
    public void testRemoveCollection() {
        System.out.println("---testRemoveCollection");
        storeData();
        XmlRpcClient xmlrpc = getClient();
        try {
            List<Object> params = new ArrayList<Object>(1);
            params.add(TARGET_COLLECTION.toString());
            Boolean b = (Boolean) xmlrpc.execute("hasCollection", params);
            Assert.assertTrue(b.booleanValue());

            b = (Boolean) xmlrpc.execute("removeCollection", params);
            Assert.assertTrue(b.booleanValue());

            b = (Boolean) xmlrpc.execute("hasCollection", params);
            Assert.assertFalse(b.booleanValue());
        } catch (XmlRpcException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

	@Test
    public void testRemoveDoc() {
        System.out.println("---testRemoveDoc");
        storeData();
        XmlRpcClient xmlrpc = getClient();
        try {
            List<Object> params = new ArrayList<Object>(1);
            params.add(TARGET_RESOURCE.toString());
            Boolean b = (Boolean) xmlrpc.execute("hasDocument", params);

            Assert.assertTrue(b.booleanValue());

            b = (Boolean) xmlrpc.execute("remove", params);
            Assert.assertTrue(b.booleanValue());

            b = (Boolean) xmlrpc.execute("hasDocument", params);
            Assert.assertFalse(b.booleanValue());
        } catch (XmlRpcException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }
    }

	@Test
	public void testRetrieveDoc() {
        System.out.println("---testRetrieveDoc");
        storeData();
		System.out.println("Retrieving document " + TARGET_RESOURCE);
		Hashtable<String, String> options = new Hashtable<String, String>();
        options.put("indent", "yes");
        options.put("encoding", "UTF-8");
        options.put("expand-xincludes", "yes");
        options.put("process-xsl-pi", "no");
        
        Vector<Object> params = new Vector<Object>();
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
	    	Assert.fail(e.getMessage());  
	    }			
	}
	
	@Test
	public void testCharEncoding() {
        System.out.println("---testCharEncoding");
        storeData();
		try {
			System.out.println("Testing charsets returned by query");
			Vector<Object> params = new Vector<Object>();
			String query = "distinct-values(//para)";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(new Hashtable<Object, Object>());
			XmlRpcClient xmlrpc = getClient();
	        HashMap<?,?> result = (HashMap<?,?>) xmlrpc.execute( "queryP", params );
	        Object[] resources = (Object[]) result.get("results");
	        //TODO : check the number of resources before !
	        Assert.assertEquals(resources.length, 2);
	        String value = (String)resources[0];
	        Assert.assertEquals(value, "\u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF");
	        System.out.println("Result1: " + value);
	        value = (String)resources[1];
	        Assert.assertEquals(value, "\uC5F4\uB2E8\uACC4");
	        System.out.println("Result2: " + value);
	    } catch (Exception e) {            
	    	Assert.fail(e.getMessage());
        }
	}
	
	@Test
	public void testQuery() {
        System.out.println("---testQuery");
        storeData();
		try {
			Vector<Object> params = new Vector<Object>();
			String query = 
				"(::pragma exist:serialize indent=no::) //para";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(Integer.valueOf(10));
			params.addElement(Integer.valueOf(1));
			params.addElement(new Hashtable());
			XmlRpcClient xmlrpc = getClient();
	        byte[] result = (byte[]) xmlrpc.execute( "query", params );
	        Assert.assertNotNull(result);
	        Assert.assertTrue(result.length > 0);
	        System.out.println(new String(result, "UTF-8"));
	    } catch (Exception e) {            
	    	Assert.fail(e.getMessage());  
	    }	        
	}	
	
	@Test
	public void testQueryWithStylesheet() {
        System.out.println("---testQueryWithStylesheet");
        storeData();
		try {
			HashMap<String, String> options = new HashMap<String, String>();
			options.put(EXistOutputKeys.STYLESHEET, "test.xsl");
			options.put(EXistOutputKeys.STYLESHEET_PARAM + ".testparam", "Test");
			options.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
			//TODO : check the number of resources before !
			Vector<Object> params = new Vector<Object>();
			String query = "//para[1]";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(options);
			XmlRpcClient xmlrpc = getClient();
	        Integer handle = (Integer) xmlrpc.execute( "executeQuery", params );
	        Assert.assertNotNull(handle);
	        
	        params.clear();
	        params.addElement(handle);
	        params.addElement(Integer.valueOf(0));
	        params.addElement(options);
	        byte[] item = (byte[]) xmlrpc.execute( "retrieve", params );
	        Assert.assertNotNull(item);
	        Assert.assertTrue(item.length > 0);
	        String out = new String(item, "UTF-8");
	        System.out.println("Received: " + out);
	        XMLAssert.assertXMLEqual("<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
	        		"<p>Test: \u00E4\u00E4\u00F6\u00F6\u00FC\u00FC\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC\u00DF\u00DF</p>", out);
	    } catch (Exception e) {            
	    	Assert.fail(e.getMessage());  
	    }	        
	}

    @Test
    public void testCompile() {
        System.out.println("---testCompile");
        storeData();
		try {
			Vector<Object> params = new Vector<Object>();
			String query = "<a>Invalid<a>";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(new Hashtable<Object, Object>());
			XmlRpcClient xmlrpc = getClient();
			System.out.println("Executing query: " + query);
	        Map stats = (Map) xmlrpc.execute( "compile", params );
	        Assert.assertNotNull(stats);
            Assert.assertNotNull(stats.get("error"));
        } catch (Exception e) {
            Assert.fail(e.getMessage());
        }
    }

    @Test
    public void testAddAccount() {
        System.out.println("---testAddAccount");
        try {
            String user = "rudi";
            String passwd = "pass";
            String simpleMd5 = MessageDigester.md5(passwd, true);
            String digest = MessageDigester.md5(user + ":exist:" + passwd, false);
            List<Object> params = new ArrayList<Object>(12);
			params.add("rudi");
			params.add(simpleMd5);
			params.add(digest);
			params.add(new String[] { "guest" });
			params.add(true);
                        params.add(Permission.DEFAULT_UMASK);
			params.add(new HashMap<String, String>());
            
			XmlRpcClient xmlrpc = getClient();
			xmlrpc.execute("addAccount", params);

            XmlRpcClientConfigImpl config = (XmlRpcClientConfigImpl) xmlrpc.getClientConfig();
            config.setBasicUserName("admin");
            config.setBasicPassword("");
            xmlrpc.execute("sync", new ArrayList());
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
    }

	@Test
	public void testExecuteQuery() {
        System.out.println("---testExecuteQuery");
        storeData();
		try {
			Vector<Object> params = new Vector<Object>();
			String query = "distinct-values(//para)";
			params.addElement(query.getBytes("UTF-8"));
			params.addElement(new Hashtable<Object, Object>());
			XmlRpcClient xmlrpc = getClient();
			System.out.println("Executing query: " + query);
	        Integer handle = (Integer) xmlrpc.execute( "executeQuery", params );
	        Assert.assertNotNull(handle);
	        
	        params.clear();
	        params.addElement(handle);
	        Integer hits = (Integer) xmlrpc.execute( "getHits", params );
	        Assert.assertNotNull(hits);
	        System.out.println("Found: " + hits.intValue());
	        
	        Assert.assertEquals(hits.intValue(), 2);	        
        
	        params.addElement(new Integer(0));
	        params.addElement(new Hashtable<Object, Object>());
	        byte[] item = (byte[]) xmlrpc.execute( "retrieve", params );
	        System.out.println(new String(item, "UTF-8"));
	        
	        params.clear();
	        params.addElement(handle);
	        params.addElement(Integer.valueOf(1));
	        params.addElement(new Hashtable<Object, Object>());
	        item = (byte[]) xmlrpc.execute( "retrieve", params );
	        System.out.println(new String(item, "UTF-8"));
	    } catch (Exception e) {            
	    	Assert.fail(e.getMessage());  
	    }	        
	}
	
	@Test
	public void testQueryModuleExternalVar() {
        System.out.println("---testQueryModuleExternalVar");
        storeData();
		try {
			System.out.println("Quering with external variable definied in module ...");
			Vector<Object> params = new Vector<Object>();
			params.addElement(QUERY_MODULE_DATA.getBytes("UTF-8"));
			
			Hashtable<String, Object> qp = new Hashtable<String, Object>();
			
			HashMap<String, Object> namespaceDecls = new HashMap<String, Object>();
			namespaceDecls.put("tm", "http://exist-db.org/test/module");
			namespaceDecls.put("tm-query", "http://exist-db.org/test/module/query");
			qp.put(RpcAPI.NAMESPACES, namespaceDecls);
			
			HashMap<String, Object> variableDecls = new HashMap<String, Object>();
			variableDecls.put("tm:imported-external-string", "imported-string-value");
			variableDecls.put("tm-query:local-external-string", "local-string-value");
			qp.put(RpcAPI.VARIABLES, variableDecls);
			
			params.addElement(qp);
			
			XmlRpcClient xmlrpc = getClient();
			HashMap<String, Object[]> result = (HashMap) xmlrpc.execute("queryP", params);
	        Object[] resources = (Object[]) result.get("results");
	        Assert.assertEquals(resources.length, 2);
	        String value = (String) resources[0];
	        Assert.assertEquals(value, "imported-string-value");
	        System.out.println("Imported external: " + value);
	        value = (String) resources[1];
	        Assert.assertEquals(value, "local-string-value");
	        System.out.println("Local external: " + value);
	        
	    } catch (Exception e) {            
	    	Assert.fail(e.getMessage());  
	    }	        
	}
	
	@Test
	public void testCollectionWithAccentsAndSpaces() {
        System.out.println("---testCollectionWithAccentsAndSpaces");
        storeData();
		try {
			System.out.println("Creating collection with accents and spaces in name ...");
			Vector<Object> params = new Vector<Object>();
			params.addElement(SPECIAL_COLLECTION.toString());
			XmlRpcClient xmlrpc = getClient();
			xmlrpc.execute( "createCollection", params );
			
			System.out.println("Storing document " + XML_DATA);
			params.clear();
			params.addElement(XML_DATA);
			params.addElement(SPECIAL_RESOURCE.toString());
			params.addElement(Integer.valueOf(1));
			
			Boolean result = (Boolean)xmlrpc.execute("parse", params);
			Assert.assertTrue(result.booleanValue());
			
			params.clear();
			params.addElement(SPECIAL_COLLECTION.removeLastSegment().toString());
	
			HashMap collection = (HashMap) xmlrpc.execute("describeCollection", params);
			Object[] collections = (Object[]) collection.get("collections");
			boolean foundMatch=false;
			String targetCollectionName = SPECIAL_COLLECTION.lastSegment().toString();
			for (int i = 0; i < collections.length; i++) {
				String childName = (String) collections[i];
				System.out.println("Child collection: " + childName);
				if(childName.equals(targetCollectionName)) {
					foundMatch=true;
					break;
				}
			}
			Assert.assertTrue("added collection not found", foundMatch);
			
			System.out.println("Retrieving document '" + SPECIAL_RESOURCE.toString() + "'");
			HashMap<String, String> options = new HashMap<String, String>();
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
	    	Assert.fail(e.getMessage());  
	    }			
	}
	
	protected XmlRpcClient getClient() {
        try {
            XmlRpcClient client = new XmlRpcClient();
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEnabledForExtensions(true);
            config.setServerURL(new URL(URI));
            config.setBasicUserName("admin");
            config.setBasicPassword("");
            client.setConfig(config);
            return client;
        } catch (MalformedURLException e) {
        	e.printStackTrace();
            return null;
        }
    }

	public static void main(String[] args) {
		org.junit.runner.JUnitCore.main(XmlRpcTest.class.getName());
		//Explicit shutdownDB for the shutdownDB hook
		System.exit(0);		
	}	
}
