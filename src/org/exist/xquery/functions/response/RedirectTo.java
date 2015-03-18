/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions.response;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;


/**
 * DOCUMENT ME!
 *
 * @author  Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RedirectTo extends BasicFunction
{
    protected static final Logger logger = LogManager.getLogger(RedirectTo.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("redirect-to", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sends a HTTP redirect response (302) to the client. Note: this is not supported by the Cocooon " +
			"generator. Use a sitemap redirect instead.",
			new SequenceType[] { new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI to redirect the client to") },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

    /**
     * Creates a new RedirectTo object.
     *
     * @param  context
     */
    public RedirectTo( XQueryContext context )
    {
        super( context, signature );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
        final ResponseModule myModule    = (ResponseModule)context.getModule( ResponseModule.NAMESPACE_URI );

        final String         redirectURI = args[0].getStringValue();

        // response object is read from global variable $response
        final Variable       var         = myModule.resolveVariable( ResponseModule.RESPONSE_VAR );

        if( ( var == null ) || ( var.getValue() == null ) ) {
            throw( new XPathException( this, "No response object found in the current XQuery context." ) );
        }

        if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
            throw( new XPathException( this, "Variable $response is not bound to an Java object." ) );
        }

        final JavaObjectValue value = (JavaObjectValue)var.getValue().itemAt( 0 );

        if( value.getObject() instanceof ResponseWrapper ) {

            try {
                ( (ResponseWrapper)value.getObject() ).sendRedirect( redirectURI );
            }
            catch( final IOException e ) {
                throw( new XPathException( this, "An IO exception occurred during redirect: " + e.getMessage(), e ) );
            }
        } else {
            throw( new XPathException( this, "Variable response is not bound to a response object." ) );
        }
        
        return( Sequence.EMPTY_SEQUENCE );
    }

}
