package org.exist.xquery.functions.request;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.exist.http.RESTTest;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests expected behaviour of request:get-header() XQuery function
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class GetHeaderTest extends RESTTest {

	private final static String HTTP_HEADER_NAME = "header1";
	private final static String xquery = "<request-header name=\""
			+ HTTP_HEADER_NAME + "\">{request:get-header(\"" + HTTP_HEADER_NAME
			+ "\")}</request-header>";

	@Test
	public void testGetNoHeader() {
		testGetHeader(null);
	}

	@Test
	public void testEmptyHeader() {
		testGetHeader("");
	}

	@Test
	public void testHeaderValue() {
		testGetHeader("value1");
	}

	private void testGetHeader(String headerValue) {
		final GetMethod get = new GetMethod(getCollectionRootUri());

		final NameValuePair qsParams[] = {
				new NameValuePair("_query", xquery),
				new NameValuePair("_indent", "no"),
				new NameValuePair("_wrap", "no")
		};

		final StringBuilder xmlExpectedResponse = new StringBuilder("<request-header name=\"" + HTTP_HEADER_NAME + "\">");
		if (headerValue != null) {
			get.setRequestHeader(HTTP_HEADER_NAME, headerValue);

			xmlExpectedResponse.append(headerValue);
		}
		xmlExpectedResponse.append("</request-header>");

		get.setQueryString(qsParams);

		try {
			int httpResult = client.executeMethod(get);

			final StringBuilder xmlActualResponse = new StringBuilder();
			try (final InputStream is = get.getResponseBodyAsStream()) {
				byte buf[] = new byte[4096];
				int read = -1;
				while ((read = is.read(buf)) > -1) {
					xmlActualResponse.append(new String(buf, 0, read));
				}
			}

			assertEquals(httpResult, HttpStatus.SC_OK);

			assertXMLEqual(xmlExpectedResponse
					.toString(), xmlActualResponse.toString());

		} catch (final IOException | SAXException ioe) {
			fail(ioe.getMessage());
		} finally {
			get.releaseConnection();
		}
	}
}