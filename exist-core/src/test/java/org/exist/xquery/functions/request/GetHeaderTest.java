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
import org.exist.util.io.FastByteArrayOutputStream;
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
		Request request = Request.Get(getCollectionRootUri() + "?_query=" + URLEncoder.encode(xquery, "UTF-8") + "&_indent=no&_wrap=no");

		final StringBuilder xmlExpectedResponse = new StringBuilder("<request-header name=\"" + HTTP_HEADER_NAME + "\">");
		if (headerValue != null) {
			request = request.addHeader(HTTP_HEADER_NAME, headerValue);
			xmlExpectedResponse.append(headerValue);
		}
		xmlExpectedResponse.append("</request-header>");

		final HttpResponse response = request.execute().returnResponse();

		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
			response.getEntity().writeTo(os);
			assertXMLEqual(xmlExpectedResponse
					.toString(), new String(os.toByteArray(), UTF_8));
		}
	}
}