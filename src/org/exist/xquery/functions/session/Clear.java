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
 *  $Id: SessionAttributes.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.session;

import java.util.Enumeration;

import org.exist.dom.QName;
import org.exist.http.servlets.SessionWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class Clear extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"clear",
				SessionModule.NAMESPACE_URI,
				SessionModule.PREFIX),
			"Removes all attributes from the current HTTP session. Does NOT invalidate the session.",
			null,
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

	/**
	 * @param context
	 */
	public Clear(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		SessionModule myModule = (SessionModule) context.getModule(SessionModule.NAMESPACE_URI);

		//session object is read from global variable $session
		Variable var = myModule.resolveVariable(SessionModule.SESSION_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("Session not set");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $session is not bound to an Java object.");
		JavaObjectValue session = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(session.getObject() instanceof SessionWrapper)
		{
			SessionWrapper sessionWrapper = (SessionWrapper)session.getObject();
			for(Enumeration e = sessionWrapper.getAttributeNames(); e.hasMoreElements();)
			{
				String attribName = (String) e.nextElement();
				sessionWrapper.removeAttribute(attribName);
			}
			return Sequence.EMPTY_SEQUENCE;
		}
		else
			throw new XPathException("Type error: variable $session is not bound to a session object");
	}
}
