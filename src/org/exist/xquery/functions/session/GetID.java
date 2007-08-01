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
 *  $Id: GetSessionID.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.session;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Returns the ID of the current session or an empty sequence
 * if there is no session.
 * 
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class GetID extends Function
{

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("get-id", SessionModule.NAMESPACE_URI, SessionModule.PREFIX),
			"Returns the ID of the current session or an empty sequence if there is no session.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
	);
	
	public final static FunctionSignature deprecated = new FunctionSignature(
			new QName("get-session-id", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the ID of the current session or an empty sequence if there is no session.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
			"Moved to session module and renamed to session:get-id."
	);
	
	public GetID(XQueryContext context)
	{
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		SessionModule myModule = (SessionModule)context.getModule(SessionModule.NAMESPACE_URI);
		
		/* session object is read from global variable $session */
		Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("Session not set");
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");
		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(session.getObject() instanceof SessionWrapper)
		{
			String id = ((SessionWrapper)session.getObject()).getId();
			if (id == null)
				return Sequence.EMPTY_SEQUENCE;
			return(new StringValue(id));
		}
		else
		{
			throw new XPathException("Type error: variable $request is not bound to a request object");
		}
	}
}
