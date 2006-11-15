/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier (wolfgang@exist-db.org) 
 *  and others (see http://exist-db.org)
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
package org.exist.http.test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;
import java.util.Map;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.mortbay.util.MultiException;

import sun.misc.BASE64Encoder;

/** A test case for accessing a remote server via REST-Style Web API.
 * @author wolf
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class RESTServiceTest extends TestCase {

    private static StandaloneServer server = null;

    private final static String SERVER_URI = "http://localhost:8088";

    private final static String COLLECTION_URI = SERVER_URI + DBBroker.ROOT_COLLECTION + "/test";

    private final static String RESOURCE_URI = SERVER_URI + DBBroker.ROOT_COLLECTION
            + "/test/test.xml";
    
    private final static String XML_DATA = "<test>"
            + "<para>\u00E4\u00E4\u00FC\u00FC\u00F6\u00F6\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC</para>"
            + "</test>";

    private final static String XUPDATE = "<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">"
            + "<xu:append select=\"/test\" child=\"1\">"
            + "<para>Inserted paragraph.</para>"
            + "</xu:append>" + "</xu:modifications>";

    private final static String QUERY_REQUEST = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<query xmlns=\"http://exist.sourceforge.net/NS/exist\">"
            + "<properties>"
            + "<property name=\"indent\" value=\"yes\"/>"
            + "<property name=\"encoding\" value=\"UTF-8\"/>"
            + "</properties>"
            + "<text>"
            + "xquery version \"1.0\";"
            + "(::pragma exist:serialize indent=no ::)"
            + "//para[. = '\u00E4\u00E4\u00FC\u00FC\u00F6\u00F6\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC']/text()"
            + "</text>" + "</query>";
    
    private final static String TEST_MODULE =
    	"module namespace t=\"http://test.foo\";\n" +
    	"declare variable $t:VAR { 'World!' };";
    
    private final static String TEST_XQUERY =
    	"xquery version \"1.0\";\n" +
    	"declare option exist:serialize \"method=text media-type=text/text\";\n" +
    	"import module namespace req=\"http://exist-db.org/xquery/request\";\n" +
    	"import module namespace t=\"http://test.foo\" at \"module.xq\";\n" +
    	"let $param := req:get-parameter('p', ())\n" +
    	"return\n" +
    	"	($param, ' ', $t:VAR)";
    
    private final static String TEST_XQUERY_PARAMETER =
    "xquery version \"1.0\";\n" +
    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
    "import module namespace requestparametermod=\"http://exist-db.org/xquery/requestparametermod\" at \"requestparametermod.xqm\";\n" +
    "concat(\"xql=\", request:get-parameter(\"doc\",())),\n" +
    "concat(\"xqm=\", $requestparametermod:request)";
    
    private final static String TEST_XQUERY_PARAMETER_MODULE =
   	"module namespace requestparametermod = \"http://exist-db.org/xquery/requestparametermod\";\n" +   	
    "declare namespace request=\"http://exist-db.org/xquery/request\";\n" +
    "declare variable $requestparametermod:request { request:get-parameter(\"doc\",())};\n";
    
    
    private String credentials;
    
    public RESTServiceTest(String name) {
        super(name);
        
        credentials = new BASE64Encoder().encode("admin:".getBytes());
    }

    protected void setUp() {
        //Don't worry about closing the server : the shutdown hook will do the job
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
                            Exception e0 = (Exception) i.next();
                            if (e0 instanceof BindException) {
                                System.out.println("A server is running already !");
                                rethrow = false;
                                break;
                            }
                        }
                        if (rethrow)
                            throw e;
                    }
                }
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testPut() {
        try {
            System.out.println("--- Storing document ---");
            HttpURLConnection connect = getConnection(RESOURCE_URI);
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "text/xml");
            Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
            writer.write(XML_DATA);
            writer.close();

            connect.connect();
            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            doGet();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testPutFailAgainstCollection() {
        try {
            System.out.println("--- Storing document against collection URI - should fail ---");
            HttpURLConnection connect = getConnection(COLLECTION_URI);
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "text/xml");
            Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
            writer.write(XML_DATA);
            writer.close();

            connect.connect();
            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 400, r);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testPutWithCharset() {
        try {
            System.out.println("--- Storing document ---");
            HttpURLConnection connect = getConnection(RESOURCE_URI);
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("PUT");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "text/xml; charset=UTF-8");

            Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
            writer.write(XML_DATA);
            writer.close();

            connect.connect();
            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            doGet();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testXUpdate() {
        try {
            HttpURLConnection connect = preparePost(XUPDATE);
            connect.connect();
            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            doGet();
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testQueryPost() {
        try {
            HttpURLConnection connect = preparePost(QUERY_REQUEST);
            connect.connect();
            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            System.out.println(readResponse(connect.getInputStream()));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testQueryGet() {
        try {
            String uri = COLLECTION_URI
                    + "?_query="
                    + URLEncoder
                            .encode(
                                    "doc('"
                                            + DBBroker.ROOT_COLLECTION
                                            + "/test/test.xml')//para[. = '\u00E4\u00E4\u00FC\u00FC\u00F6\u00F6\u00C4\u00C4\u00D6\u00D6\u00DC\u00DC']/text()",
                                    "UTF-8");
            HttpURLConnection connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            System.out.println(readResponse(connect.getInputStream()));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testRequestModule() {
        try {
            String uri = COLLECTION_URI + "?_query=request:get-uri()&_wrap=no";
            HttpURLConnection connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            String response = readResponse(connect.getInputStream()).trim();
            assertEquals(response, DBBroker.ROOT_COLLECTION + "/test");

            uri = COLLECTION_URI + "?_query=request:get-url()&_wrap=no";
            connect = getConnection(uri);
            connect.setRequestMethod("GET");
            connect.connect();

            r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);

            response = readResponse(connect.getInputStream()).trim();
            //TODO : the server name may have been renamed by the Web server
            assertEquals(response, SERVER_URI + DBBroker.ROOT_COLLECTION + "/test");
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testRequestGetParameterFromModule()
    {
    	try
    	{
    		/* store the documents that we need for this test */
    		System.out.println("--- Storing xquery and module ---");
    		doPut(TEST_XQUERY_PARAMETER, "requestparameter.xql");
    		doPut(TEST_XQUERY_PARAMETER_MODULE, "requestparametermod.xqm");
    		
	        /* execute the stored xquery a few times */
    		HttpURLConnection connect;
    		int iHttpResult;
	        for(int i=0; i < 5; i++)
	        {
	            System.out.println("--- Executing stored xquery, iteration=" + i + " ---");
	            connect = getConnection(COLLECTION_URI + "/requestparameter.xql?doc=somedoc" + i);
	            connect.setRequestMethod("GET");
	            connect.connect();
	
	            iHttpResult = connect.getResponseCode();
	            assertEquals("Server returned response code " + iHttpResult, 200, iHttpResult);
	            String contentType = connect.getContentType();
	            int semicolon = contentType.indexOf(';');
	            if (semicolon > 0) {
	                contentType = contentType.substring(0, semicolon).trim();
	            }
	            assertEquals("Server returned content type " + contentType, "text/html", contentType);
	
	            //get the response of the query
	            String response = readResponse(connect.getInputStream());
	            
	            String strXQLRequestParameter = response.substring("xql=".length(), response.indexOf("xqm="));
	            String strXQMRequestParameter = response.substring(response.indexOf("xqm=") + "xqm=".length(), response.lastIndexOf("\r\n"));
	            
	            //check the responses
	            assertEquals("XQuery Request Parameter is: \"" + strXQLRequestParameter + "\" expected: \"somedoc"+i + "\"", "somedoc"+i, strXQLRequestParameter);
	            assertEquals("XQuery Module Request Parameter is: \"" + strXQMRequestParameter + "\" expected: \"somedoc"+i + "\"", "somedoc"+i, strXQMRequestParameter);
	        }
        }
        catch(Exception e)
        {
        	fail(e.getMessage());
        }
    }

    public void testStoredQuery() {
        try {
            System.out.println("--- Storing query ---");
            doPut(TEST_MODULE, "module.xq");
            doPut(TEST_XQUERY, "test.xq");

            doStoredQuery(false);
            doStoredQuery(true);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    protected void doPut(String data, String path) {
    	try {
    		HttpURLConnection connect = getConnection(COLLECTION_URI + '/' + path);
    		connect.setRequestProperty("Authorization", "Basic " + credentials);
    		connect.setRequestMethod("PUT");
    		connect.setDoOutput(true);
    		connect.setRequestProperty("ContentType", "application/xquery");
    		Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
    		writer.write(data);
    		writer.close();
    		
    		connect.connect();
    		int r = connect.getResponseCode();
    		assertEquals("Server returned response code " + r, 200, r);
    	} catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    protected void doStoredQuery(boolean cacheHeader) {
    	try {
            System.out.println("--- Calling query: " + COLLECTION_URI + "/test.xq?p=Hello");
            HttpURLConnection connect = getConnection(COLLECTION_URI + "/test.xq?p=Hello");
            connect.setRequestMethod("GET");
            connect.connect();

            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);
            
            dumpHeaders(connect);
            String cached = connect.getHeaderField("X-XQuery-Cached");
            System.out.println("X-XQuery-Cached: " + cached);
            assertNotNull(cached);
            assertEquals(cacheHeader, Boolean.valueOf(cached).booleanValue());
            
            String contentType = connect.getContentType();
            int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
            }
            assertEquals("Server returned content type " + contentType, "text/text", contentType);

            System.out.println('"' + readResponse(connect.getInputStream()) + '"');
            
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    protected void doGet() {
        try {
            System.out.println("--- Retrieving document ---");
            HttpURLConnection connect = getConnection(RESOURCE_URI);
            connect.setRequestMethod("GET");
            connect.connect();

            int r = connect.getResponseCode();
            assertEquals("Server returned response code " + r, 200, r);
            String contentType = connect.getContentType();
            int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
            }
            assertEquals("Server returned content type " + contentType, "text/xml", contentType);

            System.out.println(readResponse(connect.getInputStream()));
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    protected HttpURLConnection preparePost(String content) {
        try {
            HttpURLConnection connect = getConnection(RESOURCE_URI);
            connect.setRequestProperty("Authorization", "Basic " + credentials);
            connect.setRequestMethod("POST");
            connect.setDoOutput(true);
            connect.setRequestProperty("ContentType", "text/xml");

            Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
            writer.write(content);
            writer.close();

            return connect;
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }

    protected String readResponse(InputStream is) {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            String line;
            StringBuffer out = new StringBuffer();
            while ((line = reader.readLine()) != null) {
                out.append(line);
                out.append("\r\n");
            }
            return out.toString();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }

    protected HttpURLConnection getConnection(String url) {
        try {
            URL u = new URL(url);
            return (HttpURLConnection) u.openConnection();
        } catch (Exception e) {
            fail(e.getMessage());
        }
        return null;
    }

    protected void dumpHeaders(HttpURLConnection connect) {
    	Map headers = connect.getHeaderFields();
    	for (Iterator i = headers.entrySet().iterator(); i.hasNext(); ) {
    		Map.Entry entry = (Map.Entry) i.next();
    		System.out.println(entry.getKey() + ": " + entry.getValue());
    	}
    }
    
    public static void main(String[] args) {
        TestRunner.run(RESTServiceTest.class);
        //Explicit shutdown for the shutdown hook
        System.exit(0);
    }
}
