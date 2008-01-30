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

import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.RequestEntity;
import org.exist.dom.QName;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.Properties;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 20070905
 * @version 1.2
 */
public class POSTFunction extends BaseHTTPClientFunction
{
    public final static FunctionSignature signature =
        new FunctionSignature(
        new QName( "post", NAMESPACE_URI, PREFIX ),
        "Performs a HTTP POST request. $a is the URL, $b is the XML POST payload/content, $c determines if cookies persist for the query lifetime. $d defines any HTTP Request Headers to set in the form <headers><header name=\"\" value=\"\"/></headers>."
        + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>"
        + " where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.",
        new SequenceType[] {
            new SequenceType( Type.ANY_URI, Cardinality.EXACTLY_ONE ),
            new SequenceType( Type.NODE, Cardinality.EXACTLY_ONE ),
            new SequenceType( Type.BOOLEAN, Cardinality.EXACTLY_ONE ),
            new SequenceType( Type.ELEMENT, Cardinality.ZERO_OR_ONE )
            },
        new SequenceType( Type.ITEM, Cardinality.EXACTLY_ONE )
        );
    
    
    public POSTFunction( XQueryContext context )
    {
        super( context, signature );
    }
    
    
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        Sequence    response = null;
        
        // must be a URL
        if( args[0].isEmpty() ) {
            return( Sequence.EMPTY_SEQUENCE );
        }
        
        //get the url
        String url                  = args[0].itemAt( 0 ).getStringValue();
        
        //get the payload
        Item payload                = args[1].itemAt( 0 );
        
        //get the persist cookies
        boolean persistCookies      = args[2].effectiveBooleanValue();
        
        //serialize the node to SAX
        ByteArrayOutputStream baos  = new ByteArrayOutputStream();
        OutputStreamWriter osw      = null;
        try {
            osw = new OutputStreamWriter( baos, "UTF-8" );
        } catch (UnsupportedEncodingException e) {
            throw new XPathException("Internal error");
        }
        SAXSerializer sax           = new SAXSerializer(osw, new Properties());
        
        try {
            payload.toSAX( context.getBroker(), sax, new Properties() );
            osw.flush();
            osw.close();
        }
        catch( Exception e ) {
            e.printStackTrace();
        } 
        //setup POST request
        PostMethod post         = new PostMethod( url );
        RequestEntity entity    = new ByteArrayRequestEntity( baos.toByteArray(), "text/xml; charset=utf-8" );
        
        post.setRequestEntity( entity );
        
        //setup POST Request Headers
        if( !args[3].isEmpty() ) {
            setHeaders( post, ((NodeValue)args[3].itemAt(0)).getNode() );
        }
        
        try {
            //execute the request
            response = doRequest( context, post, persistCookies );

        }
        catch( IOException ioe ) {
            throw( new XPathException( ioe ) );
        }
        finally {
            post.releaseConnection();
        }
        
        return( response );
    }
}
