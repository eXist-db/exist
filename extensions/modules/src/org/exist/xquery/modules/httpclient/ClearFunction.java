/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010-2013 The eXist-db Project
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;

import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.apache.commons.httpclient.HttpState;


/**
 * Clears HTTP persistent state items
 *
 * @author   Adam Retter <adam.retter@devon.gov.uk>
 * @author   Andrzej Taramina <andrzej@chaeron.com>
 * @version  1.3
 * @serial   20100228
 */

public class ClearFunction extends BaseHTTPClientFunction
{
    protected static final Logger           logger       = LogManager.getLogger( ClearFunction.class );

    public final static FunctionSignature[] signatures   = {
		 new FunctionSignature( 
			new QName( "clear-all", NAMESPACE_URI, PREFIX ), 
			"Clears all persistent state (eg. cookies, credentials, etc.) stored in the current session on the client.", 
			null, 
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) 
			),
			
        new FunctionSignature( 
			new QName( "clear-persistent-cookies", NAMESPACE_URI, PREFIX ), 
			"Clears any persistent cookies stored in the current session on the client.", 
			null, 
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) 
			),
		
		new FunctionSignature( 
			new QName( "clear-persistent-credentials", NAMESPACE_URI, PREFIX ), 
			"Clears any persistent credentials stored in the current session on the client.", 
			null, 
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) 
			),

		new FunctionSignature( 
			new QName( "clear-persistent-proxy-credentials", NAMESPACE_URI, PREFIX ), 
			"Clears any persistent proxy credentials stored in the current session on the client.", 
			null, 
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) 
		)
    };

	
    public ClearFunction( XQueryContext context, FunctionSignature signature  )
    {
        super( context, signature );
    }

	
    public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
    {
		if( isCalledAs( "clear-all" ) ) {
        	context.setXQueryContextVar( HTTP_MODULE_PERSISTENT_STATE, null );
		} else {
			HttpState state = (HttpState)context.getXQueryContextVar( HTTP_MODULE_PERSISTENT_STATE );
			
			if( state != null ) {
				if( isCalledAs( "clear-persistent-cookies" ) ) {
					state.clearCookies();
				} else if( isCalledAs( "clear-persistent-credentials" ) ) {
					state.clearCredentials();
				} else if( isCalledAs( "clear-persistent-proxy-credentials" ) ) {
					state.clearProxyCredentials();
				} 
			}
		}
		
        return( Sequence.EMPTY_SEQUENCE );
    }

}
