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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.security.AuthenticationException;
import org.exist.security.SecurityManager;
import org.exist.security.Subject;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class SetCurrentUser extends BasicFunction {

	private static final Logger logger = LogManager.getLogger(SetCurrentUser.class);
	
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-current-user", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Change the user identity for the current HTTP session. Subsequent XQueries in the session will run with the " +
			"new user identity.",
			new SequenceType[] {
				new FunctionParameterSequenceType("user-name", Type.STRING, Cardinality.EXACTLY_ONE, "The user name"),
				new FunctionParameterSequenceType("password", Type.STRING, Cardinality.EXACTLY_ONE, "The password")
			},
			new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true if the user name and password represent a valid user"));
	
	public SetCurrentUser(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		
		final RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $session
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			{throw new XPathException(this, "No request object found in the current XQuery context.");}
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this, "Variable $request is not bound to an Java object.");}
		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(value.getObject() instanceof RequestWrapper)
		{
			final RequestWrapper request = (RequestWrapper)value.getObject();
		
			//get the username and password parameters
			final String userName = args[0].getStringValue();
			final String passwd = args[1].getStringValue();
			
			//try and validate the user and password
			final SecurityManager security = context.getBroker().getBrokerPool().getSecurityManager();
			Subject user;
			try {
				user = security.authenticate(userName, passwd);
			} catch (final AuthenticationException e) {
				logger.warn("Could not validate user " + userName + " ["+ e.getMessage() + "]");
				return BooleanValue.FALSE;
			}
//			why it was empty if user wasn't found??? -shabanovd
//			if (user == null)
//				return Sequence.EMPTY_SEQUENCE;

			//validated user, store in session
			context.getBroker().setUser(user);
			final SessionWrapper session = request.getSession(true);
			session.setAttribute("user", userName);
			session.setAttribute("password", new StringValue(passwd));
			return BooleanValue.TRUE;
		}
		else
		{
			throw new XPathException(this, "Variable $request is not bound to a Request object.");
		}
	}

}
