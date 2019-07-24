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
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.xquery.*;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import javax.annotation.Nonnull;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class GetURI extends StrictRequestFunction {

	protected static final Logger logger = LogManager.getLogger(GetURI.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("get-uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the URI of the current request. This will be the original URI as received from " +
            "the client. Possible modifications done by the URL rewriter will not be visible.",
			null,
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the URI of the request")),
        new FunctionSignature(
			new QName("get-effective-uri", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
			"Returns the URI of the current request. If the request was forwarded via URL rewriting, " +
            "the function returns the effective, rewritten URI, not the original URI which was received " +
            "from the client.",
			null,
			new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the URI of the request"))
    };

	public GetURI(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
			throws XPathException {
		final Object attr = request.getAttribute(XQueryURLRewrite.RQ_ATTR_REQUEST_URI);
		if (attr == null || isCalledAs("get-effective-uri")) {
			return new AnyURIValue(request.getRequestURI());
		} else {
			return new AnyURIValue(attr.toString());
		}
	}
}
