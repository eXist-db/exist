/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import org.apache.xmlrpc.client.XmlRpcClient;
import org.apache.xmlrpc.client.XmlRpcClientConfigImpl;
import org.custommonkey.xmlunit.Diff;
import org.exist.jetty.JettyStart;
import org.exist.storage.serializers.XIncludeFilter;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Vector;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * @author jim fuller at webcomposite.com
 * 
 * Test XInclude Serialiser via REST/XMLRPC/WEBDAV/SOAP interfaces
 * 
 * TODO: need to refacotr to avoid catching unexpected exceptions
 * 
 * 
 */
public class XIncludeSerializerTest {

	private static JettyStart server = null;
    
    private final static XmldbURI XINCLUDE_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xinclude_test");
    private final static XmldbURI XINCLUDE_NESTED_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("xinclude_test/data");

    // jetty.port.standalone
    private final static String XMLRPC_URI = "http://127.0.0.1:" + System.getProperty("jetty.port") + "/xmlrpc";
    private final static String REST_URI = "http://admin:admin@127.0.0.1:" + System.getProperty("jetty.port") + "/db/xinclude_test";

    private final static String XML_DATA1 =
    	"<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='metatags.xml'/>" +
    	"</root>" +
    	"</test>";
    
    private final static String XML_DATA2 =
    	"<html>" +
    	"<head>" +
    	"<metatag xml:id='metatag' name='test' description='test'/>" +
    	"</head>" +
    	"</html>";

    private final static String XML_DATA3 =
    	"<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='../xinclude_test/data/metatags.xml'/>" +
    	"</root>" +
    	"</test>";

    private final static String XML_DATA4 =
    	"<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='data/metatags.xml'/>" +
    	"</root>" +
    	"</test>";

    private final static String XML_DATA5 =
        "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='data/metatags.xml' xpointer='xpointer(//metatag)'/>" +
    	"</root>" +
    	"</test>";

    private final static String XML_DATA6 =
        "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='data/metatags.xml' xpointer='metatag'/>" +
    	"</root>" +
    	"</test>";

    private final static String XML_DATA7 =
        "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='data/unknown.xml'>" +
        "<xi:fallback><warning>Not found</warning></xi:fallback>" +
        "</xi:include>" +
        "</root>" +
    	"</test>";

    private final static String XML_DATA8 =
        "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>" +
    	"<root>" +
    	"<xi:include href='data/unknown.xml'/>" +
        "</root>" +
    	"</test>";

    private final static String XML_RESULT ="<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>"+
    "<root>" +
    "<html>" +
    "<head>"+
    "<metatag xml:id='metatag' name='test' description='test'/>" +
    "</head>" +
    "</html>" +
    "</root>"+
    "</test>";

    private final static String XML_RESULT_XPOINTER = "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>"+
    "<root>" +
    "<metatag xml:id='metatag' name='test' description='test'/>" +
    "</root>"+
    "</test>";

    private final static String XML_RESULT_FALLBACK1 = "<test xmlns:xi='" + XIncludeFilter.XINCLUDE_NS + "'>"+
        "<root>" +
        "<warning>Not found</warning>" +
        "</root>" +
    	"</test>";



    /*
     * REST tests
     *
     */
    @Test
    public void absSimpleREST() {
       try { 

    	    // path needs to indicate indent and wrap is off
        	String uri = REST_URI + "/test_simple.xml?_indent=no&_wrap=no";

        	// we use honest http
        	HttpURLConnection connect = getConnection(uri);
	    connect.setRequestMethod("GET");
	    connect.connect();

	    BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
	    String line;
	    StringBuffer out = new StringBuffer();
	    
	    while ((line = reader.readLine()) != null) {
	        out.append(line);
	        out.append("\r\n");
	    }
	    
	    String responseXML = out.toString();

        System.out.println("response XML:"+ responseXML);
        System.out.println("control XML" + XML_RESULT);

        Diff myDiff = new Diff(XML_RESULT, responseXML);
        assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
        assertTrue("but are they identical? " + myDiff, myDiff.identical());
        
        } catch (Exception e) {
            fail(e.getMessage());
        }
        
    }//absSimpleREST

    @Test
    public void relSimpleREST1() {
        try { 
         	String uri = REST_URI + "/test_relative1.xml?_indent=no&_wrap=no";
      
         	HttpURLConnection connect = getConnection(uri);
 	    connect.setRequestMethod("GET");
 	    connect.connect();

 	    BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
 	    String line;
 	    StringBuffer out = new StringBuffer();
 	    while ((line = reader.readLine()) != null) {
 	        out.append(line);
 	        out.append("\r\n");
 	    }
 	    String responseXML = out.toString();

        System.out.println("response XML:"+ responseXML);
        System.out.println("control XML" + XML_RESULT);

         Diff myDiff = new Diff(XML_RESULT, responseXML);
         assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
         assertTrue("but are they identical? " + myDiff, myDiff.identical());
         
         } catch (Exception e) {
             fail(e.getMessage());
         }
         
     }//relSimpleREST1


    @Test
    public void relSimpleREST2() {
        try { 
         // path needs to indicate indent and wrap is off
         	String uri = REST_URI + "/test_relative2.xml?_indent=no&_wrap=no";
      
         	HttpURLConnection connect = getConnection(uri);
 	    connect.setRequestMethod("GET");
 	    connect.connect();

 	    BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
 	    String line;
 	    StringBuffer out = new StringBuffer();
 	    while ((line = reader.readLine()) != null) {
 	        out.append(line);
 	        out.append("\r\n");
 	    }
 	    String responseXML = out.toString();

        System.out.println("response XML:"+ responseXML);
        System.out.println("control XML" + XML_RESULT);

         Diff myDiff = new Diff(XML_RESULT, responseXML);
         assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
         assertTrue("but are they identical? " + myDiff, myDiff.identical());
         
         } catch (Exception e) {
             fail(e.getMessage());
         }
         
     }//relSimpleREST

    @Test
    public void xPointerREST3() {
        try {
            String uri = REST_URI + "/test_xpointer1.xml?_indent=no&_wrap=no";

            HttpURLConnection connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
            String line;
            StringBuffer out = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            String responseXML = out.toString();

            System.out.println("response XML:"+ responseXML);
            System.out.println("control XML" + XML_RESULT_XPOINTER);

            Diff myDiff = new Diff(XML_RESULT_XPOINTER, responseXML);
            assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
            assertTrue("but are they identical? " + myDiff, myDiff.identical());

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void xPointerREST4() {
        try {
            String uri = REST_URI + "/test_xpointer2.xml?_indent=no&_wrap=no";

            HttpURLConnection connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
            String line;
            StringBuffer out = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            String responseXML = out.toString();

            System.out.println("response XML:"+ responseXML);
            System.out.println("control XML" + XML_RESULT_XPOINTER);

            Diff myDiff = new Diff(XML_RESULT_XPOINTER, responseXML);
            assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
            assertTrue("but are they identical? " + myDiff, myDiff.identical());

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void fallback1() {
        try {
            String uri = REST_URI + "/test_fallback1.xml?_indent=no&_wrap=no";

            HttpURLConnection connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
            String line;
            StringBuffer out = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            String responseXML = out.toString();

            System.out.println("response XML:"+ responseXML);
            System.out.println("control XML" + XML_RESULT_FALLBACK1);

            Diff myDiff = new Diff(XML_RESULT_FALLBACK1, responseXML);
            assertTrue("pieces of XML are similar " + myDiff, myDiff.similar());
            assertTrue("but are they identical? " + myDiff, myDiff.identical());

        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @Test(expected = IOException.class)
    public void fallback2() throws Exception {
        String uri = REST_URI + "/test_fallback2.xml?_indent=no&_wrap=no";

        HttpURLConnection connect = getConnection(uri);
        connect.setRequestMethod("GET");
        connect.connect();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connect.getInputStream(), "UTF-8"));
        String line;
        StringBuffer out = new StringBuffer();
        while ((line = reader.readLine()) != null) {
            out.append(line);
            out.append("\r\n");
        }
        String responseXML = out.toString();

        System.out.println("response XML:"+ responseXML);
    }

    // @TODO add full url test e.g. http://www.example.org/test.xml for xinclude
    
    
    // @TODO add simple and relative url with xpointer
    //ex. <xi:include href="../javascript.xml#xpointer(html/head)"/>

    
    // @TODO add simple and relative url with xpointer and namespaces
    // ex. <xi:include href="../javascript.xml#xmlns(x=http://www.w3.org/1999/xhtml)xpointer(/x:html/x:head)"/>
    
    
    /*
     * XML-RPC tests
     *
     */
    // @TODO check serialisation via this interface, simple and relative

    /*
     * WebDAV tests
     *
     */
    
    // @TODO check serialisation via this interface, simple and relative???
    // probably overkill
     
    /*
     * SOAP tests
     *
     */
    // probably overkill
 

   
    // @TODO check serialisation via this interface, simple and relative???
    // probably overkill
   
    /*
     * helper functions
     *
     */
    protected HttpURLConnection getConnection(String url) {

    	try {
            URL u = new URL(url);
            return (HttpURLConnection) u.openConnection();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;

    }//httpurlconnection

    protected static XmlRpcClient getClient() {
        try {
            XmlRpcClient client = new XmlRpcClient();
            XmlRpcClientConfigImpl config = new XmlRpcClientConfigImpl();
            config.setEnabledForExtensions(true);
            config.setServerURL(new URL(XMLRPC_URI));
            config.setBasicUserName("admin");
            config.setBasicPassword("");
            client.setConfig(config);
            return client;
        } catch (MalformedURLException e) {
            return null;
        }
    }
   
   // @TODO create reader for xml 
 
    /*
     * SetUp / TearDown functions
     *
     */
	@BeforeClass
    public static void startDB() {
        //Don't worry about closing the server : the shutdownDB hook will do the job
        try {
            if (server == null) {
                server = new JettyStart();
                System.out.println("Starting standalone server...");
                server.run();
            }

			System.out.println("Creating collection " + XINCLUDE_COLLECTION);
			XmlRpcClient xmlrpc = getClient();
			Vector<Object> params = new Vector<Object>();
			params.addElement(XINCLUDE_COLLECTION.toString());
			@SuppressWarnings("unused")
			Boolean resultColl1 = (Boolean)xmlrpc.execute("createCollection", params);

			params.clear();			
			params.addElement(XINCLUDE_NESTED_COLLECTION.toString());
			@SuppressWarnings("unused")
			Boolean resultColl2 = (Boolean)xmlrpc.execute("createCollection", params);

			System.out.println("Loading test document data");
			params.clear();
			params.addElement(XML_DATA1);
			params.addElement("/db/xinclude_test/test_simple.xml");
			params.addElement(new Integer(1));			
			@SuppressWarnings("unused")
			Boolean resultFile1 = (Boolean)xmlrpc.execute("parse", params);

			params.clear();
			params.addElement(XML_DATA2);
			params.addElement("/db/xinclude_test/metatags.xml");
			params.addElement(new Integer(1));			
			@SuppressWarnings("unused")
			Boolean resultFile3 = (Boolean)xmlrpc.execute("parse", params);

			params.clear();
			params.addElement(XML_DATA2);
			params.addElement("/db/xinclude_test/data/metatags.xml");
			params.addElement(new Integer(1));			
			@SuppressWarnings("unused")
			Boolean resultFile4 = (Boolean)xmlrpc.execute("parse", params);
			
			params.clear();
			params.addElement(XML_DATA3);
			params.addElement("/db/xinclude_test/test_relative1.xml");
			params.addElement(new Integer(1));			
			@SuppressWarnings("unused")
			Boolean resultFile5 = (Boolean)xmlrpc.execute("parse", params);

			params.clear();
			params.addElement(XML_DATA4);
			params.addElement("/db/xinclude_test/test_relative2.xml");
			params.addElement(new Integer(1));			
			@SuppressWarnings("unused")
			Boolean resultFile6 = (Boolean)xmlrpc.execute("parse", params);

            params.clear();
			params.addElement(XML_DATA5);
			params.addElement("/db/xinclude_test/test_xpointer1.xml");
			params.addElement(new Integer(1));
			@SuppressWarnings("unused")
			Boolean resultFile7 = (Boolean)xmlrpc.execute("parse", params);

            params.clear();
			params.addElement(XML_DATA6);
			params.addElement("/db/xinclude_test/test_xpointer2.xml");
			params.addElement(new Integer(1));
			@SuppressWarnings("unused")
			Boolean resultFile8 = (Boolean)xmlrpc.execute("parse", params);

            params.clear();
			params.addElement(XML_DATA7);
			params.addElement("/db/xinclude_test/test_fallback1.xml");
			params.addElement(new Integer(1));
			@SuppressWarnings("unused")
			Boolean resultFile9 = (Boolean)xmlrpc.execute("parse", params);

            params.clear();
			params.addElement(XML_DATA8);
			params.addElement("/db/xinclude_test/test_fallback2.xml");
			params.addElement(new Integer(1));
			@SuppressWarnings("unused")
			Boolean resultFile10 = (Boolean)xmlrpc.execute("parse", params);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() throws Exception {
    	try{
			XmlRpcClient xmlrpc = getClient();
			Vector<Object> params = new Vector<Object>();
			params.clear();
			params.addElement("/db/xinclude_test");
			@SuppressWarnings("unused")
			Boolean resultRemove = (Boolean)xmlrpc.execute("removeCollection", params);
	} catch (Exception e) {
        fail(e.getMessage());	
    }
    }//tearDown

    
    public static void main(String[] args) {
        org.junit.runner.JUnitCore.runClasses(XIncludeSerializerTest.class);
    }

}//XIncludeserializertest
