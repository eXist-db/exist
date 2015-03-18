/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 * Set the max inactive interval for the current session
 * 
 * @author José María Fernández (jmfg@users.sourceforge.net)
 */
public class SetMaxInactiveInterval extends Function 
{
//	private static final Logger logger = LogManager.getLogger(GetAttribute.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName( "set-max-inactive-interval", SessionModule.NAMESPACE_URI, SessionModule.PREFIX ),
			"Sets the maximum time interval, in seconds, that the servlet container " +
			"will keep this session open between client accesses. After this interval, " +
			"the servlet container will invalidate the session. A negative time indicates " +
			"the session should never timeout. ",
			new SequenceType[] {
				new FunctionParameterSequenceType("interval", Type.INT, Cardinality.EXACTLY_ONE, "The maximum inactive interval (in seconds) before closing the session")
			},
			new SequenceType( Type.ITEM, Cardinality.EMPTY ) );
	
	public SetMaxInactiveInterval( XQueryContext context ) 
	{
		super( context, signature );
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval( Sequence contextSequence, Item contextItem ) throws XPathException 
	{
		JavaObjectValue session;
		
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
        
		final SessionModule myModule = (SessionModule)context.getModule( SessionModule.NAMESPACE_URI );

		// session object is read from global variable $session
		final Variable var = myModule.resolveVariable( SessionModule.SESSION_VAR );
		
		if( var == null || var.getValue() == null ) {
			// No saved session, so create one
			session = SessionModule.createSession( context, this );
		} else if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
			throw( new XPathException( this, "Variable $session is not bound to a Java object." ) );
		} else {
			session = (JavaObjectValue)var.getValue().itemAt( 0 );
		}
		
		// get attribute name parameter
		final int interval = ((IntegerValue)getArgument(0).eval( contextSequence, contextItem ).convertTo(Type.INT)).getInt();
		
		if( session.getObject() instanceof SessionWrapper ) {
			((SessionWrapper)session.getObject()).setMaxInactiveInterval(interval);
		} else {
			throw( new XPathException( this, "Type error: variable $session is not bound to a session object" ) );
		}

		return( Sequence.EMPTY_SEQUENCE );
	}
}
