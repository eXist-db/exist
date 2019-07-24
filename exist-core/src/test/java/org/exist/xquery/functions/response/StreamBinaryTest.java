package org.exist.xquery.functions.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.net.URLEncoder;

import org.apache.commons.codec.binary.Base64;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.exist.http.RESTTest;
import org.exist.util.io.FastByteArrayOutputStream;
import org.junit.Test;

/**
 * Tests expected behaviour of response:stream-binary() XQuery function
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class StreamBinaryTest extends RESTTest {

	@Test
	public void testStreamBinary() throws IOException {
		
		final String testValue = "hello world";
		final String xquery = "response:stream-binary(xs:base64Binary('" +  Base64.encodeBase64String(testValue.getBytes())  + "'), 'application/octet-stream', 'test.bin')";

		final Request get = Request.Get(getCollectionRootUri() + "?_query=" + URLEncoder.encode(xquery, "UTF-8") + "&_indent=no");

		final HttpResponse response = get.execute().returnResponse();
		assertEquals(HttpStatus.SC_OK, response.getStatusLine().getStatusCode());

		try (final FastByteArrayOutputStream os = new FastByteArrayOutputStream()) {
			response.getEntity().writeTo(os);

			assertArrayEquals(testValue.getBytes(), os.toByteArray());
		}
	}
}