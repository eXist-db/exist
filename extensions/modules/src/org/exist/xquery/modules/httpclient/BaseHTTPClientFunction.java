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

import org.apache.commons.httpclient.Header;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpState;
import org.apache.commons.httpclient.NameValuePair;
import org.apache.commons.httpclient.ProxyHost;
import org.apache.commons.httpclient.URIException;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.apache.commons.httpclient.util.EncodingUtil;

import org.apache.log4j.Logger;

import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import org.exist.dom.QName;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.httpclient.Credentials;
import org.apache.commons.httpclient.NTCredentials;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;


/**
 * Base class for HTTP client methods
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.3
 * @serial   20100228
 */

public abstract class BaseHTTPClientFunction extends BasicFunction
{
    protected static final Logger                        logger                         = Logger.getLogger( BaseHTTPClientFunction.class );
	
    protected static final FunctionParameterSequenceType URI_PARAM                      = new FunctionParameterSequenceType( "url", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URL to process" );
    protected static final FunctionParameterSequenceType PUT_CONTENT_PARAM              = new FunctionParameterSequenceType( "content", Type.ITEM, Cardinality.EXACTLY_ONE, "The XML PUT payload/content. If it is an XML Node it will be serialized. If it is a binary stream it pass as it, any other type will be atomized into a string." );
    protected static final FunctionParameterSequenceType POST_CONTENT_PARAM             = new FunctionParameterSequenceType( "content", Type.ITEM, Cardinality.EXACTLY_ONE, "The XML POST payload/content. If it is an XML Node it will be serialized, any other type will be atomized into a string." );
    protected static final FunctionParameterSequenceType POST_FORM_PARAM                = new FunctionParameterSequenceType( "content", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The form data in the format <httpclient:fields><httpclient:field name=\"\" value=\"\" type=\"string|file\"/>...</httpclient:fields>.  If the field values will be suitably URLEncoded and sent with the mime type application/x-www-form-urlencoded." );
    protected static final FunctionParameterSequenceType PERSIST_PARAM                  = new FunctionParameterSequenceType( "persist", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "Indicates if the HTTP state (eg. cookies, credentials, etc.) should persist for the life of this xquery" );
    protected static final FunctionParameterSequenceType REQUEST_HEADER_PARAM           = new FunctionParameterSequenceType( "request-headers", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Any HTTP Request Headers to set in the form  <headers><header name=\"\" value=\"\"/></headers>" );
    protected static final FunctionParameterSequenceType OPTIONS_PARAM                  = new FunctionParameterSequenceType( "parser-options", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Feature and Property options to be passed to the HTML/XML parser in the form <options><feature name=\"\" value=\"{true|false}\"/><property name=\"\" value=\"\"/></options>" );
    protected static final FunctionParameterSequenceType INDENTATION_PARAM              = new FunctionParameterSequenceType("indentation", Type.INTEGER, Cardinality.EXACTLY_ONE, "Indentation level.  If this parameter is added, then the XML being put will be serailazed with indentation and the number is the number of characters for each level of indentation.  If this parameter is not include, then the XML is serialized to one line of text.");

    protected static final FunctionReturnSequenceType    XML_BODY_RETURN                = new FunctionReturnSequenceType( Type.ITEM, Cardinality.EXACTLY_ONE, "the XML body content" );

    final static String                                  NAMESPACE_URI                  = HTTPClientModule.NAMESPACE_URI;
    final static String                                  PREFIX                         = HTTPClientModule.PREFIX;
    final static String                                  HTTP_MODULE_PERSISTENT_STATE 	= HTTPClientModule.HTTP_MODULE_PERSISTENT_STATE;

    final static String                                  HTTP_EXCEPTION_STATUS_CODE     = "500";

	
    public BaseHTTPClientFunction( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );
    }

	
    /**
     * Parses header parameters and sets them on the Request.
     *
     * @param   method   The Http Method to set the request headers on
     * @param   headers  The headers node e.g. <headers><header name="Content-Type" value="application/xml"/></headers>
     *
     * @throws  XPathException 
     */
    protected void setHeaders( HttpMethod method, Node headers ) throws XPathException
    {
        if( ( headers.getNodeType() == Node.ELEMENT_NODE ) && headers.getLocalName().equals( "headers" ) ) {
            NodeList headerList = headers.getChildNodes();

            for( int i = 0; i < headerList.getLength(); i++ ) {
                Node header = headerList.item( i );

                if( ( header.getNodeType() == Node.ELEMENT_NODE ) && header.getLocalName().equals( "header" ) ) {
                    String name  = ( ( Element )header ).getAttribute( "name" );
                    String value = ( ( Element )header ).getAttribute( "value" );

                    if( ( name == null ) || ( value == null ) ) {
                        throw( new XPathException( this, "Name or value attribute missing for request header parameter" ) );
                    }

                    method.addRequestHeader( new Header( name, value ) );
                }
            }
        }
    }


    /**
     * Performs a HTTP Request.
     *
     * @param   context         The context of the calling XQuery
     * @param   method          The HTTP method for the request
     * @param   persistState  	If true existing HTTP state (cookies, credentials, etc) are re-used and athe state is persisted for future HTTP Requests
     *
     * @return  DOCUMENT ME!
     *
     * @throws  IOException     
     * @throws  XPathException  
     */
    protected Sequence doRequest( XQueryContext context, HttpMethod method, boolean persistState, Map<String, Boolean> parserFeatures, Map<String, String> parserProperties) throws IOException, XPathException
    {
        int      statusCode      = 0;
        Sequence encodedResponse = null;

        HttpClient http = new HttpClient();

		//execute the request
		
        try {
			 //use existing state?
	        if( persistState ) {
	
	            //get existing state
	           HttpState state = (HttpState)context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_STATE );
	
	            if( state != null ) {
					http.setState( state );
	            }
	        }
			
            String configFile = System.getProperty("http.configfile");
            if (configFile != null) {

                if (logger.isDebugEnabled()) {
                    logger.debug("http.configfile='" + configFile + "'");
                }

                Properties props = new Properties();       
                try {
                    File propsFile = new File(configFile);
                    
                    if (logger.isDebugEnabled()) {
                        logger.debug("Loading proxy settings from " + propsFile.getAbsolutePath());
                    }
                    props.load(new FileInputStream(propsFile));
                    
                } catch (IOException ex) {
                    logger.error("Failed to read proxy configuration from '" + configFile + "'");
                }

                // Hostname / port
                String proxyHost = props.getProperty("proxy.host");
                int proxyPort = Integer.valueOf(props.getProperty("proxy.port", "8080"));

                // Username / password
                String proxyUser = props.getProperty("proxy.user");
                String proxyPassword = props.getProperty("proxy.password");

                // NTLM specifics
                String proxyDomain = props.getProperty("proxy.ntlm.domain");
                if ("NONE".equalsIgnoreCase(proxyDomain)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Forcing removal NTLM");
                    }
                    proxyDomain = null;
                }

                // Set scope       
                AuthScope authScope = new AuthScope(proxyHost, proxyPort);

                // Setup right credentials
                Credentials credentials = null;
                if (proxyDomain == null) {
                    credentials = new UsernamePasswordCredentials(proxyUser, proxyPassword);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Using NTLM authentication for '" + proxyDomain + "'");
                    }
                    credentials = new NTCredentials(proxyUser, proxyPassword, proxyHost, proxyDomain);
                }

                // Set details
                HttpState state = http.getState();
                http.getHostConfiguration().setProxy(proxyHost, proxyPort);
                state.setProxyCredentials(authScope, credentials);

                if (logger.isDebugEnabled()) {
                    logger.info("Set proxy: " + proxyUser + "@" + proxyHost + ":" 
                            + proxyPort + (proxyDomain == null ? "" : " (NTLM:'" 
                            + proxyDomain + "')"));
                }
            }
            
            // Legacy: set the proxy server (if any)
            String proxyHost = System.getProperty( "http.proxyHost" );
            if( proxyHost != null ) {
                //TODO: support for http.nonProxyHosts e.g. -Dhttp.nonProxyHosts="*.devonline.gov.uk|*.devon.gov.uk"

                ProxyHost proxy = new ProxyHost( proxyHost, Integer.parseInt( System.getProperty( "http.proxyPort" ) ) );
                http.getHostConfiguration().setProxyHost( proxy );
            } 

            //perform the request
            statusCode      = http.executeMethod( method );

            encodedResponse = encodeResponseAsXML( context, method, statusCode, parserFeatures, parserProperties );

            //persist state?
            if( persistState ) {
                context.setXQueryContextVar( HTTP_MODULE_PERSISTENT_STATE, http.getState() );
            }
        }
        catch( Exception e ) {
            LOG.error(e.getMessage(), e);
            encodedResponse = encodeErrorResponse( context, e.getMessage() );
        }

        return( encodedResponse );
    }


    /**
     * Takes the HTTP Response and encodes it as an XML structure.
     *
     * @param   context     The context of the calling XQuery
     * @param   method      The HTTP Request Method
     * @param   statusCode  The status code returned from the http method invocation
     *
     * @return  The data in XML format
     *
     * @throws  XPathException 
     * @throws  IOException     
     */
    private Sequence encodeResponseAsXML( XQueryContext context, HttpMethod method, int statusCode, Map<String, Boolean> parserFeatures, Map<String, String> parserProperties) throws XPathException, IOException
    {
        Sequence       xmlResponse = null;

        MemTreeBuilder builder     = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "response", NAMESPACE_URI, PREFIX ), null );
        builder.addAttribute( new QName( "statusCode", null, null ), String.valueOf( statusCode ) );

        //Add all the response headers
        builder.startElement( new QName( "headers", NAMESPACE_URI, PREFIX ), null );

        NameValuePair[] headers = method.getResponseHeaders();

        for( int i = 0; i < headers.length; i++ ) {
            builder.startElement( new QName( "header", NAMESPACE_URI, PREFIX ), null );
            builder.addAttribute( new QName( "name", null, null ), headers[i].getName() );
            builder.addAttribute( new QName( "value", null, null ), headers[i].getValue() );
            builder.endElement();
        }

        builder.endElement();

        if( !( ( method instanceof HeadMethod ) || ( method instanceof OptionsMethod ) ) ) { // Head and Options methods never have any response body

            // Add the response body node
            builder.startElement( new QName( "body", NAMESPACE_URI, PREFIX ), null );

            insertResponseBody( context, method, builder, parserFeatures, parserProperties);

            builder.endElement();
        }

        builder.endElement();

        xmlResponse = ( NodeValue )builder.getDocument().getDocumentElement();

        return( xmlResponse );
    }


    /**
     * Takes an exception message and encodes it as an XML response structure.
     *
     * @param   context  The context of the calling XQuery
     * @param   message  The exception error message
     *
     * @return  The response in XML format
     *
     * @throws  IOException     
     * @throws  XPathException 
     */
    private Sequence encodeErrorResponse( XQueryContext context, String message ) throws IOException, XPathException
    {
        Sequence       xmlResponse = null;

        MemTreeBuilder builder     = context.getDocumentBuilder();

        builder.startDocument();
        builder.startElement( new QName( "response", NAMESPACE_URI, PREFIX ), null );
        builder.addAttribute( new QName( "statusCode", null, null ), HTTP_EXCEPTION_STATUS_CODE );

        builder.startElement( new QName( "body", NAMESPACE_URI, PREFIX ), null );

        builder.addAttribute( new QName( "type", null, null ), "text" );
        builder.addAttribute( new QName( "encoding", null, null ), "URLEncoded" );

        if( message != null ) {
            builder.characters( URLEncoder.encode( message, "UTF-8" ) );
        }

        builder.endElement();

        builder.endElement();

        xmlResponse = ( NodeValue )builder.getDocument().getDocumentElement();

        return( xmlResponse );
    }


    /**
     * Takes the HTTP Response Body from the HTTP Method and attempts to insert it into the response tree we are building.
     *
     * <p>Conversion Preference - 1) Try and parse as XML, if successful returns a Node 2) Try and parse as HTML returning as XML compatible HTML, if
     * successful returns a Node 3) Return as base64Binary encoded data</p>
     *
     * @param   context  The context of the calling XQuery
     * @param   method   The HTTP Request Method
     * @param   builder  The MemTreeBuilder that is being used
     *
     * @throws  IOException     
     * @throws  XPathException  
     */
    private void insertResponseBody( XQueryContext context, HttpMethod method, MemTreeBuilder builder, Map<String, Boolean>parserFeatures, Map<String, String>parserProperties) throws IOException, XPathException
    {
        @SuppressWarnings( "unused" )
        boolean     parsed       = false;
        NodeImpl    responseNode = null;
        InputStream bodyAsStream = method.getResponseBodyAsStream();

        // check if there is a response body
        if( bodyAsStream != null ) {

            long contentLength = ( ( HttpMethodBase )method ).getResponseContentLength();

            if( contentLength > Integer.MAX_VALUE ) { //guard from overflow
                throw( new XPathException( this, "HTTPClient response too large to be buffered: " + contentLength + " bytes" ) );
            }

            ByteArrayOutputStream outstream = new ByteArrayOutputStream();
            byte[]                buffer    = new byte[4096];
            int                   len;

            while( ( len = bodyAsStream.read( buffer ) ) > 0 ) {
                outstream.write( buffer, 0, len );
            }
            outstream.close();
            byte[]   body             = outstream.toByteArray();
            
            // determine the type of the response document
            Header responseContentType = method.getResponseHeader( "Content-Type" );
            MimeType responseMimeType = getResponseMimeType( responseContentType );
            if (responseContentType != null)
            	builder.addAttribute( new QName( "mimetype", null, null ), responseContentType.getValue() );

            //try and parse the response as XML
            try {
                responseNode = ( NodeImpl )ModuleUtils.streamToXML( context, new ByteArrayInputStream( body ) );
                builder.addAttribute( new QName( "type", null, null ), "xml" );
                responseNode.copyTo( null, new DocumentBuilderReceiver( builder ) );
            } catch(SAXException se) {
                // could not parse to xml
                // not an error in itself, it will be treated either as HTML,
                // text or binary here below
                String msg = "Request for URI '"
                    + method.getURI().toString()
                    + "' Could not parse http response content as XML (will try html, text or fallback to binary): "
                    + se.getMessage();
                if ( logger.isDebugEnabled() ) {
                    logger.debug(msg, se);
                }
                else {
                    logger.info(msg);
                }
            } catch(IOException ioe) {
                String msg = "Request for URI '" + method.getURI().toString() + "' Could not read http response content: " + ioe.getMessage();
                logger.error(msg, ioe);
                throw new XPathException(msg, ioe);
            }

            if( responseNode == null ) {
                //response is NOT parseable as XML

                //is it a html document?
                if( responseMimeType.getName().equals( MimeType.HTML_TYPE.getName() ) ) {

                    //html document
                    try {

                        //parse html to xml(html)
                        responseNode = (NodeImpl)ModuleUtils.htmlToXHtml(context, method.getURI().toString(), new InputSource(new ByteArrayInputStream(body)), parserFeatures, parserProperties).getDocumentElement();
                        builder.addAttribute( new QName( "type", null, null ), "xhtml" );
                        responseNode.copyTo( null, new DocumentBuilderReceiver( builder ) );
                    }
                    catch( URIException ue ) {
                        throw( new XPathException( this, ue.getMessage(), ue ) );
                    }
                    catch( SAXException se ) {
                        //could not parse to xml(html)
                        logger.debug("Could not parse http response content from HTML to XML: " + se.getMessage(), se);
                    }
                }
            }

            if( responseNode == null ) {

                if( responseMimeType.getName().startsWith( "text/" ) ) {

                    // Assume it's a text body and URL encode it
                    builder.addAttribute( new QName( "type", null, null ), "text" );
                    builder.addAttribute( new QName( "encoding", null, null ), "URLEncoded" );
                    builder.characters( URLEncoder.encode( EncodingUtil.getString( body, ( ( HttpMethodBase )method ).getResponseCharSet() ), "UTF-8" ) );
                } else {

                    // Assume it's a binary body and Base64 encode it
                    builder.addAttribute( new QName( "type", null, null ), "binary" );
                    builder.addAttribute( new QName( "encoding", null, null ), "Base64Encoded" );

                    if( body != null ) {
                        BinaryValue binary = null;
                        try {
                            binary = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(body));
                            builder.characters( binary.getStringValue() );
                        } finally {
                            // free resources
                            if (binary != null)
                                binary.destroy(context, null);
                        }
                    }
                }
            }
        }
    }


    /**
     * Given the Response Header for Content-Type this function returns an appropriate eXist MimeType.
     *
     * @param   responseHeaderContentType  The HTTP Response Header containing the Content-Type of the Response.
     *
     * @return  The corresponding eXist MimeType
     */
    protected MimeType getResponseMimeType( Header responseHeaderContentType )
    {
        MimeType returnMimeType = MimeType.BINARY_TYPE;

        if( responseHeaderContentType != null ) {

            if( responseHeaderContentType.getName().equals( "Content-Type" ) ) {

                String responseContentType = responseHeaderContentType.getValue();
                int    contentTypeEnd      = responseContentType.indexOf( ";" );

                if( contentTypeEnd == -1 ) {
                    contentTypeEnd = responseContentType.length();
                }

                String    responseMimeType = responseContentType.substring( 0, contentTypeEnd );
                MimeTable mimeTable        = MimeTable.getInstance();
                MimeType  mimeType         = mimeTable.getContentType( responseMimeType );

                if( mimeType != null ) {
                    returnMimeType = mimeType;
                }
            }
        }

        return( returnMimeType );
    }

    protected class FeaturesAndProperties {
        private Map<String,Boolean> features = new HashMap<String, Boolean>();
        private Map<String,String> properties = new HashMap<String, String>();

        public FeaturesAndProperties(Map<String,Boolean>features, Map<String,String>properties) {
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

    protected FeaturesAndProperties getParserFeaturesAndProperties(Node options) throws XPathException {

        Map<String,Boolean> features = new HashMap<String, Boolean>();
        Map<String,String> properties = new HashMap<String, String>();

        if((options.getNodeType() == Node.ELEMENT_NODE ) && options.getLocalName().equals("options")) {
            NodeList optionList = options.getChildNodes();

            for(int i = 0; i < optionList.getLength(); i++) {
                Node option = optionList.item( i );

                if((option.getNodeType() == Node.ELEMENT_NODE)) {
                    String name  = ((Element)option).getAttribute("name");
                    String value = ((Element)option).getAttribute("value");

                    if((name == null) || (value == null)) {
                        throw(new XPathException( this, "Name or value attribute missing for parser feature/property"));
                    }

                    if(option.getLocalName().equals("feature")) {
                        if(value.matches("(true|false)")) {
                            features.put(name, Boolean.parseBoolean(value));
                        } else {
                            throw(new XPathException(this, "Feature value must be true or false"));
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
