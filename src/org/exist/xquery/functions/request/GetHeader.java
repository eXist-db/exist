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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XPathUtil;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Alain Pannetier <alain.m.pannetier@gmail.com>
 * 
 * Adjusted and Committed by Adam Retter <adam.retter@devon.gov.uk>
 */

public class GetHeader extends BasicFunction {

	protected static final Logger logger = LogManager.getLogger(GetHeader.class);

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("get-header", RequestModule.NAMESPACE_URI,
					RequestModule.PREFIX),
			"Returns the HTTP request header identified by $header-name. The list of all "
					+ "headers included in the HTTP request are available through the "
					+ "request:get-header-names function.",
			new SequenceType[] { 
				new FunctionParameterSequenceType("header-name", Type.STRING, Cardinality.EXACTLY_ONE, "The HTTP request header name") 
				}, 
				new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the HTTP request header value"));

	public GetHeader(XQueryContext context) {
		super(context, signature);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[],
	 *      org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		final RequestModule myModule = (RequestModule) context
				.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		final Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		if (var == null || var.getValue() == null
				|| var.getValue().getItemType() != Type.JAVA_OBJECT)
			{throw new XPathException(this,
					"Variable $request is not bound to an Java object.");}

		// get parameters
		final String param = args[0].getStringValue();

		final JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		if (value.getObject() instanceof RequestWrapper) {
			final String headerValue = ((RequestWrapper) value.getObject())
					.getHeader(param);
			if (headerValue == null) {
				return Sequence.EMPTY_SEQUENCE;
			} else {
				return XPathUtil.javaObjectToXPath(headerValue, null, false);
			}
		} else
			{throw new XPathException(
					"Variable $request is not bound to a Request object.");}
	}
}