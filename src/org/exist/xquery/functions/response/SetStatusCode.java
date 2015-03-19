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
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * Set's a HTTP server status code on the HTTP Response.
 *
 * @author  Andrzej Taramina <andrzej@chaeron.com>
 * @see     org.exist.xquery.Function
 */
public class SetStatusCode extends Function
{
    protected static final Logger logger = LogManager.getLogger(SetStatusCode.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-status-code", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sets a HTTP server status code on the HTTP Response.",
			new SequenceType[] {
				new FunctionParameterSequenceType("code", Type.INTEGER, Cardinality.EXACTLY_ONE, "The status code")
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY));

    public SetStatusCode( XQueryContext context )
    {
        super( context, signature );
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
     */
    public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException
    {
        if( context.getProfiler().isEnabled() ) {
            context.getProfiler().start( this );
            context.getProfiler().message( this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName( this.getDependencies() ) );

            if( contextSequence != null ) {
                context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence );
            }

            if( contextItem != null ) {
                context.getProfiler().message( this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence() );
            }
        }

        final ResponseModule myModule = (ResponseModule)context.getModule( ResponseModule.NAMESPACE_URI );

        // response object is read from global variable $response
        final Variable       var      = myModule.resolveVariable( ResponseModule.RESPONSE_VAR );

        if( ( var == null ) || ( var.getValue() == null ) ) {
            throw( new XPathException( this, "Response not set" ) );
        }

        if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
            throw( new XPathException( this, "Variable $response is not bound to a Java object." ) );
        } 
        final JavaObjectValue response = (JavaObjectValue)var.getValue().itemAt( 0 );

        //get parameter
        final int             code     = ( (IntegerValue)getArgument( 0 ).eval( contextSequence, contextItem ).convertTo( Type.INTEGER ) ).getInt();

        //set response status code
        if( response.getObject() instanceof ResponseWrapper ) {
            ( (ResponseWrapper)response.getObject() ).setStatusCode( code );
        } else {
            throw( new XPathException( this, "Type error: variable $response is not bound to a response object" ) );
        }

        return( Sequence.EMPTY_SEQUENCE );
    }
}
