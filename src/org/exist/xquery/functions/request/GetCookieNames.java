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
package org.exist.xquery.functions.request;

import javax.servlet.http.Cookie;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class GetCookieNames extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(GetCookieNames.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-cookie-names",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the names of all Cookies in the request",
			FunctionSignature.NO_ARGS,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "a sequence of the names of all Cookies in the request"));

	public GetCookieNames(XQueryContext context) {
		super(context, signature);
	}

	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		final RequestModule myModule = (RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if (var == null || var.getValue() == null || var.getValue().getItemType() != Type.JAVA_OBJECT) {
			return Sequence.EMPTY_SEQUENCE;
		}

		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper)
		{
			final Cookie[] cookies = ((RequestWrapper)value.getObject()).getCookies();
			if(cookies != null)
			{
				if(cookies.length != 0)
				{
					final ValueSequence names = new ValueSequence();
				
					for(int c = 0; c < cookies.length; c++)
					{
						names.add(new StringValue(cookies[c].getName()));
					}
					
					return names;
				}
			}
			return Sequence.EMPTY_SEQUENCE;
		}
		else
			{throw new XPathException(this, "Variable $request is not bound to a Request object.");}
	}
}
