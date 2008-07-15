package org.exist.xquery.functions.request;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.methods.GetMethod;
import org.exist.http.RESTTest;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * Tests expected behaviour of request:get-parameter() XQuery function
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */

public class GetParameterTest extends RESTTest {

	private final static String HTTP_REQUEST_PARAM_NAME = "param1";
	private final static String xquery = "<request-param name=\""
			+ HTTP_REQUEST_PARAM_NAME
			+ "\">{for $value in request:get-parameter(\""
			+ HTTP_REQUEST_PARAM_NAME
			+ "\", ()) return <value>{$value}</value>}</request-param>";

	@Test
	public void testNoParameter() {
		testGetParameter(null);
	}

	@Test
	public void testEmptyParameter() {
		testGetParameter(new String[] { "" });
	}

	@Test
	public void testSingleValueParameter() {
		testGetParameter(new String[] { "value1" });
	}

	@Test
	public void testMultiValueParameter() {
		testGetParameter(new String[] { "value1", "value2", "value3", "value4" });
	}

	private void testGetParameter(String paramValues[]) {
		GetMethod get = new GetMethod(COLLECTION_ROOT_URL);

		NameValuePair qsParams[] = null;
		NameValuePair qsXQueryParam = new NameValuePair("_query", xquery);
		NameValuePair qsIndentParam = new NameValuePair("_indent", "no");

		StringBuilder xmlExpectedResponse = new StringBuilder(
				"<exist:result xmlns:exist=\"http://exist.sourceforge.net/NS/exist\" exist:hits=\"1\" exist:start=\"1\" exist:count=\"1\"><request-param name=\""
						+ HTTP_REQUEST_PARAM_NAME + "\">");
		if (paramValues == null || paramValues.length == 0) {
			qsParams = new NameValuePair[2];
			qsParams[0] = qsXQueryParam;
			qsParams[1] = qsIndentParam;
		} else {
			qsParams = new NameValuePair[paramValues.length + 2];
			qsParams[0] = qsXQueryParam;
			qsParams[1] = qsIndentParam;
			for (int i = 0; i < paramValues.length; i++) {
				qsParams[i + 2] = new NameValuePair(HTTP_REQUEST_PARAM_NAME,
						paramValues[i]);
				xmlExpectedResponse.append("<value>" + paramValues[i]
						+ "</value>");
			}
		}
		xmlExpectedResponse.append("</request-param></exist:result>");

		get.setQueryString(qsParams);

		try {
			int httpResult = client.executeMethod(get);

			byte buf[] = new byte[1024];
			int read = -1;
			StringBuilder xmlActualResponse = new StringBuilder();
			InputStream is = get.getResponseBodyAsStream();
			while ((read = is.read(buf)) > -1) {
				xmlActualResponse.append(new String(buf, 0, read));
			}

			assertEquals(httpResult, HttpStatus.SC_OK);

			assertXMLEqual(xmlActualResponse.toString(), xmlExpectedResponse
					.toString());

		} catch (HttpException he) {
			fail(he.getMessage());
		} catch (IOException ioe) {
			fail(ioe.getMessage());
		} catch (SAXException sae) {
			fail(sae.getMessage());
		} finally {
			get.releaseConnection();
		}
	}
}