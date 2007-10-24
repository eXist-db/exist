/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
 *  $Id: SetCurrentUser.java 800 2004-10-31 18:11:45 +0000 (Sun, 31 Oct 2004) wolfgang_m $
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.security.SecurityManager;
import org.exist.security.User;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class SetCurrentUser extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("set-current-user", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Change the user identity for the current HTTP session. Subsequent XQueries in the session will run with the " +
			"new user identity.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("set-current-user", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Change the user identity for the current HTTP session. Subsequent XQueries in the session will run with the " +
			"new user identity.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE),
			"Moved to session module. See session:set-current-user.");
	
	public SetCurrentUser(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		// request object is read from global variable $session
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("No request object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(value.getObject() instanceof RequestWrapper)
		{
			RequestWrapper request = (RequestWrapper)value.getObject();
		
			//get the username and password parameters
			String userName = args[0].getStringValue();
			String passwd = args[1].getStringValue();
			
			//try and validate the user and password
			SecurityManager security = context.getBroker().getBrokerPool().getSecurityManager();
			User user = security.getUser(userName);
			if (user == null)
				return Sequence.EMPTY_SEQUENCE;
			if (user.validate(passwd))
			{
				//validated user, store in session
				context.getBroker().setUser(user);
				SessionWrapper session = request.getSession(true);
				session.setAttribute("user", userName);
				session.setAttribute("password", new StringValue(passwd));
				return BooleanValue.TRUE;
			}
			else
			{
				LOG.warn("Could not validate user " + userName);
				return BooleanValue.FALSE;
			}
		}
		else
		{
			throw new XPathException("Variable $request is not bound to a Request object.");
		}
	}

}
