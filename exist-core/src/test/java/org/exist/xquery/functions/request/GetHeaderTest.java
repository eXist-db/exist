/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.request;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.URLEncoder;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.exist.http.RESTTest;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests expected behaviour of request:get-header() XQuery function
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class GetHeaderTest extends RESTTest {

	private final static String HTTP_HEADER_NAME = "header1";
	private final static String xquery = "<request-header name=\""
			+ HTTP_HEADER_NAME + "\">{request:get-header(\"" + HTTP_HEADER_NAME
			+ "\")}</request-header>";

	@Test
	public void testGetNoHeader() throws IOException, SAXException {
		testGetHeader(null);
	}

	@Test
	public void testEmptyHeader() throws IOException, SAXException {
		testGetHeader("");
	}

	@Test
	public void testHeaderValue() throws IOException, SAXException {
		testGetHeader("value1");
	}

	private void testGetHeader(String headerValue) throws IOException, SAXException {
		Request request = Request.Get(getCollectionRootUri() + "?_query=" + URLEncoder.encode(xquery, UTF_8) + "&_indent=no&_wrap=no");

		final StringBuilder xmlExpectedResponse = new StringBuilder("<request-header name=\"" + HTTP_HEADER_NAME + "\">");
		if (headerValue != null) {
			request = request.addHeader(HTTP_HEADER_NAME, headerValue);
			xmlExpectedResponse.append(headerValue);
		}
		xmlExpectedResponse.append("</request-header>");

		final HttpResponse response = request.execute().returnResponse();

		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
			response.getEntity().writeTo(os);
			assertXMLEqual(xmlExpectedResponse
					.toString(), new String(os.toByteArray(), UTF_8));
		}
	}
}