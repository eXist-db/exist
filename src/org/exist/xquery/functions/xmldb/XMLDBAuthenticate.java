/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions.xmldb;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.security.User;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 * @author Andrzej Taramina (andrzej@chaeron.com)
 * @author ljo
 */

public class XMLDBAuthenticate extends BasicFunction {
    private static final Logger logger = Logger.getLogger(XMLDBAuthenticate.class);

	public final static FunctionSignature authenticateSignature =
			new FunctionSignature(
				new QName("authenticate", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Check if the user, $user-id, can authenticate against the database collection $collection-uri. The function simply tries to " +
				"read the collection $collection-uri, using the credentials " +
				"$user-id and $password. " +
				"It returns true if the authentication succeeds, false otherwise.",
				new SequenceType[] {
				    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
				    new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.ZERO_OR_ONE, "The user-id"),
				    new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password")
				},
				new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() on successful authentication, false() otherwise")
			);
	
    public final static FunctionSignature loginSignatures[] = {
		
        new FunctionSignature(
            new QName("login", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Login the user, $user-id, and set it as the owner " +
            "of the currently executing XQuery. " +
			"It returns true if the authentication succeeds, false otherwise. " +
            "If called from a HTTP context the login is cached for the " +
            "lifetime of the HTTP session and may be used for any XQuery " +
            "run in that session. " +
            "If an HTTP session does not already exist, none will be created.",
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.ZERO_OR_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() on successful authentication and owner elevation, false() otherwise")
		),
	
		new FunctionSignature(
            new QName("login", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Login the user, $user-id, and set it as the owner " +
            "of the currently executing XQuery. " +
			"It returns true() if the authentication succeeds, " +
            "false() otherwise. " +
            "If called from a HTTP context the login is cached for the " +
            "lifetime of the HTTP session and may be used for any XQuery" +
            "run in that session. " +
            "$create-session specifies whether to create an HTTP session on " +
            "successful authentication or not. " +
            "If $create-session is false() or the empty sequence no session " + 
            "will be created if one does not already exist.",
            new SequenceType[] {
                new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
                new FunctionParameterSequenceType("user-id", Type.STRING, Cardinality.ZERO_OR_ONE, "The user-id"),
                new FunctionParameterSequenceType("password", Type.STRING, Cardinality.ZERO_OR_ONE, "The password"),
		new FunctionParameterSequenceType("create-session", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "whether to create the session or not on successful authentication, default false()")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() on successful authentication and owner elevation, false() otherwise")
		)
	};
    
	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBAuthenticate( XQueryContext context, FunctionSignature signature ) 
	{
		super( context, signature );
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    public Sequence eval( Sequence[] args, Sequence contextSequence )
        throws XPathException {
        if( args[1].isEmpty() ) {
            return BooleanValue.FALSE;
        }
        
        String uri = args[0].getStringValue();
        String userName = args[1].getStringValue();
        String password = args[2].getStringValue();
		
        boolean createSession = false;
		
        if (args.length > 3) {
            createSession = args[3].effectiveBooleanValue();
        }
        
        XmldbURI targetColl;
		
        if( !uri.startsWith( XmldbURI.XMLDB_SCHEME + ':' ) ) {
            targetColl = XmldbURI.EMBEDDED_SERVER_URI.resolveCollectionPath( XmldbURI.create( uri ) );
        } else {
            targetColl = XmldbURI.create( uri );
        }
        
        try {
            Collection root = DatabaseManager.getCollection( targetColl.toString(), userName, password );
			
            if( root == null ) {
                logger.error("Unable to authenticate user: target collection " + targetColl + " does not exist");
                throw( new XPathException( this, "Unable to authenticate user: target collection " + targetColl + " does not exist" ) );
            }
			
            if( isCalledAs( "login" ) ) {
                UserManagementService ums = (UserManagementService)root.getService( "UserManagementService", "1.0" );
                User user = ums.getUser( userName );
                context.getBroker().setUser( user );
                
                /** if there is a http session cache the user in the http session */
                cacheUserInHttpSession( user, createSession );
            }
			
            return BooleanValue.TRUE;
        } catch (XMLDBException e) {
            return BooleanValue.FALSE;
        }
    }
	
	/**
	 * If there is a HTTP Session, then this will store the user object in the session under the key
	 * defined by XQueryContext.HTTP_SESSIONVAR_XMLDB_USER
	 * 
	 * @param user	The User to cache in the session
	 * @param createSession	Create session?
	 */
	private void cacheUserInHttpSession( User user, boolean createSession ) throws XPathException
	{
		Variable var = getSessionVar( createSession );
		
        if( var != null && var.getValue() != null ) {
    		if( var.getValue().getItemType() == Type.JAVA_OBJECT ) {
        		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
        		
        		if( session.getObject() instanceof SessionWrapper ) {
        			((SessionWrapper)session.getObject()).setAttribute( XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user );
        		}
    		}
    	}
	}
					
					
	/**
	 * Get the HTTP Session variable. Create it if requested and it doesn't exist.
	 * 
	 * @param createSession	Create session?
	 */
	private Variable getSessionVar( boolean createSession ) throws XPathException
	{
		SessionModule sessionModule = (SessionModule)context.getModule( SessionModule.NAMESPACE_URI );
        Variable var = sessionModule.resolveVariable( SessionModule.SESSION_VAR );
		
		if( createSession && ( var == null || var.getValue() == null ) ) {
			SessionWrapper session  = null;
			RequestModule reqModule = (RequestModule)context.getModule( RequestModule.NAMESPACE_URI );
		
			// request object is read from global variable $request
			Variable reqVar = reqModule.resolveVariable( RequestModule.REQUEST_VAR );
			
			if( reqVar == null || reqVar.getValue() == null ) {
			    logger.error("No request object found in the current XQuery context.");

			    throw( new XPathException( this, "No request object found in the current XQuery context." ) );
			}
			if( reqVar.getValue().getItemType() != Type.JAVA_OBJECT ) {
			    logger.error( "Variable $request is not bound to an Java object.");
				throw( new XPathException( this, "Variable $request is not bound to an Java object." ) );
			}
			
			JavaObjectValue reqValue = (JavaObjectValue)reqVar.getValue().itemAt( 0) ;
			
			if( reqValue.getObject() instanceof RequestWrapper ) {
				session = ((RequestWrapper)reqValue.getObject()).getSession( true );
				var = sessionModule.declareVariable( SessionModule.SESSION_VAR, session );
			}
		}
		
		return( var );
	}

}
