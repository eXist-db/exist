/*
 *  eXist EXPath HTTP Client Module send-request functions
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
package org.expath.exist;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpConnection;
import org.expath.httpclient.HttpRequest;
import org.expath.httpclient.HttpResponse;
import org.expath.httpclient.impl.ApacheHttpConnection;
import org.expath.httpclient.impl.RequestParser;
import org.expath.tools.model.Element;
import org.expath.tools.model.exist.EXistElement;
import org.expath.httpclient.model.exist.EXistResult;
import org.expath.tools.model.exist.EXistSequence;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 * @version EXPath HTTP Client Module Candidate 9 January 2010 http://expath.org/spec/http-client/20100109
 */
public class SendRequestFunction extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(SendRequestFunction.class);
    
    private final static FunctionParameterSequenceType REQUEST_PARAM = new FunctionParameterSequenceType("request", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "request contains the various parameters of the request, for instance the HTTP method to use or the HTTP headers. Among other things, it can also contain the other param's values: the URI and the bodies. If they are not set as parameter to the function, their value in $request, if any, is used instead. See the following section (http://www.expath.org/spec/http-client#d2e183) for the detailed definition of the http:request element. If the parameter does not follow the grammar defined in this spec, this is an error [err:HC005].");
    private final static FunctionParameterSequenceType HREF_PARAM = new FunctionParameterSequenceType("href", Type.STRING, Cardinality.ZERO_OR_ONE, "$href is the HTTP or HTTPS URI to send the request to. It is an xs:anyURI, but is declared as a string to be able to pass literal strings (without requiring to explicitly cast it to an xs:anyURI)");
    private final static FunctionParameterSequenceType BODIES_PARAM = new FunctionParameterSequenceType("bodies", Type.ITEM, Cardinality.ZERO_OR_MORE, "$bodies is the request body content, for HTTP methods that can contain a body in the request (e.g. POST). This is an error if this param is not the empty sequence for methods that must be empty (e.g. DELETE). The details of the methods are defined in their respective specs (e.g. [RFC 2616] or [RFC 4918]). In case of a multipart request, it can be a sequence of several items, each one is the body of the corresponding body descriptor in $request.");
    private final static FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.ITEM, Cardinality.ONE_OR_MORE, "A sequence representing the response from the server. This sequence has an http:response element as first item, which is followed by an additional item for each body or body part in the response. Further detail can be found here - http://www.expath.org/spec/http-client#d2e483");
    
    public final static FunctionSignature signatures[] = {
        //http:send-request($request as element(http:request)?) as item()+
        new FunctionSignature(
            new QName("send-request", HttpClientModule.NAMESPACE_URI, HttpClientModule.PREFIX),
            "Sends a HTTP request to a server and returns the response.",
            new SequenceType[]{
                REQUEST_PARAM
            },
            RETURN_TYPE
        ),
        //http:send-request($request as element(http:request)?, $href as xs:string?) as item()+
        new FunctionSignature(
            new QName("send-request", HttpClientModule.NAMESPACE_URI, HttpClientModule.PREFIX),
            "Sends a HTTP request to a server and returns the response.",
            new SequenceType[]{
                REQUEST_PARAM,
                HREF_PARAM
            },
            RETURN_TYPE
        ),
        //http:send-request($request as element(http:request)?, $href as xs:string?, $bodies as item()*) as item()+
        new FunctionSignature(
            new QName("send-request", HttpClientModule.NAMESPACE_URI, HttpClientModule.PREFIX),
            "Sends a HTTP request to a server and returns the response.",
            new SequenceType[]{
                REQUEST_PARAM,
                HREF_PARAM,
                BODIES_PARAM
            },
            RETURN_TYPE
        )
    };
    
    /**
     * SendRequestFunction Constructor
     *
     * @param context	The Context of the calling XQuery
     * @param signature The actual signature of the function
     */
    public SendRequestFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
    
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        
        Sequence bodies = Sequence.EMPTY_SEQUENCE;
        String href = null;
        NodeValue request = null;
        
        switch(getArgumentCount()) {
            case 3:
                bodies = args[2];
            case 2: {
                Item i = args[1].itemAt(0);
                if ( i != null ) {
                    href = i.getStringValue();
                }
            }
            case 1:
                request = (NodeValue)args[0].itemAt(0);
                break;
                
            default:
                return Sequence.EMPTY_SEQUENCE;
        }
        
        return sendRequest(request, href, bodies);
    }
    
    private Sequence sendRequest(final NodeValue request, final String href, final Sequence bodies) throws XPathException {
        
        HttpRequest req = null;
        try {
            final org.expath.tools.model.Sequence b = new EXistSequence(bodies, getContext());
            final Element r = new EXistElement(request, getContext());
            final RequestParser parser = new RequestParser(r);
            req = parser.parse(b, href);

            // override anyway it href exists
            if (href != null && !href.isEmpty() ) {
                req.setHref(href);
            }
        
            final URI uri = new URI(req.getHref());
            final EXistResult result = sendOnce(uri, req, parser);
            return result.getResult();
                
        } catch(final URISyntaxException ex ) {
            throw new XPathException(this, "Href is not valid: " + req != null ? req.getHref() : "" + ". " + ex.getMessage(), ex);
        } catch(final HttpClientException hce) {
            throw new XPathException(this, hce.getMessage(), hce);
        }
    }
    
    /**
     * Send one request, not following redirect but handling authentication.
     * 
     * Authentication may require to reply to an authentication challenge,
     * by sending again the request, with credentials.
     */
    private EXistResult sendOnce(final URI uri, final HttpRequest request, final RequestParser parser) throws HttpClientException {
            EXistResult result = new EXistResult(getContext());
            HttpConnection conn = null;
            try {
                conn = new ApacheHttpConnection(uri);
                final HttpResponse response = request.send(result, conn, parser.getCredentials());
                if(response.getStatus() == HttpStatus.SC_UNAUTHORIZED && parser.getCredentials() != null) {
                    // requires authorization, try again with auth
                    result = new EXistResult(getContext());
                    request.send(result, conn, parser.getCredentials());
                }
            } finally {
                if(conn != null) {
                    try {
                        conn.disconnect();
                    } catch(final HttpClientException hcee) {
                        logger.warn(hcee.getMessage(), hcee);
                    }
                }
            }
        
        return result;
    }
}
