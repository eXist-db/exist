/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.request;

import jakarta.servlet.http.Cookie;

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

import java.net.URLDecoder;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

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
		if(request.isEmpty()) {
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
					return new StringValue(this, decode(cookie.getValue()));
				}
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}
	
	/**
	 * Symmetric with {@code HttpResponseWrapper#encodeCookieValue}, which percent-encodes a
	 * cookie's value on write (RFC 6265's cookie-octet grammar excludes bytes >= 0x80, and Jetty
	 * 12 enforces that strictly enough to drop or fail the whole request otherwise -- see
	 * NonAsciiCookieRoundTripTest). A cookie this eXist instance didn't set itself -- from a
	 * browser, or another app sharing the domain -- may not be percent-encoded at all: fall back
	 * to the raw value rather than throwing on a lone {@code '%'} that isn't a valid escape, and
	 * accept that a raw, unescaped {@code '+'} in such a cookie is indistinguishable from an
	 * encoded space and will decode as one (a cookie this instance set itself is unaffected: a
	 * literal {@code '+'} in the original value is escaped to {@code %2B} on write).
	 */
	private String decode(final String value) {
		try {
			return URLDecoder.decode(value, UTF_8);
		} catch (final IllegalArgumentException e) {
			return value;
		}
	}
}
