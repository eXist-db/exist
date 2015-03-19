/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2011 The eXist Project
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Returns an attribute stored in the current session or an empty sequence
 * if the attribute does not exist.
 * 
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class GetAttribute extends Function 
{
//	private static final Logger logger = LogManager.getLogger(GetAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "get-attribute", SessionModule.NAMESPACE_URI, SessionModule.PREFIX ),
			"Returns an attribute stored in the current session object or an empty sequence " +
			"if the attribute cannot be found.",
			new SequenceType[] {
				new FunctionParameterSequenceType( "name", Type.STRING, Cardinality.EXACTLY_ONE, "The session attribute name" )
			},
			new FunctionReturnSequenceType( Type.ITEM, Cardinality.ZERO_OR_MORE, "the attribute value" ) );
	
	public GetAttribute( XQueryContext context ) 
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
			return( Sequence.EMPTY_SEQUENCE );
		}
		
		if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
			throw( new XPathException( this, "Variable $session is not bound to a Java object." ) );
		}

		final JavaObjectValue session = (JavaObjectValue)var.getValue().itemAt( 0 );
		
		// get attribute name parameter
		final String attribName = getArgument( 0 ).eval( contextSequence, contextItem ).getStringValue();
		
		if( session.getObject() instanceof SessionWrapper ) {
			try {
				final Object o = ( (SessionWrapper)session.getObject() ).getAttribute( attribName );
				if( o == null ) {
					return( Sequence.EMPTY_SEQUENCE );
				}
				return( XPathUtil.javaObjectToXPath( o, context ) );
			}
			catch( final IllegalStateException ise ) {
				//TODO: if we throw an exception here it means that getAttribute()
				//cannot be called after invalidate() on the session object. This is the 
				//way that it works in Java, however this isnt the way it works in xquery currently
				//we can change this but we need to be aware of the consequences, the eXist admin webapp is a
				//good example of what happens if you change this - try logging out of the webapp ;-)
				// - deliriumsky
				
				//log.error(ise.getStackTrace());	
				//throw new XPathException(this, "Session has an IllegalStateException for getAttribute() - " + ise.getStackTrace() + System.getProperty("line.separator") + System.getProperty("line.separator") + "Did you perhaps call session:invalidate() previously?");

				return( Sequence.EMPTY_SEQUENCE );
			}
		} else {
			throw( new XPathException( this, "Type error: variable $session is not bound to a session object" ) );
		}
	}
}
