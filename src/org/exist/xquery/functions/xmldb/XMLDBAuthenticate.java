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
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
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
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XMLDBAuthenticate extends BasicFunction {

	public final static FunctionSignature authenticateSignature =
			new FunctionSignature(
				new QName("authenticate", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Check if a user is registered as database user. The function simply tries to " +
				"read the database collection specified in the first parameter $a, using the " +
				"supplied username in $b and password in $c. " +
				"It returns true if the attempt succeeds, false otherwise.",
				new SequenceType[] {
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
				new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));
	
    public final static FunctionSignature loginSignature =
        new FunctionSignature(
            new QName("login", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Check if a user is registered as database user and change the user identity for the " +
            "current XQuery script. The function simply tries to " +
            "read the database collection specified in the first parameter $a, using the " +
            "supplied username in $b and password in $c. Contrary to the authenticate function," +
            "login will set the current user for the xquery script to the authenticated user. " +
            "It returns true if the attempt succeeds, false otherwise. If called from a HTTP context" +
            "then the login is cached for the lifetime of the HTTP session and may be used for all XQuery" +
            "scripts in that session.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE));
    
	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBAuthenticate(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		if(args[1].isEmpty())
			return BooleanValue.FALSE;

        String uri = args[0].getStringValue();
		String userName = args[1].getStringValue();
		String password = args[2].getStringValue();

        XmldbURI targetColl;
        if (!uri.startsWith(XmldbURI.XMLDB_SCHEME + ':'))
            targetColl = XmldbURI.EMBEDDED_SERVER_URI.resolveCollectionPath(XmldbURI.create(uri));
        else
            targetColl = XmldbURI.create(uri);
        try {
			Collection root = DatabaseManager.getCollection(targetColl.toString(), userName, password);
            if (root == null)
                throw new XPathException(getASTNode(), "Unable to authenticate user: target collection " + targetColl +
                    " does not exist");
            if (isCalledAs("login")) {
                UserManagementService ums = (UserManagementService) root.getService("UserManagementService", "1.0");
                User user = ums.getUser(userName);
                context.getBroker().setUser(user);
                
                /** if there is a http session cache the user in the http session */
                cacheUserInHttpSession(user);
            }
			return BooleanValue.TRUE;
		} catch (XMLDBException e) {
            if (LOG.isDebugEnabled())
                LOG.debug("Failed to authenticate user '" + userName + "' on " + uri, e);
			return BooleanValue.FALSE;
		}
	}
	
	/**
	 * If there is a HTTP Session, then this will store the user object in the session under the key
	 * defined by XQueryContext.HTTP_SESSIONVAR_XMLDB_USER
	 * 
	 * @param user	The User to cache in the session
	 */
	private void cacheUserInHttpSession(User user) throws XPathException
	{
        SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);
        Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var != null && var.getValue() != null)
		{
    		if(var.getValue().getItemType() == Type.JAVA_OBJECT)
    		{
        		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
        		
        		if(session.getObject() instanceof SessionWrapper)
        		{
        			((SessionWrapper)session.getObject()).setAttribute(XQueryContext.HTTP_SESSIONVAR_XMLDB_USER, user);
        		}
    		}
    	}
	}

}
