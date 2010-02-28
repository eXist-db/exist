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

import org.apache.commons.httpclient.Cookie;
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
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
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
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.net.URLEncoder;


/**
 * DOCUMENT ME!
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.2
 * @serial   20070905
 */

public abstract class BaseHTTPClientFunction extends BasicFunction
{
    protected static final Logger                        logger                         = Logger.getLogger( BaseHTTPClientFunction.class );
	
    protected static final FunctionParameterSequenceType URI_PARAM                      = new FunctionParameterSequenceType( "url", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URL to process" );
    protected static final FunctionParameterSequenceType PUT_CONTENT_PARAM              = new FunctionParameterSequenceType( "content", Type.NODE, Cardinality.EXACTLY_ONE, "The XML PUT payload/content. If it is an XML Node it will be serialized, any other type will be atomized into a string." );
    protected static final FunctionParameterSequenceType POST_CONTENT_PARAM             = new FunctionParameterSequenceType( "content", Type.ITEM, Cardinality.EXACTLY_ONE, "The XML POST payload/content. If it is an XML Node it will be serialized, any other type will be atomized into a string." );
    protected static final FunctionParameterSequenceType POST_FORM_PARAM                = new FunctionParameterSequenceType( "content", Type.ELEMENT, Cardinality.EXACTLY_ONE, "The form data in the format <httpclient:fields><httpclient:field name=\"\" value=\"\" type=\"string|file\"/>...</httpclient:fields>.  If the field values will be suitably URLEncoded and sent with the mime type application/x-www-form-urlencoded." );
    protected static final FunctionParameterSequenceType PERSIST_PARAM                  = new FunctionParameterSequenceType( "persist", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The to indicate if the cookies persist for the query lifetime" );
    protected static final FunctionParameterSequenceType REQUEST_HEADER_PARAM           = new FunctionParameterSequenceType( "request-headers", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "Any HTTP Request Headers to set in the form  <headers><header name=\"\" value=\"\"/></headers>" );

    protected static final FunctionReturnSequenceType    XML_BODY_RETURN                = new FunctionReturnSequenceType( Type.ITEM, Cardinality.EXACTLY_ONE, "the XML body content" );

    final static String                                  NAMESPACE_URI                  = HTTPClientModule.NAMESPACE_URI;
    final static String                                  PREFIX                         = HTTPClientModule.PREFIX;
    final static String                                  HTTP_MODULE_PERSISTENT_COOKIES = HTTPClientModule.HTTP_MODULE_PERSISTENT_COOKIES;

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
     * @throws  XPathException  DOCUMENT ME!
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
     * @param   method          The HTTP methor for the request
     * @param   persistCookies  If true existing cookies are re-used and any issued cookies are persisted for future HTTP Requests
     *
     * @return  DOCUMENT ME!
     *
     * @throws  IOException     DOCUMENT ME!
     * @throws  XPathException  DOCUMENT ME!
     */
    protected Sequence doRequest( XQueryContext context, HttpMethod method, boolean persistCookies ) throws IOException, XPathException
    {
        int      statusCode      = 0;
        Sequence encodedResponse = null;

        //use existing cookies?
        if( persistCookies ) {

            //set existing cookies
            Cookie[] cookies = ( Cookie[] )context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES );

            if( cookies != null ) {

                for( int c = 0; c < cookies.length; c++ ) {
                    method.setRequestHeader( "Cookie", cookies[c].toExternalForm() );
                }
            }
        }

        //execute the request
        HttpClient http = new HttpClient();

        try {

            //set the proxy server (if any)
            String proxyHost = System.getProperty( "http.proxyHost" );

            if( proxyHost != null ) {
                //TODO: support for http.nonProxyHosts e.g. -Dhttp.nonProxyHosts="*.devonline.gov.uk|*.devon.gov.uk"

                ProxyHost proxy = new ProxyHost( proxyHost, Integer.parseInt( System.getProperty( "http.proxyPort" ) ) );
                http.getHostConfiguration().setProxyHost( proxy );
            }

            //perform the request
            statusCode      = http.executeMethod( method );

            encodedResponse = encodeResponseAsXML( context, method, statusCode );

            //persist cookies?
            if( persistCookies ) {

                //store/update cookies
                HttpState state           = http.getState();
                Cookie[]  incomingCookies = state.getCookies();
                Cookie[]  currentCookies  = ( Cookie[] )context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES );

                context.setXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES, mergeCookies( currentCookies, incomingCookies ) );
            }
        }
        catch( Exception e ) {
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
     * @throws  XPathException  DOCUMENT ME!
     * @throws  IOException     DOCUMENT ME!
     */
    private Sequence encodeResponseAsXML( XQueryContext context, HttpMethod method, int statusCode ) throws XPathException, IOException
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

            insertResponseBody( context, method, builder );

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
     * @throws  IOException     DOCUMENT ME!
     * @throws  XPathException  DOCUMENT ME!
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
     * @throws  IOException     DOCUMENT ME!
     * @throws  XPathException  DOCUMENT ME!
     */
    private void insertResponseBody( XQueryContext context, HttpMethod method, MemTreeBuilder builder ) throws IOException, XPathException
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
            MimeType responseMimeType = getResponseMimeType( method.getResponseHeader( "Content-Type" ) );
            builder.addAttribute( new QName( "mimetype", null, null ), method.getResponseHeader( "Content-Type" ).getValue() );

            //try and parse the response as XML
            try {
                responseNode = ( NodeImpl )ModuleUtils.streamToXML( context, new ByteArrayInputStream( body ) );
                builder.addAttribute( new QName( "type", null, null ), "xml" );
                responseNode.copyTo( null, new DocumentBuilderReceiver( builder ) );
            }
            catch( SAXException se ) {
                //could not parse to xml
            }

            if( responseNode == null ) {
                //response is NOT parseable as XML

                //is it a html document?
                if( responseMimeType.getName().equals( MimeType.HTML_TYPE.getName() ) ) {

                    //html document
                    try {

                        //parse html to xml(html)
                        responseNode = ( NodeImpl )ModuleUtils.htmlToXHtml( context, method.getURI().toString(), new InputSource( new ByteArrayInputStream( body ) ) ).getDocumentElement();
                        builder.addAttribute( new QName( "type", null, null ), "xhtml" );
                        responseNode.copyTo( null, new DocumentBuilderReceiver( builder ) );
                    }
                    catch( URIException ue ) {
                        throw( new XPathException( this, ue.getMessage(), ue ) );
                    }
                    catch( SAXException se ) {
                        //could not parse to xml(html)
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
                        Base64Binary binary = new Base64Binary( body );
                        builder.characters( binary.getStringValue() );
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


    /**
     * Merges two cookie arrays together.
     *
     * <p>If cookies are equal (same name, path and comain) then the incoming cookie is favoured over the current cookie</p>
     *
     * @param   current   The cookies already known
     * @param   incoming  The new cookies
     *
     * @return  DOCUMENT ME!
     */
    protected Cookie[] mergeCookies( Cookie[] current, Cookie[] incoming )
    {
        Cookie[] cookies = null;

        if( current == null ) {

            if( ( incoming != null ) && ( incoming.length > 0 ) ) {
                cookies = incoming;
            }
        } else if( incoming == null ) {
            cookies = current;
        } else {

            java.util.HashMap<Integer, Cookie> replacements = new java.util.HashMap<Integer, Cookie>();
            java.util.Vector<Cookie>           additions    = new java.util.Vector<Cookie>();

            for( int i = 0; i < incoming.length; i++ ) {
                boolean cookieExists = false;

                for( int c = 0; c < current.length; i++ ) {

                    if( current[c].equals( incoming[i] ) ) {

                        //replacement
                        replacements.put( new Integer( c ), incoming[i] );
                        cookieExists = true;
                        break;
                    }
                }

                if( !cookieExists ) {

                    //add
                    additions.add( incoming[i] );
                }
            }

            cookies = new Cookie[current.length + additions.size()];

            //resolve replacements/copies
            for( int c = 0; c < current.length; c++ ) {

                if( replacements.containsKey( new Integer( c ) ) ) {

                    //replace
                    cookies[c] = replacements.get( new Integer( c ) );
                } else {

                    //copy
                    cookies[c] = current[c];
                }
            }

            //resolve additions
            for( int a = 0; a < additions.size(); a++ ) {
                int offset = current.length + a;
                cookies[offset] = additions.get( a );
            }
        }

        return( cookies );
    }

}
