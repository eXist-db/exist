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
package org.exist.xquery.functions.response;

import java.io.IOException;

import org.exist.dom.QName;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class RedirectTo extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("redirect-to", ResponseModule.NAMESPACE_URI, ResponseModule.PREFIX),
			"Sends a HTTP redirect response (302) to the client. Note: this is not supported by the Cocooon " +
			"generator. Use a sitemap redirect instead.",
			new SequenceType[] { new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.ITEM, Cardinality.EMPTY));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName("redirect-to", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Sends a HTTP redirect response (302) to the client. Note: this is not supported by the Cocooon " +
			"generator. Use a sitemap redirect instead.",
			new SequenceType[] { new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.ITEM, Cardinality.EMPTY),
			"Moved to 'response' module.");

	/**
	 * @param context
	 */
	public RedirectTo(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		ResponseModule myModule = (ResponseModule)context.getModule(ResponseModule.NAMESPACE_URI);
		
		String redirectURI = args[0].getStringValue();
		// response object is read from global variable $response
		Variable var = myModule.resolveVariable(ResponseModule.RESPONSE_VAR);
		if(var == null || var.getValue() == null)
			throw new XPathException("No response object found in the current XQuery context.");
		if (var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $response is not bound to an Java object.");

		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof ResponseWrapper)
			try {
				((ResponseWrapper) value.getObject()).sendRedirect(redirectURI);
			} catch (IOException e) {
				throw new XPathException("An IO exception occurred during redirect: " + e.getMessage(), e);
			}
		else
			throw new XPathException("Variable response is not bound to a response object.");
		return Sequence.EMPTY_SEQUENCE;
	}
	
}
