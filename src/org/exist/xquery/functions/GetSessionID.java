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
package org.exist.xquery.functions.request;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;

/**
 * Returns the ID of the current session or an empty sequence
 * if there is no session.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class GetSessionID extends Function
{

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("get-session-id", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the ID of the current session or an empty sequence if there is no session.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
	);
		
	public GetSessionID(XQueryContext context)
	{
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		RequestModule myModule = (RequestModule)context.getModule(RequestModule.NAMESPACE_URI);
		
		/* session object is read from global variable $session */
		
		//get the request variable
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		
		//is the Request variable ok?
		if(var == null || var.getValue() == null)
			throw new XPathException("Request object not found");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");
		
		//get an ObjectValue from the request variable
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		//is the ObjectValue's object an instance of RequestWrapper (i.e. is it the right type)
		if(value.getObject() instanceof RequestWrapper)
		{
			//get the request
			RequestWrapper request = (RequestWrapper)value.getObject();
			//get the session from the request
			SessionWrapper session = request.getSession(false);
			
			//Is there a session?
			if(session == null)
			{
				return(Sequence.EMPTY_SEQUENCE);
			}
			else
			{
				//return the ID of the Session
				return(new StringValue(session.getId()));
			}
		}
		else
		{
			throw new XPathException("Type error: variable $request is not bound to a request object");
		}
	}
}
