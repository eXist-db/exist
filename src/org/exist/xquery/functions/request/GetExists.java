/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
 *  $Id: GetExists.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.request;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Andrzej Taramina <andrzej@chaeron.com>
 */
public class GetExists extends BasicFunction 
{

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "exists", RequestModule.NAMESPACE_URI, RequestModule.PREFIX ),
			"Returns whether a request object exists.",
			null,
			new SequenceType( Type.BOOLEAN, Cardinality.EXACTLY_ONE ) );

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
		
		RequestModule myModule = (RequestModule)context.getModule( RequestModule.NAMESPACE_URI );
		
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable( RequestModule.REQUEST_VAR );
		
		if( var == null || var.getValue() == null ) {
			exists = BooleanValue.FALSE;
		} 
			
		return( exists );
	}
	
}
