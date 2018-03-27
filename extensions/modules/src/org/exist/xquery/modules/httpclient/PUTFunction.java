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
import org.apache.commons.httpclient.methods.StringRequestEntity;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.IndentingXMLWriter;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;

import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * Performs HTTP Put method
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.3
 * @serial   20100228
 */
public class PUTFunction extends BaseHTTPClientFunction
{
    protected static final Logger         logger    = LogManager.getLogger( PUTFunction.class );

    public final static FunctionSignature signatures[]  = {
		new FunctionSignature(
			new QName( "put", NAMESPACE_URI, PREFIX ),
			"Performs a HTTP PUT request.." + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>" +
			" where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.",
			new SequenceType[] {
	            URI_PARAM, PUT_CONTENT_PARAM, PERSIST_PARAM, REQUEST_HEADER_PARAM
	        },
			XML_BODY_RETURN
		),

	    new FunctionSignature(
            new QName( "put", NAMESPACE_URI, PREFIX ),
            "Performs a HTTP PUT request.." + " This method returns the HTTP response encoded as an XML fragment, that looks as follows: <httpclient:response xmlns:httpclient=\"http://exist-db.org/xquery/httpclient\" statusCode=\"200\"><httpclient:headers><httpclient:header name=\"name\" value=\"value\"/>...</httpclient:headers><httpclient:body type=\"xml|xhtml|text|binary\" mimetype=\"returned content mimetype\">body content</httpclient:body></httpclient:response>" +
            " where XML body content will be returned as a Node, HTML body content will be tidied into an XML compatible form, a body with mime-type of \"text/...\" will be returned as a URLEncoded string, and any other body content will be returned as xs:base64Binary encoded data.",
            new SequenceType[] {
                URI_PARAM, PUT_CONTENT_PARAM, PERSIST_PARAM, REQUEST_HEADER_PARAM, INDENTATION_PARAM
            },
            XML_BODY_RETURN
        )
    };

    public PUTFunction( XQueryContext context, FunctionSignature signature )
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

        //get the persist state
        boolean               persistState 	 = args[2].effectiveBooleanValue();

        String indentLevel = (args.length >= 5 && !args[4].isEmpty()) ? args[4].itemAt(0).toString() : null;

        RequestEntity entity = null;
        
        if( Type.subTypeOf( payload.getType(), Type.NODE ) ) {
	        //serialize the node to SAX

            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream();
                 final OutputStreamWriter osw = new OutputStreamWriter(baos, UTF_8)) {

                IndentingXMLWriter xmlWriter = new IndentingXMLWriter(osw);
                Properties outputProperties = new Properties();
                outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
                if (indentLevel != null) {
                    outputProperties.setProperty(OutputKeys.INDENT, "yes");
                    outputProperties.setProperty(EXistOutputKeys.INDENT_SPACES, indentLevel);
                } else {
                    outputProperties.setProperty(OutputKeys.INDENT, "no");
                }
                xmlWriter.setOutputProperties(outputProperties);

                SAXSerializer sax = new SAXSerializer();

                sax.setReceiver(xmlWriter);

                payload.toSAX(context.getBroker(), sax, new Properties());
                osw.flush();

                entity = new ByteArrayRequestEntity(baos.toByteArray(), "application/xml; charset=utf-8");
            } catch( Exception e ) {
                throw new XPathException(this, e);
            }

        } else if( Type.subTypeOf( payload.getType(), Type.BASE64_BINARY ) ) { 

        	entity = new ByteArrayRequestEntity(payload.toJavaObject(byte[].class));
        	
        } else {

            try {
                entity = new StringRequestEntity( payload.getStringValue(), "text/text; charset=utf-8", "UTF-8" );
            }
            catch( UnsupportedEncodingException uee ) {
                uee.printStackTrace();
            }
        }


        //setup PUT request
        PutMethod     put    = new PutMethod( url );

        put.setRequestEntity( entity );

        //setup PUT Request Headers
        if( !args[3].isEmpty() ) {
            setHeaders( put, ( ( NodeValue )args[3].itemAt( 0 ) ).getNode() );
        }

        try {

            //execute the request
            response = doRequest(context, put, persistState, null, null);

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
