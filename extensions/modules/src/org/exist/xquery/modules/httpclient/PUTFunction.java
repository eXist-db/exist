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

import org.apache.commons.httpclient.methods.ByteArrayRequestEntity;
import org.apache.commons.httpclient.methods.PutMethod;
import org.apache.commons.httpclient.methods.RequestEntity;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.XMLWriter;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import java.util.Properties;


/**
 * DOCUMENT ME!
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.2
 * @serial   20070905
 */
public class PUTFunction extends BaseHTTPClientFunction
{
    protected static final Logger         logger    = Logger.getLogger( PUTFunction.class );

    public final static FunctionSignature signature = 
		new FunctionSignature( 
			new QName( "put", NAMESPACE_URI, PREFIX ), 
			"Performs a HTTP PUT request.." + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>" + 
			" where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.", 
			new SequenceType[] {
	            URI_PARAM, PUT_CONTENT_PARAM, PERSIST_PARAM, REQUEST_HEADER_PARAM
	        }, 
			XML_BODY_RETURN 
		);

	
    public PUTFunction( XQueryContext context )
    {
        super( context, signature );
    }

	
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        Sequence response = null;

        // must be a URL
        if( args[0].isEmpty() ) {
            return( Sequence.EMPTY_SEQUENCE );
        }

        //get the url
        String                url            = args[0].itemAt( 0 ).getStringValue();

        //get the payload
        Item                  payload        = args[1].itemAt( 0 );

        //get the persist cookies
        boolean               persistCookies = args[2].effectiveBooleanValue();

        //serialize the node to SAX
        ByteArrayOutputStream baos           = new ByteArrayOutputStream();
        OutputStreamWriter    osw            = null;

        try {
            osw = new OutputStreamWriter( baos, "UTF-8" );
        }
        catch( UnsupportedEncodingException e ) {
            throw( new XPathException( this, "Internal error" ) );
        }
        XMLWriter     xmlWriter = new XMLWriter( osw );
        SAXSerializer sax       = new SAXSerializer();

        sax.setReceiver( xmlWriter );

        try {
            payload.toSAX( context.getBroker(), sax, new Properties() );
            osw.flush();
            osw.close();
        }
        catch( Exception e ) {
            e.printStackTrace();
        }

        //setup PUT request
        PutMethod     put    = new PutMethod( url );
        RequestEntity entity = new ByteArrayRequestEntity( baos.toByteArray(), "application/xml; charset=utf-8" );

        put.setRequestEntity( entity );

        //setup PUT Request Headers
        if( !args[3].isEmpty() ) {
            setHeaders( put, ( ( NodeValue )args[3].itemAt( 0 ) ).getNode() );
        }

        try {

            //execute the request
            response = doRequest( context, put, persistCookies );

        }
        catch( IOException ioe ) {
            throw( new XPathException( this, ioe.getMessage(), ioe ) );
        }
        finally {
            put.releaseConnection();
        }

        return( response );
    }
}
