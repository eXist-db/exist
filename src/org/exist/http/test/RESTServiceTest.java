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
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import junit.framework.TestCase;

/**
 * @author wolf
 */
public class RESTServiceTest extends TestCase {

	private final static String SERVER_URI = "http://localhost:8088";
	
	private final static String RESOURCE_URI =SERVER_URI + "/db/test/test.xml";
	
	private final static String XML_DATA =
		"<test>" +
		"<para>ääüüööÄÄÖÖÜÜ</para>" +
		"</test>";
	
	private final static String XUPDATE =
		"<xu:modifications xmlns:xu=\"http://www.xmldb.org/xupdate\" version=\"1.0\">" +
		"<xu:append select=\"/test\" child=\"1\">" +
		"<para>Inserted paragraph.</para>" +
		"</xu:append>" +
		"</xu:modifications>";
	
	private final static String QUERY_REQUEST =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
		"<query xmlns=\"http://exist.sourceforge.net/NS/exist\">" +
		"<properties>" +
		"<property name=\"indent\" value=\"yes\"/>" +
		"<property name=\"encoding\" value=\"UTF-8\"/>" +
		"</properties>" +
		"<text>//para[. = 'ääüüööÄÄÖÖÜÜ']/text()</text>" +
		"</query>";
	
	public RESTServiceTest(String name) {
		super(name);
	}
	
	public void testPut() throws IOException {
		System.out.println("--- Storing document ---");
		HttpURLConnection connect = getConnection(RESOURCE_URI);
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
	}
	
	public void testXUpdate() throws IOException {
		HttpURLConnection connect = preparePost(XUPDATE);
		connect.connect();
		int r = connect.getResponseCode();
		assertEquals("Server returned response code " + r, 200, r);
		
		doGet();
	}
	
	public void testQueryPost() throws IOException {
		HttpURLConnection connect = preparePost(QUERY_REQUEST);
		connect.connect();
		int r = connect.getResponseCode();
		assertEquals("Server returned response code " + r, 200, r);
		
		System.out.println(readResponse(connect.getInputStream()));
	}
	
	protected void doGet() throws IOException {
		System.out.println("--- Retrieving document ---");
		HttpURLConnection connect = getConnection(RESOURCE_URI);
		connect.setRequestMethod("GET");
		connect.connect();
		
		int r = connect.getResponseCode();
		assertEquals("Server returned response code " + r, 200, r);
		
		System.out.println(readResponse(connect.getInputStream()));
	}
	
	protected HttpURLConnection preparePost(String content) throws IOException {
		HttpURLConnection connect = getConnection(RESOURCE_URI);
		connect.setRequestMethod("POST");
		connect.setDoOutput(true);
		connect.setRequestProperty("ContentType", "text/xml");
		
		Writer writer = new OutputStreamWriter(connect.getOutputStream(), "UTF-8");
		writer.write(content);
		writer.close();
		
		return connect;
	}
	
	protected String readResponse(InputStream is) throws IOException {
		BufferedReader reader = 
			new BufferedReader(new InputStreamReader(is, "UTF-8"));
		String line;
		StringBuffer out = new StringBuffer();
		while((line = reader.readLine()) != null) {
			out.append(line);
			out.append("\r\n");
		}
		return out.toString();
	}
	
	protected HttpURLConnection getConnection(String url) throws IOException {
		URL u = new URL(url);
		return (HttpURLConnection)u.openConnection();
	}
	
	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		super.setUp();
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
	}

}
