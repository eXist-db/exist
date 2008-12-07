/*
 *  eXist SQL Module Extension GetConnectionFunction
 *  Copyright (C) 2008 Adam Retter <adam@exist-db.org>
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
 *  $Id: GetConnectionFunction.java 4126 2006-09-18 21:20:17 +0000 (Mon, 18 Sep 2006) deliriumsky $
 */

package org.exist.xquery.modules.jndi;


import java.util.Properties;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
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
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @serial 2008-12-02
 * @version 1.0
 * 
 * @see org.exist.xquery.BasicFunction#BasicFunction(org.exist.xquery.XQueryContext,
 *      org.exist.xquery.FunctionSignature)
 */

public class GetDirContextFunction extends BasicFunction 
{
	public final static FunctionSignature[] signatures = {
			
			new FunctionSignature(
					new QName( "get-dir-context", JNDIModule.NAMESPACE_URI, JNDIModule.PREFIX ),
					"Open's a JNDI Directory Context. Expects "
							+ " JNDI Directory Context environment properties to be set in $a in the"
							+ " form <properties><property name=\"\" value=\"\"/></properties>. "
							+ "Returns an xs:long representing the directory context handle.",
					new SequenceType[] {
							new SequenceType( Type.ELEMENT, Cardinality.ZERO_OR_ONE ) 
					},
					new SequenceType( Type.LONG, Cardinality.ZERO_OR_ONE ) )
			};

	/**
	 * GetDirContextFunction Constructor
	 * 
	 * @param context
	 *            The Context of the calling XQuery
	 */
	
	public GetDirContextFunction( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

	
	/**
	 * evaluate the call to the xquery get-dir-context() function, it is really
	 * the main entry point of this class
	 * 
	 * @param args				arguments from the get-connection() function call
	 * @param contextSequence 	the Context Sequence to operate on (not used here internally!)
	 * @return 					A xs:long representing a handle to the connection
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		// Were properties specified
		if( args[0].isEmpty() ) {
			return( Sequence.EMPTY_SEQUENCE );
		}

		try {
			DirContext dirCtx = null;

			Properties env = ModuleUtils.parseProperties( ( (NodeValue)args[0].itemAt( 0 ) ).getNode() );
			
			dirCtx = new InitialDirContext( env );

			// store the JNDI Directory Context and return the uid handle of the context
			
			return( new IntegerValue( JNDIModule.storeJNDIContext( context, dirCtx ) ) );
		}
		catch( NamingException ne ) {
			LOG.error( "jndi:get-dir-context() Cannot get JNDI directory context: " + ne );
			throw( new XPathException( getASTNode(), "jndi:get-dir-context() Cannot get JNDI directory context: " + ne ) );
		}
	}
}
