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
package org.exist.xquery.functions.session;

//import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina <andrzej@chaeron.com>
 * @author Loren Cahlander
 */
public class GetExists extends BasicFunction 
{
//	private static final Logger logger = Logger.getLogger(GetExists.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "exists", SessionModule.NAMESPACE_URI, SessionModule.PREFIX ),
			"Returns whether a session object exists.",
			null,
			new FunctionReturnSequenceType( Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the session object exists" ) );

	/**
	 * @param context
	 */
	
	public GetExists( XQueryContext context ) 
	{
		super( context, signature );
	}
	

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	
	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException 
	{
		BooleanValue exists = BooleanValue.TRUE;
		
		final SessionModule myModule = (SessionModule)context.getModule( SessionModule.NAMESPACE_URI );
		
		// session object is read from global variable $session
		final Variable var = myModule.resolveVariable( SessionModule.SESSION_VAR );
		
		if( var == null || var.getValue() == null ) {
			exists = BooleanValue.FALSE;
		} 
			
		return( exists );
	}
	
}
