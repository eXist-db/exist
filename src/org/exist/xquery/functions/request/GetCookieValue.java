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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * @author Adam Retter (adam.retter@devon.gov.uk)
 */
public class GetCookieValue extends RequestFunction {

	protected static final Logger logger = LogManager.getLogger(GetCookieValue.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-cookie-value",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the value of a named Cookie.",
			new SequenceType[] {
				new FunctionParameterSequenceType("cookie-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the cookie to retrieve the value from.")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the value of the named Cookie"));

	public GetCookieValue(final XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Optional<RequestWrapper> request)
			throws XPathException {
		if(!request.isPresent()) {
			return Sequence.EMPTY_SEQUENCE;
		} else {
			return getCookieValue(args, request.get());
		}
	}

	private Sequence getCookieValue(final Sequence[] args, final RequestWrapper request) throws XPathException {
		final Cookie[] cookies = request.getCookies();
		if(cookies != null) {
			// get the cookieName to match
			final String cookieName = args[0].getStringValue();
			for (final Cookie cookie : cookies) {
				if (cookie.getName().equals(cookieName)) {
					return new StringValue(decode(cookie.getValue()));
				}
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}
	
	// TODO: remove this hack after fixing HTTP 1.1	
	private String decode (final String value) {
        return new String(value.getBytes(ISO_8859_1));
	}
}
