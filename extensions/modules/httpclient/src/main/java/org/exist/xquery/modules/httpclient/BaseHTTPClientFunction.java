/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2007-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.modules.httpclient;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.util.EncodingUtil;
import org.apache.commons.io.input.CloseShieldInputStream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.xquery.*;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

/**
 * Base class for HTTP client methods
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @version 1.3
 * @serial 20100228
 */
public abstract class BaseHTTPClientFunction extends BasicFunction {

    protected static final Logger logger = LogManager.getLogger(BaseHTTPClientFunction.class);

    protected static final FunctionParameterSequenceType URI_PARAM = new FunctionParameterSequenceType("url", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URL to process");
    protected static final FunctionParameterSequenceType PUT_CONTENT_PARAM = new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.EXACTLY_ONE, "The XML PUT payload/content. If it is an XML Node it will be serialized. If it is a binary stream it pass as it, any other type will be atomized into a string.");
    protected static final FunctionParameterSequenceType POST_CONTENT_PARAM = new FunctionParameterSequenceType("content", Type.ITEM, Cardinality.EXACTLY_ONE, "The XML POST payload/content. If it is an XML Node it will be serialized, any other type will be atomized into a string.");
    protected static final FunctionParameterSequenceType POST_FORM_PARAM = new FunctionParameterSequenceType("content", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The form data in the format <httpclient:fields><httpclient:field name=\"\" value=\"\" type=\"string|file\"/>...</httpclient:fields>.  If the field values will be suitably URLEncoded and sent with the mime type application/x-www-form-urlencoded.");
    protected static final FunctionParameterSequenceType PERSIST_PARAM = new FunctionParameterSequenceType("persist", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Indicates if the HTTP state (eg. cookies, credentials, etc.) should persist for the life of this xquery");
    protected static final FunctionParameterSequenceType REQUEST_HEADER_PARAM = new FunctionParameterSequenceType("request-headers", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Any HTTP Request Headers to set in the form  <headers><header name=\"\" value=\"\"/></headers>");
    protected static final FunctionParameterSequenceType OPTIONS_PARAM = new FunctionParameterSequenceType("parser-options", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Feature and Property options to be passed to the HTML/XML parser in the form <options><feature name=\"\" value=\"{true|false}\"/><property name=\"\" value=\"\"/></options>");
    protected static final FunctionParameterSequenceType INDENTATION_PARAM = new FunctionParameterSequenceType("indentation", Type.INTEGER, Cardinality.EXACTLY_ONE, "Indentation level.  If this parameter is added, then the XML being put will be serailazed with indentation and the number is the number of characters for each level of indentation.  If this parameter is not include, then the XML is serialized to one line of text.");

    protected static final FunctionReturnSequenceType XML_BODY_RETURN = new FunctionReturnSequenceType(Type.ITEM, Cardinality.EXACTLY_ONE, "the XML body content");

    final static String NAMESPACE_URI = HTTPClientModule.NAMESPACE_URI;
    final static String PREFIX = HTTPClientModule.PREFIX;
    final static String HTTP_MODULE_PERSISTENT_STATE = HTTPClientModule.HTTP_MODULE_PERSISTENT_STATE;
    final static String HTTP_MODULE_PERSISTENT_OPTIONS = HTTPClientModule.HTTP_MODULE_PERSISTENT_OPTIONS;

    final static String HTTP_EXCEPTION_STATUS_CODE = "500";

    public BaseHTTPClientFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    /**
     * Parses header parameters and sets them on the Request.
     *
     * @param method The Http Method to set the request headers on
     * @param headers The headers node e.g.
     * <headers><header name="Content-Type" value="application/xml"/></headers>
     *
     * @throws XPathException
     */
    protected void setHeaders(final HttpMethod method, final Node headers) throws XPathException {
        if ((headers.getNodeType() == Node.ELEMENT_NODE) && headers.getLocalName().equals("headers")) {
            final NodeList headerList = headers.getChildNodes();

            for (int i = 0; i < headerList.getLength(); i++) {
                final Node header = headerList.item(i);

                if ((header.getNodeType() == Node.ELEMENT_NODE) && header.getLocalName().equals("header")) {
                    final String name = ((Element) header).getAttribute("name");
                    final String value = ((Element) header).getAttribute("value");

                    if (name == null || value == null) {
                        throw new XPathException(this, "Name or value attribute missing for request header parameter");
                    }

                    method.addRequestHeader(new Header(name, value));
                }
            }
        }
    }

    /**
     * Performs a HTTP Request.
     *
     * @param context The context of the calling XQuery
     * @param method The HTTP method for the request
     * @param persistState If true existing HTTP state (cookies, credentials,
     * etc) are re-used and athe state is persisted for future HTTP Requests
     * @param parserFeatures Map of NekoHtml parser features to be used for the
     * HTML parser. If null, the session-wide options will be used.
     * @param parserProperties Map of NekoHtml parser properties to be used for
     * the HTML parser. If null, the session-wide options will be used.
     *
     * @return DOCUMENT ME!
     *
     * @throws IOException
     * @throws XPathException
     */
    protected Sequence doRequest(final XQueryContext context, final HttpMethod method, boolean persistState, Map<String, Boolean> parserFeatures, Map<String, String> parserProperties) throws IOException, XPathException {

        Sequence encodedResponse = null;

        final HttpClient http = HTTPClientModule.httpClient;

        FeaturesAndProperties defaultFeaturesAndProperties = (FeaturesAndProperties) context.getXQueryContextVar(HTTP_MODULE_PERSISTENT_OPTIONS);
        if (defaultFeaturesAndProperties != null) {
            if (parserFeatures == null) {
                parserFeatures = defaultFeaturesAndProperties.getFeatures();
            }
            if (parserProperties == null) {
                parserProperties = defaultFeaturesAndProperties.getProperties();
            }
        }

        //execute the request
        try {

            //use existing state?
            if (persistState) {
                //get existing state
                final HttpState state = (HttpState) context.getXQueryContextVar(HTTP_MODULE_PERSISTENT_STATE);
                if (state != null) {
                    http.setState(state);
                }
            }

            //perform the request
            final int statusCode = http.executeMethod(method);

            encodedResponse = encodeResponseAsXML(context, method, statusCode, parserFeatures, parserProperties);

            //persist state?
            if (persistState) {
                context.setXQueryContextVar(HTTP_MODULE_PERSISTENT_STATE, http.getState());
            }
        } catch (final Exception e) {
            LOG.error(e.getMessage(), e);
            encodedResponse = encodeErrorResponse(context, e.getMessage());
        }

        return encodedResponse;
    }

    /**
     * Takes the HTTP Response and encodes it as an XML structure.
     *
     * @param context The context of the calling XQuery
     * @param method The HTTP Request Method
     * @param statusCode The status code returned from the http method
     * invocation
     *
     * @return The data in XML format
     *
     * @throws XPathException
     * @throws IOException
     */
    private Sequence encodeResponseAsXML(final XQueryContext context, final HttpMethod method, final int statusCode, final Map<String, Boolean> parserFeatures, final Map<String, String> parserProperties) throws XPathException, IOException {

        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(new QName("response", NAMESPACE_URI, PREFIX), null);
        builder.addAttribute(new QName("statusCode", null, null), String.valueOf(statusCode));

        //Add all the response headers
        builder.startElement(new QName("headers", NAMESPACE_URI, PREFIX), null);

        final NameValuePair[] headers = method.getResponseHeaders();

        for (final NameValuePair header : headers) {
            builder.startElement(new QName("header", NAMESPACE_URI, PREFIX), null);
            builder.addAttribute(new QName("name", null, null), header.getName());
            builder.addAttribute(new QName("value", null, null), header.getValue());
            builder.endElement();
        }

        builder.endElement();

        if (!(method instanceof HeadMethod || method instanceof OptionsMethod)) { // Head and Options methods never have any response body

            // Add the response body node
            builder.startElement(new QName("body", NAMESPACE_URI, PREFIX), null);

            insertResponseBody(context, method, builder, parserFeatures, parserProperties);

            builder.endElement();
        }

        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

    /**
     * Takes an exception message and encodes it as an XML response structure.
     *
     * @param context The context of the calling XQuery
     * @param message The exception error message
     *
     * @return The response in XML format
     *
     * @throws IOException
     * @throws XPathException
     */
    private Sequence encodeErrorResponse(final XQueryContext context, final String message) throws IOException, XPathException {

        final MemTreeBuilder builder = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement(new QName("response", NAMESPACE_URI, PREFIX), null);
        builder.addAttribute(new QName("statusCode", null, null), HTTP_EXCEPTION_STATUS_CODE);

        builder.startElement(new QName("body", NAMESPACE_URI, PREFIX), null);

        builder.addAttribute(new QName("type", null, null), "text");
        builder.addAttribute(new QName("encoding", null, null), "URLEncoded");

        if (message != null) {
            builder.characters(URLEncoder.encode(message, "UTF-8"));
        }

        builder.endElement();

        builder.endElement();

        return (NodeValue) builder.getDocument().getDocumentElement();
    }

    /**
     * Takes the HTTP Response Body from the HTTP Method and attempts to insert
     * it into the response tree we are building.
     *
     * <p>
     * Conversion Preference - 1) Try and parse as XML, if successful returns a
     * Node 2) Try and parse as HTML returning as XML compatible HTML, if
     * successful returns a Node 3) Return as base64Binary encoded data</p>
     *
     * @param context The context of the calling XQuery
     * @param method The HTTP Request Method
     * @param builder The MemTreeBuilder that is being used
     *
     * @throws IOException
     * @throws XPathException
     */
    private void insertResponseBody(final XQueryContext context, final HttpMethod method, final MemTreeBuilder builder, final Map<String, Boolean> parserFeatures, final Map<String, String> parserProperties) throws IOException, XPathException {
        NodeImpl responseNode = null;

        final InputStream bodyAsStream = method.getResponseBodyAsStream();

        // check if there is a response body
        if (bodyAsStream != null) {

            CachingFilterInputStream cfis = null;
            FilterInputStreamCache cache = null;
            try {

                //we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
                cache = FilterInputStreamCacheFactory.getCacheInstance(() -> (String) context.getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY), bodyAsStream);

                cfis = new CachingFilterInputStream(cache);

                //mark the start of the stream
                cfis.mark(Integer.MAX_VALUE);

                // determine the type of the response document
                final Header responseContentType = method.getResponseHeader("Content-Type");

                final MimeType responseMimeType = getResponseMimeType(responseContentType);
                if (responseContentType != null) {
                    builder.addAttribute(new QName("mimetype", null, null), responseContentType.getValue());
                }

                //try and parse the response as XML
                try {
                    //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
                    final InputStream shieldedInputStream = new CloseShieldInputStream(cfis);
                    responseNode = (NodeImpl) ModuleUtils.streamToXML(context, shieldedInputStream);
                    builder.addAttribute(new QName("type", null, null), "xml");
                    responseNode.copyTo(null, new DocumentBuilderReceiver(builder));
                } catch (final SAXException se) {
                    // could not parse to xml
                    // not an error in itself, it will be treated either as HTML,
                    // text or binary here below
                    final String msg = "Request for URI '"
                            + method.getURI().toString()
                            + "' Could not parse http response content as XML (will try html, text or fallback to binary): "
                            + se.getMessage();
                    if (logger.isDebugEnabled()) {
                        logger.debug(msg, se);
                    } else {
                        logger.info(msg);
                    }
                } catch (final IOException ioe) {
                    final String msg = "Request for URI '" + method.getURI().toString() + "' Could not read http response content: " + ioe.getMessage();
                    logger.error(msg, ioe);
                    throw new XPathException(msg, ioe);
                }

                if (responseNode == null) {
                    //response is NOT parseable as XML

                    //is it a html document?
                    if (responseMimeType.getName().equals(MimeType.HTML_TYPE.getName())) {

                        //html document
                        try {

                            //reset the stream to the start, as we need to reuse since attempting to parse to XML
                            cfis.reset();

                            //parse html to xml(html)
                            //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
                            final InputStream shieldedInputStream = new CloseShieldInputStream(cfis);

                            responseNode = (NodeImpl) ModuleUtils.htmlToXHtml(context, new InputSource(shieldedInputStream), parserFeatures, parserProperties).getDocumentElement();
                            builder.addAttribute(new QName("type", null, null), "xhtml");
                            responseNode.copyTo(null, new DocumentBuilderReceiver(builder));
                        } catch (final URIException ue) {
                            throw new XPathException(this, ue.getMessage(), ue);
                        } catch (final SAXException se) {
                            //could not parse to xml(html)
                            logger.debug("Could not parse http response content from HTML to XML: " + se.getMessage(), se);
                        }
                    }
                }

                if (responseNode == null) {

                    //reset the stream to the start, as we need to reuse since attempting to parse to HTML->XML
                    cfis.reset();

                    if (responseMimeType.getName().startsWith("text/")) {

                        // Assume it's a text body and URL encode it
                        builder.addAttribute(new QName("type", null, null), "text");
                        builder.addAttribute(new QName("encoding", null, null), "URLEncoded");

                        try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                            baos.write(cfis);
                            builder.characters(URLEncoder.encode(EncodingUtil.getString(baos.toByteArray(), ((HttpMethodBase) method).getResponseCharSet()), "UTF-8"));
                        }
                    } else {

                        // Assume it's a binary body and Base64 encode it
                        builder.addAttribute(new QName("type", null, null), "binary");
                        builder.addAttribute(new QName("encoding", null, null), "Base64Encoded");

                        BinaryValue binary = null;
                        try {
                            binary = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), cfis);
                            builder.characters(binary.getStringValue());
                        } finally {
                            // free resources
                            if (binary != null) {
                                binary.destroy(context, null);
                            }
                        }
                    }
                }
            } finally {
                if (cache != null) {
                    try {
                        cache.invalidate();
                    } catch (final IOException ioe) {
                        LOG.error(ioe.getMessage(), ioe);
                    }
                }

                if (cfis != null) {
                    try {
                        cfis.close();
                    } catch (final IOException ioe) {
                        LOG.error(ioe.getMessage(), ioe);
                    }
                }
            }
        }
    }

    /**
     * Given the Response Header for Content-Type this function returns an
     * appropriate eXist MimeType.
     *
     * @param responseHeaderContentType The HTTP Response Header containing the
     * Content-Type of the Response.
     *
     * @return The corresponding eXist MimeType
     */
    protected MimeType getResponseMimeType(final Header responseHeaderContentType) {
        MimeType returnMimeType = MimeType.BINARY_TYPE;

        if (responseHeaderContentType != null) {

            if (responseHeaderContentType.getName().equals("Content-Type")) {

                final String responseContentType = responseHeaderContentType.getValue();
                int contentTypeEnd = responseContentType.indexOf(";");
                if (contentTypeEnd == -1) {
                    contentTypeEnd = responseContentType.length();
                }

                final String responseMimeType = responseContentType.substring(0, contentTypeEnd);
                final MimeTable mimeTable = MimeTable.getInstance();
                final MimeType mimeType = mimeTable.getContentType(responseMimeType);

                if (mimeType != null) {
                    returnMimeType = mimeType;
                }
            }
        }

        return returnMimeType;
    }

    protected static class FeaturesAndProperties {
        private final Map<String, Boolean> features;
        private final Map<String, String> properties;

        public FeaturesAndProperties(final Map<String, Boolean> features, final Map<String, String> properties) {
            this.features = features;
            this.properties = properties;
        }

        public Map<String, Boolean> getFeatures() {
            return features;
        }

        public Map<String, String> getProperties() {
            return properties;
        }
    }

    protected FeaturesAndProperties getParserFeaturesAndProperties(final Node options) throws XPathException {

        final Map<String, Boolean> features = new HashMap<>();
        final Map<String, String> properties = new HashMap<>();

        if ((options.getNodeType() == Node.ELEMENT_NODE) && options.getLocalName().equals("options")) {
            NodeList optionList = options.getChildNodes();

            for (int i = 0; i < optionList.getLength(); i++) {
                final Node option = optionList.item(i);

                if ((option.getNodeType() == Node.ELEMENT_NODE)) {
                    final String name = ((Element) option).getAttribute("name");
                    final String value = ((Element) option).getAttribute("value");

                    if ((name == null) || (value == null)) {
                        throw (new XPathException(this, "Name or value attribute missing for parser feature/property"));
                    }

                    if (option.getLocalName().equals("feature")) {
                        if (value.matches("(true|false)")) {
                            features.put(name, Boolean.parseBoolean(value));
                        } else {
                            throw (new XPathException(this, "Feature value must be true or false"));
                        }
                    } else if (option.getLocalName().equals("property")) {
                        properties.put(name, value);
                    }
                }
            }
        }

        return new FeaturesAndProperties(features, properties);
    }
}
