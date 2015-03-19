/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist team
 *  http://exist-db.org
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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Date;

import javax.xml.datatype.Duration;


/**
 * Set's a HTTP Cookie on the HTTP Response.
 *
 * @author  Adam Retter <adam.retter@devon.gov.uk>
 * @author  José María Fernández (jmfg@users.sourceforge.net)
 * @see     org.exist.xquery.Function
 */
public class SetCookie extends Function
{
    protected static final Logger logger = LogManager.getLogger(SetCookie.class);
	protected static final FunctionParameterSequenceType NAME_PARAM = new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE, "The cookie name");
	protected static final FunctionParameterSequenceType VALUE_PARAM = new FunctionParameterSequenceType("value", Type.STRING, Cardinality.EXACTLY_ONE, "The cookie value");
	protected static final FunctionParameterSequenceType MAX_AGE_PARAM = new FunctionParameterSequenceType("max-age", Type.DURATION, Cardinality.ZERO_OR_ONE, "The xs:duration of the cookie");
	protected static final FunctionParameterSequenceType SECURE_PARAM = new FunctionParameterSequenceType("secure-flag", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "The flag for whether the cookie is to be secure (i.e., only transferred using HTTPS)");
	protected static final FunctionParameterSequenceType DOMAIN_PARAM = new FunctionParameterSequenceType("domain", Type.STRING, Cardinality.ZERO_OR_ONE, "The cookie domain");
	protected static final FunctionParameterSequenceType PATH_PARAM = new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_ONE, "The cookie path");

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sets a HTTP Cookie on the HTTP Response.",
			new SequenceType[] { NAME_PARAM, VALUE_PARAM },
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sets a HTTP Cookie on the HTTP Response.",
			new SequenceType[] { NAME_PARAM, VALUE_PARAM, MAX_AGE_PARAM, SECURE_PARAM },
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("set-cookie", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sets a HTTP Cookie on the HTTP Response.",
			new SequenceType[] { NAME_PARAM, VALUE_PARAM, MAX_AGE_PARAM, SECURE_PARAM, DOMAIN_PARAM, PATH_PARAM },
			new SequenceType(Type.ITEM, Cardinality.EMPTY))
		};

    public SetCookie( XQueryContext context, FunctionSignature signature )
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
        final JavaObjectValue response  = (JavaObjectValue)var.getValue().itemAt( 0 );

        //get parameters
        final String          name      = getArgument( 0 ).eval( contextSequence, contextItem ).getStringValue();
        final String          value     = getArgument( 1 ).eval( contextSequence, contextItem ).getStringValue();

        Sequence        ageSeq    = Sequence.EMPTY_SEQUENCE;
        Sequence        secureSeq = Sequence.EMPTY_SEQUENCE;
        Sequence        domainSeq = Sequence.EMPTY_SEQUENCE;
        Sequence        pathSeq   = Sequence.EMPTY_SEQUENCE;
        int             maxAge    = -1;

        if( getArgumentCount() > 2 ) {
            ageSeq    = getArgument( 2 ).eval( contextSequence, contextItem );
            secureSeq = getArgument( 3 ).eval( contextSequence, contextItem );

            if( !ageSeq.isEmpty() ) {
                final Duration duration = ( (DurationValue)ageSeq.itemAt( 0 ) ).getCanonicalDuration();
                maxAge = (int)( duration.getTimeInMillis( new Date( System.currentTimeMillis() ) ) / 1000L );
            }

            if( getArgumentCount() > 4 ) {
                domainSeq = getArgument( 4 ).eval( contextSequence, contextItem );
                pathSeq   = getArgument( 5 ).eval( contextSequence, contextItem );
            }
        }

        //set response header
        if( response.getObject() instanceof ResponseWrapper ) {

            switch( getArgumentCount() ) {

                case 2: {
                    ( (ResponseWrapper)response.getObject() ).addCookie( name, value );
                    break;
                }

                case 4: {
                    if( secureSeq.isEmpty() ) {
                        ( (ResponseWrapper)response.getObject() ).addCookie( name, value, maxAge );
                    } else {
                        ( (ResponseWrapper)response.getObject() ).addCookie( name, value, maxAge, ( (BooleanValue)secureSeq.itemAt( 0 ) ).effectiveBooleanValue() );
                    }
                    break;
                }

                case 6: {
                    boolean secure = false;
                    String  domain = null;
                    String  path   = null;
                    if( !secureSeq.isEmpty() ) {
                        secure = ( (BooleanValue)secureSeq.itemAt( 0 ) ).effectiveBooleanValue();
                    }
                    if( !domainSeq.isEmpty() ) {
                        domain = domainSeq.itemAt( 0 ).getStringValue();
                    }
                    if( !pathSeq.isEmpty() ) {
                        path = pathSeq.itemAt( 0 ).getStringValue();
                    }
                    ( (ResponseWrapper)response.getObject() ).addCookie( name, value, maxAge, secure, domain, path );
                    break;
                }
            }
        } else {
            throw( new XPathException( this, "Type error: variable $response is not bound to a response object" ) );
        }
        
        return( Sequence.EMPTY_SEQUENCE );
    }
}
