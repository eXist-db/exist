/*
 Copyright (c) 2012, Adam Retter
 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright
 notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 notice, this list of conditions and the following disclaimer in the
 documentation and/or other materials provided with the distribution.
 * Neither the name of Adam Retter Consulting nor the
 names of its contributors may be used to endorse or promote products
 derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
 DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.extensions.exquery.xdm.type.impl.BinaryTypedValue;
import org.exist.extensions.exquery.xdm.type.impl.DocumentTypedValue;
import org.exist.extensions.exquery.xdm.type.impl.StringTypedValue;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.BinaryValueManager;
import org.exist.xquery.value.StringValue;
import org.exquery.http.HttpRequest;
import org.exquery.http.HttpResponse;
import org.exquery.restxq.ResourceFunction;
import org.exquery.restxq.ResourceFunctionExecuter;
import org.exquery.restxq.RestXqErrorCodes;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.RestXqServiceSerializer;
import org.exquery.restxq.impl.AbstractRestXqService;
import org.exquery.xdm.type.SequenceImpl;
import org.exquery.xquery.Sequence;
import org.exquery.xquery.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
class RestXqServiceImpl extends AbstractRestXqService {

    private final static Logger LOG = LogManager.getLogger(RestXqServiceImpl.class);

    private final BrokerPool brokerPool;
    private final BinaryValueManager binaryValueManager;

    public RestXqServiceImpl(final ResourceFunction resourceFunction, final BrokerPool brokerPool) {
        super(resourceFunction);
        this.brokerPool = brokerPool;
        this.binaryValueManager = new BinaryValueManager() {

            final List<BinaryValue> binaryValues = new ArrayList<>();

            @Override
            public void registerBinaryValueInstance(final BinaryValue binaryValue) {
                binaryValues.add(binaryValue);
            }

            @Override
            public void runCleanupTasks() {
                for (final BinaryValue binaryValue : binaryValues) {
                    try {
                        binaryValue.close();
                    } catch (final IOException ioe) {
                        LOG.error("Unable to close binary value: " + ioe.getMessage(), ioe);
                    }
                }
                binaryValues.clear();
            }

            @Override
            public String getCacheClass() {
                return (String) getBrokerPool().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
            }
        };
    }

    private BrokerPool getBrokerPool() {
        return brokerPool;
    }

    @Override
    public void service(final HttpRequest request, final HttpResponse response, final ResourceFunctionExecuter resourceFunctionExecuter, final RestXqServiceSerializer restXqServiceSerializer) throws RestXqServiceException {
        super.service(request, response, resourceFunctionExecuter, restXqServiceSerializer);
        binaryValueManager.runCleanupTasks();
    }

    @Override
    protected Sequence extractRequestBody(final HttpRequest request) throws RestXqServiceException {

        //TODO don't use close shield input stream and move parsing of form parameters from HttpServletRequestAdapter into RequestBodyParser
        InputStream is;
        FilterInputStreamCache cache = null;

        try {

            //first, get the content of the request
            is = new CloseShieldInputStream(request.getInputStream());

            if (is.available() <= 0) {
                return null;
            }

            //if marking is not supported, we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
            if (!is.markSupported()) {
                cache = FilterInputStreamCacheFactory.getCacheInstance(() -> {
                    final Configuration configuration = getBrokerPool().getConfiguration();
                    return (String) configuration.getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                }, is);

                is = new CachingFilterInputStream(cache);
            }

            is.mark(Integer.MAX_VALUE);
        } catch (final IOException ioe) {
            throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, ioe);
        }

        Sequence result = null;
        try {

            //was there any POST content?
            if (is != null && is.available() > 0) {
                String contentType = request.getContentType();
                // 1) determine if exists mime database considers this binary data
                if (contentType != null) {
                    //strip off any charset encoding info
                    if (contentType.contains(";")) {
                        contentType = contentType.substring(0, contentType.indexOf(";"));
                    }
                    MimeType mimeType = MimeTable.getInstance().getContentType(contentType);

                    if(contentType.toLowerCase().equals("multipart/related")){
                    	
                    	// multipart/related request
                    	String encoding = request.getCharacterEncoding();
                    	if (encoding == null) {
                            encoding = "UTF-8";
                        }
                    	
                    	final byte[] partsNodeAsBytes = extractBodyParts(request, is, encoding).getBytes(encoding);
                		final InputStream partsNodeAsIs = new ByteArrayInputStream(partsNodeAsBytes);
                		final DocumentImpl doc = parseAsXml(partsNodeAsIs);
                		if(doc != null){
                			result = new SequenceImpl<>(new DocumentTypedValue(doc));
                		}
                    	
                    } else if (mimeType != null && !mimeType.isXMLType()) {

                        //binary data
                        try {

                            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), is);
                            if (binaryValue != null) {
                                result = new SequenceImpl<>(new BinaryTypedValue(binaryValue));
                            }
                        } catch (final XPathException xpe) {
                            throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, xpe);
                        }
                    }
                }

                if (result == null) {
                    //2) not binary, try and parse as an XML document
                    final DocumentImpl doc = parseAsXml(is);
                    if (doc != null) {
                        result = new SequenceImpl<>(new DocumentTypedValue(doc));
                    }
                }

                if (result == null) {

                    String encoding = request.getCharacterEncoding();
                    // 3) not a valid XML document, return a string representation of the document
                    if (encoding == null) {
                        encoding = "UTF-8";
                    }

                    try {
                        //reset the stream, as we need to reuse for string parsing
                        is.reset();

                        final StringValue str = parseAsString(is, encoding);
                        if (str != null) {
                            result = new SequenceImpl<>(new StringTypedValue(str));
                        }
                    } catch (final IOException ioe) {
                        throw new RestXqServiceException(RestXqErrorCodes.RQDY0014, ioe);
                    }
                }
            }
        } catch (IOException e) {
            throw new RestXqServiceException(e.getMessage());
        } finally {

            if (cache != null) {
                try {
                    cache.invalidate();
                } catch (final IOException ioe) {
                    LOG.error(ioe.getMessage(), ioe);
                }
            }

            if (is != null) {
                /*
                 * Do NOT close the stream if its a binary value,
                 * because we will need it later for serialization
                 */
                boolean isBinaryType = false;
                if (result != null) {
                    try {
                        final Type type = result.head().getType();
                        isBinaryType = (type == Type.BASE64_BINARY || type == Type.HEX_BINARY);
                    } catch (final IndexOutOfBoundsException ioe) {
                        LOG.warn("Called head on an empty HTTP Request body sequence", ioe);
                    }
                }

                if (!isBinaryType) {
                    try {
                        is.close();
                    } catch (final IOException ioe) {
                        LOG.error(ioe.getMessage(), ioe);
                    }
                }
            }
        }

        if (result != null) {
            return result;
        } else {
            return Sequence.EMPTY_SEQUENCE;
        }
    }

    private DocumentImpl parseAsXml(final InputStream is) {

        DocumentImpl result = null;
        XMLReader reader = null;

        try {
            //try and construct xml document from input stream, we use eXist's in-memory DOM implementation

            //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
            final InputSource src = new InputSource(new CloseShieldInputStream(is));

            reader = getBrokerPool().getParserPool().borrowXMLReader();
            final MemTreeBuilder builder = new MemTreeBuilder();
            builder.startDocument();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            reader.setContentHandler(receiver);
            reader.setProperty("http://xml.org/sax/properties/lexical-handler", receiver);
            reader.parse(src);
            builder.endDocument();
            final Document doc = receiver.getDocument();

            result = (DocumentImpl) doc;
        } catch (final SAXException | IOException saxe) {
            //do nothing, we will default to trying to return a string below
        } finally {
            if (reader != null) {
                getBrokerPool().getParserPool().returnXMLReader(reader);
            }
        }

        return result;
    }

    private static StringValue parseAsString(final InputStream is, final String encoding) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int read = -1;
        while ((read = is.read(buf)) > -1) {
            bos.write(buf, 0, read);
        }
        final String s = new String(bos.toByteArray(), encoding);
        return new StringValue(s);
    }
    
    /**
     * Extract parts from multipart/related request and build XML http:multipart Node as String including body parts and headers
     * The first body element is the root part.
	 * Body content is represented as BASE64
	 * 
     * <http:multipart>
  	 * 		<http:header name="content-id" value="..." />
  	 *		<http:body media-type="...">...</http:body>
	 *		.
	 *		. 
	 * 	</http:multipart>
	 * 
     */
    private String extractBodyParts(final HttpRequest request, final InputStream is, final String encoding) throws RestXqServiceException, IOException{
    	
    	final String requestContentType = request.getContentType();
    	final String boundary = getMultiPartBoundary(requestContentType);
    	final String rootId = getRootPartId(requestContentType);
		final String content = getRequestContent(is, encoding);
		
		// build http:header and http:body for each body part within a http:mulitpart as XML String
		// @see <a href="http://expath.org/spec/http-client">HTTP Client Module</a>
		final StringBuilder xmlPartsAsStr = new StringBuilder();
		
		// compile part Content-Type pattern before going through the parts
		final Pattern contentTypePtn = Pattern.compile("(?i:Content-Type): (.*?)(;|\\s|$)");
		final Pattern contentIdPtn = Pattern.compile("(?i:Content-ID): (.*?)(;|\\s|$)");
		final Pattern contTransEncPtn = Pattern.compile("(?i:Content-Transfer-Encoding): (.*?)(;|\\s|$)");
		
		// split the content by boundary and try to extract part headers for each split.
		// Its a valid part if headers contain a content-type 
		// and the are followed by an empty line then part contents
		final String[] parts = content.split("--" + boundary);
		boolean isRootFound = false;
		for(int i = 0; i < parts.length; i++){
			
			final int seperatorIndex = parts[i].indexOf("\n\n");
			// not a valid part
			if(seperatorIndex <= 0){
				continue;
			}
			final String partHeaders = parts[i].substring(0, seperatorIndex);
			
			// find content ID
			final Matcher mtc = contentIdPtn.matcher(partHeaders);
			String partContentId = "";
			// leave content id empty if it is not present
			// @see <a href="https://tools.ietf.org/html/rfc2387#section-6.1">RFC 2387</a>
			if(mtc.find()){
				partContentId = mtc.group(1);
			}
			
			// determine whether current body part is a root part
			boolean isRoot = false;
			if(!isRootFound){
				if(rootId != null && !rootId.isEmpty()){
    				if(rootId.equals(partContentId)){
    					isRoot = true;
    					isRootFound = true;
    				}
    			}else{
    				isRoot = true;
					isRootFound = true;
    			}
			}
			
			// remove first and last <> from part content id
			if(partContentId.startsWith("<") && partContentId.endsWith(">")){
				partContentId = partContentId.substring(1, partContentId.length() - 1);
			}
			// content-id as part http:headers
			// @see <a href="http://expath.org/spec/http-client">HTTP Client Module</a>
			final String contIdAsHeaderElem = String.format("<http:header name=\"content-id\" value=\"%s\"/>", partContentId);
			
			// extract content and remove last character (line break)
			// @see <a href="https://www.w3.org/Protocols/rfc1341/7_2_Multipart.html">RFC 1341 (MIME)</a>
			String partContent = parts[i].substring(seperatorIndex + 2);
			partContent = partContent.substring(0, partContent.length() -1);
			final String contAsBodyElem = buildBodyPartAsXmlStr(contentTypePtn, contTransEncPtn, partHeaders, partContent);
			
			// if current part is a root one then insert at the begin of the list
			if(isRoot){
				xmlPartsAsStr.insert(0, contAsBodyElem);
				xmlPartsAsStr.insert(0, contIdAsHeaderElem); 
			}else{
				xmlPartsAsStr.append(contIdAsHeaderElem);
				xmlPartsAsStr.append(contAsBodyElem);
			}
		}
		
		final String httpNs = "http://expath.org/ns/http-client";
		xmlPartsAsStr.insert(0, String.format("<http:multipart xmlns:http=\"%s\">", httpNs));
		xmlPartsAsStr.append("</http:multipart>");
		
		return xmlPartsAsStr.toString();
    }
    
    /**
     * Get Multipart Boundary from request content-type header
     */
    private String getMultiPartBoundary(final String contentType) throws RestXqServiceException{
    	
    	final String boundaryRegExp = "boundary=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
    	final Pattern ptn = Pattern.compile(boundaryRegExp);
		final Matcher mtc = ptn.matcher(contentType);
		String boundary = null;
		if(mtc.find()){
			boundary = mtc.group(1);
		}
		if(boundary == null || boundary.isEmpty()){
			throw new RestXqServiceException("No multipart boundary could be found");
		}
		return boundary;
    }
    
    /**
     * Get root part ID
     * If start parameter not present in Content-Type then
     * the first body part is root part
     * @see <a href="https://tools.ietf.org/html/rfc2387#section-3.2">RFC 2387</a>
     */
    private String getRootPartId(final String contentType){
    	
    	final String rootIdRegExp = "start=['\"]{0,1}(.*?)('|\"|;|\\s|$)";
		final Pattern ptn = Pattern.compile(rootIdRegExp);
		final Matcher mtc = ptn.matcher(contentType);
		if(mtc.find()){
			return mtc.group(1);
		}else{
			return null;
		}
    }
    
    /**
     * Get request body as String from InputStream
     */
    private String getRequestContent(final InputStream is, final String encoding) throws RestXqServiceException, IOException{
    	final String content;
    	try (BufferedReader buffer = new BufferedReader(new InputStreamReader(is, encoding))) {
    		content = buffer.lines().collect(Collectors.joining("\n"));
    	}
    	if(content == null || content.isEmpty()){
    		throw new RestXqServiceException("Request content could not be readed");
    	}
    	return content;
    }
    
    /**
     * Build a http:body xml node as String including a media-type attribute 
     * 
     * @see <a href="http://expath.org/spec/http-client">HTTP Client Module</a>
     */
    private String buildBodyPartAsXmlStr(final Pattern typePtn, final Pattern transEncPtn, final String headers, String content) throws RestXqServiceException{
    	// find content type
    	final Matcher typeMtc = typePtn.matcher(headers);
		String contentType = null;
		if(typeMtc.find()){
			contentType = typeMtc.group(1);
		}
		// not a valid part if content-type is not present
		if(contentType == null || contentType.isEmpty()){
			throw new RestXqServiceException("No body part Content-Type could be found");
		}
    	
    	// find transfer encoding
    	final Matcher transEncMtc = transEncPtn.matcher(headers);
		String transferEncodeing = null;
		if(transEncMtc.find()){
			transferEncodeing = transEncMtc.group(1);
		}
		// if the transfer encoding is BASE64 then return the the content in a http:body node
		// and declare the media-type as binary
		if(transferEncodeing != null && transferEncodeing.toLowerCase().equals("base64")){
			return String.format("<http:body media-type=\"%s\">%s</http:body>", contentType, content);
		}else{
			content = Base64.getEncoder().encodeToString(content.getBytes());
			return String.format("<http:body media-type=\"%s\">%s</http:body>", contentType, content);
		}
    }
    
}
