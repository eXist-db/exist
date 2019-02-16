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
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.Optional;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class GetCookieNames extends RequestFunction {

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

	public GetCookieNames(final XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Optional<RequestWrapper> request)
			throws XPathException {
		return request.map(this::getCookieNames)
				.orElse(Sequence.EMPTY_SEQUENCE);
	}

	private Sequence getCookieNames(final RequestWrapper request) {
		final Cookie[] cookies = request.getCookies();
		if (cookies != null && cookies.length > 0) {
			final ValueSequence names = new ValueSequence();
			for (final Cookie cookie : cookies) {
				names.add(new StringValue(cookie.getName()));
			}
			return names;
		} else {
			return Sequence.EMPTY_SEQUENCE;
		}
	}
}
