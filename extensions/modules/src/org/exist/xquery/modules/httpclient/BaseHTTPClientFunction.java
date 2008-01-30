/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.modules.httpclient;

import org.apache.commons.httpclient.*;
import org.apache.commons.httpclient.methods.HeadMethod;
import org.apache.commons.httpclient.methods.OptionsMethod;
import org.exist.dom.QName;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.memtree.NodeImpl;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.net.URLEncoder;


/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 20070905
 * @version 1.2
 */

public abstract class BaseHTTPClientFunction extends BasicFunction 
{
    
    final static String NAMESPACE_URI                       = HTTPClientModule.NAMESPACE_URI;
    final static String PREFIX                              = HTTPClientModule.PREFIX;
    final static String HTTP_MODULE_PERSISTENT_COOKIES      = HTTPClientModule.HTTP_MODULE_PERSISTENT_COOKIES;
    
    final static String HTTP_EXCEPTION_STATUS_CODE          = "500";
    
    
    public BaseHTTPClientFunction( XQueryContext context, FunctionSignature signature )
    {
        super( context, signature );
    }
    
    
    /**
    * Parses header parameters and sets them on the Request
    * 
    * @param method The Http Method to set the request headers on
    * @param headers The headers node e.g. <headers><header name="Content-Type" value="text/xml"/></headers>
    */
    protected void setHeaders( HttpMethod method, Node headers ) throws XPathException
    {
        if( headers.getNodeType() == Node.ELEMENT_NODE && headers.getLocalName().equals( "headers" ) ) {
            NodeList headerList = headers.getChildNodes();
            
            for( int i = 0; i < headerList.getLength(); i++ ) {
                Node header = headerList.item( i );
                
                if( header.getNodeType() == Node.ELEMENT_NODE && header.getLocalName().equals( "header" ) ) {
                    String name  = ((Element)header).getAttribute( "name" );
                    String value = ((Element)header).getAttribute( "value" );
                    
                    if( name == null || value == null ) {
                        throw( new XPathException( "Name or value attribute missing for request header parameter" ) );
                    }
                    
                    method.addRequestHeader( new Header( name, value ) );
                }
            }
        }
    }
    
    
    /**
    * Performs a HTTP Request
    * 
    * @param context   The context of the calling XQuery
    * @param method    The HTTP methor for the request
    * @param persistCookies    If true existing cookies are re-used and any issued cookies are persisted for future HTTP Requests 
    */
    protected Sequence doRequest( XQueryContext context, HttpMethod method, boolean persistCookies ) throws IOException, XPathException
    {
        int statusCode = 0;
        Sequence encodedResponse = null;
        
        //use existing cookies?
        if( persistCookies ) {
            //set existing cookies
            Cookie[] cookies = (Cookie[])context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES) ;
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
            String proxyHost = System.getProperty( "http.proxyHost") ; 
            if( proxyHost != null) {
                //TODO: support for http.nonProxyHosts e.g. -Dhttp.nonProxyHosts="*.devonline.gov.uk|*.devon.gov.uk"
                
                ProxyHost proxy = new ProxyHost( proxyHost, Integer.parseInt( System.getProperty( "http.proxyPort" ) ) );
                http.getHostConfiguration().setProxyHost( proxy );
            }
            
            //perform the request
            statusCode = http.executeMethod( method );
            
            encodedResponse = encodeResponseAsXML( context, method, statusCode );
            
            //persist cookies?
            if( persistCookies ) {
                //store/update cookies
                HttpState state             = http.getState();
                Cookie[] incomingCookies    = state.getCookies();
                Cookie[] currentCookies     = (Cookie[])context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_COOKIES );
                
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
     * @param context       The context of the calling XQuery
     * @param method        The HTTP Request Method
     * @param statusCode    The status code returned from the http method invocation
     * 
     * @return The data in XML format
     */
    private Sequence encodeResponseAsXML( XQueryContext context, HttpMethod method, int statusCode ) throws XPathException, IOException
    {
        Sequence    xmlResponse     = null;
        
        MemTreeBuilder builder = context.getDocumentBuilder();
        
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
        
        if( !( method instanceof HeadMethod || method instanceof OptionsMethod ) ) {  // Head and Options methods never have any response body
            // Add the response body node
            builder.startElement( new QName( "body", NAMESPACE_URI, PREFIX ), null );
            
            insertResponseBody( context, method, builder );
            
            builder.endElement();
        }
        
        builder.endElement();
        
        xmlResponse = (NodeValue)builder.getDocument().getDocumentElement();
        
        return( xmlResponse );
    }
    
    
    /**
    * Takes an exception message and encodes it as an XML response structure.
    * 
    * @param context       The context of the calling XQuery
    * @param message       The exception error message
    * 
    * @return The response in XML format
    */
    private Sequence encodeErrorResponse( XQueryContext context, String message ) throws IOException, XPathException
    {
        Sequence    xmlResponse     = null;
        
        MemTreeBuilder builder = context.getDocumentBuilder();
        
        builder.startDocument();
        builder.startElement( new QName( "response", NAMESPACE_URI, PREFIX ), null );
        builder.addAttribute( new QName( "statusCode", null, null ), HTTP_EXCEPTION_STATUS_CODE );
        
        builder.startElement( new QName( "body", NAMESPACE_URI, PREFIX ), null );
        
        builder.addAttribute( new QName( "type", null, null ), "text" );
        builder.addAttribute( new QName( "encoding", null, null ), "URLEncoded" );
        if (message != null)
            builder.characters( URLEncoder.encode( message, "UTF-8" ) );
        
        builder.endElement();
        
        builder.endElement();
        
        xmlResponse = (NodeValue)builder.getDocument().getDocumentElement();
        
        return( xmlResponse );
    }
    
    
    /**
    * Takes the HTTP Response Body from the HTTP Method and attempts to insert it into the response tree we are building.
    * 
    * Conversion Preference -
    * 1) Try and parse as XML, if successful returns a Node
    * 2) Try and parse as HTML returning as XML compatible HTML, if successful returns a Node
    * 3) Return as base64Binary encoded data
    * 
    * @param context   The context of the calling XQuery
    * @param method    The HTTP Request Method
    * @param builder   The MemTreeBuilder that is being used
    * 
    * @return The data in an suitable XQuery datatype value
    */
    private void insertResponseBody( XQueryContext context, HttpMethod method, MemTreeBuilder builder ) throws IOException, XPathException
    {
        boolean     parsed       = false;
        NodeImpl    responseNode = null;
        String      bodyAsString = method.getResponseBodyAsString();
        
        // check if there is a response body
        if( bodyAsString != null ) {
            
            // determine the type of the response document
            MimeType responseMimeType = getResponseMimeType( method.getResponseHeader( "Content-Type" ) );
            builder.addAttribute( new QName( "mimetype", null, null ), method.getResponseHeader( "Content-Type" ).getValue() );
            
            //try and parse the response as XML
            try {
                //TODO: replace getResponseBodyAsString() with getResponseBodyAsStream()
                responseNode = (NodeImpl)ModuleUtils.stringToXML( context, bodyAsString );
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
                        responseNode = (NodeImpl)ModuleUtils.htmlToXHtml(context, method.getURI().toString(), new InputSource(method.getResponseBodyAsStream() ) ).getDocumentElement();
                        builder.addAttribute( new QName( "type", null, null ), "xhtml" );
                        responseNode.copyTo( null, new DocumentBuilderReceiver( builder ) );                  
                    }
                    catch( URIException ue ) {
                        throw( new XPathException (ue ) );
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
                    builder.characters( URLEncoder.encode( method.getResponseBodyAsString(), "UTF-8" ) );
                } else {
                    // Assume it's a binary body and Base64 encode it
                    byte[] body = method.getResponseBody();
                    
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
        * Given the Response Header for Content-Type this function returns an appropriate eXist MimeType
        * 
        * @param responseHeaderContentType The HTTP Response Header containing the Content-Type of the Response.
        * @return The corresponding eXist MimeType
        */
    protected MimeType getResponseMimeType( Header responseHeaderContentType )
    {
        MimeType returnMimeType = MimeType.BINARY_TYPE;
        
        if( responseHeaderContentType != null ) {
            if( responseHeaderContentType.getName().equals( "Content-Type" ) ) {
                
                String responseContentType  = responseHeaderContentType.getValue();
                int contentTypeEnd          = responseContentType.indexOf( ";" );
                
                if( contentTypeEnd == -1 ) {
                    contentTypeEnd = responseContentType.length();
                }
                
                String responseMimeType = responseContentType.substring( 0, contentTypeEnd );
                MimeTable mimeTable     = MimeTable.getInstance();
                MimeType mimeType       = mimeTable.getContentType( responseMimeType );
                
                if( mimeType != null ) {
                    returnMimeType= mimeType;
                }
            }
        }
        
        return( returnMimeType );
    }
    
    
    /**
        * Merges two cookie arrays together
        * 
        * If cookies are equal (same name, path and comain) then the incoming cookie is favoured over the current cookie
        * 
        * @param current   The cookies already known
        * @param incoming  The new cookies
        * 
        * 
        */
    protected Cookie[] mergeCookies( Cookie[] current, Cookie[] incoming )
    {
        Cookie[] cookies = null;
        
        if( current == null ) {
            if( incoming != null && incoming.length > 0 ) {
                cookies = incoming;
            }
        } else if( incoming == null ) {
            cookies = current;
        } else {
            
            java.util.HashMap replacements  = new java.util.HashMap();
            java.util.Vector  additions     = new java.util.Vector();
            
            for( int i = 0; i < incoming.length; i++ ) {
                boolean cookieExists = false;
                
                for( int c = 0; c < current.length; i++ ) {
                    if( current[c].equals( incoming[i] ) ) {
                        //replacement               
                        replacements.put( new Integer(c), incoming[i] );
                        cookieExists = true;
                        break;
                    }
                }
                
                if( !cookieExists ) {
                    //add
                    additions.add( incoming[i] );
                }
            }
            
            cookies = new Cookie[ current.length + additions.size() ];
            //resolve replacements/copies
            for( int c = 0; c < current.length; c++ ) {
                if( replacements.containsKey( new Integer(c) ) ) {
                    //replace
                    cookies[c] = (Cookie)replacements.get( new Integer(c) );
                } else {
                    //copy
                    cookies[c] = current[c];
                }
            }
            //resolve additions
            for( int a = 0; a < additions.size(); a++ ) {
                int offset = current.length + a;
                cookies[offset] = (Cookie)additions.get( a );
            }
        }
        
        return( cookies );
    }
    
    
}
