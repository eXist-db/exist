package org.exist.xquery.functions.response;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.exist.http.RESTTest;
import org.junit.Test;

/**
 * Tests expected behaviour of response:stream-binary() XQuery function
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class StreamBinaryTest extends RESTTest {
	
        @Test
	public void testStreamBinary() {
		
                final String testValue = "hello world";
                final String xquery = "response:stream-binary(xs:base64Binary('" +  Base64.encodeBase64String(testValue.getBytes())  + "'), 'application/octet-stream', 'test.bin')";
            
                GetMethod get = new GetMethod(COLLECTION_ROOT_URL);

		NameValuePair qsParams[] = { new NameValuePair("_query", xquery),
				new NameValuePair("_indent", "no") };
		get.setQueryString(qsParams);

		try {
			int httpResult = client.executeMethod(get);

			byte buf[] = new byte[1024];
			int read = -1;
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			InputStream is = get.getResponseBodyAsStream();
			while ((read = is.read(buf)) > -1) {
				baos.write(buf, 0, read);
			}

			assertEquals(httpResult, HttpStatus.SC_OK);

			assertArrayEquals(testValue.getBytes(), baos.toByteArray());

		} catch (HttpException he) {
			fail(he.getMessage());
		} catch (IOException ioe) {
			fail(ioe.getMessage());
		} finally {
			get.releaseConnection();
		}
	}
}