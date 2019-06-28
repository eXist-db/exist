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
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:alain.m.pannetier@gmail.com">Alain Pannetier</a>
 * @author <a href="adam.retter@devon.gov.uk">Adam Retter</a>
 */

public class GetHeader extends StrictRequestFunction {

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

	public GetHeader(final XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
			throws XPathException {
		final String param = args[0].getStringValue();
		final String headerValue = request.getHeader(param);
		if (headerValue == null) {
			return Sequence.EMPTY_SEQUENCE;
		} else {
			return XPathUtil.javaObjectToXPath(headerValue, null, false);
		}
	}
}