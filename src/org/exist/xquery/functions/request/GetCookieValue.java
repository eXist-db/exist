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
 *  $Id: RequestParameter.java 2895 2006-03-15 19:11:34 +0000 (Wed, 15 Mar 2006) wolfgang_m $
 */
package org.exist.xquery.functions.request;

import javax.servlet.http.Cookie;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class GetCookieValue extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-cookie-value",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the value of the Cookie named in $a.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));

	public GetCookieValue(XQueryContext context) {
		super(context, signature);
	}

	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		
		RequestModule myModule = (RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if (var == null || var.getValue() == null || var.getValue().getItemType() != Type.JAVA_OBJECT)
			return Sequence.EMPTY_SEQUENCE;

		// get the cookieName to match
		String cookieName = args[0].getStringValue();

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper)
		{
			Cookie[] cookies = ((RequestWrapper)value.getObject()).getCookies();
			if(cookies != null)
			{
				for(int c = 0; c < cookies.length; c++)
				{
					if(cookies[c].getName().equals(cookieName))
					{
						return new StringValue(cookies[c].getValue());
					}
				}
			}
			
			return Sequence.EMPTY_SEQUENCE;
		}
		else
			throw new XPathException("Variable $request is not bound to a Request object.");
	}
}
