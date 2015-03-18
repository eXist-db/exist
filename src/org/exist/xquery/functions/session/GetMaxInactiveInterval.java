/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
 *  ixitar@exist-db.org
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

//import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Returns an attribute stored in the current session or an empty sequence
 * if the attribute does not exist.
 * 
 * @author Loren Cahlander
 */
public class GetMaxInactiveInterval extends Function 
{
//	private static final Logger logger = LogManager.getLogger(GetAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "get-max-inactive-interval", SessionModule.NAMESPACE_URI, SessionModule.PREFIX ),
			"Returns the maximum time interval, in seconds, that the servlet container " +
			"will keep this session open between client accesses. After this interval, " +
			"the servlet container will invalidate the session. The maximum time interval " +
			"can be set with the session:set-max-inactive-interval function. A negative time indicates " +
			"the session should never timeout. ",
			null,
			new FunctionReturnSequenceType( Type.INT, Cardinality.EXACTLY_ONE, "the maximum time interval, in seconds" ) );
	
	public GetMaxInactiveInterval( XQueryContext context ) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException 
	{
		
		final SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);
		
		// session object is read from global variable $session
		final Variable var = myModule.resolveVariable( SessionModule.SESSION_VAR );
		
		if( var == null || var.getValue() == null ) {
			// throw( new XPathException( this, "Session not set" ) );
			return( XPathUtil.javaObjectToXPath( Integer.valueOf(-1), context ) );
		}
		
		if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
			throw( new XPathException( this, "Variable $session is not bound to a Java object." ) );
		}

		final JavaObjectValue session = (JavaObjectValue)var.getValue().itemAt( 0 );
		
		if( session.getObject() instanceof SessionWrapper ) {
			try {
				final int interval = ( (SessionWrapper)session.getObject() ).getMaxInactiveInterval();
				return( XPathUtil.javaObjectToXPath( Integer.valueOf(interval), context ) );
			}
			catch( final IllegalStateException ise ) {
				return( XPathUtil.javaObjectToXPath( Integer.valueOf(-1), context ) );
			}
		} else {
			throw( new XPathException( this, "Type error: variable $session is not bound to a session object" ) );
		}
	}
}
