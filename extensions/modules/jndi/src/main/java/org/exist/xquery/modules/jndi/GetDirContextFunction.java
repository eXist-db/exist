/*
 *  eXist SQL Module Extension GetConnectionFunction
 *  Copyright (C) 2008-09 Adam Retter <adam@exist-db.org>
 *  www.adamretter.co.uk
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

package org.exist.xquery.modules.jndi;


import java.util.Properties;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.util.ParametersExtractor;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * eXist JNDI Module Extension GetDirContextFunction
 * 
 * Get a connection to a JNDI Directory
 * 
 * @author <a href="mailto:andrzej@chaeron.com">Andrzej Taramina</a>
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */
public class GetDirContextFunction extends BasicFunction 
{
	protected static final Logger logger = LogManager.getLogger(GetDirContextFunction.class);

	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "get-dir-context", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
					"Opens a JNDI Directory Context.",
					new SequenceType[] {
							new FunctionParameterSequenceType( "properties", Type.ELEMENT, Cardinality.ZERO_OR_ONE, "The JNDI Directory Context environment properties to be set in the form <properties><property name=\"\" value=\"\"/></properties>." ) 
					},
					new FunctionReturnSequenceType( Type.LONG, Cardinality.ZERO_OR_ONE, "the directory context handle" ) )
			};

	public GetDirContextFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

	@Override
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		// Were properties specified
		if( args[0].isEmpty() ) {
			return( Sequence.EMPTY_SEQUENCE );
		}

		try {
			DirContext dirCtx = null;

			Properties env = ParametersExtractor.parseProperties( ( (NodeValue)args[0].itemAt( 0 ) ).getNode() );
			
			dirCtx = new InitialDirContext( env );

			// store the JNDI Directory Context and return the uid handle of the context
			
			return( new IntegerValue( JNDIModule.storeJNDIContext( context, dirCtx ) ) );
		}
		catch( NamingException ne ) {
			logger.error( "jndi:get-dir-context() Cannot get JNDI directory context: ", ne );
			throw( new XPathException( this, "jndi:get-dir-context() Cannot get JNDI directory context: " + ne ) );
		}
	}
}
