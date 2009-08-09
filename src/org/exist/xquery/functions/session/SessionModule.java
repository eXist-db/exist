/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-09 The eXist Project
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

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Type;

/**
 * Module function definitions for transform module.
 *
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Adam Retter (adam.retter@devon.gov.uk)
 * @author Loren Cahlander
 * @author ljo
 */
public class SessionModule extends AbstractInternalModule 
{
	public static final String NAMESPACE_URI = "http://exist-db.org/xquery/session";
	public static final String PREFIX = "session";
    public final static String INCLUSION_DATE = "2006-04-09";
    public final static String RELEASED_IN_VERSION = "eXist-1.0";

	public static final QName SESSION_VAR = new QName("session", NAMESPACE_URI, PREFIX);
	
	public static final FunctionDef[] functions = {
		new FunctionDef( Create.signature, Create.class ),
		new FunctionDef( Clear.signature, Clear.class ),
		new FunctionDef( EncodeURL.signature, EncodeURL.class ),
		new FunctionDef( GetID.signature, GetID.class ),
		new FunctionDef( GetAttribute.signature, GetAttribute.class ),
		new FunctionDef( RemoveAttribute.signature, RemoveAttribute.class ),
		new FunctionDef( GetAttributeNames.signature, GetAttributeNames.class ),
		new FunctionDef( Invalidate.signature, Invalidate.class ),
		new FunctionDef( SetAttribute.signature, SetAttribute.class) ,
		new FunctionDef( SetCurrentUser.signature, SetCurrentUser.class ),
		new FunctionDef( GetExists.signature, GetExists.class )
	};
	
	public SessionModule() throws XPathException 
	{
		super( functions );
		// predefined module global variables:
		declareVariable( SESSION_VAR, null );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() 
	{
		return "A module for dealing with the HTTP session."; 
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() 
	{
		return( NAMESPACE_URI );
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() 
	{
		return( PREFIX );
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
	
	/**
	 * Utility method to create a session and store it in the context as a variable
	 
	 * @param context
	 */
	static JavaObjectValue createSession( XQueryContext context, Function fn ) throws XPathException 
	{
		JavaObjectValue ret = null;
		
		RequestModule myModule = (RequestModule)context.getModule( RequestModule.NAMESPACE_URI );
		
		// request object is read from global variable $request
		Variable var = myModule.resolveVariable( RequestModule.REQUEST_VAR );
		
		if( var == null || var.getValue() == null ) {
			throw( new XPathException( fn, "No request object found in the current XQuery context." ) );
		}
	
		if( var.getValue().getItemType() != Type.JAVA_OBJECT ) {
			throw( new XPathException( fn, "Variable $request is not bound to an Java object." ) );
		}

		JavaObjectValue value = (JavaObjectValue)var.getValue().itemAt( 0 );
		
		if( value.getObject() instanceof RequestWrapper ) {
			SessionModule  sessionModule 	= (SessionModule)context.getModule( SessionModule.NAMESPACE_URI );
			SessionWrapper session 			= ((RequestWrapper)value.getObject()).getSession( true );
			
			sessionModule.declareVariable( SessionModule.SESSION_VAR, session );
			ret = (JavaObjectValue)sessionModule.resolveVariable( SessionModule.SESSION_VAR ).getValue().itemAt( 0 );
		} else {
			throw( new XPathException( fn, "Variable $request is not bound to a Request object." ) );
		}
		
		return( ret );
	}

}
