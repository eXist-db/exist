package org.exist.extensions.exquery.restxq.impl.multipart;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.methods.PostMethod;
import org.exist.util.Base64Encoder;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Tests multipart/related requests in restXQ.
 * 
 * Send a multipart/related request to an restXQ endpoint, with post header in the following form:
 * 
 *  Content-Type: "multipart/related; start='ROOT_PART_CONTENT_ID'; boundary='TEST_BOUNDARY'"
 * 
 * and post request:
 * 	--TEST_BOUNDARY
 *	Content-Type: text/plain
 *	Content-Transfer-Encoding: 8bit
 *	Content-ID: test1
 *
 *	TEST CONTENT 1
 *	--TEST_BOUNDARY
 *	Content-Type: text/xml
 *	Content-Transfer-Encoding: 8bit
 *	 Content-ID: test2
 *
 *	TEST CONTENT 2
 *	--TEST_BOUNDARY
 *	Content-Type: text/plain
 *	Content-Transfer-Encoding: base46
 *	Content-ID: test3
 *
 *	VEVTVCBDT05URU5UIDM=
 *	--TEST_BOUNDARY--
 *
 * If start parameter is not setted then the first part is a root part by default. 
 * The expected results is a http:multipart element with a http:header for each http:body childs elements:
 *
 *	<http:multipart xmlns:http="http://expath.org/ns/http-client">
 *    	<http:header name="content-id" value="test1"/>
 *    	<http:body media-type="text/plain">VEVTVCBDT05URU5UIDE=</http:body>
 *    	<http:header name="content-id" value="test2"/>
 *    	<http:body media-type="text/xml">PGRhdGE+VEVTVCBDT05URU5UIDI8L2RhdGE+</http:body>
 *    	<http:header name="content-id" value="test3"/>
 *    	<http:body media-type="text/plain">VEVTVCBDT05URU5UIDM=</http:body>
 *	</http:multipart>
 *
 * The first body element is the root part.
 * If part header "Content-Transfer-Encoding" is setted to base64 then the part content will not be encoded,
 * otherwise the content will be encoded to base64
 * 
 * 
 * @author Maan Al Balkhi <maan.al.balkhi@gmail.com>
 * 
 */
public class MultipartRelatedRequestTest extends MultipartTest {
	
	private static final String TEST_URL = REQUEST_URL + "/requestTest";
	
    @Test
    public void multipartXMlTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			"<data>TEST CONTENT 2</data>\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		assertEquals("http:multipart", multipartNode.getNodeName());
		assertEquals("http://expath.org/ns/http-client", multipartNode.getNamespaceURI());
		
		final NodeList childNodes = multipartNode.getChildNodes();
		
		assertEquals(4, childNodes.getLength());
		assertEquals("http:header", childNodes.item(0).getNodeName());
		assertEquals("http:body", childNodes.item(1).getNodeName());
		assertEquals("http:header", childNodes.item(2).getNodeName());
		assertEquals("http:body", childNodes.item(3).getNodeName());
    }
    
    @Test
    public void partsHeadersNameAttributeTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			"<data>TEST CONTENT 2</data>\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node firstHeader = multipartNode.getChildNodes().item(0);
		assertEquals(2, firstHeader.getAttributes().getLength());
		assertEquals("name", firstHeader.getAttributes().item(0).getNodeName());
		assertEquals("content-id", firstHeader.getAttributes().item(0).getNodeValue());
		
		final Node secondHeader = multipartNode.getChildNodes().item(2);
		assertEquals(2, secondHeader.getAttributes().getLength());
		assertEquals("name", secondHeader.getAttributes().item(0).getNodeName());
		assertEquals("content-id", secondHeader.getAttributes().item(0).getNodeValue());
    }
    
    @Test
    public void partsHeadersValueAttributeTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			"<data>TEST CONTENT 2</data>\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node firstHeader = multipartNode.getChildNodes().item(0);
		assertEquals(2, firstHeader.getAttributes().getLength());
		assertEquals("value", firstHeader.getAttributes().item(1).getNodeName());
		assertEquals("test1", firstHeader.getAttributes().item(1).getNodeValue());
		
		final Node secondHeader = multipartNode.getChildNodes().item(2);
		assertEquals(2, secondHeader.getAttributes().getLength());
		assertEquals("value", secondHeader.getAttributes().item(1).getNodeName());
		assertEquals("test2", secondHeader.getAttributes().item(1).getNodeValue());
    }
    
    @Test
    public void partsBodiesAttributeTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			"<data>TEST CONTENT 2</data>\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node firstBody = multipartNode.getChildNodes().item(1);
		assertEquals(1, firstBody.getAttributes().getLength());
		assertEquals("media-type", firstBody.getAttributes().item(0).getNodeName());
		
		final Node secondBody = multipartNode.getChildNodes().item(3);
		assertEquals(1, secondBody.getAttributes().getLength());
		assertEquals("media-type", secondBody.getAttributes().item(0).getNodeName());
    }
    
    @Test
    public void partsBodiesMediaTypeTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			"<data>TEST CONTENT 2</data>\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: application/pdf\n"+
    			"Content-Transfer-Encoding: base64\n"+
    			"Content-ID: test3\n"+
    			"\n"+
    			"VEVTVCBDT05URU5UIDM=\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node firstBody = multipartNode.getChildNodes().item(1);
		assertEquals("text/plain", firstBody.getAttributes().item(0).getNodeValue());
		
		final Node secondBody = multipartNode.getChildNodes().item(3);
		assertEquals("text/xml", secondBody.getAttributes().item(0).getNodeValue());
		
		final Node thirdBody = multipartNode.getChildNodes().item(5);
		assertEquals("application/pdf", thirdBody.getAttributes().item(0).getNodeValue());
    }
    
    @Test
    public void partsBodiesContentTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	
    	final String content1 = "TEST CONTENT 1";
    	final String content2 = "<data>TEST CONTENT 2</data>";
    	final String content3 = "VEVTVCBDT05URU5UIDM=";
    	
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			content1 + "\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/xml\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test2\n"+
    			"\n"+
    			content2 + "\n"+
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: base64\n"+
    			"Content-ID: test3\n"+
    			"\n"+
    			content3 + "\n"+
    			"--TEST_BOUNDARY--";	  		   
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node firstBody = multipartNode.getChildNodes().item(1);
		final Base64Encoder enc = new Base64Encoder();
        enc.translate(content1.getBytes());
		assertEquals(new String(enc.getCharArray()), firstBody.getTextContent());
		
		final Node secondBody = multipartNode.getChildNodes().item(3);
		enc.reset();
		enc.translate(content2.getBytes());
		assertEquals(new String(enc.getCharArray()), secondBody.getTextContent());
		
		// if Content-Transfer-Encoding is base64 then it will be not encoded
		final Node thirdBody = multipartNode.getChildNodes().item(5);
		assertEquals(content3, thirdBody.getTextContent());
    }
    
    @Test
    public void rootPartTest() throws IOException, ParserConfigurationException, SAXException{
    	final String rootContentId = "test2";
    	
    	final String contentTypeHeader = "multipart/related; start='" + rootContentId + "'; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
				"Content-Transfer-Encoding: 8bit\n"+
				"Content-ID: test1\n"+
				"\n"+
				"TEST CONTENT 1\n"+
				"--TEST_BOUNDARY\n"+
				"Content-Type: text/xml\n"+
				"Content-Transfer-Encoding: 8bit\n"+
				"Content-ID: test2\n"+
				"\n"+
				"<data>TEST CONTENT 2</data>\n"+
				"--TEST_BOUNDARY\n"+
				"Content-Type: application/pdf\n"+
				"Content-Transfer-Encoding: base64\n"+
				"Content-ID: test3\n"+
				"\n"+
				"VEVTVCBDT05URU5UIDM=\n"+
				"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	final Node multipartNode = getMultipartFromPostResponse(post);
    	assertEquals(200, post.getStatusCode());
    	
		final Node header = multipartNode.getChildNodes().item(0);
		assertEquals(rootContentId, header.getAttributes().item(1).getNodeValue());
		
		final Node body = multipartNode.getChildNodes().item(1);
		final Base64Encoder enc = new Base64Encoder();
        enc.translate("<data>TEST CONTENT 2</data>".getBytes());
		
		assertEquals("text/xml", body.getAttributes().item(0).getNodeValue());
		assertEquals(new String(enc.getCharArray()), body.getTextContent());
    }
    
    @Test
    public void missingPartContentTypeHeaderExceptionTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related; boundary='TEST_BOUNDARY'";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	
    	assertEquals(500, post.getStatusCode());
    	assertTrue(readResponse(post).contains("Caused by: javax.servlet.ServletException: No body part Content-Type could be found"));
    }
    
    @Test
    public void missingBoundaryParamInContentTypeHeaderExceptionTest() throws IOException, ParserConfigurationException, SAXException{
    	final String contentTypeHeader = "multipart/related";
    	final String request = 
    			"--TEST_BOUNDARY\n"+
    			"Content-Type: text/plain\n"+
    			"Content-Transfer-Encoding: 8bit\n"+
    			"Content-ID: test1\n"+
    			"\n"+
    			"TEST CONTENT 1\n"+
    			"--TEST_BOUNDARY--";
    	
    	final PostMethod post = sendPostRequest(TEST_URL, request, contentTypeHeader);
    	assertEquals(500, post.getStatusCode());
    	assertTrue(readResponse(post).contains("javax.servlet.ServletException: No multipart boundary could be found"));
    }
    
    private Node getMultipartFromPostResponse(final PostMethod post) throws IOException, ParserConfigurationException, SAXException{
    	final String response = readResponse(post);
		final Document responseDoc = parseToXML(response);
		
		final Node multipartNode = responseDoc.getFirstChild();
		
		// filter extra text nodes from whitespace between the child elements
		final NodeList childNodes = multipartNode.getChildNodes();
		for(int i = 0; i < childNodes.getLength() ; i++){
			if(childNodes.item(i).getNodeType() == Node.TEXT_NODE){
				multipartNode.removeChild(childNodes.item(i));
			}
		}
		
		return multipartNode;
    }
    
    private Document parseToXML(final String xmlStr) throws ParserConfigurationException, SAXException, IOException{
    	final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		factory.setNamespaceAware(true);
		final DocumentBuilder builder = factory.newDocumentBuilder();
		return builder.parse(new ByteArrayInputStream(xmlStr.getBytes()));
    }
}
