package org.exist.extensions.exquery.restxq.impl.multipart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.methods.PostMethod;
import org.junit.Test;
import org.xml.sax.SAXException;


/**
 * Tests multipart/related response in restXQ.
 * 
 * Send a text/xml post request to an restXQ endpoint.
 * The endpoint returns the sended xml data directly without any manipulation.
 * The return media-type is multipart, that's will be serialized to multipart response.
 * 
 *
 * 	The serilized XML:
 * 
 *	<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>
 *		<http:header name='Content-Type' value='text/plain'/>
 *		<http:header name='Content-ID' value='test_1'/>
 *		<http:body>TEST CONTENT 1</http:body>
 *		<http:header name='Content-Type' value='text/xml'/>
 *		<http:header name='Content-ID' value='test_2'/>
 *		<http:body><data>TEST CONTENT 2</data></http:body>
 *		<http:header name='Content-Type' value='text/plain'/>
 *		<http:header name='Content-ID' value='test_3'/>
 *		<http:header name='Content-Transfer-Encoding' value='base64'/>
 *		<http:body>VEVTVCBDT05URU5UIDM=</http:body>
 *	</http:multipart>
 *
 * The expected response Content-Type header:
 *	multipart/related; start=test_1; boundary=TEST_BOUNDARY; charset=utf-8
 *
 * The expected response:
 * 
 * 	--TEST_BOUNDARY
 *	Content-Type: text/plain
 *	Content-Transfer-Encoding: 8bit
 *	Content-ID: test_1
 *
 *	TEST CONTENT 1
 *	--TEST_BOUNDARY
 *	Content-Type: text/xml
 *	Content-Transfer-Encoding: 8bit
 *	 Content-ID: test_2
 *
 *	TEST CONTENT 2
 *	--TEST_BOUNDARY
 *	Content-Type: text/plain
 *	Content-Transfer-Encoding: base46
 *	Content-ID: test_3
 *
 *	VEVTVCBDT05URU5UIDM=
 *	--TEST_BOUNDARY--
 *
 * If start parameter is not setted then the first part is a root part by default. 
 * The expected results is a http:multipart element with a http:header for each http:body childs elements:
 *

 *
 * The first body element is the root part.
 * If part header "Content-Transfer-Encoding" is setted to base64 then the part content will not be encoded,
 * otherwise the content will be encoded to base64
 * 
 * 
 * @author Maan Al Balkhi <maan.al.balkhi@gmail.com>
 * 
 */
public class MultipartRelatedResponseTest extends MultipartTest {
	
	private static final String TEST_URL = REQUEST_URL + "/responseTest";
	
	@Test
	public void responsePartsContentsTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
						"<http:header name='Content-Type' value='text/xml'/>"+
						"<http:header name='Content-ID' value='test_2'/>"+
						"<http:body><data>TEST CONTENT 2</data></http:body>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_3'/>"+
						"<http:header name='Content-Transfer-Encoding' value='base64'/>"+
						"<http:body>VEVTVCBDT05URU5UIDM=</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String[] responseLines = readResponse(post).split("\n");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("First part Content: ", "TEST CONTENT 1", responseLines[4]);
		assertEquals("Second part Content:", "<data>TEST CONTENT 2</data>", responseLines[9]);
		assertEquals("Third part Content:", "VEVTVCBDT05URU5UIDM=", responseLines[15]);
	}
	
	@Test
	public void partsHeadersTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:header name='Content-Transfer-Encoding' value='8bit'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String[] responseLines = readResponse(post).split("\n");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("requiered header: Content-Type header", "Content-Type: text/plain", responseLines[1]);
		assertEquals("optional header: Content-ID", "Content-ID: test_1", responseLines[2]);
		assertEquals("optional header: Content-Transfer-Encodeing", "Content-Transfer-Encoding: 8bit", responseLines[3]);
	}
	
	@Test
	public void partsBoundaryTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:header name='Content-Transfer-Encoding' value='8bit'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
						"<http:header name='Content-Type' value='text/xml'/>"+
						"<http:header name='Content-ID' value='test_2'/>"+
						"<http:body><data>TEST CONTENT 2</data></http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String[] responseLines = readResponse(post).split("\n");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("boundary for first part", "--TEST_BOUNDARY", responseLines[0]);
		assertEquals("boundary for second part", "--TEST_BOUNDARY", responseLines[6]);
	}
	
	@Test
	public void endingBoundaryTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(200, post.getStatusCode());
		assertTrue("end boundary", readResponse(post).endsWith("--TEST_BOUNDARY--"));
	}
	
	@Test
	public void partHeaderAndContentDelimiterTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(200, post.getStatusCode());
		assertTrue("Delimiter between part header and content ", readResponse(post).contains("Content-ID: test_1\n\nTEST CONTENT 1"));
	}
	
	@Test
	public void rootIdTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:header name='content-id' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";

		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String responseHeader = post.getResponseHeader("Content-Type").getValue();
		
		assertEquals(200, post.getStatusCode());
		assertTrue("start parameter in header is not present ", responseHeader.contains("start=\"test_1\""));
	}
	
	@Test
	public void matchingBoundaryTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
						"<http:header name='Content-Type' value='text/xml'/>"+
						"<http:header name='Content-ID' value='test_2'/>"+
						"<http:body><data>TEST CONTENT 2</data></http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String responseContentType = post.getResponseHeader("Content-Type").getValue();
		
		final String boundaryRegExp = "boundary=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
    	final Pattern ptn = Pattern.compile(boundaryRegExp);
		final Matcher mtc = ptn.matcher(responseContentType);
		final String boundary;
		if(mtc.find()){
			boundary = mtc.group(1);
		}else{
			boundary = "";
		}
		
		final String[] responseLines = readResponse(post).split("\n");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("boundary for first part","--" + boundary, responseLines[0]);
		assertEquals("boundary for second part","--" + boundary, responseLines[5]);
		assertEquals("ending boundary","--" + boundary + "--", responseLines[10]);
	}
	
	@Test
	public void matchingRootIdTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='Content-Type' value='text/plain'/>"+
						"<http:header name='Content-ID' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
						"<http:header name='Content-Type' value='text/xml'/>"+
						"<http:header name='Content-ID' value='test_2'/>"+
						"<http:body><data>TEST CONTENT 2</data></http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		final String responseContentType = post.getResponseHeader("Content-Type").getValue();
		
		final String rootIdRegExp = "start=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
    	final Pattern ptn = Pattern.compile(rootIdRegExp);
		final Matcher mtc = ptn.matcher(responseContentType);
		final String rootId;
		if(mtc.find()){
			rootId = mtc.group(1);
		}else{
			rootId = "";
		}
		
		final String[] responseLines = readResponse(post).split("\n");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("first part content-id","Content-ID: " + rootId, responseLines[2]);
		assertFalse("second part content-id",("Content-ID: " + rootId).equals(responseLines[7]));
	}
	
	@Test
	public void responseHeaderTest() throws ParserConfigurationException, SAXException, HttpException, IOException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client' boundary='TEST_BOUNDARY'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:header name='content-id' value='test_1'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";

		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(200, post.getStatusCode());
		assertEquals("multipart/related; start=\"test_1\"; boundary=\"TEST_BOUNDARY\"; charset=UTF-8", post.getResponseHeader("Content-Type").getValue());
	}
	
	@Test
	public void missingBoundaryAttributeTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: Multipart boundary could not be found"));
	}
	
	@Test
	public void nonMultipartResponseNodeTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<data xmlns:http='http://expath.org/ns/http-client'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</data>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: Multipart response must have a multipart root node"));
	}
	
	@Test
	public void invalidNameSpaceTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='some_name_space'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: Invalid namespace for multipart"));
	}
	
	@Test
	public void missingPartContentTypeHeaderTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client'>"+
						"<http:header name='content-type' value='text/plain'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
						"<http:header name='content-id' value='test_2'/>"+
						"<http:body>TEST CONTENT 2</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: A multipart entity must have a Content-Type header"));
	}
	
	@Test
	public void missingPartHeaderNameAttrTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client'>"+
						"<http:header  value='text/plain'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: Header musst include name and value attributes"));
	}
	
	@Test
	public void missingPartHeaderValueAttrTest() throws HttpException, IOException, ParserConfigurationException, SAXException{
		final String request = 
				"<http:multipart xmlns:http='http://expath.org/ns/http-client'>"+
						"<http:header name='content-type'/>"+
						"<http:body>TEST CONTENT 1</http:body>"+
				"</http:multipart>";
		
		final PostMethod post = sendPostRequest(TEST_URL, request, "text/xml");
		
		assertEquals(500, post.getStatusCode());
		assertTrue(readResponse(post).contains("org.exquery.restxq.RestXqServiceException: Header musst include name and value attributes"));
	}
	
}
